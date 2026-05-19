package com.biasharaai.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.biasharaai.R
import com.biasharaai.databinding.FragmentBusinessProfileEditBinding
import com.biasharaai.ui.base.BaseFragment
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BusinessProfileEditFragment : BaseFragment() {

    private var _binding: FragmentBusinessProfileEditBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BusinessProfileEditViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentBusinessProfileEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.btnSave.setOnClickListener { save() }
        binding.btnRedoOnboarding.setOnClickListener {
            viewModel.resetOnboarding()
            findNavController().navigate(R.id.action_businessProfileEditFragment_to_businessProfileOnboardingFragment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.profile.collect { profile -> bind(profile) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    if (event is BusinessProfileEditEvent.Saved) {
                        Snackbar.make(binding.root, R.string.business_profile_saved, Snackbar.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    }
                }
            }
        }
    }

    private fun bind(profile: com.biasharaai.data.local.db.BusinessProfile) {
        binding.editBusinessName.setText(profile.businessName)
        binding.editOwnerName.setText(profile.ownerName)
        binding.editBusinessType.setText(profile.businessType)
        binding.editPrimaryServices.setText(profile.primaryServices)
        binding.editPrimaryProducts.setText(profile.primaryProducts)
        binding.editSpecialisation.setText(profile.specialisation)
        binding.editTargetCustomer.setText(profile.targetCustomer)
        binding.editLocation.setText(profile.location)
        binding.editOpenDays.setText(profile.openDays)
        binding.editOpenHours.setText(profile.openHours)
        binding.editMainSuppliers.setText(profile.mainSuppliers)
        binding.editBusinessGoal.setText(profile.businessGoal)
        binding.editMonthlyTarget.setText(
            profile.monthlyRevenueTarget?.let { if (it > 0) it.toString() else "" } ?: "",
        )
    }

    private fun save() {
        val name = binding.editBusinessName.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            Snackbar.make(binding.root, R.string.business_profile_name_required, Snackbar.LENGTH_SHORT).show()
            return
        }
        val target = binding.editMonthlyTarget.text?.toString()?.trim()?.toDoubleOrNull()
        val current = viewModel.profile.value
        viewModel.save(
            current.copy(
                businessName = name,
                ownerName = binding.editOwnerName.text?.toString()?.trim().orEmpty(),
                businessType = binding.editBusinessType.text?.toString()?.trim()
                    ?.takeIf { it.isNotEmpty() } ?: "mixed",
                primaryServices = binding.editPrimaryServices.text?.toString()?.trim()?.ifBlank { null },
                primaryProducts = binding.editPrimaryProducts.text?.toString()?.trim()?.ifBlank { null },
                specialisation = binding.editSpecialisation.text?.toString()?.trim()?.ifBlank { null },
                targetCustomer = binding.editTargetCustomer.text?.toString()?.trim()?.ifBlank { null },
                location = binding.editLocation.text?.toString()?.trim()?.ifBlank { null },
                openDays = binding.editOpenDays.text?.toString()?.trim()?.ifBlank { null },
                openHours = binding.editOpenHours.text?.toString()?.trim()?.ifBlank { null },
                mainSuppliers = binding.editMainSuppliers.text?.toString()?.trim()?.ifBlank { null },
                businessGoal = binding.editBusinessGoal.text?.toString()?.trim()?.ifBlank { null },
                monthlyRevenueTarget = target,
            ),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
