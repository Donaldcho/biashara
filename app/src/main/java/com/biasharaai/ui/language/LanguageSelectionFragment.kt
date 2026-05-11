package com.biasharaai.ui.language

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.biasharaai.R
import com.biasharaai.databinding.FragmentLanguageSelectionBinding
import com.biasharaai.locale.LanguagePreferences
import com.biasharaai.ui.base.BaseFragment

class LanguageSelectionFragment : BaseFragment() {

    private var _binding: FragmentLanguageSelectionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLanguageSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnEnglish.setOnClickListener { selectLocale("en") }
        binding.btnSwahili.setOnClickListener { selectLocale("sw") }
        binding.btnHausa.setOnClickListener { selectLocale("ha") }
        binding.btnYoruba.setOnClickListener { selectLocale("yo") }
        binding.btnAmharic.setOnClickListener { selectLocale("am") }
    }

    private fun selectLocale(languageTag: String) {
        val context = requireContext().applicationContext
        LanguagePreferences.persistLocale(context, languageTag)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
        val options = NavOptions.Builder()
            .setPopUpTo(R.id.languageSelectionFragment, true)
            .build()
        findNavController().navigate(R.id.homeFragment, null, options)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
