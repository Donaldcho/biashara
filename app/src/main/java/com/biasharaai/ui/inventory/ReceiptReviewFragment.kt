package com.biasharaai.ui.inventory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.biasharaai.R
import com.biasharaai.databinding.FragmentReceiptReviewBinding
import com.biasharaai.receipt.ReceiptLineItem
import com.biasharaai.ui.base.BaseFragment
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ReceiptReviewFragment : BaseFragment() {

    private var _binding: FragmentReceiptReviewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReceiptReviewViewModel by viewModels()

    private val lines = mutableListOf<ReceiptDraftLine>()
    private lateinit var adapter: ReceiptReviewAdapter
    private var showFallbackBannerFromJsonError: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentReceiptReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lines.clear()
        lines.addAll(buildInitialLines())

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        adapter = ReceiptReviewAdapter(
            onLineUpdated = { pos, _ ->
                adapter.notifyItemChanged(pos, ReceiptReviewAdapter.PAYLOAD_STROKE)
                updateCommitLabel()
            },
            onDeleteLine = { pos ->
                if (lines.size <= 1) {
                    Snackbar.make(binding.root, R.string.receipt_review_cannot_delete_last, Snackbar.LENGTH_SHORT).show()
                    return@ReceiptReviewAdapter
                }
                lines.removeAt(pos)
                adapter.submitList(lines.toList())
                updateCommitLabel()
            },
        )
        binding.recyclerLines.adapter = adapter
        adapter.submitList(lines.toList())

        val fallback = arguments?.getBoolean(ReceiptScanFragment.ARG_FALLBACK) == true
        binding.textFallbackMessage.visibility = if (fallback) View.VISIBLE else View.GONE

        binding.btnCommit.setOnClickListener { commitInventory() }
        updateCommitLabel()
    }

    private fun buildInitialLines(): List<ReceiptDraftLine> {
        val fallback = arguments?.getBoolean(ReceiptScanFragment.ARG_FALLBACK) == true
        if (fallback) {
            return listOf(ReceiptDraftLine.emptyRow())
        }
        val json = arguments?.getString(ReceiptScanFragment.ARG_LINES_JSON).orEmpty()
        return try {
            val parsed = Gson().fromJson(json, Array<ReceiptLineItem>::class.java).toList()
            if (parsed.isEmpty()) {
                listOf(ReceiptDraftLine.emptyRow())
            } else {
                parsed.map { ReceiptDraftLine.fromParsed(it) }
            }
        } catch (_: Exception) {
            showFallbackBannerFromJsonError = true
            listOf(ReceiptDraftLine.emptyRow())
        }
    }

    private fun updateCommitLabel() {
        val n = lines.count { it.isCompleteForSave() }
        binding.btnCommit.text = getString(R.string.receipt_review_commit, n)
        val allFilled = lines.isNotEmpty() && lines.all { it.isCompleteForSave() }
        binding.btnCommit.isEnabled = allFilled
    }

    private fun commitInventory() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = viewModel.insertParsedLines(lines)
            result.fold(
                onSuccess = { count ->
                    Snackbar.make(
                        binding.root,
                        getString(R.string.receipt_review_success, count),
                        Snackbar.LENGTH_SHORT,
                    ).show()
                    findNavController().popBackStack(R.id.inventoryListFragment, inclusive = false)
                },
                onFailure = { e ->
                    val msg = when (e.message) {
                        "no_valid_lines" -> getString(R.string.receipt_review_no_valid_lines)
                        else -> e.message ?: getString(R.string.receipt_review_commit_failed)
                    }
                    Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                },
            )
        }
    }

    override fun onDestroyView() {
        binding.recyclerLines.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
