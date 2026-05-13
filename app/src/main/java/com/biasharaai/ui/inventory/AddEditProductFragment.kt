package com.biasharaai.ui.inventory

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil.load
import com.biasharaai.R
import com.biasharaai.ai.AudioCaptureHelper
import com.biasharaai.ai.VoiceInputProcessor
import com.biasharaai.data.local.db.Product
import com.biasharaai.databinding.FragmentAddEditProductBinding
import com.biasharaai.media.ProductPhotoStore
import com.biasharaai.ui.base.BaseFragment
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
 * - Optional product photo: camera (FileProvider) or gallery; scaled JPEG in app storage; [Product.imageUrl].
 */
@AndroidEntryPoint
class AddEditProductFragment : BaseFragment() {

    private var _binding: FragmentAddEditProductBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddEditProductViewModel by viewModels()

    @Inject
    lateinit var voiceInputProcessor: VoiceInputProcessor

    @Inject
    lateinit var productPhotoStore: ProductPhotoStore

    private var cameraCaptureFile: File? = null
    private var originalImageUrl: String? = null
    private var pendingPhotoPath: String? = null
    private var imageExplicitlyRemoved: Boolean = false
    private var boundProductId: Long = Long.MIN_VALUE

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

    private val pickProductPhoto =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@registerForActivityResult
            viewLifecycleOwner.lifecycleScope.launch {
                val path = withContext(Dispatchers.IO) {
                    productPhotoStore.saveScaledFromContentUri(uri)
                }
                if (!isAdded) return@launch
                if (path != null) {
                    commitPendingPhotoPath(path)
                    bindProductImage()
                } else {
                    Snackbar.make(
                        binding.root,
                        R.string.product_photo_failed,
                        Snackbar.LENGTH_LONG,
                    ).show()
                }
            }
        }

    private val takeProductPhoto =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val file = cameraCaptureFile
            cameraCaptureFile = null
            if (file == null) return@registerForActivityResult
            if (!success) {
                file.delete()
                return@registerForActivityResult
            }
            viewLifecycleOwner.lifecycleScope.launch {
                val path = withContext(Dispatchers.IO) {
                    productPhotoStore.saveScaledJpeg(file)
                }
                if (!isAdded) return@launch
                if (path != null) {
                    commitPendingPhotoPath(path)
                    bindProductImage()
                } else {
                    Snackbar.make(
                        binding.root,
                        R.string.product_photo_failed,
                        Snackbar.LENGTH_LONG,
                    ).show()
                }
            }
        }

    private val productCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchTakeProductPhoto()
            } else {
                Snackbar.make(
                    binding.root,
                    R.string.product_photo_permission_camera,
                    Snackbar.LENGTH_LONG,
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
        setupSuggestPrice()
        setupProductPhoto()
        prefillBarcode()
        observeViewModel()
        observeLabelScanResult()
        updateSuggestPriceButtonVisibility()
        bindProductImage()
    }

    /** Called from [PricingSuggestionBottomSheet] when the user applies a suggested price. */
    fun applySuggestedPrice(price: Double) {
        val text = if (price % 1.0 == 0.0) {
            price.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.2f", price).trimEnd('0').trimEnd('.')
        }
        binding.editPrice.setText(text)
        binding.editPrice.setSelection(binding.editPrice.text?.length ?: 0)
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
                    val desc = handle.get<String>(com.biasharaai.ui.scanner.LabelScannerFragment.RESULT_DESCRIPTION_KEY)
                    if (!desc.isNullOrBlank()) {
                        binding.editDescription.setText(desc)
                        binding.editDescription.setSelection(desc.length)
                    }
                    val cat = handle.get<String>(com.biasharaai.ui.scanner.LabelScannerFragment.RESULT_CATEGORY_KEY)
                    if (!cat.isNullOrBlank()) {
                        binding.editCategory.setText(cat)
                        binding.editCategory.setSelection(cat.length)
                    }
                    updateSuggestPriceButtonVisibility()
                    Snackbar.make(
                        binding.root,
                        R.string.product_scan_label_success,
                        Snackbar.LENGTH_SHORT,
                    ).show()
                    handle.remove<String>(com.biasharaai.ui.scanner.LabelScannerFragment.RESULT_KEY)
                    handle.remove<String>(com.biasharaai.ui.scanner.LabelScannerFragment.RESULT_DESCRIPTION_KEY)
                    handle.remove<String>(com.biasharaai.ui.scanner.LabelScannerFragment.RESULT_CATEGORY_KEY)
                }
            }
    }

    private fun setupSuggestPrice() {
        binding.btnSuggestPrice.setOnClickListener {
            val draft = buildDraftProductForPricing() ?: return@setOnClickListener
            PricingSuggestionBottomSheet.newInstance(draft).show(
                childFragmentManager,
                "pricing_suggestion",
            )
        }
        val suggestWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSuggestPriceButtonVisibility()
            }
        }
        binding.editCost.addTextChangedListener(suggestWatcher)
        binding.editCategory.addTextChangedListener(suggestWatcher)
    }

    private fun updateSuggestPriceButtonVisibility() {
        val costOk = binding.editCost.text.toString().toDoubleOrNull()?.let { it > 0 } == true
        val categoryOk = binding.editCategory.text.toString().trim().isNotEmpty()
        binding.btnSuggestPrice.visibility =
            if (costOk && categoryOk) View.VISIBLE else View.GONE
    }

    private fun buildDraftProductForPricing(): Product? {
        val cost = binding.editCost.text.toString().toDoubleOrNull() ?: return null
        if (cost <= 0) return null
        val category = binding.editCategory.text.toString().trim()
        if (category.isEmpty()) return null
        val name = binding.editName.text.toString().trim()
            .ifBlank { getString(R.string.product_title_new) }
        val price = binding.editPrice.text.toString().toDoubleOrNull() ?: 0.0
        val stock = binding.editStock.text.toString().toIntOrNull() ?: 0
        return Product(
            id = viewModel.editingProductId,
            name = name,
            description = binding.editDescription.text.toString().trim().ifBlank { null },
            price = price,
            cost = cost,
            stockQuantity = stock,
            category = category,
            barcodeValue = binding.editBarcode.text.toString().trim().ifBlank { null },
            imageUrl = resolveImageUrlForSave(),
        )
    }

    private fun setupProductPhoto() {
        binding.btnPhotoGallery.setOnClickListener {
            pickProductPhoto.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
        binding.btnPhotoCamera.setOnClickListener {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED -> launchTakeProductPhoto()
                else -> productCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        binding.btnPhotoRemove.setOnClickListener {
            val oldPending = pendingPhotoPath
            if (!oldPending.isNullOrBlank() &&
                oldPending != originalImageUrl &&
                productPhotoStore.isAppStoredAbsolutePath(oldPending)
            ) {
                productPhotoStore.deleteIfAppStored(oldPending)
            }
            pendingPhotoPath = null
            imageExplicitlyRemoved = true
            bindProductImage()
        }
    }

    private fun commitPendingPhotoPath(path: String) {
        val oldPending = pendingPhotoPath
        if (!oldPending.isNullOrBlank() &&
            oldPending != path &&
            oldPending != originalImageUrl &&
            productPhotoStore.isAppStoredAbsolutePath(oldPending)
        ) {
            productPhotoStore.deleteIfAppStored(oldPending)
        }
        pendingPhotoPath = path
        imageExplicitlyRemoved = false
    }

    private fun resolveImageUrlForSave(): String? = when {
        imageExplicitlyRemoved -> null
        !pendingPhotoPath.isNullOrBlank() -> pendingPhotoPath
        else -> originalImageUrl
    }

    private fun launchTakeProductPhoto() {
        val file = productPhotoStore.createCameraCaptureFile()
        cameraCaptureFile = file
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file,
        )
        takeProductPhoto.launch(uri)
    }

    private fun bindProductImage() {
        val path = when {
            imageExplicitlyRemoved -> null
            !pendingPhotoPath.isNullOrBlank() -> pendingPhotoPath
            else -> originalImageUrl
        }
        val hasLocal = !path.isNullOrBlank() &&
            !path.startsWith("http", ignoreCase = true) &&
            File(path).isFile
        val hasRemote = !path.isNullOrBlank() && path.startsWith("http", ignoreCase = true)
        if (hasLocal) {
            binding.imageProduct.background = null
            binding.imageProduct.load(File(path!!)) {
                crossfade(true)
                error(R.drawable.bg_product_thumb)
            }
            binding.btnPhotoRemove.visibility = View.VISIBLE
        } else if (hasRemote) {
            binding.imageProduct.background = null
            binding.imageProduct.load(path!!) {
                crossfade(true)
                error(R.drawable.bg_product_thumb)
            }
            binding.btnPhotoRemove.visibility = View.VISIBLE
        } else {
            binding.imageProduct.setImageDrawable(null)
            binding.imageProduct.setBackgroundResource(R.drawable.bg_product_thumb)
            binding.btnPhotoRemove.visibility = View.GONE
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
                imageUrl = resolveImageUrlForSave(),
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
            if (boundProductId != product.id) {
                boundProductId = product.id
                originalImageUrl = product.imageUrl
                pendingPhotoPath = null
                imageExplicitlyRemoved = false
            }
            binding.editName.setText(product.name)
            binding.editDescription.setText(product.description ?: "")
            binding.editPrice.setText(product.price.toString())
            binding.editCost.setText(product.cost.toString())
            binding.editStock.setText(product.stockQuantity.toString())
            binding.editCategory.setText(product.category ?: "")
            binding.editBarcode.setText(product.barcodeValue ?: "")
            updateSuggestPriceButtonVisibility()
            bindProductImage()
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
