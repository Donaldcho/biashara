package com.biasharaai.ui.insights

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.biasharaai.R
import com.biasharaai.databinding.FragmentCreditBinding
import com.biasharaai.money.MoneyFormatter
import com.biasharaai.pos.cart.CartRepository
import com.biasharaai.ui.base.BaseFragment
import com.biasharaai.whatsapp.WhatsAppIntegration
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CreditFragment : BaseFragment() {

    private var _binding: FragmentCreditBinding? = null
    private val binding get() = _binding!!

    private val creditViewModel: CreditViewModel by viewModels()
    private val reminderViewModel: DebtReminderViewModel by viewModels()

    private lateinit var adapter: DebtAdapter

    @Inject
    lateinit var moneyFormatter: MoneyFormatter

    @Inject
    lateinit var cartRepository: CartRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCreditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = DebtAdapter(
            moneyFormatter = moneyFormatter,
            onPaid = { id -> creditViewModel.markPaid(id) },
            onRemind = { id -> reminderViewModel.requestReminderDraft(id) },
        )
        binding.recyclerDebts.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerDebts.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    creditViewModel.unpaidRows.collect { rows ->
                        adapter.submitList(rows)
                        val empty = rows.isEmpty()
                        binding.textCreditEmpty.visibility = if (empty) View.VISIBLE else View.GONE
                        binding.recyclerDebts.visibility = if (empty) View.GONE else View.VISIBLE
                    }
                }
                launch {
                    combine(
                        creditViewModel.totalOutstanding,
                        cartRepository.activeSettings,
                    ) { total, _ -> total }.collect { total ->
                        binding.textTotalOutstanding.text = moneyFormatter.format(total)
                    }
                }
                launch {
                    cartRepository.activeSettings
                        .map { it?.currencyCode }
                        .distinctUntilChanged()
                        .collect {
                            if (::adapter.isInitialized && adapter.itemCount > 0) {
                                adapter.notifyDataSetChanged()
                            }
                        }
                }
                launch {
                    reminderViewModel.events.collect { event ->
                        when (event) {
                            is DebtReminderEvent.Preview -> showSmsPreview(event.draft)
                            is DebtReminderEvent.Message -> Snackbar.make(
                                binding.root,
                                getString(event.textRes),
                                Snackbar.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private fun showSmsPreview(draft: DebtReminderDraft) {
        val scroll = android.widget.ScrollView(requireContext()).apply {
            val pad = resources.getDimensionPixelSize(R.dimen.screen_edge_padding)
            setPadding(pad, pad, pad, pad)
        }
        val body = TextView(requireContext()).apply {
            text = draft.smsBody
            textSize = 15f
            setPadding(0, 0, 0, padPx(8))
        }
        scroll.addView(body)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.credit_remind_preview_title)
            .setView(scroll)
            .setNeutralButton(R.string.credit_send_via_whatsapp) { _, _ ->
                if (!WhatsAppIntegration.sendText(requireContext(), draft.smsBody, draft.phone)) {
                    Snackbar.make(binding.root, R.string.credit_whatsapp_intent_failed, Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.credit_send_via_sms) { _, _ ->
                val uri = Uri.parse("smsto:${Uri.encode(draft.phone)}")
                val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                    putExtra("sms_body", draft.smsBody)
                }
                runCatching { startActivity(intent) }.onFailure {
                    Snackbar.make(binding.root, R.string.credit_sms_intent_failed, Snackbar.LENGTH_LONG).show()
                }
            }
            .show()
    }

    private fun padPx(dp: Int): Int = (resources.displayMetrics.density * dp).toInt()
}
