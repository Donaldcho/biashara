package com.biasharaai.ui.settings

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.biasharaai.R
import com.biasharaai.data.local.db.StaffMember
import com.biasharaai.databinding.FragmentStaffSettingsBinding
import com.biasharaai.databinding.ItemStaffMemberBinding
import com.biasharaai.enterprise.EnterpriseRolePermissions
import com.biasharaai.ui.base.BaseFragment
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StaffSettingsFragment : BaseFragment() {

    private var _binding: FragmentStaffSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StaffSettingsViewModel by viewModels()

    private lateinit var adapter: StaffAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentStaffSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        adapter = StaffAdapter(
            onDeactivate = { viewModel.deactivate(it) },
            onChangeRole = { showRoleDialog(it) },
            onSetOperator = { showOperatorPinDialog(it) },
            onSetPin = { showSetPinDialog(it, selectAfterSave = false) },
        )
        binding.recyclerStaff.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerStaff.adapter = adapter
        binding.btnAddStaff.setOnClickListener { showAddDialog() }
        binding.btnClearDeviceOperator.setOnClickListener { viewModel.clearDeviceOperator() }
        binding.textRolePermissions.text = buildRolePermissionSummary()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.staff.collect { adapter.submitList(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        StaffSettingsViewModel.Event.StaffSaved -> {
                            Snackbar.make(binding.root, R.string.staff_saved, Snackbar.LENGTH_SHORT).show()
                        }
                        StaffSettingsViewModel.Event.RoleUpdated -> {
                            Snackbar.make(binding.root, R.string.staff_role_updated, Snackbar.LENGTH_SHORT).show()
                        }
                        StaffSettingsViewModel.Event.StaffDeactivated -> {
                            Snackbar.make(binding.root, R.string.staff_deactivated, Snackbar.LENGTH_SHORT).show()
                        }
                        StaffSettingsViewModel.Event.OperatorSelected -> {
                            Snackbar.make(binding.root, R.string.staff_operator_selected, Snackbar.LENGTH_SHORT).show()
                        }
                        StaffSettingsViewModel.Event.OperatorCleared -> {
                            Snackbar.make(binding.root, R.string.staff_operator_cleared, Snackbar.LENGTH_SHORT).show()
                        }
                        StaffSettingsViewModel.Event.PinSaved -> {
                            Snackbar.make(binding.root, R.string.staff_pin_saved, Snackbar.LENGTH_SHORT).show()
                        }
                        StaffSettingsViewModel.Event.PinRequired -> {
                            Snackbar.make(binding.root, R.string.staff_pin_required, Snackbar.LENGTH_SHORT).show()
                        }
                        StaffSettingsViewModel.Event.InvalidPin -> {
                            Snackbar.make(binding.root, R.string.staff_pin_invalid, Snackbar.LENGTH_SHORT).show()
                        }
                        StaffSettingsViewModel.Event.InvalidPinFormat -> {
                            Snackbar.make(binding.root, R.string.staff_pin_format_invalid, Snackbar.LENGTH_SHORT).show()
                        }
                        StaffSettingsViewModel.Event.InvalidName -> {
                            Snackbar.make(binding.root, R.string.staff_name_required, Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedOperator.collect { operator ->
                    adapter.setSelectedOperatorId(operator?.id)
                    binding.textDeviceOperator.text = if (operator == null) {
                        getString(R.string.staff_operator_none)
                    } else {
                        getString(
                            R.string.staff_operator_current,
                            operator.name,
                            roleLabel(operator.role),
                        )
                    }
                    binding.btnClearDeviceOperator.visibility = if (operator == null) {
                        View.GONE
                    } else {
                        View.VISIBLE
                    }
                }
            }
        }
    }

    private fun showAddDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_staff_member, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.input_staff_name)
        val roleToggle = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.toggle_staff_role)
        val permissions = dialogView.findViewById<TextView>(R.id.text_staff_role_permissions)
        roleToggle.check(R.id.btn_staff_role_staff)
        updateDialogPermissionText(permissions, StaffMember.ROLE_STAFF)
        roleToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                updateDialogPermissionText(permissions, roleFromToggle(checkedId))
            }
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.staff_add_button)
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val name = nameInput.text?.toString().orEmpty()
            if (name.isBlank()) {
                nameInput.error = getString(R.string.staff_name_required)
                return@setOnClickListener
            }
            viewModel.addStaff(
                name = name,
                role = roleFromToggle(roleToggle.checkedButtonId),
            )
            dialog.dismiss()
        }
    }

    private fun showRoleDialog(member: StaffMember) {
        val roles = EnterpriseRolePermissions.supportedRoles
        val checked = roles.indexOf(EnterpriseRolePermissions.normalizeRole(member.role)).coerceAtLeast(0)
        var selected = roles[checked]
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.staff_role_change_title, member.name))
            .setSingleChoiceItems(roleLabels(), checked) { _, which ->
                selected = roles[which]
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.updateRole(member, selected)
            }
            .show()
    }

    private fun showOperatorPinDialog(member: StaffMember) {
        if (member.pinHash.isNullOrBlank() || member.pinSalt.isNullOrBlank()) {
            showSetPinDialog(member, selectAfterSave = true)
            return
        }
        val input = pinInput().apply {
            hint = getString(R.string.staff_pin_hint)
        }
        val container = pinDialogContainer(input)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.staff_pin_enter_title, member.name))
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val pin = input.text?.toString().orEmpty()
            if (!isPinFormatValid(pin)) {
                input.error = getString(R.string.staff_pin_format_invalid)
                return@setOnClickListener
            }
            viewModel.selectDeviceOperator(member, pin)
            dialog.dismiss()
        }
    }

    private fun showSetPinDialog(member: StaffMember, selectAfterSave: Boolean) {
        val pin = pinInput().apply {
            hint = getString(R.string.staff_pin_hint)
        }
        val confirm = pinInput().apply {
            hint = getString(R.string.staff_pin_confirm_hint)
        }
        val container = pinDialogContainer(pin, confirm)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.staff_pin_set_title, member.name))
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val pinText = pin.text?.toString().orEmpty()
            val confirmText = confirm.text?.toString().orEmpty()
            if (!isPinFormatValid(pinText)) {
                pin.error = getString(R.string.staff_pin_format_invalid)
                return@setOnClickListener
            }
            if (pinText != confirmText) {
                confirm.error = getString(R.string.staff_pin_mismatch)
                return@setOnClickListener
            }
            viewModel.setStaffPin(member, pinText, selectAfterSave)
            dialog.dismiss()
        }
    }

    private fun pinDialogContainer(vararg inputs: EditText): LinearLayout =
        LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val padding = resources.getDimensionPixelSize(R.dimen.pos_dialog_padding)
            setPadding(padding, padding, padding, 0)
            inputs.forEachIndexed { index, input ->
                if (index > 0) {
                    input.setPadding(input.paddingLeft, padding / 2, input.paddingRight, input.paddingBottom)
                }
                addView(input)
            }
        }

    private fun pinInput(): EditText =
        EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            maxLines = 1
        }

    private fun isPinFormatValid(pin: String): Boolean =
        pin.length in 4..8 && pin.all { it.isDigit() }

    private fun roleLabels(): Array<String> =
        EnterpriseRolePermissions.supportedRoles.map { roleLabel(it) }.toTypedArray()

    private fun roleLabel(role: String): String = when (EnterpriseRolePermissions.normalizeRole(role)) {
        StaffMember.ROLE_OWNER -> getString(R.string.staff_role_owner)
        StaffMember.ROLE_MANAGER -> getString(R.string.staff_role_manager)
        else -> getString(R.string.staff_role_staff)
    }

    private fun roleLabel(context: android.content.Context, role: String): String =
        when (EnterpriseRolePermissions.normalizeRole(role)) {
            StaffMember.ROLE_OWNER -> context.getString(R.string.staff_role_owner)
            StaffMember.ROLE_MANAGER -> context.getString(R.string.staff_role_manager)
            else -> context.getString(R.string.staff_role_staff)
        }

    private fun roleFromToggle(checkedId: Int): String = when (checkedId) {
        R.id.btn_staff_role_owner -> StaffMember.ROLE_OWNER
        R.id.btn_staff_role_manager -> StaffMember.ROLE_MANAGER
        else -> StaffMember.ROLE_STAFF
    }

    private fun updateDialogPermissionText(view: TextView, role: String) {
        view.text = when (EnterpriseRolePermissions.normalizeRole(role)) {
            StaffMember.ROLE_OWNER -> getString(R.string.staff_permissions_owner)
            StaffMember.ROLE_MANAGER -> getString(R.string.staff_permissions_manager)
            else -> getString(R.string.staff_permissions_staff)
        }
    }

    private fun buildRolePermissionSummary(): String {
        fun line(role: String, permissions: String): String =
            "${roleLabel(role)}: $permissions"
        return listOf(
            line(StaffMember.ROLE_OWNER, getString(R.string.staff_permissions_owner)),
            line(StaffMember.ROLE_MANAGER, getString(R.string.staff_permissions_manager)),
            line(StaffMember.ROLE_STAFF, getString(R.string.staff_permissions_staff)),
        ).joinToString("\n")
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private inner class StaffAdapter(
        private val onDeactivate: (StaffMember) -> Unit,
        private val onChangeRole: (StaffMember) -> Unit,
        private val onSetOperator: (StaffMember) -> Unit,
        private val onSetPin: (StaffMember) -> Unit,
    ) : androidx.recyclerview.widget.ListAdapter<StaffMember, StaffAdapter.VH>(
        object : androidx.recyclerview.widget.DiffUtil.ItemCallback<StaffMember>() {
            override fun areItemsTheSame(a: StaffMember, b: StaffMember) = a.id == b.id
            override fun areContentsTheSame(a: StaffMember, b: StaffMember) = a == b
        },
    ) {
        private var selectedOperatorId: Long? = null

        fun setSelectedOperatorId(id: Long?) {
            selectedOperatorId = id
            notifyDataSetChanged()
        }

        inner class VH(val binding: ItemStaffMemberBinding) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemStaffMemberBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val member = getItem(position)
            val context = holder.itemView.context
            holder.binding.textName.text = member.name
            holder.binding.textRole.text = roleLabel(context, member.role)
            holder.binding.btnRole.setOnClickListener { onChangeRole(member) }
            holder.binding.btnDeviceOperator.isEnabled = member.id != selectedOperatorId
            holder.binding.btnDeviceOperator.text = if (member.id == selectedOperatorId) {
                context.getString(R.string.staff_operator_active)
            } else {
                context.getString(R.string.staff_use_on_device)
            }
            holder.binding.btnDeviceOperator.setOnClickListener { onSetOperator(member) }
            holder.binding.btnPin.text = if (member.pinHash.isNullOrBlank()) {
                context.getString(R.string.staff_pin_set_button)
            } else {
                context.getString(R.string.staff_pin_change_button)
            }
            holder.binding.btnPin.setOnClickListener { onSetPin(member) }
            holder.binding.btnDeactivate.setOnClickListener { onDeactivate(member) }
        }
    }
}
