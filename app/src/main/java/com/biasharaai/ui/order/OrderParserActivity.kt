package com.biasharaai.ui.order

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.biasharaai.MainActivity
import com.biasharaai.R
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.databinding.ActivityOrderParserBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OrderParserActivity : AppCompatActivity() {

    @Inject
    lateinit var capabilityTier: CapabilityTier

    private lateinit var binding: ActivityOrderParserBinding
    private val viewModel: OrderParserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (capabilityTier == CapabilityTier.RULES_BASED) {
            Toast.makeText(this, R.string.order_parser_tier_rules_only_message, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding = ActivityOrderParserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.order_parser_no_text, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state -> renderState(state) }
                }
                launch {
                    viewModel.events.collect { ev ->
                        when (ev) {
                            is OrderParserEvent.Toast ->
                                Toast.makeText(this@OrderParserActivity, ev.message, Toast.LENGTH_LONG).show()
                            is OrderParserEvent.SaleRecorded -> openReceiptAndFinish(ev.transactionId)
                        }
                    }
                }
            }
        }

        if (savedInstanceState == null) {
            viewModel.startParse(text)
        } else if (viewModel.uiState.value is OrderParserUiState.Ready) {
            showReviewIfNeeded()
        }
    }

    private fun renderState(state: OrderParserUiState) {
        when (state) {
            is OrderParserUiState.Loading -> {
                binding.panelLoading.isVisible = true
                binding.reviewContainer.isVisible = false
            }
            is OrderParserUiState.Ready -> {
                binding.panelLoading.isVisible = false
                binding.reviewContainer.isVisible = true
                showReviewIfNeeded()
            }
            is OrderParserUiState.Error -> {
                val msg = mapError(state.message)
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun mapError(code: String): String = when (code) {
        "EMPTY" -> getString(R.string.order_parser_no_text)
        "NO_MODEL" -> getString(R.string.order_parser_error_no_model)
        "PARSE_FAILED" -> getString(R.string.order_parser_error_parse)
        "OFFLINE" -> getString(R.string.order_parser_offline_message)
        else -> code
    }

    private fun showReviewIfNeeded() {
        if (supportFragmentManager.findFragmentById(R.id.review_container) != null) return
        supportFragmentManager.beginTransaction()
            .replace(R.id.review_container, OrderReviewFragment())
            .commit()
    }

    private fun openReceiptAndFinish(transactionId: Long) {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_RECEIPT_TRANSACTION_ID, transactionId)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
        )
        finish()
    }
}
