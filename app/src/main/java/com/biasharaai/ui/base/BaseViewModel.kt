package com.biasharaai.ui.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope

abstract class BaseViewModel : ViewModel() {

    /** Structured concurrency scope cancelled when this ViewModel is cleared. */
    protected val coroutineScope: CoroutineScope
        get() = viewModelScope
}
