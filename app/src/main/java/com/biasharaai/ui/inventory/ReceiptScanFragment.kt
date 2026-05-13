package com.biasharaai.ui.inventory

import android.Manifest
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.biasharaai.R
import com.biasharaai.databinding.FragmentReceiptScanBinding
import com.biasharaai.receipt.ReceiptParser
import com.biasharaai.ui.base.BaseFragment
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * Still-image receipt capture (CameraX [ImageCapture]) — Prompt U4.
 */
@AndroidEntryPoint
class ReceiptScanFragment : BaseFragment() {

    private var _binding: FragmentReceiptScanBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var receiptParser: ReceiptParser

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

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
        _binding = FragmentReceiptScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.fabClose.setOnClickListener { findNavController().navigateUp() }
        binding.btnGrantPermission.setOnClickListener {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        binding.btnCapture.setOnClickListener { captureReceipt() }

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
        imageCapture = null
        _binding = null
    }

    private fun showCameraUi() {
        binding.previewView.visibility = View.VISIBLE
        binding.documentFrame.visibility = View.VISIBLE
        binding.cornerTl.visibility = View.VISIBLE
        binding.cornerTr.visibility = View.VISIBLE
        binding.cornerBl.visibility = View.VISIBLE
        binding.cornerBr.visibility = View.VISIBLE
        binding.btnCapture.visibility = View.VISIBLE
        binding.permissionDeniedGroup.visibility = View.GONE
    }

    private fun showPermissionDeniedUi() {
        binding.previewView.visibility = View.GONE
        binding.documentFrame.visibility = View.GONE
        binding.cornerTl.visibility = View.GONE
        binding.cornerTr.visibility = View.GONE
        binding.cornerBl.visibility = View.GONE
        binding.cornerBr.visibility = View.GONE
        binding.btnCapture.visibility = View.GONE
        binding.permissionDeniedGroup.visibility = View.VISIBLE
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                bindCamera(cameraProvider)
            },
            ContextCompat.getMainExecutor(requireContext()),
        )
    }

    private fun bindCamera(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder()
            .build()
            .also { it.surfaceProvider = binding.previewView.surfaceProvider }

        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        imageCapture = capture

        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                viewLifecycleOwner,
                selector,
                preview,
                capture,
            )
        } catch (_: Exception) {
            Snackbar.make(binding.root, R.string.receipt_camera_bind_failed, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun captureReceipt() {
        val capture = imageCapture ?: return
        binding.btnCapture.isEnabled = false
        binding.progressProcessing.visibility = View.VISIBLE

        val file = File(requireContext().cacheDir, "receipt_cap_${System.currentTimeMillis()}.jpg")
        val opts = ImageCapture.OutputFileOptions.Builder(file).build()

        capture.takePicture(
            opts,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    view?.post {
                        if (!isAdded) {
                            file.delete()
                            return@post
                        }
                        viewLifecycleOwner.lifecycleScope.launch {
                            try {
                                val bmp = withContext(Dispatchers.IO) {
                                    BitmapFactory.decodeFile(file.absolutePath).also { file.delete() }
                                }
                                if (bmp == null) {
                                    if (isAdded && _binding != null) {
                                        binding.progressProcessing.visibility = View.GONE
                                        binding.btnCapture.isEnabled = true
                                    }
                                    goReview(fallback = true, json = "[]")
                                    return@launch
                                }
                                val result = withContext(Dispatchers.IO) {
                                    receiptParser.parseReceipt(bmp)
                                }
                                if (isAdded && _binding != null) {
                                    binding.progressProcessing.visibility = View.GONE
                                    binding.btnCapture.isEnabled = true
                                }
                                when (result) {
                                    is ReceiptParser.ParseResult.Success ->
                                        goReview(fallback = false, json = Gson().toJson(result.items))
                                    ReceiptParser.ParseResult.ManualFallback ->
                                        goReview(fallback = true, json = "[]")
                                }
                            } catch (_: Exception) {
                                if (isAdded && _binding != null) {
                                    binding.progressProcessing.visibility = View.GONE
                                    binding.btnCapture.isEnabled = true
                                }
                                goReview(fallback = true, json = "[]")
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    view?.post {
                        file.delete()
                        if (isAdded && _binding != null) {
                            binding.progressProcessing.visibility = View.GONE
                            binding.btnCapture.isEnabled = true
                            goReview(fallback = true, json = "[]")
                        }
                    }
                }
            },
        )
    }

    private fun goReview(fallback: Boolean, json: String) {
        findNavController().navigate(
            R.id.action_receiptScanFragment_to_receiptReviewFragment,
            bundleOf(
                ARG_LINES_JSON to json,
                ARG_FALLBACK to fallback,
            ),
        )
    }

    companion object {
        const val ARG_LINES_JSON = "lines_json"
        const val ARG_FALLBACK = "fallback_mode"
    }
}
