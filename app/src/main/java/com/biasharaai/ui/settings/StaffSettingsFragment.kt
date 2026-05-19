package com.biasharaai.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
import com.biasharaai.ui.base.BaseFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
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
        )
        binding.recyclerStaff.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerStaff.adapter = adapter
        binding.btnAddStaff.setOnClickListener { showAddDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.staff.collect { adapter.submitList(it) }
            }
        }
    }

    private fun showAddDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.staff_name_hint)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.staff_add_button)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    Snackbar.make(binding.root, R.string.staff_name_required, Snackbar.LENGTH_SHORT).show()
                } else {
                    viewModel.addStaff(name)
                }
            }
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private class StaffAdapter(
        private val onDeactivate: (StaffMember) -> Unit,
    ) : androidx.recyclerview.widget.ListAdapter<StaffMember, StaffAdapter.VH>(
        object : androidx.recyclerview.widget.DiffUtil.ItemCallback<StaffMember>() {
            override fun areItemsTheSame(a: StaffMember, b: StaffMember) = a.id == b.id
            override fun areContentsTheSame(a: StaffMember, b: StaffMember) = a == b
        },
    ) {
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
            holder.binding.textName.text = member.name
            holder.binding.textRole.text = member.role
            holder.binding.btnDeactivate.setOnClickListener { onDeactivate(member) }
        }
    }
}
