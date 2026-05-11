package com.biasharaai.ui.inventory

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.biasharaai.R
import com.biasharaai.ai.AudioCaptureHelper
import com.biasharaai.ai.VoiceInputProcessor
import com.biasharaai.databinding.FragmentAddEditProductBinding
import com.biasharaai.ui.base.BaseFragment
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * Add / Edit product form.
 *
 * Navigation arguments:
 * - `product_id` (Long) — 0L for new product, >0 for editing.
 * - `barcode_value` (String?) — pre-filled barcode from scanner.
 *
 * Features:
 * - Voice input via Gemma 3n multimodal (FULL_AI) or SpeechRecognizer fallback.
 * - Scan shortcut on barcode field opens BarcodeScannerFragment in SCAN_TO_ADD mode.
 * - Barcode pre-fill when arriving from the scanner.
 */
@AndroidEntryPoint
class AddEditProductFragment : BaseFragment() {

    private var _binding: FragmentAddEditProductBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddEditProductViewModel by viewModels()

    @Inject
    lateinit var voiceInputProcessor: VoiceInputProcessor

    // ── Voice input (SpeechRecognizer fallback) ────────────────────────

    private val speechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val spoken = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                if (!spoken.isNullOrBlank()) {
                    binding.editName.setText(spoken)
                    binding.editName.setSelection(spoken.length)
                }
            }
        }

    // ── Audio permission ───────────────────────────────────────────────

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startAiVoiceCapture()
            } else {
                Snackbar.make(
                    binding.root,
                    getString(R.string.voice_permission_denied),
                    Snackbar.LENGTH_SHORT,
                ).show()
            }
        }

    // ── Lifecycle ───────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAddEditProductBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupVoiceInput()
        setupBarcodeScan()
        setupLabelScan()
        setupSaveButton()
        prefillBarcode()
        observeViewModel()
        observeLabelScanResult()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Setup ───────────────────────────────────────────────────────────

    private fun setupToolbar() {
        binding.toolbar.title = getString(
            if (viewModel.isEditing) R.string.product_title_edit else R.string.product_title_new,
        )
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupVoiceInput() {
        binding.layoutName.setEndIconOnClickListener {
            if (voiceInputProcessor.usesOnDeviceAi) {
                // FULL_AI path: use Gemma 3n multimodal audio
                if (AudioCaptureHelper.hasRecordPermission(requireContext())) {
                    startAiVoiceCapture()
                } else {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            } else {
                // PARTIAL_AI / RULES_BASED fallback: system SpeechRecognizer
                val intent = voiceInputProcessor.createSpeechRecognizerIntent(
                    locale = Locale.getDefault(),
                    prompt = getString(R.string.product_voice_prompt),
                )
                try {
                    speechLauncher.launch(intent)
                } catch (_: Exception) {
                    Snackbar.make(
                        binding.root,
                        getString(R.string.product_voice_unavailable),
                        Snackbar.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    /**
     * Start on-device AI voice capture and transcription.
     *
     * Shows a recording indicator, captures audio via [AudioCaptureHelper],
     * transcribes via [VoiceInputProcessor], and populates the name field.
     */
    private fun startAiVoiceCapture() {
        viewLifecycleOwner.lifecycleScope.launch {
            showRecordingIndicator(true)
            try {
                val transcription = voiceInputProcessor.transcribeWithAi(
                    locale = Locale.getDefault(),
                    durationMs = AudioCaptureHelper.DEFAULT_DURATION_MS,
                )
                if (!transcription.isNullOrBlank()) {
                    binding.editName.setText(transcription)
                    binding.editName.setSelection(transcription.length)
                } else {
                    Snackbar.make(
                        binding.root,
                        getString(R.string.voice_transcription_failed),
                        Snackbar.LENGTH_SHORT,
                    ).show()
                }
            } finally {
                showRecordingIndicator(false)
            }
        }
    }

    private fun showRecordingIndicator(show: Boolean) {
        binding.recordingIndicator.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun setupBarcodeScan() {
        binding.layoutBarcode.setEndIconOnClickListener {
            // Navigate to scanner in SCAN_TO_ADD mode.
            findNavController().navigate(
                R.id.action_addEditProductFragment_to_barcodeScannerFragment,
                bundleOf("scan_mode" to "SCAN_TO_ADD"),
            )
        }
    }

    /** Tap "Scan label" → open the OCR camera fragment; result is delivered via SavedStateHandle. */
    private fun setupLabelScan() {
        binding.btnScanLabel.setOnClickListener {
            findNavController().navigate(
                R.id.action_addEditProductFragment_to_labelScannerFragment,
            )
        }
    }

    private fun observeLabelScanResult() {
        val handle = findNavController().currentBackStackEntry?.savedStateHandle ?: return
        handle.getLiveData<String>(com.biasharaai.ui.scanner.LabelScannerFragment.RESULT_KEY)
            .observe(viewLifecycleOwner) { scanned ->
                if (!scanned.isNullOrBlank()) {
                    binding.editName.setText(scanned)
                    binding.editName.setSelection(scanned.length)
                    Snackbar.make(
                        binding.root,
                        R.string.product_scan_label_success,
                        Snackbar.LENGTH_SHORT,
                    ).show()
                    handle.remove<String>(com.biasharaai.ui.scanner.LabelScannerFragment.RESULT_KEY)
                }
            }
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            viewModel.saveProduct(
                name = binding.editName.text.toString(),
                description = binding.editDescription.text.toString(),
                priceText = binding.editPrice.text.toString(),
                costText = binding.editCost.text.toString(),
                stockText = binding.editStock.text.toString(),
                category = binding.editCategory.text.toString(),
                barcodeValue = binding.editBarcode.text.toString(),
            )
        }
    }

    private fun prefillBarcode() {
        val barcode = viewModel.prefillBarcode
        if (!barcode.isNullOrBlank() && binding.editBarcode.text.isNullOrBlank()) {
            binding.editBarcode.setText(barcode)
        }
    }

    // ── Observers ───────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { collectExistingProduct() }
                launch { collectSaving() }
                launch { collectEvents() }
            }
        }
    }

    private suspend fun collectExistingProduct() {
        viewModel.existingProduct.collect { product ->
            product ?: return@collect
            binding.editName.setText(product.name)
            binding.editDescription.setText(product.description ?: "")
            binding.editPrice.setText(product.price.toString())
            binding.editCost.setText(product.cost.toString())
            binding.editStock.setText(product.stockQuantity.toString())
            binding.editCategory.setText(product.category ?: "")
            binding.editBarcode.setText(product.barcodeValue ?: "")
        }
    }

    private suspend fun collectSaving() {
        viewModel.saving.collect { isSaving ->
            binding.btnSave.isEnabled = !isSaving
            binding.btnSave.text = getString(
                if (isSaving) R.string.product_btn_saving else R.string.product_btn_save,
            )
        }
    }

    private suspend fun collectEvents() {
        viewModel.events.collect { event ->
            when (event) {
                is AddEditProductViewModel.Event.Saved -> {
                    Snackbar.make(
                        binding.root,
                        getString(R.string.product_saved),
                        Snackbar.LENGTH_SHORT,
                    ).show()
                    findNavController().navigateUp()
                }

                is AddEditProductViewModel.Event.ValidationError -> {
                    clearErrors()
                    event.errors["name"]?.let { binding.layoutName.error = it }
                    event.errors["price"]?.let { binding.layoutPrice.error = it }
                    event.errors["cost"]?.let { binding.layoutCost.error = it }
                    event.errors["stock"]?.let { binding.layoutStock.error = it }
                }

                is AddEditProductViewModel.Event.Error -> {
                    Snackbar.make(
                        binding.root,
                        event.message,
                        Snackbar.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun clearErrors() {
        binding.layoutName.error = null
        binding.layoutPrice.error = null
        binding.layoutCost.error = null
        binding.layoutStock.error = null
    }
}
