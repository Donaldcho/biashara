package com.biasharaai.ai

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * On-device sentence embedding engine backed by MiniLM-L6-v2 (384-dim, ~22 MB).
 *
 * The TFLite model file must be present at [MODEL_ASSET_PATH] inside the APK assets.
 * [initialize] returns false if the asset is missing (e.g. during K0 before the model
 * is bundled); callers must check [isLoaded] before calling [embed].
 *
 * Tokenizer: K0 ships a hash-based approximation so the inference pipeline compiles and
 * runs. Full WordPiece tokenizer is introduced in K2/K3 with the knowledge ingestor.
 */
class EmbeddingEngine {

    private var interpreter: Interpreter? = null

    val isLoaded: Boolean get() = interpreter != null

    fun initialize(context: Context): Boolean {
        return runCatching {
            val bytes = context.assets.open(MODEL_ASSET_PATH).use { it.readBytes() }
            val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
                order(ByteOrder.nativeOrder())
                put(bytes)
                rewind()
            }
            interpreter = Interpreter(buffer)
            true
        }.getOrDefault(false)
    }

    /**
     * Returns a 384-element embedding vector for [text].
     * Returns a zero vector if the engine is not loaded.
     */
    fun embed(text: String): FloatArray {
        val interp = interpreter ?: return FloatArray(EMBEDDING_DIM)
        val tokenIds = tokenize(text)
        val attentionMask = IntArray(MAX_SEQ_LEN) { i -> if (tokenIds[i] != 0) 1 else 0 }
        val tokenTypeIds = IntArray(MAX_SEQ_LEN) { 0 }

        // MiniLM-L6-v2 expects three [1, MAX_SEQ_LEN] int32 inputs.
        val inputs = arrayOf<Any>(
            Array(1) { tokenIds },
            Array(1) { attentionMask },
            Array(1) { tokenTypeIds },
        )
        // Output: last_hidden_state [1, MAX_SEQ_LEN, EMBEDDING_DIM]
        val lastHiddenState = Array(1) { Array(MAX_SEQ_LEN) { FloatArray(EMBEDDING_DIM) } }
        val outputs = mapOf(0 to lastHiddenState as Any)
        interp.runForMultipleInputsOutputs(inputs, outputs)
        // Use CLS token (index 0) as the sentence embedding.
        return lastHiddenState[0][0]
    }

    fun serialize(vector: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(vector.size * Float.SIZE_BYTES).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            vector.forEach { putFloat(it) }
        }
        return buf.array()
    }

    fun deserialize(bytes: ByteArray): FloatArray {
        val buf = ByteBuffer.wrap(bytes).apply { order(ByteOrder.LITTLE_ENDIAN) }
        return FloatArray(bytes.size / Float.SIZE_BYTES) { buf.getFloat() }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    // K0 simplified tokenizer: maps words to stable IDs via hashCode.
    // WordPiece vocabulary lookup added in K2 alongside KnowledgeIngestor.
    private fun tokenize(text: String): IntArray {
        val words = text.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val ids = IntArray(MAX_SEQ_LEN)
        ids[0] = CLS_TOKEN_ID
        words.take(MAX_SEQ_LEN - 2).forEachIndexed { i, word ->
            ids[i + 1] = (word.hashCode() and 0x7FFF).coerceAtLeast(1)
        }
        return ids
    }

    companion object {
        const val EMBEDDING_DIM = 384
        const val MODEL_ASSET_PATH = "models/minilm-l6-v2.tflite"
        private const val MAX_SEQ_LEN = 128
        private const val CLS_TOKEN_ID = 101
    }
}
