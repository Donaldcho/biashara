package com.biasharaai.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.speech.tts.TextToSpeech
import android.widget.ArrayAdapter
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.biasharaai.R
import com.biasharaai.ai.VoiceInputPreferences
import com.biasharaai.data.local.db.AppSettings
import com.biasharaai.databinding.FragmentVoiceSettingsBinding
import com.biasharaai.ui.base.BaseFragment
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@AndroidEntryPoint
class VoiceSettingsFragment : BaseFragment() {

    private var _binding: FragmentVoiceSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VoiceSettingsViewModel by viewModels()

    @Inject
    lateinit var voiceInputPreferences: VoiceInputPreferences

    private lateinit var whisperModelIds: Array<String>
    private lateinit var whisperModelLabels: Array<String>
    private lateinit var voiceLangValues: Array<String>
    private lateinit var voiceLangLabels: Array<String>

    private var bindingFromState = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentVoiceSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        whisperModelIds = resources.getStringArray(R.array.voice_whisper_model_ids)
        whisperModelLabels = resources.getStringArray(R.array.voice_whisper_model_labels)
        voiceLangValues = resources.getStringArray(R.array.voice_language_mode_values)
        voiceLangLabels = resources.getStringArray(R.array.voice_language_mode_labels)

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.dropdownWhisperModel.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, whisperModelLabels),
        )
        binding.dropdownVoiceLanguage.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, voiceLangLabels),
        )

        binding.dropdownWhisperModel.setOnItemClickListener { _, _, position, _ ->
            if (bindingFromState || position !in whisperModelIds.indices) return@setOnItemClickListener
            viewModel.setWhisperModelId(whisperModelIds[position])
        }

        binding.dropdownVoiceLanguage.setOnItemClickListener { _, _, position, _ ->
            if (bindingFromState || position !in voiceLangValues.indices) return@setOnItemClickListener
            viewModel.setVoiceLanguageMode(voiceLangValues[position])
        }

        binding.sliderSilenceMs.addOnChangeListener { _, value, _ ->
            if (!bindingFromState) {
                binding.textSilenceValue.text = getString(
                    R.string.voice_settings_silence_summary,
                    value / 1000f,
                )
            }
        }

        binding.sliderTtsRate.addOnChangeListener { _, value, _ ->
            if (!bindingFromState) {
                binding.textTtsRateValue.text = getString(R.string.voice_settings_tts_rate_value, value)
            }
        }

        binding.sliderTtsPitch.addOnChangeListener { _, value, _ ->
            if (!bindingFromState) {
                binding.textTtsPitchValue.text = getString(R.string.voice_settings_tts_pitch_value, value)
            }
        }

        binding.sliderSilenceMs.addOnSliderTouchListener(
            object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {}

                override fun onStopTrackingTouch(slider: Slider) {
                    if (bindingFromState) return
                    viewModel.setSilenceTimeoutMs(slider.value.roundToInt())
                }
            },
        )

        binding.sliderTtsRate.addOnSliderTouchListener(
            object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {}

                override fun onStopTrackingTouch(slider: Slider) {
                    if (bindingFromState) return
                    viewModel.setTtsSpeechRate(slider.value.toDouble())
                }
            },
        )

        binding.sliderTtsPitch.addOnSliderTouchListener(
            object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {}

                override fun onStopTrackingTouch(slider: Slider) {
                    if (bindingFromState) return
                    viewModel.setTtsPitch(slider.value.toDouble())
                }
            },
        )

        binding.switchVoiceInput.setOnCheckedChangeListener { _, isChecked ->
            if (bindingFromState) return@setOnCheckedChangeListener
            viewModel.setVoiceInputEnabled(isChecked)
        }

        binding.switchTtsEnabled.setOnCheckedChangeListener { _, isChecked ->
            if (bindingFromState) return@setOnCheckedChangeListener
            viewModel.setTtsEnabled(isChecked)
            refreshTtsControlsEnabled(isChecked)
        }

        binding.switchTtsAutoAgent.setOnCheckedChangeListener { _, isChecked ->
            if (bindingFromState) return@setOnCheckedChangeListener
            viewModel.setTtsAutoReadAgentAlerts(isChecked)
        }

        binding.switchTtsAutoChat.setOnCheckedChangeListener { _, isChecked ->
            if (bindingFromState) return@setOnCheckedChangeListener
            viewModel.setTtsAutoReadQueryAnswers(isChecked)
        }

        binding.btnPrepareWhisper.setOnClickListener { viewModel.prepareWhisperModel() }

        binding.btnInstallTtsData.setOnClickListener {
            startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
        }

        binding.btnTestTts.setOnClickListener { viewModel.playTestUtterance() }

        observeSettings()
        observeVoiceInputPref()
        observePreparingWhisper()
        observeEvents()
        refreshWhisperStatusUi()
    }

    override fun onResume() {
        super.onResume()
        refreshWhisperStatusUi()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun observeSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect { s ->
                    if (s != null) bindAppSettings(s)
                }
            }
        }
    }

    private fun observeVoiceInputPref() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                voiceInputPreferences.voiceInputEnabled.collect { enabled ->
                    bindingFromState = true
                    binding.switchVoiceInput.isChecked = enabled
                    bindingFromState = false
                }
            }
        }
    }

    private fun observePreparingWhisper() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.preparingWhisper.collect { preparing ->
                    binding.btnPrepareWhisper.isEnabled = !preparing
                    binding.btnPrepareWhisper.text = if (preparing) {
                        getString(R.string.voice_settings_whisper_preparing)
                    } else {
                        getString(R.string.voice_settings_whisper_prepare)
                    }
                }
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    val anchor = binding.root
                    when (event) {
                        VoiceSettingsViewModel.Event.WhisperReady -> {
                            Snackbar.make(anchor, R.string.voice_settings_whisper_ready, Snackbar.LENGTH_SHORT)
                                .show()
                            refreshWhisperStatusUi()
                        }
                        is VoiceSettingsViewModel.Event.WhisperPrepareFailed -> {
                            Snackbar.make(
                                anchor,
                                getString(R.string.voice_settings_whisper_failed, event.message),
                                Snackbar.LENGTH_LONG,
                            ).show()
                            refreshWhisperStatusUi()
                        }
                    }
                }
            }
        }
    }

    private fun bindAppSettings(s: AppSettings) {
        bindingFromState = true

        val wIdx = whisperModelIds.indexOf(s.whisperModelId).let { if (it >= 0) it else 0 }
        binding.dropdownWhisperModel.setText(whisperModelLabels[wIdx], false)

        val modeKey = s.voiceLanguageMode.trim().let { m ->
            if (m.equals("auto", ignoreCase = true)) "AUTO" else m.lowercase(Locale.US)
        }
        val langIdx = voiceLangValues.indexOfFirst {
            it.equals(modeKey, ignoreCase = true)
        }.let { if (it >= 0) it else 0 }
        binding.dropdownVoiceLanguage.setText(voiceLangLabels[langIdx], false)

        val silence = s.silenceTimeoutMs.toFloat().coerceIn(
            binding.sliderSilenceMs.valueFrom,
            binding.sliderSilenceMs.valueTo,
        )
        binding.sliderSilenceMs.value = silence
        binding.textSilenceValue.text = getString(
            R.string.voice_settings_silence_summary,
            silence / 1000f,
        )

        binding.switchTtsEnabled.isChecked = s.ttsEnabled
        refreshTtsControlsEnabled(s.ttsEnabled)

        val rate = s.ttsSpeechRate.toFloat().coerceIn(
            binding.sliderTtsRate.valueFrom,
            binding.sliderTtsRate.valueTo,
        )
        binding.sliderTtsRate.value = rate
        binding.textTtsRateValue.text = getString(R.string.voice_settings_tts_rate_value, rate)

        val pitch = s.ttsPitch.toFloat().coerceIn(
            binding.sliderTtsPitch.valueFrom,
            binding.sliderTtsPitch.valueTo,
        )
        binding.sliderTtsPitch.value = pitch
        binding.textTtsPitchValue.text = getString(R.string.voice_settings_tts_pitch_value, pitch)

        binding.switchTtsAutoAgent.isChecked = s.ttsAutoReadAgentAlerts
        binding.switchTtsAutoChat.isChecked = s.ttsAutoReadQueryAnswers

        bindingFromState = false
    }

    private fun refreshTtsControlsEnabled(ttsEnabled: Boolean) {
        binding.sliderTtsRate.isEnabled = ttsEnabled
        binding.sliderTtsPitch.isEnabled = ttsEnabled
        binding.switchTtsAutoAgent.isEnabled = ttsEnabled
        binding.switchTtsAutoChat.isEnabled = ttsEnabled
        binding.btnTestTts.isEnabled = ttsEnabled
    }

    private fun refreshWhisperStatusUi() {
        val ready = viewModel.whisperIsReady()
        binding.textWhisperStatus.text = if (ready) {
            getString(R.string.voice_settings_whisper_status_ready)
        } else {
            getString(R.string.voice_settings_whisper_status_not_loaded)
        }
    }
}
