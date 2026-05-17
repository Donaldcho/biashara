package com.biasharaai.ui.knowledge

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import com.biasharaai.R
import com.biasharaai.databinding.BottomSheetContextualHelpBinding
import com.biasharaai.knowledge.ContextualHelpEngine
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ContextualHelpBottomSheet : BottomSheetDialogFragment() {

    @Inject lateinit var helpEngine: ContextualHelpEngine

    private var _binding: BottomSheetContextualHelpBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetContextualHelpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val screenId = arguments?.getString(ARG_SCREEN_ID) ?: run {
            showNoContent()
            return
        }
        val languageCode = arguments?.getString(ARG_LANGUAGE_CODE) ?: "en"

        binding.btnCloseHelp.setOnClickListener { dismiss() }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val help = helpEngine.helpForScreen(screenId, languageCode)
                if (help == null) {
                    showNoContent()
                    return@repeatOnLifecycle
                }

                binding.tvHelpTitle.text = help.helpTitle
                binding.tvHelpSnippet.text = help.helpSnippet

                if (help.lessonId != null) {
                    binding.btnStartLesson.isVisible = true
                    binding.btnStartLesson.setOnClickListener {
                        dismiss()
                        navigateToLesson(help.lessonId)
                    }
                } else {
                    binding.btnStartLesson.isVisible = false
                }
            }
        }
    }

    private fun showNoContent() {
        binding.tvHelpTitle.setText(com.biasharaai.R.string.help_title)
        binding.tvHelpSnippet.setText(com.biasharaai.R.string.help_no_content)
        binding.btnStartLesson.isVisible = false
    }

    private fun navigateToLesson(lessonId: String) {
        try {
            val navHostFragment = requireActivity().supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            navHostFragment?.navController?.navigate(
                R.id.action_global_open_lesson,
                bundleOf("lessonId" to lessonId),
            )
        } catch (_: Exception) {
            // Nav not available in this context — no-op
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ContextualHelpBottomSheet"
        private const val ARG_SCREEN_ID = "screenId"
        private const val ARG_LANGUAGE_CODE = "languageCode"

        fun newInstance(screenId: String, languageCode: String = "en") =
            ContextualHelpBottomSheet().apply {
                arguments = bundleOf(
                    ARG_SCREEN_ID to screenId,
                    ARG_LANGUAGE_CODE to languageCode,
                )
            }
    }
}
