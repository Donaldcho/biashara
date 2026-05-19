package com.biasharaai.ui.pos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.biasharaai.R
import com.biasharaai.data.local.db.StaffMember
import com.biasharaai.data.local.db.StaffMemberDao
import com.biasharaai.databinding.FragmentStaffPickerBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StaffPickerBottomSheet : BottomSheetDialogFragment() {

    @Inject lateinit var staffMemberDao: StaffMemberDao

    var onSelected: ((StaffMember?) -> Unit)? = null

    private var _binding: FragmentStaffPickerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentStaffPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val serviceName = arguments?.getString(ARG_SERVICE_NAME).orEmpty()
        binding.textTitle.text = getString(R.string.staff_picker_title, serviceName)
        binding.btnSkip.setOnClickListener {
            onSelected?.invoke(null)
            dismiss()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val staff = staffMemberDao.getActiveOnce()
            staff.forEach { member ->
                val chip = Chip(requireContext()).apply {
                    text = member.name
                    isCheckable = false
                    setOnClickListener {
                        onSelected?.invoke(member)
                        dismiss()
                    }
                }
                binding.chipGroupStaff.addView(chip)
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "StaffPickerBottomSheet"
        private const val ARG_SERVICE_NAME = "service_name"

        fun newInstance(serviceName: String): StaffPickerBottomSheet =
            StaffPickerBottomSheet().apply {
                arguments = bundleOf(ARG_SERVICE_NAME to serviceName)
            }
    }
}
