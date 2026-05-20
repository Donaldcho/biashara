package com.biasharaai.ui.settings

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import com.biasharaai.ui.insights.CashFlowInsightsFragment
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
import com.biasharaai.cloud.EnterpriseDeploymentMode
import com.biasharaai.data.local.db.StaffMember
import com.biasharaai.databinding.FragmentSettingsBinding
import com.biasharaai.money.RegionalDefaults
import com.biasharaai.ui.base.BaseFragment
import com.biasharaai.ui.order.OrderParserActivity
import com.biasharaai.productline.ProductLineManager
import com.biasharaai.productline.showProRequiredSnackbar
import com.biasharaai.ui.order.showOrderParserTierBlockedDialogIfNeeded
import com.biasharaai.whatsapp.WhatsAppIntegration
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
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

    @Inject lateinit var productLineManager: ProductLineManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshLicenceState()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        displayCapabilityInfo()
        displayAppVersion()
        setupButtons()
        setupInferenceConfigurations()
        setupCloudAnalysis()
        setupOrderParserFromClipboard()
        setupWhatsappIntegration()
        setupVoiceSettingsNav()
        setupLedgerNav()
        setupLicence()
        setupCurrency()
        setupAgentRunHistoryNav()
        setupStaffSettingsNav()
        setupEnterpriseOperator()
        setupBusinessProfileNav()
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
                    val code = s?.currencyCode?.uppercase(Locale.ROOT) ?: RegionalDefaults.CURRENCY_CODE
                    val idx = codes.indexOf(code).let { if (it >= 0) it else 0 }
                    binding.textCurrencyCurrent.text = names[idx]
                }
            }
        }
    }

    private fun setupAgentRunHistoryNav() {
        binding.btnAgentActivity.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_agentSettingsFragment)
        }
    }

    private fun setupBusinessProfileNav() {
        binding.btnBusinessProfile.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_businessProfileEditFragment)
        }
    }

    private fun setupStaffSettingsNav() {
        binding.btnStaffSettings.visibility = if (productLineManager.isProEnabled()) View.VISIBLE else View.GONE
        binding.btnStaffSettings.setOnClickListener {
            if (!productLineManager.isProEnabled()) {
                binding.root.showProRequiredSnackbar(productLineManager)
                return@setOnClickListener
            }
            viewModel.requestEnterpriseAction(SettingsViewModel.RestrictedAction.OPEN_STAFF_SETTINGS)
        }
    }

    private fun setupEnterpriseOperator() {
        binding.btnEnterpriseOperator.setOnClickListener { showEnterpriseOperatorDialog() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentEnterpriseOperator.collect { operator ->
                    binding.textEnterpriseOperatorStatus.text = if (operator == null) {
                        getString(R.string.settings_enterprise_operator_none)
                    } else {
                        getString(
                            R.string.settings_enterprise_operator_status,
                            operator.name,
                            roleLabel(operator.role),
                        )
                    }
                }
            }
        }
    }

    private fun showEnterpriseOperatorDialog() {
        val staff = viewModel.enterpriseStaff.value
        if (staff.isEmpty()) {
            Snackbar.make(
                binding.root,
                R.string.settings_enterprise_operator_no_staff,
                Snackbar.LENGTH_SHORT,
            ).show()
            return
        }
        val currentId = viewModel.currentEnterpriseOperator.value?.id
        val labels = buildList {
            add(getString(R.string.staff_operator_none))
            staff.forEach { member ->
                add("${member.name} (${roleLabel(member.role)})")
            }
        }.toTypedArray()
        val checked = staff.indexOfFirst { it.id == currentId }.let { index ->
            if (index >= 0) index + 1 else 0
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_enterprise_operator_dialog_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                if (which == 0) {
                    viewModel.selectEnterpriseOperator(null)
                } else {
                    showEnterpriseOperatorPinDialog(staff[which - 1])
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEnterpriseOperatorPinDialog(member: StaffMember) {
        if (member.pinHash.isNullOrBlank() || member.pinSalt.isNullOrBlank()) {
            Snackbar.make(binding.root, R.string.staff_pin_required, Snackbar.LENGTH_LONG).show()
            return
        }
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.staff_pin_hint)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            maxLines = 1
        }
        val padding = resources.getDimensionPixelSize(R.dimen.pos_dialog_padding)
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, 0)
            addView(input)
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.staff_pin_enter_title, member.name))
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val pin = input.text?.toString().orEmpty()
            if (pin.length !in 4..8 || pin.any { !it.isDigit() }) {
                input.error = getString(R.string.staff_pin_format_invalid)
                return@setOnClickListener
            }
            viewModel.selectEnterpriseOperator(member, pin)
            dialog.dismiss()
        }
    }

    private fun setupLicence() {
        binding.btnApplyLicence.setOnClickListener {
            val key = binding.editLicenceKey.text?.toString().orEmpty()
            if (key.isBlank()) {
                Snackbar.make(binding.root, R.string.settings_licence_invalid, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.applyLicenceKey(key)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.licenceKey.collect { key ->
                    if (key == null) {
                        binding.textLicenceCurrent.text = getString(R.string.settings_licence_invalid)
                        updateEnterpriseDeploymentVisibility()
                        return@collect
                    }
                    binding.textLicenceCurrent.text = getString(
                        R.string.settings_licence_current,
                        getString(viewModel.productLineNameRes(key.productLine)),
                        getString(viewModel.editionNameRes(key.edition)),
                        key.maxDevices,
                    )
                    updateEnterpriseDeviceSummary()
                    updateEnterpriseDeploymentVisibility()
                }
            }
        }
    }

    private fun showCurrencyPicker() {
        val codes = resources.getStringArray(R.array.supported_currency_codes)
        val names = resources.getStringArray(R.array.supported_currency_names)
        val current = viewModel.shopSettings.value?.currencyCode?.uppercase(Locale.ROOT)
            ?: RegionalDefaults.CURRENCY_CODE
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

    private fun setupVoiceSettingsNav() {
        binding.btnVoiceSettings.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_voiceSettingsFragment)
        }
    }

    private fun setupLedgerNav() {
        binding.btnOpenLedger.setOnClickListener {
            viewModel.requestEnterpriseAction(SettingsViewModel.RestrictedAction.OPEN_LEDGER)
        }
    }

    private fun performEnterpriseAction(action: SettingsViewModel.RestrictedAction) {
        when (action) {
            SettingsViewModel.RestrictedAction.OPEN_LEDGER -> openLedger()
            SettingsViewModel.RestrictedAction.OPEN_STAFF_SETTINGS ->
                findNavController().navigate(R.id.action_settingsFragment_to_staffSettingsFragment)
            SettingsViewModel.RestrictedAction.SAVE_CLOUD_SETTINGS -> saveCloudSettingsFromFields()
            SettingsViewModel.RestrictedAction.UPLOAD_ANALYTICS_JSON -> confirmCloudJsonUpload()
            SettingsViewModel.RestrictedAction.UPLOAD_SQLITE_DATABASE -> confirmCloudSqliteUpload()
            SettingsViewModel.RestrictedAction.SYNC_ENTERPRISE_QUEUE -> viewModel.syncEnterpriseQueue()
        }
    }

    private fun openLedger() {
        findNavController().navigate(
            R.id.action_settingsFragment_to_insightsFragment,
            bundleOf(CashFlowInsightsFragment.ARG_INITIAL_TAB to CashFlowInsightsFragment.TAB_LEDGER),
        )
    }

    private fun saveCloudSettingsFromFields() {
        val keyText = binding.inputCloudApiKey.text?.toString()?.trim().orEmpty()
        viewModel.saveCloudAnalysis(
            enabled = binding.switchCloudAnalysisEnabled.isChecked,
            deploymentMode = selectedDeploymentMode(),
            endpointUrl = binding.inputCloudEndpoint.text?.toString().orEmpty(),
            newApiKeyIfNonBlank = keyText.ifBlank { null },
        )
        binding.inputCloudApiKey.text?.clear()
    }

    private fun confirmCloudJsonUpload() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.settings_cloud_upload_json_confirm)
            .setPositiveButton(R.string.settings_cloud_btn_upload_json) { _, _ ->
                viewModel.uploadCloudAnalyticsJson()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmCloudSqliteUpload() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_cloud_upload_db_confirm_title)
            .setMessage(R.string.settings_cloud_upload_db_confirm_message)
            .setPositiveButton(R.string.settings_cloud_btn_upload_db) { _, _ ->
                viewModel.uploadCloudSqliteDatabase()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun roleLabel(role: String): String = when (role.uppercase(Locale.ROOT)) {
        StaffMember.ROLE_OWNER -> getString(R.string.staff_role_owner)
        StaffMember.ROLE_MANAGER -> getString(R.string.staff_role_manager)
        else -> getString(R.string.staff_role_staff)
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

    private fun setupWhatsappIntegration() {
        val installed = WhatsAppIntegration.isInstalled(requireContext())
        binding.textWhatsappStatus.text = getString(
            if (installed) {
                R.string.settings_whatsapp_status_ready
            } else {
                R.string.settings_whatsapp_status_missing
            },
        )
        binding.btnOpenWhatsapp.setOnClickListener {
            if (!WhatsAppIntegration.open(requireContext())) {
                Snackbar.make(binding.root, R.string.settings_whatsapp_open_failed, Snackbar.LENGTH_LONG).show()
            }
        }
        binding.btnWhatsappHelp.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_whatsapp_how_to)
                .setMessage(R.string.settings_whatsapp_help_body)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun setupModelSettingsNav() {
        binding.btnManageModels.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_modelSettingsFragment)
        }
    }

    private fun setupButtons() {
        setupModelSettingsNav()
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
                viewModel.redownloadModel()
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

                        is SettingsViewModel.Event.CloudSettingsSaved -> {
                            Snackbar.make(
                                binding.root,
                                R.string.settings_cloud_saved,
                                Snackbar.LENGTH_SHORT,
                            ).show()
                        }

                        is SettingsViewModel.Event.CloudUploadSucceeded -> {
                            Snackbar.make(
                                binding.root,
                                R.string.settings_cloud_upload_success,
                                Snackbar.LENGTH_SHORT,
                            ).show()
                        }

                        is SettingsViewModel.Event.CloudUploadFailed -> {
                            val msg = when (event.message) {
                                SettingsViewModel.CLOUD_ERR_NOT_ENABLED ->
                                    getString(R.string.settings_cloud_not_enabled)
                                SettingsViewModel.CLOUD_ERR_MISSING_URL ->
                                    getString(R.string.settings_cloud_missing_url)
                                SettingsViewModel.CLOUD_ERR_MISSING_KEY ->
                                    getString(R.string.settings_cloud_missing_key)
                                else -> getString(R.string.settings_cloud_upload_failed, event.message)
                            }
                            Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                        }

                        is SettingsViewModel.Event.EnterpriseSyncComplete -> {
                            Snackbar.make(
                                binding.root,
                                getString(
                                    R.string.settings_enterprise_sync_done,
                                    event.sent,
                                    event.failed,
                                ),
                                Snackbar.LENGTH_LONG,
                            ).show()
                        }

                        is SettingsViewModel.Event.EnterpriseSyncFailed -> {
                            val msg = when (event.message) {
                                SettingsViewModel.CLOUD_ERR_NOT_ENABLED ->
                                    getString(R.string.settings_cloud_not_enabled)
                                SettingsViewModel.CLOUD_ERR_MISSING_URL ->
                                    getString(R.string.settings_cloud_missing_url)
                                SettingsViewModel.CLOUD_ERR_MISSING_KEY ->
                                    getString(R.string.settings_cloud_missing_key)
                                else -> getString(R.string.settings_enterprise_sync_failed, event.message)
                            }
                            Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                        }

                        is SettingsViewModel.Event.EnterpriseServiceDiscovered -> {
                            binding.inputCloudEndpoint.setText(event.endpointUrl)
                            binding.toggleEnterpriseDeploymentMode.check(R.id.btn_deployment_on_premise)
                            Snackbar.make(
                                binding.root,
                                getString(R.string.settings_enterprise_discovered, event.endpointUrl),
                                Snackbar.LENGTH_LONG,
                            ).show()
                        }

                        is SettingsViewModel.Event.EnterpriseDiscoveryFailed -> {
                            Snackbar.make(
                                binding.root,
                                R.string.settings_enterprise_discovery_failed,
                                Snackbar.LENGTH_LONG,
                            ).show()
                        }

                        is SettingsViewModel.Event.EnterpriseBranchSaved -> {
                            Snackbar.make(
                                binding.root,
                                R.string.settings_enterprise_branch_saved,
                                Snackbar.LENGTH_SHORT,
                            ).show()
                        }

                        is SettingsViewModel.Event.EnterpriseBranchInvalid -> {
                            Snackbar.make(
                                binding.root,
                                R.string.settings_enterprise_branch_invalid,
                                Snackbar.LENGTH_SHORT,
                            ).show()
                        }

                        is SettingsViewModel.Event.EnterpriseActionAllowed -> {
                            performEnterpriseAction(event.action)
                        }

                        is SettingsViewModel.Event.EnterprisePermissionDenied -> {
                            Snackbar.make(
                                binding.root,
                                getString(
                                    R.string.settings_enterprise_permission_denied,
                                    event.operatorName,
                                    roleLabel(event.operatorRole),
                                ),
                                Snackbar.LENGTH_LONG,
                            ).show()
                        }

                        is SettingsViewModel.Event.EnterpriseOperatorChanged -> {
                            Snackbar.make(
                                binding.root,
                                R.string.settings_enterprise_operator_changed,
                                Snackbar.LENGTH_SHORT,
                            ).show()
                        }

                        is SettingsViewModel.Event.EnterpriseOperatorPinRequired -> {
                            Snackbar.make(
                                binding.root,
                                R.string.staff_pin_required,
                                Snackbar.LENGTH_LONG,
                            ).show()
                        }

                        is SettingsViewModel.Event.EnterpriseOperatorPinInvalid -> {
                            Snackbar.make(
                                binding.root,
                                R.string.staff_pin_invalid,
                                Snackbar.LENGTH_SHORT,
                            ).show()
                        }

                        is SettingsViewModel.Event.LicenceApplied -> {
                            binding.editLicenceKey.text?.clear()
                            val status = if (event.proEnabled) {
                                R.string.settings_pro_features_on
                            } else {
                                R.string.settings_pro_features_off
                            }
                            Snackbar.make(
                                binding.root,
                                getString(R.string.settings_licence_applied, getString(status)),
                                Snackbar.LENGTH_LONG,
                            ).show()
                        }

                        is SettingsViewModel.Event.LicenceInvalid -> {
                            Snackbar.make(
                                binding.root,
                                event.message.ifBlank { getString(R.string.settings_licence_invalid) },
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
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isCloudUploading.collect { uploading ->
                    binding.btnCloudUploadJson.isEnabled = !uploading
                    binding.btnCloudUploadSqlite.isEnabled = !uploading
                    binding.btnCloudAnalysisSave.isEnabled = !uploading
                    binding.btnCloudUploadJson.text = if (uploading) {
                        getString(R.string.settings_cloud_uploading)
                    } else {
                        getString(R.string.settings_cloud_btn_upload_json)
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isEnterpriseSyncing.collect { syncing ->
                    binding.btnEnterpriseSyncNow.isEnabled = !syncing
                    binding.btnEnterpriseSyncNow.text = if (syncing) {
                        getString(R.string.settings_enterprise_syncing)
                    } else {
                        getString(R.string.settings_enterprise_sync_now)
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isEnterpriseDiscovering.collect { discovering ->
                    binding.btnEnterpriseDiscoverService.isEnabled = !discovering
                    binding.btnEnterpriseDiscoverService.text = if (discovering) {
                        getString(R.string.settings_enterprise_discovering)
                    } else {
                        getString(R.string.settings_enterprise_discover_service)
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

    private fun setupCloudAnalysis() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.cloudSettings.collect { s ->
                    updateEnterpriseDeploymentVisibility()
                    applyDeploymentMode(s.deploymentMode)
                    binding.switchCloudAnalysisEnabled.setOnCheckedChangeListener(null)
                    if (binding.switchCloudAnalysisEnabled.isChecked != s.enabled) {
                        binding.switchCloudAnalysisEnabled.isChecked = s.enabled
                    }
                    val url = s.endpointUrl
                    if (binding.inputCloudEndpoint.text?.toString() != url) {
                        binding.inputCloudEndpoint.setText(url)
                    }
                    binding.textCloudApiKeySaved.visibility =
                        if (s.hasApiKey) View.VISIBLE else View.GONE
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.enterpriseDevices.collect { devices ->
                    updateEnterpriseDeviceSummary(devices.size)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.enterpriseAuditEvents.collect { events ->
                    val last = events.firstOrNull()
                    binding.textEnterpriseAuditSummary.text = if (last == null) {
                        getString(R.string.settings_enterprise_audit_empty)
                    } else {
                        getString(R.string.settings_enterprise_audit_last, last.action)
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.enterpriseBranches.collect { branches ->
                    binding.textEnterpriseBranchSummary.text = resources.getQuantityString(
                        R.plurals.settings_enterprise_branches_count,
                        branches.size,
                        branches.size,
                    )
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pendingEnterpriseSyncCount.collect { count ->
                    binding.textEnterpriseSyncSummary.text = if (count == 0) {
                        getString(R.string.settings_enterprise_sync_empty)
                    } else {
                        resources.getQuantityString(
                            R.plurals.settings_enterprise_sync_pending_count,
                            count,
                            count,
                        )
                    }
                }
            }
        }
        binding.btnCloudAnalysisSave.setOnClickListener {
            viewModel.requestEnterpriseAction(SettingsViewModel.RestrictedAction.SAVE_CLOUD_SETTINGS)
        }
        binding.btnEnterpriseDiscoverService.setOnClickListener {
            viewModel.discoverEnterpriseService()
        }
        binding.btnCloudUploadJson.setOnClickListener {
            viewModel.requestEnterpriseAction(SettingsViewModel.RestrictedAction.UPLOAD_ANALYTICS_JSON)
        }
        binding.btnCloudUploadSqlite.setOnClickListener {
            viewModel.requestEnterpriseAction(SettingsViewModel.RestrictedAction.UPLOAD_SQLITE_DATABASE)
        }
        binding.btnEnterpriseActivity.setOnClickListener {
            showEnterpriseActivityDialog()
        }
        binding.btnEnterpriseSyncNow.setOnClickListener {
            viewModel.requestEnterpriseAction(SettingsViewModel.RestrictedAction.SYNC_ENTERPRISE_QUEUE)
        }
        binding.btnEnterpriseBranch.setOnClickListener {
            showEnterpriseBranchDialog()
        }
    }

    private fun updateEnterpriseDeploymentVisibility() {
        val visibility = if (viewModel.isEnterprisePro) View.VISIBLE else View.GONE
        binding.textCloudAnalysisSection.visibility = visibility
        binding.cardCloudAnalysis.visibility = visibility
        binding.textEnterpriseDeploymentTitle.visibility = visibility
        binding.textEnterpriseDeploymentBody.visibility = visibility
        binding.toggleEnterpriseDeploymentMode.visibility = visibility
        binding.textEnterpriseDeviceSummary.visibility = visibility
        binding.textEnterpriseAuditSummary.visibility = visibility
        binding.textEnterpriseBranchSummary.visibility = visibility
        binding.textEnterpriseSyncSummary.visibility = visibility
        binding.btnEnterpriseBranch.visibility = visibility
        binding.btnEnterpriseSyncNow.visibility = visibility
        binding.btnEnterpriseActivity.visibility = visibility
        binding.btnEnterpriseDiscoverService.visibility = visibility
        binding.textEnterpriseOperatorStatus.visibility = visibility
        binding.btnEnterpriseOperator.visibility = visibility
        if (!viewModel.isEnterprisePro) {
            binding.toggleEnterpriseDeploymentMode.check(R.id.btn_deployment_cloud)
        }
    }

    private fun updateEnterpriseDeviceSummary(deviceCount: Int = viewModel.enterpriseDevices.value.size) {
        val limit = viewModel.licenceKey.value?.maxDevices ?: 1
        binding.textEnterpriseDeviceSummary.text = getString(
            if (deviceCount > limit) {
                R.string.settings_enterprise_devices_over_limit
            } else {
                R.string.settings_enterprise_devices_status
            },
            deviceCount,
            limit,
        )
    }

    private fun showEnterpriseActivityDialog() {
        val devices = viewModel.enterpriseDevices.value
        val events = viewModel.enterpriseAuditEvents.value
        val branches = viewModel.enterpriseBranches.value
        val pendingSync = viewModel.pendingEnterpriseSyncCount.value
        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val body = buildString {
            append(getString(R.string.settings_enterprise_devices_section))
            append("\n")
            if (devices.isEmpty()) {
                append("- ")
                append(getString(R.string.settings_enterprise_devices_status, 0, viewModel.licenceKey.value?.maxDevices ?: 1))
            } else {
                devices.take(8).forEach { device ->
                    append("- ")
                    append(device.displayName)
                    append(" - ")
                    append(device.deploymentMode)
                    append(" - ")
                    append(dateFormat.format(Date(device.lastSeenAt)))
                    append("\n")
                }
            }
            append("\n")
            append(getString(R.string.settings_enterprise_branches_section))
            append("\n")
            if (branches.isEmpty()) {
                append("- ")
                append(resources.getQuantityString(R.plurals.settings_enterprise_branches_count, 0, 0))
                append("\n")
            } else {
                branches.take(8).forEach { branch ->
                    append("- ")
                    append(branch.name)
                    append(" (")
                    append(branch.code)
                    append(")")
                    if (branch.isDefault) append(" - default")
                    append("\n")
                }
            }
            append(
                if (pendingSync == 0) {
                    getString(R.string.settings_enterprise_sync_empty)
                } else {
                    resources.getQuantityString(
                        R.plurals.settings_enterprise_sync_pending_count,
                        pendingSync,
                        pendingSync,
                    )
                },
            )
            append("\n\n")
            append(getString(R.string.settings_enterprise_audit_section))
            append("\n")
            if (events.isEmpty()) {
                append("- ")
                append(getString(R.string.settings_enterprise_audit_empty))
            } else {
                events.take(8).forEach { event ->
                    append("- ")
                    append(event.action)
                    append(" - ")
                    append(dateFormat.format(Date(event.createdAt)))
                    append("\n")
                    append(event.summary)
                    append("\n")
                }
            }
        }.trim()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_enterprise_activity_title)
            .setMessage(body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showEnterpriseBranchDialog() {
        val current = viewModel.enterpriseBranches.value.firstOrNull { it.isDefault }
            ?: viewModel.enterpriseBranches.value.firstOrNull()
        val dialogView = layoutInflater.inflate(R.layout.dialog_enterprise_branch, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.input_enterprise_branch_name)
        val locationInput = dialogView.findViewById<TextInputEditText>(R.id.input_enterprise_branch_location)
        nameInput.setText(current?.name.orEmpty().ifBlank { "Main branch" })
        locationInput.setText(current?.location.orEmpty())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_enterprise_branch_title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.saveDefaultEnterpriseBranch(
                    name = nameInput.text?.toString().orEmpty(),
                    location = locationInput.text?.toString(),
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyDeploymentMode(mode: EnterpriseDeploymentMode) {
        binding.toggleEnterpriseDeploymentMode.check(
            when (mode) {
                EnterpriseDeploymentMode.ON_PREMISE -> R.id.btn_deployment_on_premise
                EnterpriseDeploymentMode.CLOUD -> R.id.btn_deployment_cloud
            },
        )
    }

    private fun selectedDeploymentMode(): EnterpriseDeploymentMode =
        if (binding.toggleEnterpriseDeploymentMode.checkedButtonId == R.id.btn_deployment_cloud) {
            EnterpriseDeploymentMode.CLOUD
        } else {
            EnterpriseDeploymentMode.ON_PREMISE
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
