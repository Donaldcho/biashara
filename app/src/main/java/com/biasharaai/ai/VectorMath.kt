package com.biasharaai.ai

import kotlin.math.sqrt

object VectorMath {

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must be the same length (${a.size} vs ${b.size})" }
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt((normA * normB).toDouble()).toFloat()
        return if (denom == 0f) 0f else dot / denom
    }

    fun topK(query: FloatArray, candidates: List<FloatArray>, k: Int): List<Int> {
        return candidates.indices
            .sortedByDescending { cosineSimilarity(query, candidates[it]) }
            .take(k)
    }
}
