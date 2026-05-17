package com.biasharaai.ui.cash

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.biasharaai.R
import com.biasharaai.data.local.db.LedgerDirection
import com.biasharaai.databinding.FragmentCashScanBinding
import com.biasharaai.ui.base.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@AndroidEntryPoint
class CashScanFragment : BaseFragment() {

    private var _binding: FragmentCashScanBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CashScanViewModel by viewModels()

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) { showCameraUi(); startCamera() } else showPermissionDeniedUi()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCashScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        val direction = arguments?.getString(ARG_DIRECTION)
            ?.let { runCatching { LedgerDirection.valueOf(it) }.getOrDefault(LedgerDirection.MONEY_IN) }
            ?: LedgerDirection.MONEY_IN
        viewModel.direction = direction

        binding.tvDirection.text = if (direction == LedgerDirection.MONEY_IN)
            getString(R.string.cash_confirm_direction_in)
        else
            getString(R.string.cash_confirm_direction_out)
        binding.cardDirection.setCardBackgroundColor(
            ContextCompat.getColor(
                requireContext(),
                if (direction == LedgerDirection.MONEY_IN) R.color.biashara_success_green
                else R.color.biashara_red,
            ),
        )

        binding.fabClose.setOnClickListener { findNavController().navigateUp() }
        binding.btnGrantPermission.setOnClickListener {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        binding.btnCapture.setOnClickListener { captureDocument() }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val b = _binding ?: return@collect
                    when (state) {
                        is CashScanUiState.Scanning -> {
                            b.progressProcessing.visibility = View.VISIBLE
                            b.tvScanStatus.visibility = View.VISIBLE
                            b.tvScanStatus.text = getString(R.string.cash_scan_processing)
                            b.btnCapture.isEnabled = false
                        }
                        is CashScanUiState.QrFound -> {
                            b.progressProcessing.visibility = View.GONE
                            b.tvScanStatus.visibility = View.GONE
                            findNavController().navigate(
                                R.id.action_cashScanFragment_to_confirmationFragment,
                                bundleOf(
                                    ConfirmationFragment.ARG_DIRECTION to state.payload.direction.name,
                                    ConfirmationFragment.ARG_CATEGORY_TYPE to state.payload.entryType.name,
                                    ConfirmationFragment.ARG_QR_LABEL to state.payload.label,
                                    ConfirmationFragment.ARG_CAPTURE_METHOD to "QR_CODE",
                                ),
                            )
                            viewModel.resetToIdle()
                        }
                        is CashScanUiState.Confirming -> {
                            b.progressProcessing.visibility = View.GONE
                            b.tvScanStatus.visibility = View.GONE
                            findNavController().navigate(
                                R.id.action_cashScanFragment_to_confirmationFragment,
                                bundleOf(
                                    ConfirmationFragment.ARG_DIRECTION to state.direction.name,
                                    ConfirmationFragment.ARG_PARSED_AMOUNT to (state.parsed.amount?.toString() ?: ""),
                                    ConfirmationFragment.ARG_PARSED_REFERENCE to (state.parsed.reference ?: ""),
                                    ConfirmationFragment.ARG_PARSED_COUNTERPARTY to (state.parsed.counterparty ?: ""),
                                    ConfirmationFragment.ARG_RAW_TEXT to state.rawText,
                                    ConfirmationFragment.ARG_PROOF_TYPE to state.parsed.proofType.name,
                                    ConfirmationFragment.ARG_CONFIDENCE to state.parsed.confidence,
                                    ConfirmationFragment.ARG_CAPTURE_METHOD to "CAMERA_OCR",
                                    ConfirmationFragment.ARG_PARSER_ENGINE to state.parsed.engine.name,
                                ),
                            )
                            viewModel.resetToIdle()
                        }
                        is CashScanUiState.Error -> {
                            b.progressProcessing.visibility = View.GONE
                            b.tvScanStatus.visibility = View.GONE
                            b.btnCapture.isEnabled = true
                        }
                        else -> Unit
                    }
                }
            }
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            showCameraUi()
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Camera is unbound automatically — bindToLifecycle(viewLifecycleOwner, …) ties it to
        // this view's lifecycle, so CameraX cleans up when the view is destroyed.
        cameraExecutor.shutdown()
        imageCapture = null
        _binding = null
    }

    private fun showCameraUi() {
        val b = _binding ?: return
        b.previewView.visibility = View.VISIBLE
        b.documentFrame.visibility = View.VISIBLE
        b.btnCapture.visibility = View.VISIBLE
        b.permissionDeniedGroup.visibility = View.GONE
    }

    private fun showPermissionDeniedUi() {
        val b = _binding ?: return
        b.previewView.visibility = View.GONE
        b.documentFrame.visibility = View.GONE
        b.btnCapture.visibility = View.GONE
        b.permissionDeniedGroup.visibility = View.VISIBLE
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(requireContext())
        future.addListener(
            {
                if (_binding == null) return@addListener
                runCatching { bindCamera(future.get()) }
                    .onFailure { Log.e(TAG, "Camera bind failed", it) }
            },
            ContextCompat.getMainExecutor(requireContext()),
        )
    }

    private fun bindCamera(cameraProvider: ProcessCameraProvider) {
        val b = _binding ?: return
        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(1280, 720),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                ),
            )
            .build()

        val preview = Preview.Builder().build()
            .also { it.surfaceProvider = b.previewView.surfaceProvider }

        val capture = ImageCapture.Builder()
            .setResolutionSelector(resolutionSelector)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        imageCapture = capture

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
    }

    private fun captureDocument() {
        val capture = imageCapture ?: return
        val b = _binding ?: return
        b.btnCapture.isEnabled = false
        b.progressProcessing.visibility = View.VISIBLE

        val file = File(requireContext().cacheDir, "cash_cap_${System.currentTimeMillis()}.jpg")
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    view?.post {
                        if (!isAdded || _binding == null) { file.delete(); return@post }
                        viewLifecycleOwner.lifecycleScope.launch {
                            val bmp = withContext(Dispatchers.IO) {
                                BitmapFactory.decodeFile(file.absolutePath).also { file.delete() }
                            }
                            if (bmp == null) {
                                _binding?.progressProcessing?.visibility = View.GONE
                                _binding?.btnCapture?.isEnabled = true
                                return@launch
                            }
                            viewModel.onImageCaptured(bmp)
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    file.delete()
                    view?.post {
                        if (!isAdded || _binding == null) return@post
                        _binding?.progressProcessing?.visibility = View.GONE
                        _binding?.btnCapture?.isEnabled = true
                    }
                }
            },
        )
    }

    companion object {
        private const val TAG = "CashScanFragment"
        const val ARG_DIRECTION = "direction"
    }
}
