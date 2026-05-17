package com.biasharaai.voice

import android.content.Context
import com.argmaxinc.whisperkit.ExperimentalWhisperKit
import com.argmaxinc.whisperkit.WhisperKit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whisper on-device assets layout + mapping from `app_settings.whisper_model_id` strings to
 * [WhisperKit.Builder] model keys. Actual download/load is performed by WhisperKit’s
 * [WhisperKit.loadModel].
 */
@Singleton
class WhisperModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun whisperRootDir(): File =
        File(context.filesDir, "whisper").also { it.mkdirs() }

    fun modelDir(appModelId: String): File =
        File(whisperRootDir(), appModelId.safeDirName()).also { it.mkdirs() }

    @OptIn(ExperimentalWhisperKit::class)
    fun resolveWhisperKitModelKey(appSettingId: String): String =
        when (appSettingId.lowercase().trim()) {
            "whisper-base", "openai_whisper-base" -> WhisperKit.Builder.OPENAI_BASE
            "whisper-tiny-en", "openai_whisper-tiny.en" -> WhisperKit.Builder.OPENAI_TINY_EN
            "whisper-base-en", "openai_whisper-base.en" -> WhisperKit.Builder.OPENAI_BASE_EN
            "qualcomm-tiny-en" -> WhisperKit.Builder.QUALCOMM_TINY_EN
            "qualcomm-base-en" -> WhisperKit.Builder.QUALCOMM_BASE_EN
            else -> WhisperKit.Builder.OPENAI_TINY
        }

    private fun String.safeDirName(): String = replace(Regex("[^a-zA-Z0-9._-]"), "_").take(64)
}
