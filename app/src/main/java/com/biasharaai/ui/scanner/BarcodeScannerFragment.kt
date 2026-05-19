package com.biasharaai.ui.scanner

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.biasharaai.R
import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.databinding.FragmentBarcodeScannerBinding
import com.biasharaai.productline.ProductLineManager
import com.biasharaai.service.ServiceTokenCodec
import com.biasharaai.ui.base.BaseFragment
import com.biasharaai.ui.inventory.InventoryListFragment
import com.biasharaai.ui.pos.PosFragment
import com.biasharaai.ui.pos.ServiceReceiptScanFragment
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * Full-screen barcode / QR code scanner using CameraX + ML Kit.
 *
 * All barcode detection runs **on-device** — no internet required.
 *
 * Supports three [ScanMode]s passed via the `scan_mode` navigation argument:
 * - **SCAN_FOR_LOOKUP** — look up a product by barcode; if found, navigate to edit.
 * - **SCAN_TO_ADD** — capture the barcode value and pass it to the new-product form.
 * - **SCAN_TO_RECORD_SALE** — pass the barcode to the POS / sale flow (future prompt).
 */
@AndroidEntryPoint
class BarcodeScannerFragment : BaseFragment() {

    private var _binding: FragmentBarcodeScannerBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var productDao: ProductDao

    @Inject
    lateinit var productLineManager: ProductLineManager

    private lateinit var cameraExecutor: ExecutorService
    private var barcodeAnalyzer: BarcodeAnalyzer? = null

    /** Resolve [ScanMode] from the navigation argument, defaulting to LOOKUP. */
    private val scanMode: ScanMode by lazy {
        val name = arguments?.getString(ARG_SCAN_MODE) ?: ScanMode.SCAN_FOR_LOOKUP.name
        try {
            ScanMode.valueOf(name)
        } catch (_: IllegalArgumentException) {
            ScanMode.SCAN_FOR_LOOKUP
        }
    }

