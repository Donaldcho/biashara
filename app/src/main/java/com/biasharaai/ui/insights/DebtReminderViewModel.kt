package com.biasharaai.ui.insights

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.R
import com.biasharaai.ai.GemmaService
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.data.local.db.CustomerDao
import com.biasharaai.data.local.db.DebtDao
import com.biasharaai.locale.LanguagePreferences
import com.biasharaai.money.RegionalDefaults
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class DebtReminderDraft(
    val debtId: Long,
    val smsBody: String,
    val phone: String,
)

sealed interface DebtReminderEvent {
    data class Preview(val draft: DebtReminderDraft) : DebtReminderEvent
    data class Message(val textRes: Int) : DebtReminderEvent
}

@HiltViewModel
class DebtReminderViewModel @Inject constructor(
    private val debtDao: DebtDao,
    private val customerDao: CustomerDao,
    private val appSettingsDao: AppSettingsDao,
    private val gemmaService: GemmaService,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _events = MutableSharedFlow<DebtReminderEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<DebtReminderEvent> = _events.asSharedFlow()

    fun requestReminderDraft(debtId: Long) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val event = withContext(Dispatchers.IO) {
                    val debt = debtDao.getDebtById(debtId) ?: return@withContext DebtReminderEvent.Message(
                        R.string.credit_debt_missing,
                    )
                    if (debt.amount <= 0.0) {
                        return@withContext DebtReminderEvent.Message(R.string.credit_debt_missing)
                    }
                    val customer = customerDao.getCustomerById(debt.customerId)
                        ?: return@withContext DebtReminderEvent.Message(R.string.credit_customer_missing)
                    val phone = customer.phone?.trim().orEmpty()
                    if (phone.isEmpty()) {
                        return@withContext DebtReminderEvent.Message(R.string.credit_remind_no_phone)
                    }
                    val settings = appSettingsDao.getSettingsSync()
                    val currency = settings?.currencyCode ?: RegionalDefaults.CURRENCY_CODE
                    val daysOld = TimeUnit.MILLISECONDS.toDays(
                        (System.currentTimeMillis() - debt.createdAt).coerceAtLeast(0L),
                    ).toInt()
                    val dueFormatted = debt.dueDate?.let { ms ->
                        DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(ms))
                    }
                    val customerLanguage = languageNameForPrompt()
                    val prompt = """
Write a short, polite, friendly SMS reminder in $customerLanguage.
The message is from a small shop owner to a customer.
Customer name: ${customer.name}.
Amount owed: ${debt.amount} $currency.
Days outstanding: $daysOld.
${if (dueFormatted != null) "Payment was due on $dueFormatted." else ""}
Keep the message under 60 words. Do not use formal legal language.
For Cameroon, prefer simple French or English wording and show the amount in FCFA when the currency is XAF.
                    """.trimIndent()
                    val body = if (gemmaService.isAvailable) {
                        runCatching { gemmaService.generateResponse(prompt).trim() }
                            .getOrElse { fallbackSms(customer.name, debt.amount, currency, daysOld) }
                    } else {
                        fallbackSms(customer.name, debt.amount, currency, daysOld)
                    }
                    DebtReminderEvent.Preview(
                        DebtReminderDraft(debtId = debt.id, smsBody = body, phone = phone),
                    )
                }
                _events.emit(event)
            } finally {
                _loading.value = false
            }
        }
    }

    private fun languageNameForPrompt(): String {
        val tag = LanguagePreferences.getPersistedLocaleTag(appContext)
            ?.substringBefore("-")
            ?.lowercase(Locale.getDefault())
            ?: Locale.getDefault().language.lowercase(Locale.getDefault())
        return when (tag) {
            "sw" -> "Swahili"
            "ha" -> "Hausa"
            "yo" -> "Yoruba"
            "am" -> "Amharic"
            else -> "English"
        }
    }

    private fun fallbackSms(name: String, amount: Double, currency: String, days: Int): String =
        appContext.getString(
            R.string.credit_remind_fallback_sms,
            name,
            currency,
            amount,
            days,
        )
}
