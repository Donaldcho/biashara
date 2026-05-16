package com.biasharaai.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.biasharaai.R
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.ai.DownloadState
import com.biasharaai.ai.ModelDownloadManager
import com.biasharaai.databinding.FragmentModelSettingsBinding
import com.biasharaai.databinding.ItemModelCardBinding
import com.biasharaai.ui.base.BaseFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Phase 6 X3 — allows the owner to see all catalogue models, download/delete each,
 * set the primary model, and run a speed benchmark.
 */
@AndroidEntryPoint
class ModelSettingsFragment : BaseFragment() {

    private var _binding: FragmentModelSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ModelSettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentModelSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.btnHfAccess.setOnClickListener { showHuggingFaceTokenDialog() }
        observeModels()
        observeDownloadProgress()
        observeEvents()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun observeModels() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.models.collect { models ->
                    binding.containerModels.removeAllViews()
                    val downloadState = viewModel.downloadState.value
                    models.forEach { row ->
                        val cardBinding = ItemModelCardBinding.inflate(
                            layoutInflater,
                            binding.containerModels,
                            true,
                        )
                        bindModelCard(cardBinding, row, downloadState)
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.downloadState.collect { viewModel.refreshModels() }
            }
        }
    }

    private fun bindModelCard(
        card: ItemModelCardBinding,
        row: ModelRowUiState,
        downloadState: DownloadState,
    ) {
        val entry = row.entry
        val sizeLabel = formatSize(entry.sizeBytes)
        val capLabel = entry.capabilities.joinToString()

        card.textModelName.text = entry.displayName
        val meta = buildString {
            append("$sizeLabel · $capLabel")
            if (entry.requiresHfAccess) {
                append("\n")
                append(getString(R.string.model_settings_requires_hf))
            }
        }
        card.textModelMeta.text = meta
        card.chipPrimary.isVisible = row.isPrimary

        // Benchmark result
        val desc = row.descriptor
        val gpuTps = desc?.tokensPerSecGpu
        val cpuTps = desc?.tokensPerSecCpu
        if (gpuTps != null || cpuTps != null) {
            card.textBenchmark.isVisible = true
            card.textBenchmark.text = buildString {
                if (gpuTps != null) append("GPU: ${"%.1f".format(gpuTps)} t/s")
                if (cpuTps != null) {
                    if (isNotEmpty()) append(" · ")
                    append("CPU: ${"%.1f".format(cpuTps)} t/s")
                }
            }
        } else {
            card.textBenchmark.isVisible = false
        }

        // Download status label
        val isThisDownloading = downloadState == DownloadState.DOWNLOADING &&
            viewModel.downloadProgress.value.modelId == entry.modelId
        card.textDownloadStatus.text = when {
            isThisDownloading -> getString(R.string.settings_model_downloading)
            row.isDownloaded -> getString(
                R.string.settings_model_downloaded,
                formatSizeMb(entry.sizeBytes),
            )
            else -> getString(R.string.settings_model_not_downloaded)
        }

        // Benchmarking spinner
        card.layoutBenchmarking.isVisible = row.isBenchmarking

        // Progress bar (only shown while downloading this model)
        card.progressDownload.isVisible = isThisDownloading
        card.textDownloadDetail.isVisible = isThisDownloading

        // Buttons
        val tierOk = viewModel.capabilityTierValue != CapabilityTier.RULES_BASED
        card.btnDownload.isVisible = !row.isDownloaded && !isThisDownloading && tierOk
        card.btnDelete.isVisible = row.isDownloaded && !isThisDownloading
        card.btnSetPrimary.isVisible = row.isDownloaded && !row.isPrimary
        card.btnBenchmark.isVisible = row.isDownloaded && !row.isBenchmarking && !isThisDownloading

        card.btnDownload.setOnClickListener {
            showDownloadConfirm(entry.modelId, entry.displayName, entry.sizeBytes, entry.requiresHfAccess)
        }
        card.btnDelete.setOnClickListener {
            showDeleteConfirm(entry.modelId, entry.displayName)
        }
        card.btnSetPrimary.setOnClickListener {
            viewModel.setPrimary(entry.modelId)
        }
        card.btnBenchmark.setOnClickListener {
            viewModel.runBenchmark(entry.modelId)
        }
    }

    private fun observeDownloadProgress() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.downloadProgress.collect { progress: ModelDownloadManager.DownloadProgress ->
                    val targetModelId = progress.modelId ?: return@collect
                    val models = viewModel.models.value
                    val idx = models.indexOfFirst { it.entry.modelId == targetModelId }
                    if (idx < 0) return@collect
                    val cardView = binding.containerModels.getChildAt(idx) ?: return@collect
                    val card = ItemModelCardBinding.bind(cardView)
                    card.progressDownload.progress = progress.percent
                    card.textDownloadDetail.text = getString(
                        R.string.model_settings_progress_detail,
                        formatSizeMb(progress.bytesDownloaded),
                        formatSizeMb(progress.totalBytes),
                        progress.percent,
                    )
                }
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is ModelSettingsViewModel.Event.DownloadComplete ->
                            snack(getString(R.string.settings_download_complete))
                        is ModelSettingsViewModel.Event.DownloadFailed ->
                            snack(getString(R.string.settings_download_failed, event.message))
                        is ModelSettingsViewModel.Event.PrimaryModelChanged ->
                            snack(getString(R.string.model_settings_primary_changed))
                        is ModelSettingsViewModel.Event.BenchmarkComplete ->
                            snack(
                                getString(
                                    R.string.model_settings_benchmark_done,
                                    "%.1f".format(event.tokensPerSec),
                                ),
                            )
                        is ModelSettingsViewModel.Event.BenchmarkFailed ->
                            snack(getString(R.string.model_settings_benchmark_failed))
                    }
                }
            }
        }
    }

    private fun showHuggingFaceTokenDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_hf_token, null)
        val edit = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(
            R.id.edit_hf_token,
        )
        viewModel.getHuggingFaceToken()?.let { edit.setText(it) }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.model_settings_hf_access_title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val token = edit.text?.toString()
                viewModel.saveHuggingFaceToken(token)
                snack(
                    if (token.isNullOrBlank()) {
                        getString(R.string.model_settings_hf_token_cleared)
                    } else {
                        getString(R.string.model_settings_hf_token_saved)
                    },
                )
            }
            .setNeutralButton(R.string.settings_btn_delete) { _, _ ->
                viewModel.saveHuggingFaceToken(null)
                edit.setText("")
                snack(getString(R.string.model_settings_hf_token_cleared))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDownloadConfirm(
        modelId: String,
        displayName: String,
        sizeBytes: Long,
        requiresHfAccess: Boolean,
    ) {
        if (requiresHfAccess && !viewModel.hasHuggingFaceToken()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.model_settings_hf_access_title)
                .setMessage(R.string.model_settings_hf_access_message)
                .setPositiveButton(R.string.model_settings_hf_access_btn) { _, _ ->
                    showHuggingFaceTokenDialog()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        val sizeMb = formatSizeMb(sizeBytes)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_download_confirm_title)
            .setMessage(
                getString(R.string.settings_download_confirm_message, sizeMb),
            )
            .setPositiveButton(R.string.settings_btn_download) { _, _ ->
                viewModel.downloadModel(modelId)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteConfirm(modelId: String, displayName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_delete_confirm_title)
            .setMessage(R.string.settings_delete_confirm_message)
            .setPositiveButton(R.string.settings_btn_delete) { _, _ ->
                viewModel.deleteModel(modelId)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun snack(msg: String) {
        _binding?.let { Snackbar.make(it.root, msg, Snackbar.LENGTH_SHORT).show() }
    }

    private fun formatSize(bytes: Long): String {
        val mb = bytes / (1024 * 1024)
        return if (mb >= 1024) "${"%.1f".format(mb / 1024f)} GB" else "$mb MB"
    }

    private fun formatSizeMb(bytes: Long): Int = (bytes / (1024 * 1024)).toInt()
}
