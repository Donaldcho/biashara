package com.biasharaai.ui.knowledge

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.biasharaai.databinding.FragmentLessonBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LessonFragment : Fragment() {

    private var _binding: FragmentLessonBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LessonViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLessonBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val lessonId = requireArguments().getString(ARG_LESSON_ID)
        if (lessonId.isNullOrBlank()) {
            findNavController().navigateUp()
            return
        }
        viewModel.loadLesson(lessonId)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.btnNext.setOnClickListener { viewModel.advance() }
        binding.btnSkip.setOnClickListener { viewModel.skip(); findNavController().navigateUp() }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    render(state)
                }
            }
        }
    }

    private fun render(state: LessonViewModel.UiState) {
        when (state) {
            is LessonViewModel.UiState.Loading -> {
                binding.progressBar.isVisible = false
                binding.tvStepCount.isVisible = false
                binding.tvInstruction.text = ""
                binding.chipNavigationHint.isVisible = false
                binding.btnNext.isEnabled = false
            }
            is LessonViewModel.UiState.Step -> {
                binding.toolbar.title = state.lessonTitle
                binding.progressBar.progress = state.progressPercent
                binding.tvStepCount.text =
                    getString(com.biasharaai.R.string.lesson_step_count, state.stepNumber, state.totalSteps)
                binding.tvInstruction.text = state.instruction
                binding.chipNavigationHint.isVisible = state.navigationHint != null
                binding.chipNavigationHint.text = state.navigationHint ?: ""
                binding.btnNext.isEnabled = true
                binding.btnNext.setText(
                    if (state.isLastStep) com.biasharaai.R.string.lesson_done
                    else com.biasharaai.R.string.lesson_next,
                )
                binding.btnSkip.isVisible = !state.isLastStep
            }
            is LessonViewModel.UiState.Complete -> {
                findNavController().navigateUp()
            }
            is LessonViewModel.UiState.Error -> {
                findNavController().navigateUp()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_LESSON_ID = "lessonId"
    }
}
