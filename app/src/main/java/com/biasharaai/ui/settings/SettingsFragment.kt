package com.biasharaai.ui.settings

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.biasharaai.BuildConfig
import com.biasharaai.R
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.ai.DownloadState
import com.biasharaai.ai.InferenceSettingsStore
import com.biasharaai.ai.InferenceUiConfig
import com.biasharaai.ai.ModelDownloadManager
import com.biasharaai.ai.VoiceInputPreferences
import com.biasharaai.databinding.FragmentSettingsBinding
import com.biasharaai.ui.base.BaseFragment
import com.biasharaai.ui.order.OrderParserActivity
import com.biasharaai.ui.order.showOrderParserTierBlockedDialogIfNeeded
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Settings screen with AI Model management section.
 *
 * Shows device capability tier, model download status, and
 * provides download/delete actions for the Gemma LLM model.
 */
@AndroidEntryPoint
class SettingsFragment : BaseFragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()

    @Inject
    lateinit var voiceInputPreferences: VoiceInputPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        displayCapabilityInfo()
        displayAppVersion()
        setupButtons()
        setupInferenceConfigurations()
        setupOrderParserFromClipboard()
        setupVoiceInputSwitch()
        setupCurrency()
        observeDownloadState()
        observeDownloadProgress()
        observeEvents()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Static info ─────────────────────────────────────────────────────

    private fun displayCapabilityInfo() {
        val result = viewModel.capabilityResult
        val tierLabel = when (result.tier) {
            CapabilityTier.FULL_AI -> getString(R.string.settings_tier_full_ai)
            CapabilityTier.PARTIAL_AI -> getString(R.string.settings_tier_partial_ai)
            CapabilityTier.RULES_BASED -> getString(R.string.settings_tier_rules_based)
        }
        binding.textCapabilityTier.text = getString(
            R.string.settings_capability_detail,
            tierLabel,
            result.totalRamMb,
            result.apiLevel,
        )
    }

    private fun displayAppVersion() {
        binding.textAppVersion.text = getString(
            R.string.settings_app_version,
            BuildConfig.VERSION_NAME,
        )
    }

    // ── Buttons ─────────────────────────────────────────────────────────

    private fun setupCurrency() {
        binding.btnChangeCurrency.setOnClickListener { showCurrencyPicker() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.shopSettings.collect { s ->
                    val codes = resources.getStringArray(R.array.supported_currency_codes)
                    val names = resources.getStringArray(R.array.supported_currency_names)
                    val code = s?.currencyCode?.uppercase(Locale.ROOT) ?: "KES"
                    val idx = codes.indexOf(code).let { if (it >= 0) it else 0 }
                    binding.textCurrencyCurrent.text = names[idx]
                }
            }
        }
    }

    private fun showCurrencyPicker() {
        val codes = resources.getStringArray(R.array.supported_currency_codes)
        val names = resources.getStringArray(R.array.supported_currency_names)
        val current = viewModel.shopSettings.value?.currencyCode?.uppercase(Locale.ROOT) ?: "KES"
        val checked = codes.indexOf(current).let { if (it >= 0) it else 0 }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_currency_dialog_title)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                viewModel.setShopCurrency(codes[which])
                dialog.dismiss()
                val anchor = _binding?.root ?: view
                anchor?.let {
                    Snackbar.make(it, R.string.settings_currency_saved, Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setupVoiceInputSwitch() {
        binding.switchVoiceInput.setOnCheckedChangeListener(null)
        binding.switchVoiceInput.isChecked = voiceInputPreferences.isVoiceInputEnabled()
        binding.switchVoiceInput.setOnCheckedChangeListener { _, isChecked ->
            voiceInputPreferences.setVoiceInputEnabled(isChecked)
        }
    }

    private fun setupOrderParserFromClipboard() {
        binding.btnOrderParserFromClipboard.setOnClickListener {
            if (showOrderParserTierBlockedDialogIfNeeded(viewModel.capabilityResult.tier)) {
                return@setOnClickListener
            }
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty().trim()
            if (text.isBlank()) {
                Snackbar.make(
                    binding.root,
                    R.string.settings_order_parser_clipboard_empty,
                    Snackbar.LENGTH_SHORT,
                ).show()
            } else {
                startActivity(
                    Intent(requireContext(), OrderParserActivity::class.java).apply {
                        putExtra(Intent.EXTRA_TEXT, text)
                    },
                )
            }
        }
    }

    private fun setupButtons() {
        binding.btnDownloadModel.setOnClickListener {
            if (viewModel.downloadState.value == DownloadState.DOWNLOADED) {
                // Re-download: confirm first
                showRedownloadDialog()
            } else {
                showDownloadConfirmDialog()
            }
        }

        binding.btnDeleteModel.setOnClickListener {
            showDeleteConfirmDialog()
        }

        binding.btnRunBenchmark.setOnClickListener {
            viewModel.runBenchmark()
        }
    }

    private fun showDownloadConfirmDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_download_confirm_title)
            .setMessage(
                getString(
                    R.string.settings_download_confirm_message,
                    ModelDownloadManager.APPROX_MODEL_SIZE_MB,
                ),
            )
            .setPositiveButton(R.string.settings_btn_download) { _, _ ->
                viewModel.downloadModel()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRedownloadDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_redownload_title)
            .setMessage(R.string.settings_redownload_message)
            .setPositiveButton(R.string.settings_btn_redownload) { _, _ ->
                viewModel.deleteModel()
                viewModel.downloadModel()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteConfirmDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_delete_confirm_title)
            .setMessage(R.string.settings_delete_confirm_message)
            .setPositiveButton(R.string.settings_btn_delete) { _, _ ->
                viewModel.deleteModel()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ── Observers ───────────────────────────────────────────────────────

    private fun observeDownloadState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.downloadState.collect { state ->
                    updateModelStatusUi(state)
                }
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is SettingsViewModel.Event.DownloadComplete -> {
                            Snackbar.make(
                                binding.root,
                                R.string.settings_download_complete,
                                Snackbar.LENGTH_SHORT,
                            ).show()
                        }

                        is SettingsViewModel.Event.DownloadFailed -> {
                            Snackbar.make(
                                binding.root,
                                getString(R.string.settings_download_failed, event.message),
                                Snackbar.LENGTH_LONG,
                            ).setAction(R.string.settings_btn_retry) {
                                viewModel.retryDownload()
                            }.show()
                        }

                        is SettingsViewModel.Event.BenchmarkResult -> {
                            showBenchmarkResultDialog(event)
                        }

                        is SettingsViewModel.Event.BenchmarkFailed -> {
                            Snackbar.make(
                                binding.root,
                                getString(R.string.settings_benchmark_failed, event.message),
                                Snackbar.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isBenchmarking.collect { running ->
                    binding.btnRunBenchmark.isEnabled = !running
                    binding.btnRunBenchmark.text = if (running) {
                        getString(R.string.settings_benchmark_running)
                    } else {
                        getString(R.string.settings_btn_benchmark)
                    }
                }
            }
        }
    }

    private fun showBenchmarkResultDialog(result: SettingsViewModel.Event.BenchmarkResult) {
        val body = getString(
            R.string.settings_benchmark_result_body,
            result.firstTokenMs,
            result.decodeMs,
            result.approxTokens,
            String.format(Locale.getDefault(), "%.1f", result.tokensPerSecond),
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_benchmark_result_title)
            .setMessage(body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun observeDownloadProgress() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.modelDownloadManager.progress.collect { progress ->
                    if (viewModel.downloadState.value == DownloadState.DOWNLOADING) {
                        binding.progressDownload.isIndeterminate = progress.totalBytes == 0L
                        if (progress.totalBytes > 0) {
                            binding.progressDownload.setProgressCompat(progress.percent, true)
                            binding.textDownloadProgress.visibility = View.VISIBLE
                            binding.textDownloadProgress.text = formatProgress(progress)
                        }
                    }
                }
            }
        }
    }

    private fun formatProgress(progress: ModelDownloadManager.DownloadProgress): String {
        val downloadedMb = progress.bytesDownloaded / (1024.0 * 1024.0)
        val totalMb = progress.totalBytes / (1024.0 * 1024.0)

        val (downloadedStr, totalStr) = if (totalMb >= 1024) {
            String.format(Locale.getDefault(), "%.1f GB", downloadedMb / 1024) to
                String.format(Locale.getDefault(), "%.1f GB", totalMb / 1024)
        } else {
            String.format(Locale.getDefault(), "%.0f MB", downloadedMb) to
                String.format(Locale.getDefault(), "%.0f MB", totalMb)
        }

        return "$downloadedStr / $totalStr • ${progress.percent}%"
    }

    private fun updateModelStatusUi(state: DownloadState) {
        when (state) {
            DownloadState.NOT_DOWNLOADED -> {
                binding.textModelStatus.text = getString(R.string.settings_model_not_downloaded)
                binding.progressDownload.visibility = View.GONE
                binding.textDownloadProgress.visibility = View.GONE
                binding.textDataWarning.visibility = View.VISIBLE
                binding.btnDownloadModel.visibility = if (viewModel.isAiCapable) View.VISIBLE else View.GONE
                binding.btnDownloadModel.text = getString(R.string.settings_btn_download)
                binding.btnDeleteModel.visibility = View.GONE
            }

            DownloadState.DOWNLOADING -> {
                binding.textModelStatus.text = getString(R.string.settings_model_downloading)
                binding.progressDownload.visibility = View.VISIBLE
                binding.progressDownload.isIndeterminate = true // starts indeterminate, switches when progress arrives
                binding.textDataWarning.visibility = View.VISIBLE
                binding.btnDownloadModel.visibility = View.GONE
                binding.btnDeleteModel.visibility = View.GONE
            }

            DownloadState.DOWNLOADED -> {
                val sizeMb = viewModel.modelDownloadManager.modelSizeBytes / (1024 * 1024)
                binding.textModelStatus.text = getString(
                    R.string.settings_model_downloaded,
                    sizeMb,
                )
                binding.progressDownload.visibility = View.GONE
                binding.textDownloadProgress.visibility = View.GONE
                binding.textDataWarning.visibility = View.GONE
                binding.btnDownloadModel.visibility = View.VISIBLE
                binding.btnDownloadModel.text = getString(R.string.settings_btn_redownload)
                binding.btnDeleteModel.visibility = View.VISIBLE
            }

            DownloadState.FAILED -> {
                binding.textModelStatus.text = getString(R.string.settings_model_failed)
                binding.progressDownload.visibility = View.GONE
                binding.textDownloadProgress.visibility = View.GONE
                binding.textDataWarning.visibility = View.GONE
                binding.btnDownloadModel.visibility = View.VISIBLE
                binding.btnDownloadModel.text = getString(R.string.settings_btn_retry)
                binding.btnDeleteModel.visibility = View.GONE
            }
        }

        // If device is not AI-capable, show a note
        if (!viewModel.isAiCapable) {
            binding.textModelStatus.text = getString(R.string.settings_model_not_supported)
            binding.progressDownload.visibility = View.GONE
            binding.textDownloadProgress.visibility = View.GONE
            binding.textDataWarning.visibility = View.GONE
            binding.btnDownloadModel.visibility = View.GONE
            binding.btnDeleteModel.visibility = View.GONE
        }

        updateInferenceSectionVisibility()
    }

    private fun setupInferenceConfigurations() {
        binding.btnInferenceConfigurations.setOnClickListener {
            showInferenceConfigurationsDialog()
        }
        updateInferenceSectionVisibility()
    }

    private fun updateInferenceSectionVisibility() {
        val vis = if (viewModel.isAiCapable) View.VISIBLE else View.GONE
        binding.textInferenceSectionTitle.visibility = vis
        binding.cardInferenceSettings.visibility = vis
    }

    private fun showInferenceConfigurationsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_inference_settings, null)
        val store = viewModel.inferenceSettingsStore
        bindInferenceConfigurationViews(dialogView, store.load())

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.inference_dialog_title)
            .setView(dialogView)
            .setNeutralButton(R.string.inference_defaults) { _, _ ->
                bindInferenceConfigurationViews(dialogView, InferenceSettingsStore.DEFAULTS)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val cfg = readInferenceConfigurationFromViews(dialogView)
                store.save(cfg)
                viewModel.onInferenceSettingsSaved()
                Snackbar.make(
                    binding.root,
                    R.string.inference_configs_saved,
                    Snackbar.LENGTH_SHORT,
                ).show()
            }
            .show()
    }

    private fun bindInferenceConfigurationViews(root: View, cfg: InferenceUiConfig) {
        val sliderMax = root.findViewById<Slider>(R.id.slider_max_tokens)
        val sliderTopK = root.findViewById<Slider>(R.id.slider_top_k)
        val sliderTopP = root.findViewById<Slider>(R.id.slider_top_p)
        val sliderTemp = root.findViewById<Slider>(R.id.slider_temperature)
        val textMax = root.findViewById<TextView>(R.id.text_max_tokens_value)
        val textTopK = root.findViewById<TextView>(R.id.text_top_k_value)
        val textTopP = root.findViewById<TextView>(R.id.text_top_p_value)
        val textTemp = root.findViewById<TextView>(R.id.text_temperature_value)
        val toggleAccel = root.findViewById<MaterialButtonToggleGroup>(R.id.toggle_accelerator)
        val switchThinking = root.findViewById<SwitchMaterial>(R.id.switch_enable_thinking)
        val switchSpec = root.findViewById<SwitchMaterial>(R.id.switch_enable_speculative)

        sliderMax.value = cfg.maxTokens.toFloat().coerceIn(sliderMax.valueFrom, sliderMax.valueTo)
        sliderTopK.value = cfg.topK.toFloat().coerceIn(sliderTopK.valueFrom, sliderTopK.valueTo)
        sliderTopP.value = cfg.topP.coerceIn(sliderTopP.valueFrom, sliderTopP.valueTo)
        sliderTemp.value = cfg.temperature.coerceIn(sliderTemp.valueFrom, sliderTemp.valueTo)

        if (cfg.preferCpu) {
            toggleAccel.check(R.id.btn_accel_cpu)
        } else {
            toggleAccel.check(R.id.btn_accel_gpu)
        }
        switchThinking.isChecked = cfg.enableThinking
        switchSpec.isChecked = cfg.enableSpeculativeDecoding

        fun refreshLabels() {
            textMax.text = sliderMax.value.roundToInt().toString()
            textTopK.text = sliderTopK.value.roundToInt().toString()
            textTopP.text = String.format(Locale.US, "%.2f", sliderTopP.value)
            textTemp.text = String.format(Locale.US, "%.2f", sliderTemp.value)
        }
        refreshLabels()

        if (root.getTag(R.id.tag_inference_dialog_sliders_bound) != true) {
            root.setTag(R.id.tag_inference_dialog_sliders_bound, true)
            val listener = Slider.OnChangeListener { _, _, _ -> refreshLabels() }
            sliderMax.addOnChangeListener(listener)
            sliderTopK.addOnChangeListener(listener)
            sliderTopP.addOnChangeListener(listener)
            sliderTemp.addOnChangeListener(listener)
        }
    }

    private fun readInferenceConfigurationFromViews(root: View): InferenceUiConfig {
        val sliderMax = root.findViewById<Slider>(R.id.slider_max_tokens)
        val sliderTopK = root.findViewById<Slider>(R.id.slider_top_k)
        val sliderTopP = root.findViewById<Slider>(R.id.slider_top_p)
        val sliderTemp = root.findViewById<Slider>(R.id.slider_temperature)
        val toggleAccel = root.findViewById<MaterialButtonToggleGroup>(R.id.toggle_accelerator)
        val switchThinking = root.findViewById<SwitchMaterial>(R.id.switch_enable_thinking)
        val switchSpec = root.findViewById<SwitchMaterial>(R.id.switch_enable_speculative)
        return InferenceUiConfig(
            maxTokens = sliderMax.value.roundToInt(),
            topK = sliderTopK.value.roundToInt(),
            topP = sliderTopP.value,
            temperature = sliderTemp.value,
            preferCpu = toggleAccel.checkedButtonId == R.id.btn_accel_cpu,
            enableThinking = switchThinking.isChecked,
            enableSpeculativeDecoding = switchSpec.isChecked,
        )
    }
}
