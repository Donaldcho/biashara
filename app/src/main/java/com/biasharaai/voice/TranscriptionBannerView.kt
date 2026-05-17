package com.biasharaai.voice

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.content.withStyledAttributes
import androidx.core.view.isVisible
import com.biasharaai.R
import com.google.android.material.card.MaterialCardView

/**
 * Shows streaming or final transcription text with dismiss. Final text auto-hides after a short delay.
 */
class TranscriptionBannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val card: MaterialCardView
    private val hideRunnable = Runnable {
        isVisible = false
        transcriptionText = ""
    }

    var onDismissListener: (() -> Unit)? = null
    private var transcriptionText: String = ""

    init {
        LayoutInflater.from(context).inflate(R.layout.view_transcription_banner, this, true)
        card = findViewById(R.id.card_banner)
        findViewById<android.widget.ImageButton>(R.id.btn_dismiss).setOnClickListener {
            removeCallbacks(hideRunnable)
            isVisible = false
            transcriptionText = ""
            onDismissListener?.invoke()
        }

        context.withStyledAttributes(attrs, R.styleable.TranscriptionBannerView, defStyleAttr, 0) {
            val bg = getColor(R.styleable.TranscriptionBannerView_bannerBackgroundColor, 0)
            if (bg != 0) card.setCardBackgroundColor(bg)
        }
    }

    fun setTranscriptionText(text: String) {
        transcriptionText = text
        findViewById<android.widget.TextView>(R.id.text_transcription).text = text
    }

    fun showPartial(text: String) {
        removeCallbacks(hideRunnable)
        setTranscriptionText(text)
        isVisible = true
    }

    fun showFinal(text: String) {
        removeCallbacks(hideRunnable)
        setTranscriptionText(text)
        isVisible = true
        postDelayed(hideRunnable, FINAL_HIDE_MS)
    }

    fun dismiss() {
        removeCallbacks(hideRunnable)
        isVisible = false
        transcriptionText = ""
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hideRunnable)
        super.onDetachedFromWindow()
    }

    companion object {
        private const val FINAL_HIDE_MS = 2200L
    }
}
