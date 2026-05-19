package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.biasharaai.money.RegionalDefaults

/**
 * Single-row app-wide settings (POS receipts, tax, printer, voice/TTS prefs). Row `id` is always `1`.
 */
@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey
    val id: Int = 1,
    @ColumnInfo(name = "business_name", defaultValue = "My Business")
    val businessName: String = "My Business",
    @ColumnInfo(name = "currency_code", defaultValue = "XAF")
    val currencyCode: String = RegionalDefaults.CURRENCY_CODE,
    @ColumnInfo(name = "currency_symbol", defaultValue = "FCFA")
    val currencySymbol: String = RegionalDefaults.CURRENCY_SYMBOL,
    @ColumnInfo(name = "tax_rate", defaultValue = "0.0")
    val taxRate: Double = 0.0,
    @ColumnInfo(name = "tax_label", defaultValue = "Tax")
    val taxLabel: String = "Tax",
    @ColumnInfo(name = "receipt_footer", defaultValue = "Thank you!")
    val receiptFooter: String = "Thank you!",
    @ColumnInfo(name = "quick_sale_mode", defaultValue = "0")
    val quickSaleMode: Boolean = false,
    @ColumnInfo(name = "allow_price_override", defaultValue = "1")
    val allowPriceOverride: Boolean = true,
    @ColumnInfo(name = "bluetooth_printer_address")
    val bluetoothPrinterAddress: String? = null,
    @ColumnInfo(name = "printer_paper_width", defaultValue = "58")
    val printerPaperWidth: Int = 58,
    @ColumnInfo(name = "voice_input_enabled", defaultValue = "1")
    val voiceInputEnabled: Boolean = true,
    @ColumnInfo(name = "whisper_model_id", defaultValue = "whisper-tiny")
    val whisperModelId: String = "whisper-tiny",
    @ColumnInfo(name = "silence_timeout_ms", defaultValue = "2500")
    val silenceTimeoutMs: Int = 2500,
    @ColumnInfo(name = "voice_language_mode", defaultValue = "AUTO")
    val voiceLanguageMode: String = "AUTO",
    @ColumnInfo(name = "tts_enabled", defaultValue = "1")
    val ttsEnabled: Boolean = true,
    @ColumnInfo(name = "tts_speech_rate", defaultValue = "0.9")
    val ttsSpeechRate: Double = 0.9,
    @ColumnInfo(name = "tts_pitch", defaultValue = "1.0")
    val ttsPitch: Double = 1.0,
    @ColumnInfo(name = "tts_auto_read_agent_alerts", defaultValue = "0")
    val ttsAutoReadAgentAlerts: Boolean = false,
    @ColumnInfo(name = "tts_auto_read_query_answers", defaultValue = "1")
    val ttsAutoReadQueryAnswers: Boolean = true,
    @ColumnInfo(name = "pro_onboarding_shown", defaultValue = "0")
    val proOnboardingShown: Boolean = false,
    @ColumnInfo(name = "pro_activated_at")
    val proActivatedAt: Long? = null,
    /** HMAC secret for BSRC receipt QR verification (Pro). */
    @ColumnInfo(name = "voucher_signing_key")
    val voucherSigningKey: String? = null,
)
