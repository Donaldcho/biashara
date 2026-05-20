package com.biasharaai.ui.base

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class BaseViewModel : ViewModel() {

    /** Structured concurrency scope cancelled when this ViewModel is cleared. */
    protected val coroutineScope: CoroutineScope
        get() = viewModelScope

    private val crashHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(viewModelLogTag(), "Coroutine failed", throwable)
    }

    protected open fun viewModelLogTag(): String = javaClass.simpleName

    /** Launches on [viewModelScope]; logs failures instead of crashing the process. */
    protected fun launchSafe(block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch(Dispatchers.IO + crashHandler) { block() }
    }
}