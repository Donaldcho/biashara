package com.biasharaai

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.biasharaai.databinding.ActivityMainBinding
import com.biasharaai.locale.LanguagePreferences
import com.biasharaai.ui.pos.ReceiptViewModel
import androidx.lifecycle.lifecycleScope
import com.biasharaai.notifications.NotificationScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Posted from the startup splash; must be removed in [onDestroy] to avoid touches / crashes after finish. */
    private val splashFadeOutRunnable = Runnable {
        if (isFinishing || isDestroyed) return@Runnable
        binding.splashOverlay.animate()
            .alpha(0f)
            .setDuration(400)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (isFinishing || isDestroyed) return@withEndAction
                binding.splashOverlay.visibility = View.GONE
                binding.splashOverlay.alpha = 1f
            }
            .start()
    }

    @Inject
    lateinit var notificationScheduler: NotificationScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        LanguagePreferences.applyPersistedLocales(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyRootLayoutDirectionFromLocale()

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        if (savedInstanceState == null) {
            val graph = navController.navInflater.inflate(R.navigation.nav_graph)
            graph.setStartDestination(
                if (LanguagePreferences.hasPersistedLocale(this)) {
                    R.id.agentFeedFragment
                } else {
                    R.id.languageSelectionFragment
                },
            )
            navController.graph = graph
        }

        binding.bottomNav.setupWithNavController(navController)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.visibility = when (destination.id) {
                R.id.languageSelectionFragment,
                R.id.barcodeScannerFragment,
                R.id.receiptScanFragment,
                R.id.receiptReviewFragment,
                R.id.labelScannerFragment,
                R.id.addEditProductFragment,
                R.id.paymentDialogFragment,
                R.id.supplierNegotiationFragment,
                R.id.negotiationGuideFragment -> View.GONE
                else -> View.VISIBLE
            }
            applyRootLayoutDirectionFromLocale()
        }

        if (navController.currentDestination?.id == R.id.languageSelectionFragment) {
            binding.bottomNav.visibility = View.GONE
        }

        runStartupSplashIfNeeded(savedInstanceState)

        handleOpenReceiptIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            try {
                notificationScheduler.flushPendingDue()
            } catch (e: Exception) {
                Log.w(TAG, "flushPendingDue failed", e)
            }
        }
    }

    override fun onDestroy() {
        if (this::binding.isInitialized) {
            binding.splashLogoIcon.animate().cancel()
            binding.splashWordmark.animate().cancel()
            binding.splashOverlay.animate().cancel()
            binding.splashOverlay.removeCallbacks(splashFadeOutRunnable)
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenReceiptIntent(intent)
    }

    private fun handleOpenReceiptIntent(intent: Intent?) {
        val tid = intent?.getLongExtra(EXTRA_OPEN_RECEIPT_TRANSACTION_ID, -1L) ?: -1L
        if (tid <= 0L) return
        intent?.removeExtra(EXTRA_OPEN_RECEIPT_TRANSACTION_ID)
        binding.root.post {
            if (isFinishing || isDestroyed) return@post
            val navHost =
                supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                    ?: return@post
            val nav = navHost.navController
            runCatching {
                nav.navigate(
                    R.id.action_global_open_receipt,
                    bundleOf(ReceiptViewModel.ARG_TRANSACTION_ID to tid),
                )
            }.onFailure { e ->
                Log.w(TAG, "Open receipt navigation failed", e)
            }
        }
    }

    private fun applyRootLayoutDirectionFromLocale() {
        val tag = LanguagePreferences.getPersistedLocaleTag(this)
            ?: AppCompatDelegate.getApplicationLocales().toLanguageTags()
                .substringBefore(',').ifBlank { "en" }
        val rtl = tag.startsWith("am")
        ViewCompat.setLayoutDirection(
            binding.root,
            if (rtl) ViewCompat.LAYOUT_DIRECTION_RTL else ViewCompat.LAYOUT_DIRECTION_LTR,
        )
    }

    private fun runStartupSplashIfNeeded(savedInstanceState: Bundle?) {
        val overlay = binding.splashOverlay
        val logo = binding.splashLogoIcon
        val wordmark = binding.splashWordmark
        if (savedInstanceState != null) {
            overlay.visibility = View.GONE
            return
        }
        overlay.alpha = 1f
        overlay.visibility = View.VISIBLE
        logo.alpha = 0f
        logo.scaleX = 0.88f
        logo.scaleY = 0.88f
        logo.translationY = 32f
        wordmark.alpha = 0f
        wordmark.translationY = 20f

        val easeOut = PathInterpolator(0.2f, 0f, 0f, 1f)
        val decel = DecelerateInterpolator()

        logo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(560)
            .setInterpolator(easeOut)
            .withEndAction {
                if (isFinishing || isDestroyed) return@withEndAction
                wordmark.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(360)
                    .setInterpolator(decel)
                    .withEndAction {
                        if (isFinishing || isDestroyed) return@withEndAction
                        overlay.postDelayed(splashFadeOutRunnable, 480)
                    }
                    .start()
            }
            .start()
    }

    companion object {
        private const val TAG = "MainActivity"

        const val EXTRA_OPEN_RECEIPT_TRANSACTION_ID: String =
            "com.biasharaai.EXTRA_OPEN_RECEIPT_TRANSACTION_ID"
    }
}
