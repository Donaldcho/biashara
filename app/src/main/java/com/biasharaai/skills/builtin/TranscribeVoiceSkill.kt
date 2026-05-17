package com.biasharaai.skills.builtin

import android.content.Context
import com.biasharaai.ai.AudioCaptureHelper
import com.biasharaai.ai.VoiceInputProcessor
import com.biasharaai.skills.BiasharaSkill
import com.biasharaai.skills.SkillArgsParser
import com.biasharaai.skills.SkillResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** X7 — Microphone capture + on-device transcription when Whisper (or future Gemma audio) is available. */
@Singleton
class TranscribeVoiceSkill @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voiceInputProcessor: VoiceInputProcessor,
) : BiasharaSkill {
    override val id: String = ID
    override val displayName: String = "Transcribe voice"
    override val parameterSchemaJson: String =
        """{"type":"object","properties":{"durationMs":{"type":"integer","minimum":1000,"maximum":15000},"localeTag":{"type":"string"}}}"""

    override suspend fun execute(argumentsJson: String): SkillResult = withContext(Dispatchers.IO) {
        val args = SkillArgsParser.parseObject(argumentsJson).getOrElse {
            return@withContext SkillResult.Failure("INVALID_ARGS", it.message ?: "Invalid JSON")
        }
        val durationMs = SkillArgsParser.intArg(args, "durationMs", default = 5_000, min = 1_000, max = 15_000)
        val localeTag = SkillArgsParser.stringArg(args, "localeTag")
        val locale = localeTag?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()

        if (!voiceInputProcessor.shouldUseOnDeviceAi()) {
            return@withContext SkillResult.Failure(
                "REQUIRES_UI",
                "On-device STT requires WhisperKit to be initialised (voice settings / first load) " +
                    "or a future Gemma audio model. Otherwise use the in-app mic with system speech.",
            )
        }

        if (!AudioCaptureHelper.hasRecordPermission(context)) {
            return@withContext SkillResult.Failure(
                "PERMISSION_DENIED",
                "RECORD_AUDIO permission is required.",
            )
        }

        val text = runCatching {
            voiceInputProcessor.transcribeWithAi(locale, durationMs.toLong())
        }.getOrNull()

        if (text.isNullOrBlank()) {
            return@withContext SkillResult.Failure(
                "TRANSCRIPTION_EMPTY",
                "Could not transcribe audio.",
            )
        }

        SkillResult.successMap(
            mapOf(
                "transcript" to text,
                "localeTag" to locale.toLanguageTag(),
                "durationMs" to durationMs,
            ),
            summary = text.take(80),
        )
    }

    companion object {
        const val ID = "transcribe_voice"
    }
}
