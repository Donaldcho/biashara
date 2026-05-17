package com.biasharaai.ai

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Simple energy gate: treats the owner as “still speaking” until the level stays below
 * [silenceThresholdDb] for [silenceTimeoutMs]. Does **not** end the stream until at least one
 * non-silent frame has been seen, so startup silence does not immediately stop capture.
 */
class SilenceDetector(
    private val silenceThresholdDb: Float = -40f,
    private val silenceTimeoutMs: Long = 2500L,
) {
    private var lastNonSilentAt: Long = 0L
    private var heardNonSilent: Boolean = false

    fun reset() {
        lastNonSilentAt = System.currentTimeMillis()
        heardNonSilent = false
    }

    /** `true` when silence has persisted long enough **after** speech to end recording. */
    fun process(chunk: ShortArray): Boolean {
        if (chunk.isEmpty()) return false
        val rms = sqrt(chunk.sumOf { (it * it).toDouble() } / chunk.size).toFloat()
        val db = if (rms > 0f) 20f * log10((rms / Short.MAX_VALUE).toDouble()).toFloat() else -100f
        if (db > silenceThresholdDb) {
            heardNonSilent = true
            lastNonSilentAt = System.currentTimeMillis()
        }
        if (!heardNonSilent) return false
        return System.currentTimeMillis() - lastNonSilentAt > silenceTimeoutMs
    }
}
