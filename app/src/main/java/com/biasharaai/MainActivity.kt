package com.biasharaai

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.biasharaai.databinding.ActivityMainBinding
import com.biasharaai.locale.LanguagePreferences
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

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
                    R.id.homeFragment
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
                R.id.paymentDialogFragment -> View.GONE
                else -> View.VISIBLE
            }
            applyRootLayoutDirectionFromLocale()
        }

        if (navController.currentDestination?.id == R.id.languageSelectionFragment) {
            binding.bottomNav.visibility = View.GONE
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
}
