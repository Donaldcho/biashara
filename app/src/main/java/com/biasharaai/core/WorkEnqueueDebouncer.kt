package com.biasharaai.core

import java.util.concurrent.atomic.AtomicLong

/**
 * Coalesces bursty WorkManager enqueue calls (e.g. Room invalidation on bulk POS writes).
 */
class WorkEnqueueDebouncer(
    private val minIntervalMs: Long,
) {
    private val lastEnqueueAt = AtomicLong(0L)

    fun shouldEnqueue(nowMs: Long = System.currentTimeMillis()): Boolean {
        while (true) {
            val last = lastEnqueueAt.get()
            if (nowMs - last < minIntervalMs) return false
            if (lastEnqueueAt.compareAndSet(last, nowMs)) return true
        }
    }
}
