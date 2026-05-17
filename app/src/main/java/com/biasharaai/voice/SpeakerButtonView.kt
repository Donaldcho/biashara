package com.biasharaai.voice

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.speech.tts.TextToSpeech
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.content.withStyledAttributes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.biasharaai.R
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Reads the bound text via [BiasharaTtsEngine]. Resolves the engine from a Hilt [Activity] host.
 * Call [bindSpeakTarget] whenever the message text or locale changes.
 */
class SpeakerButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val button: MaterialButton
    private var boundText: String = ""
    private var boundLanguageCode: String? = null
    private var boundQueueMode: Int = TextToSpeech.QUEUE_FLUSH
    private var speakingCollectJob: Job? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_speaker_button, this, true)
        button = findViewById(R.id.btn_speaker)
        context.withStyledAttributes(attrs, R.styleable.SpeakerButtonView, defStyleAttr, 0) {
            val tint = getColor(R.styleable.SpeakerButtonView_speakerTint, 0)
            if (tint != 0) {
                button.iconTint = ColorStateList.valueOf(tint)
            }
        }
        button.setOnClickListener { onSpeakerClicked() }
        refreshButtonEnabled()
    }

    fun bindSpeakTarget(
        text: String,
        languageCode: String? = null,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
    ) {
        boundText = text
        boundLanguageCode = languageCode
        boundQueueMode = queueMode
        refreshButtonEnabled()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        refreshButtonEnabled()
    }

    private fun refreshButtonEnabled() {
        button.isEnabled = isEnabled && boundText.isNotBlank()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val owner = findViewTreeLifecycleOwner() ?: return
        val engine = resolveTts() ?: return
        speakingCollectJob = owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                engine.isSpeaking.collectLatest { speaking ->
                    button.contentDescription = if (speaking) {
                        context.getString(R.string.voice_ui_speak_stop)
                    } else {
                        context.getString(R.string.voice_ui_speak)
                    }
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        speakingCollectJob?.cancel()
        speakingCollectJob = null
        super.onDetachedFromWindow()
    }

    private fun resolveTts(): BiasharaTtsEngine? {
        val activity = context.findActivity() ?: return null
        return runCatching {
            EntryPointAccessors.fromActivity(activity, VoiceUiTtsEntryPoint::class.java)
                .biasharaTtsEngine()
        }.getOrNull()
    }

    private fun onSpeakerClicked() {
        val engine = resolveTts() ?: return
        val owner = findViewTreeLifecycleOwner() ?: return
        val text = boundText.trim()
        if (text.isEmpty()) return
        owner.lifecycleScope.launch {
            when {
                boundQueueMode == TextToSpeech.QUEUE_ADD -> {
                    engine.speak(text, boundLanguageCode, TextToSpeech.QUEUE_ADD)
                }
                engine.isSpeaking.value -> engine.stop()
                else -> engine.speak(text, boundLanguageCode, boundQueueMode)
            }
        }
    }
}