    // ── Permission handling ─────────────────────────────────────────────

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                showCameraUi()
                startCamera()
            } else {
                showPermissionDeniedUi()
            }
        }

    // ── Lifecycle ───────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentBarcodeScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.fabClose.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnGrantPermission.setOnClickListener {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // Check permission immediately
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            showCameraUi()
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        barcodeAnalyzer = null
        _binding = null
    }

    // ── UI states ───────────────────────────────────────────────────────

    private fun showCameraUi() {
        binding.previewView.visibility = View.VISIBLE
        binding.viewfinderOverlay.visibility = View.VISIBLE
        binding.viewfinderBorder.visibility = View.VISIBLE
        binding.textScanHint.visibility = View.VISIBLE
        binding.permissionDeniedGroup.visibility = View.GONE
    }

    private fun showPermissionDeniedUi() {
        binding.previewView.visibility = View.GONE
        binding.viewfinderOverlay.visibility = View.GONE
        binding.viewfinderBorder.visibility = View.GONE
        binding.textScanHint.visibility = View.GONE
        binding.permissionDeniedGroup.visibility = View.VISIBLE
    }

    // ── CameraX ─────────────────────────────────────────────────────────

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                if (_binding == null) return@addListener
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    bindCameraUseCases(cameraProvider)
                } catch (e: Exception) {
                    Log.e(TAG, "Camera provider failed", e)
                }
            },
            ContextCompat.getMainExecutor(requireContext()),
        )
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        val binding = _binding ?: return
        // Preview
        val preview = Preview.Builder()
            .build()
            .also { it.surfaceProvider = binding.previewView.surfaceProvider }

        // Image analysis with BarcodeAnalyzer
        barcodeAnalyzer = BarcodeAnalyzer { rawValue ->
            // Called on the analysis thread — dispatch to main for navigation/UI
            view?.post { handleBarcodeResult(rawValue) }
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(cameraExecutor, barcodeAnalyzer!!) }

        // Select back camera
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                viewLifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis,
            )
        } catch (e: Exception) {
            Log.e(TAG, "CameraX use-case binding failed", e)
        }
    }

    // ── Result handling ─────────────────────────────────────────────────

    private fun handleBarcodeResult(rawValue: String) {
        if (BarcodeScanRouter.isServiceToken(rawValue)) {
            if (!productLineManager.isProEnabled()) {
                val root = _binding?.root
                if (root != null) {
                    Snackbar.make(root, R.string.scanner_service_token_shop, Snackbar.LENGTH_LONG).show()
                    barcodeAnalyzer?.reset()
                }
                return
            }
            if (rawValue.startsWith(ServiceTokenCodec.PREFIX_RECEIPT) &&
                rawValue.substringAfter(ServiceTokenCodec.PREFIX_RECEIPT).contains('.')
            ) {
                findNavController().navigate(
                    R.id.serviceReceiptScanFragment,
                    ServiceReceiptScanFragment.args(rawValue),
                )
                return
            }
            if (arguments?.getBoolean(ARG_RETURN_BARCODE_TO_POS, false) == true) {
                returnBarcodeToPos(rawValue)
                return
            }
        }
        when (scanMode) {
            ScanMode.SCAN_FOR_LOOKUP -> lookupProduct(rawValue)
            ScanMode.SCAN_TO_ADD -> {
                if (arguments?.getBoolean(ARG_RETURN_BARCODE_TO_POS, false) == true) {
                    returnBarcodeToPos(rawValue)
                } else {
                    navigateToAddWithBarcode(rawValue)
                }
            }
            ScanMode.SCAN_TO_RECORD_SALE -> navigateToSaleWithBarcode(rawValue)
        }
    }

    /**
     * SCAN_FOR_LOOKUP: query Room for an existing product by barcode.
     * If found → navigate to edit. If not → show a Snackbar offering to add it.
     */
    private fun lookupProduct(barcodeValue: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val product = productDao.getProductByBarcode(barcodeValue).firstOrNull()
            val root = _binding?.root ?: return@launch
            if (product != null) {
                // Found – navigate to AddEditProductFragment with the product id
                findNavController().navigate(
                    R.id.action_barcodeScannerFragment_to_addEditProductFragment,
                    bundleOf(InventoryListFragment.ARG_PRODUCT_ID to product.id),
                )
            } else {
                // Not found – show Snackbar with option to add
                Snackbar.make(
                    root,
                    getString(R.string.scanner_product_not_found),
                    Snackbar.LENGTH_LONG,
                ).setAction(getString(R.string.scanner_add_it)) {
                    navigateToAddWithBarcode(barcodeValue)
                }.addCallback(object : Snackbar.Callback() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        // Allow re-scanning after Snackbar is dismissed (if user didn't tap "Add")
                        if (event != DISMISS_EVENT_ACTION) {
                            barcodeAnalyzer?.reset()
                        }
                    }
                }).show()
            }
        }
    }

    /**
     * POS scan slot: return the raw barcode to [PosFragment] via the previous entry’s
     * [androidx.lifecycle.SavedStateHandle], then pop the scanner.
     */
    private fun returnBarcodeToPos(barcodeValue: String) {
        findNavController().previousBackStackEntry?.savedStateHandle?.set(
            PosFragment.RESULT_KEY_SCANNED_BARCODE,
            barcodeValue,
        )
        findNavController().navigateUp()
    }

    /**
     * SCAN_TO_ADD: navigate straight to the new-product form pre-filled with the barcode.
     */
    private fun navigateToAddWithBarcode(barcodeValue: String) {
        findNavController().navigate(
            R.id.action_barcodeScannerFragment_to_addEditProductFragment,
            bundleOf(
                InventoryListFragment.ARG_PRODUCT_ID to 0L,
                ARG_BARCODE_VALUE to barcodeValue,
            ),
        )
    }

    /**
     * SCAN_TO_RECORD_SALE: navigate to the sales/POS screen (future prompt).
     * For now, fall back to lookup behaviour.
     */
    private fun navigateToSaleWithBarcode(barcodeValue: String) {
        // TODO(Prompt 6+): navigate to POS / sale recording screen
        // For now, fall back to product lookup
        lookupProduct(barcodeValue)
    }

    companion object {
        private const val TAG = "BarcodeScannerFragment"

        /** Navigation argument key: scan mode (one of [ScanMode] names). */
        const val ARG_SCAN_MODE = "scan_mode"

        /** Navigation argument key: barcode value passed to the next destination. */
        const val ARG_BARCODE_VALUE = "barcode_value"

        /**
         * When `true` with [ScanMode.SCAN_TO_ADD], the scanned value is posted to
         * [PosFragment]’s SavedStateHandle ([PosFragment.RESULT_KEY_SCANNED_BARCODE]) and the
         * scanner pops — instead of opening [com.biasharaai.ui.inventory.AddEditProductFragment].
         */
        const val ARG_RETURN_BARCODE_TO_POS = "return_barcode_to_pos"
    }
}
