package com.biasharaai.voice

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.biasharaai.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator

enum class MicrophoneUiState {
    IDLE,
    LISTENING,
    PROCESSING,
    ERROR,
}

/**
 * Mic control with IDLE / LISTENING / PROCESSING / ERROR states and optional inline [TranscriptionBannerView].
 */
class MicrophoneButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val micButton: MaterialButton
    private val progress: CircularProgressIndicator
    private val banner: TranscriptionBannerView
    private val showTranscriptionInline: Boolean
    private val micColor: Int
    private val idleTint: ColorStateList

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_microphone_button, this, true)
        micButton = findViewById(R.id.btn_mic)
        progress = findViewById(R.id.progress_mic)
        banner = findViewById(R.id.banner_transcription)

        var resolvedColor = 0
        var resolvedShowInline = false
        context.withStyledAttributes(attrs, R.styleable.MicrophoneButtonView, defStyleAttr, 0) {
            resolvedColor = getColor(
                R.styleable.MicrophoneButtonView_micColor,
                ContextCompat.getColor(context, R.color.voice_teal),
            )
            resolvedShowInline = getBoolean(R.styleable.MicrophoneButtonView_showTranscriptionInline, false)
        }
        micColor = resolvedColor
        showTranscriptionInline = resolvedShowInline
        idleTint = ColorStateList.valueOf(micColor)
        micButton.iconTint = idleTint
        banner.isVisible = false
        setState(MicrophoneUiState.IDLE)
    }

    fun setState(state: MicrophoneUiState) {
        when (state) {
            MicrophoneUiState.IDLE -> {
                micButton.isEnabled = true
                micButton.isInvisible = false
                progress.isVisible = false
                micButton.iconTint = idleTint
                micButton.contentDescription = context.getString(R.string.voice_ui_mic_idle)
            }
            MicrophoneUiState.LISTENING -> {
                micButton.isEnabled = true
                micButton.isInvisible = false
                progress.isVisible = false
                micButton.iconTint = ColorStateList.valueOf(micColor)
                micButton.contentDescription = context.getString(R.string.voice_ui_mic_listening)
            }
            MicrophoneUiState.PROCESSING -> {
                micButton.isEnabled = false
                micButton.isInvisible = true
                progress.isVisible = true
                micButton.contentDescription = context.getString(R.string.voice_ui_mic_processing)
            }
            MicrophoneUiState.ERROR -> {
                micButton.isEnabled = true
                micButton.isInvisible = false
                progress.isVisible = false
                micButton.iconTint = ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.biashara_red),
                )
                micButton.contentDescription = context.getString(R.string.voice_ui_mic_error)
            }
        }
    }

    fun setOnMicClickListener(listener: OnClickListener?) {
        micButton.setOnClickListener(listener)
    }

    fun showTranscriptionPartial(text: String) {
        if (!showTranscriptionInline) return
        banner.isVisible = true
        banner.showPartial(text)
    }

    fun showTranscriptionFinal(text: String) {
        if (!showTranscriptionInline) return
        banner.isVisible = true
        banner.showFinal(text)
    }

    fun clearTranscription() {
        banner.dismiss()
    }
}
