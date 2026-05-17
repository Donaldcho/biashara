package com.biasharaai.ui.cash

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import com.biasharaai.R
import com.biasharaai.data.local.db.CashMovementEvidence
import com.biasharaai.databinding.BottomSheetEvidenceViewerBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class EvidenceViewerBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetEvidenceViewerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetEvidenceViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = arguments ?: return

        val captureMethod = args.getString(ARG_CAPTURE_METHOD) ?: "MANUAL"
        val proofType = args.getString(ARG_PROOF_TYPE) ?: "UNKNOWN"
        val confidence = args.getFloat(ARG_CONFIDENCE, 0f)
        val reference = args.getString(ARG_REFERENCE)
        val counterparty = args.getString(ARG_COUNTERPARTY)
        val rawText = args.getString(ARG_RAW_TEXT)

        binding.tvCaptureMethod.text = getString(R.string.cash_evidence_capture_method, captureMethod)
        binding.tvProofType.text = "Type: ${proofType.replace('_', ' ')}"
        binding.tvConfidence.text = getString(R.string.cash_evidence_confidence, (confidence * 100).toInt())

        binding.tvReference.isVisible = !reference.isNullOrBlank()
        binding.tvReference.text = "Reference: $reference"

        binding.tvCounterparty.isVisible = !counterparty.isNullOrBlank()
        binding.tvCounterparty.text = "From/To: $counterparty"

        val hasRaw = !rawText.isNullOrBlank()
        binding.dividerRaw.isVisible = hasRaw
        binding.labelRawText.isVisible = hasRaw
        binding.tvRawText.isVisible = hasRaw
        binding.tvRawText.text = rawText?.take(600)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "EvidenceViewerBottomSheet"

        private const val ARG_CAPTURE_METHOD = "capture_method"
        private const val ARG_PROOF_TYPE = "proof_type"
        private const val ARG_CONFIDENCE = "confidence"
        private const val ARG_REFERENCE = "reference"
        private const val ARG_COUNTERPARTY = "counterparty"
        private const val ARG_RAW_TEXT = "raw_text"

        fun fromEvidence(evidence: CashMovementEvidence) = EvidenceViewerBottomSheet().apply {
            arguments = bundleOf(
                ARG_CAPTURE_METHOD to evidence.captureMethod.name,
                ARG_PROOF_TYPE to evidence.proofType.name,
                ARG_CONFIDENCE to evidence.parserConfidence,
                ARG_REFERENCE to evidence.parsedReference,
                ARG_COUNTERPARTY to evidence.parsedCounterparty,
                ARG_RAW_TEXT to evidence.rawText,
            )
        }
    }
}
