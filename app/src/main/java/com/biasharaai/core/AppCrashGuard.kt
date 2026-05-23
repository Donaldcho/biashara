package com.biasharaai.core

import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Logs uncaught exceptions and prevents duplicate crash-handler recursion. UI-thread crashes
 * still surface through Android's fatal handler; known best-effort background subsystem failures
 * are contained so AI/TTS workers cannot close the foreground app.
 */
object AppCrashGuard {
    private const val TAG = "BiasharaCrash"
    private val installed = AtomicBoolean(false)

    fun install(defaultHandler: Thread.UncaughtExceptionHandler?) {
        if (!installed.compareAndSet(false, true)) return
        val previous = defaultHandler ?: Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught on ${thread.name}", throwable)
            if (shouldContain(thread)) {
                Log.w(TAG, "Contained background failure on ${thread.name}; foreground process stays alive")
                return@setDefaultUncaughtExceptionHandler
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun shouldContain(thread: Thread): Boolean {
        if (thread == Looper.getMainLooper().thread) return false
        val name = thread.name
        return name.startsWith("BiasharaActiveModel") ||
            name.startsWith("BiasharaTts-warmup") ||
            name.contains("Whisper", ignoreCase = true)
    }
}
