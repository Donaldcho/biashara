package com.biasharaai.ui.base

import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

abstract class BaseFragment : Fragment() {

    /**
     * Safe UI updates from coroutine collectors — skips work after [onDestroyView] cleared binding.
     */
    protected inline fun <B : ViewBinding> withViewBinding(
        bindingProvider: () -> B?,
        block: (B) -> Unit,
    ) {
        bindingProvider()?.let(block)
    }
}