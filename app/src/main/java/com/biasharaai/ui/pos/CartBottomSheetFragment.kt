package com.biasharaai.ui.pos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.biasharaai.R
import com.biasharaai.databinding.FragmentCartBottomSheetBinding
import com.biasharaai.money.MoneyFormatter
import com.biasharaai.pos.cart.CartManager
import com.biasharaai.pos.cart.CartRepository
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CartBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentCartBottomSheetBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var cartManager: CartManager

    @Inject
    lateinit var cartRepository: CartRepository

    @Inject
    lateinit var moneyFormatter: MoneyFormatter

    private val posViewModel: PosViewModel by viewModels(ownerProducer = { requireParentFragment() })

    private lateinit var cartAdapter: CartAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCartBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cartAdapter = CartAdapter(
            cartManager = cartManager,
            formatMoney = { moneyFormatter.format(it) },
            allowPriceOverride = cartRepository.activeSettings.value?.allowPriceOverride != false,
            onRequestProductPriceOverride = { item ->
                CartLinePriceOverrideDialog.show(
                    this,
                    item,
                    moneyFormatter::format,
                ) { cartItem, newPrice ->
                    posViewModel.applyLinePriceOverride(cartItem, newPrice)
                }
            },
            onRequestServicePriceOverride = { line ->
                ServiceCartLinePriceOverrideDialog.show(
                    this,
                    line,
                    moneyFormatter::format,
                ) { serviceLine, newPrice ->
                    posViewModel.applyServiceLinePriceOverride(serviceLine, newPrice)
                }
            },
        )
        binding.recyclerCart.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerCart.adapter = cartAdapter
        CartAdapter.attachSwipeToRemove(binding.recyclerCart, cartAdapter, cartManager)

        binding.totalsBarSheet.bind(viewLifecycleOwner, cartRepository, moneyFormatter)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    cartRepository.activeSettings
                        .map { it?.allowPriceOverride != false }
                        .distinctUntilChanged()
                        .collect { allow ->
                            cartAdapter.setAllowPriceOverride(allow)
                            cartAdapter.notifyDataSetChanged()
                        }
                }
                launch {
                    cartManager.unifiedLines().collect { cartAdapter.submitList(it) }
                }
            }
        }

        binding.btnClearCart.setOnClickListener {
            cartManager.clear()
        }

        binding.btnPaySheet.setOnClickListener {
            parentFragment?.findNavController()?.navigate(R.id.action_posFragment_to_paymentDialogFragment)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CartBottomSheetFragment"

        fun newInstance(): CartBottomSheetFragment = CartBottomSheetFragment()
    }
}
