package com.biasharaai.core

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Logs uncaught exceptions and prevents duplicate crash-handler recursion.
 * Does not swallow crashes — surfaces root cause in logcat for field diagnosis.
 */
object AppCrashGuard {
    private const val TAG = "BiasharaCrash"
    private val installed = AtomicBoolean(false)

    fun install(defaultHandler: Thread.UncaughtExceptionHandler?) {
        if (!installed.compareAndSet(false, true)) return
        val previous = defaultHandler ?: Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught on ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }
}
