package com.biasharaai.productline

import android.view.View
import com.biasharaai.R
import com.google.android.material.snackbar.Snackbar

/** UX when a Shop licence hits a Pro-only control. */
fun View.showProRequiredSnackbar(productLineManager: ProductLineManager) {
    if (productLineManager.isProEnabled()) return
    Snackbar.make(this, R.string.pro_feature_required, Snackbar.LENGTH_LONG).show()
}
