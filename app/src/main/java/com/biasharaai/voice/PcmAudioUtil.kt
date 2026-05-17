package com.biasharaai.voice

/** PCM16 LE bytes for [com.argmaxinc.whisperkit.WhisperKit.transcribe]. */
internal fun shortsToPcm16Le(shorts: ShortArray): ByteArray {
    val out = ByteArray(shorts.size * 2)
    var i = 0
    for (s in shorts) {
        val v = s.toInt()
        out[i++] = (v and 0xff).toByte()
        out[i++] = ((v shr 8) and 0xff).toByte()
    }
    return out
}
