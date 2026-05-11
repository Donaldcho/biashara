package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row app-wide settings (POS receipts, tax, printer). Row `id` is always `1`.
 */
@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey
    val id: Int = 1,
    @ColumnInfo(name = "business_name", defaultValue = "My Business")
    val businessName: String = "My Business",
    @ColumnInfo(name = "currency_code", defaultValue = "KES")
    val currencyCode: String = "KES",
    @ColumnInfo(name = "currency_symbol", defaultValue = "KSh")
    val currencySymbol: String = "KSh",
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
)
