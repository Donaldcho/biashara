package com.biasharaai.ui.insights

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.biasharaai.R
import com.biasharaai.databinding.FragmentPlaceholderBinding
import com.biasharaai.ui.base.BaseFragment

class InsightsFragment : BaseFragment() {

    private var _binding: FragmentPlaceholderBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPlaceholderBinding.inflate(inflater, container, false)
        binding.placeholderTitle.setText(R.string.nav_insights)
        binding.placeholderSubtitle.setText(R.string.placeholder_screen_body)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
