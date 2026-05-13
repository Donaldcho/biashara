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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.biasharaai.ai.LabelProductEnricher
import com.biasharaai.databinding.FragmentLabelScannerBinding
import com.biasharaai.ui.base.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * Full-screen CameraX + ML Kit text-recognition scanner.
 *
 * Mirrors [BarcodeScannerFragment] but captures plain text from a product label / sign /
 * receipt. On a stable detection, runs **Gemma** (when the model is available) to suggest
 * **description** and **category** from the OCR text, then returns **name** ([RESULT_KEY]),
 * optional description ([RESULT_DESCRIPTION_KEY]), and optional category ([RESULT_CATEGORY_KEY])
 * to the previous back-stack entry via [androidx.lifecycle.SavedStateHandle], then pops.
 *
 * All processing is on-device — no internet required.
 */
@AndroidEntryPoint
class LabelScannerFragment : BaseFragment() {

    private var _binding: FragmentLabelScannerBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var labelProductEnricher: LabelProductEnricher

    private lateinit var cameraExecutor: ExecutorService
    private var textAnalyzer: TextAnalyzer? = null

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                showCameraUi()
                startCamera()
            } else {
                showPermissionDeniedUi()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLabelScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.fabClose.setOnClickListener { findNavController().navigateUp() }
        binding.btnGrantPermission.setOnClickListener {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

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
        textAnalyzer = null
        _binding = null
    }

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

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                val provider = cameraProviderFuture.get()
                bindCameraUseCases(provider)
            },
            ContextCompat.getMainExecutor(requireContext()),
        )
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder()
            .build()
            .also { it.surfaceProvider = binding.previewView.surfaceProvider }

        textAnalyzer = TextAnalyzer { text ->
            view?.post { handleTextResult(text) }
        }
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(cameraExecutor, textAnalyzer!!) }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                viewLifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis,
            )
        } catch (e: Exception) {
            Log.e(TAG, "CameraX use-case binding failed", e)
        }
    }

    private fun handleTextResult(text: String) {
        val firstLine = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?: text.trim()
        binding.progressEnrichment.visibility = View.VISIBLE
        binding.fabClose.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val enrichment = labelProductEnricher.enrich(productName = firstLine, fullOcrText = text)
            val prev = findNavController().previousBackStackEntry?.savedStateHandle
            if (prev != null) {
                prev.set(RESULT_KEY, firstLine)
                enrichment.description?.let { prev.set(RESULT_DESCRIPTION_KEY, it) }
                enrichment.category?.let { prev.set(RESULT_CATEGORY_KEY, it) }
            }
            if (isAdded) {
                _binding?.let { b ->
                    b.progressEnrichment.visibility = View.GONE
                    b.fabClose.isEnabled = true
                }
                findNavController().navigateUp()
            }
        }
    }

    companion object {
        private const val TAG = "LabelScannerFragment"

        /** SavedStateHandle key under which the recognized text is delivered to the previous fragment. */
        const val RESULT_KEY = "scanned_label_text"

        /** Gemma-suggested one-line inventory description (optional). */
        const val RESULT_DESCRIPTION_KEY = "scanned_label_description"

        /** Gemma-suggested shelf category in English (optional). */
        const val RESULT_CATEGORY_KEY = "scanned_label_category"
    }
}
