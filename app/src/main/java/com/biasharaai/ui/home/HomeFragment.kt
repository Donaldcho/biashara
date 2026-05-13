package com.biasharaai.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.biasharaai.R
import com.biasharaai.data.local.db.Alert
import com.biasharaai.data.local.db.LossAlertTypes
import com.biasharaai.databinding.FragmentHomeBinding
import com.biasharaai.ui.base.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : BaseFragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    private val alertAdapter = AlertCardAdapter(
        onReview = { alert -> navigateForAlert(alert) },
        onDismiss = { viewModel.dismissAlert(it.id) },
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerLossAlerts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = alertAdapter
            isNestedScrollingEnabled = false
            itemAnimator = null
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.activeLossAlerts.collect { alerts ->
                    binding.cardLossAlertsSection.isVisible = alerts.isNotEmpty()
                    alertAdapter.submitList(alerts)
                }
            }
        }
    }

    private fun navigateForAlert(alert: Alert) {
        val nav = findNavController()
        when (alert.alertType) {
            LossAlertTypes.SHRINKAGE, LossAlertTypes.SALES_GAP -> {
                val pid = alert.productId
                if (pid != null) {
                    nav.navigate(R.id.addEditProductFragment, bundleOf("product_id" to pid))
                } else {
                    nav.navigate(R.id.inventoryListFragment)
                }
            }
            LossAlertTypes.LOW_PRICE -> {
                val tid = alert.relatedTransactionId
                if (tid != null) {
                    nav.navigate(R.id.receiptFragment, bundleOf("transaction_id" to tid))
                } else {
                    nav.navigate(R.id.insightsFragment)
                }
            }
            LossAlertTypes.HIGH_EXPENSE -> nav.navigate(R.id.insightsFragment)
            else -> nav.navigate(R.id.inventoryListFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
