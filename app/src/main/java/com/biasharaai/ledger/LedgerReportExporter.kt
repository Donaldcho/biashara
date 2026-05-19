package com.biasharaai.ledger

import com.biasharaai.data.local.db.LedgerEntry
import com.biasharaai.data.local.db.LedgerEntryDao
import com.biasharaai.data.local.db.LedgerEntryType
import com.biasharaai.ledger.LedgerPnLCalculator
import com.biasharaai.money.MoneyFormatter
import com.biasharaai.productline.ProductLineManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Phase 9 L7 — CSV/text export for share sheet (no PDF dependency). */
@Singleton
class LedgerReportExporter @Inject constructor(
    private val ledgerEntryDao: LedgerEntryDao,
    private val moneyFormatter: MoneyFormatter,
    private val productLineManager: ProductLineManager,
    private val ledgerPnLCalculator: LedgerPnLCalculator,
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    suspend fun buildCsvReport(from: Long, to: Long, businessName: String): String {
        val entries = ledgerEntryDao.getEntriesForReportSync(from, to)
        val sb = StringBuilder()
        sb.appendLine("Biashara AI — Business Ledger")
        sb.appendLine("Business: $businessName")
        sb.appendLine("Period: ${dateFormat.format(Date(from))} — ${dateFormat.format(Date(to))}")
        sb.appendLine()
        sb.appendLine("Date,Type,Direction,Amount,Balance,Description")
        for (e in entries) {
            sb.append(dateFormat.format(Date(e.occurredAt))).append(',')
            sb.append(e.type.name).append(',')
            sb.append(e.direction.name).append(',')
            sb.append(e.amount).append(',')
            sb.append(e.runningBalance).append(',')
            sb.append(csvEscape(e.description))
            sb.appendLine()
        }
        if (entries.isNotEmpty()) {
            val last = entries.last()
            sb.appendLine()
            sb.appendLine("Closing balance: ${moneyFormatter.format(last.runningBalance)}")
        }
        return sb.toString()
    }

    suspend fun buildSummaryText(from: Long, to: Long): String {
        val entries = ledgerEntryDao.getEntriesForReportSync(from, to)
        if (entries.isEmpty()) return "No ledger entries in this period."
        val pnl = ledgerPnLCalculator.incomeBreakdown(from, to)
        val breakdown = ledgerEntryDao.getBreakdownByType(from, to)
        return buildString {
            appendLine("Ledger summary")
            appendLine("Entries: ${entries.size}")
            appendLine("Closing balance: ${moneyFormatter.format(entries.last().runningBalance)}")
            appendLine()
            appendLine("INCOME")
            appendLine("  Product Sales    ${moneyFormatter.format(pnl.productSales)}")
            if (productLineManager.isProEnabled()) {
                appendLine("  Service Sales    ${moneyFormatter.format(pnl.serviceSales)}")
                if (pnl.voucherSales > 0) {
                    appendLine("  Vouchers Sold    ${moneyFormatter.format(pnl.voucherSales)}")
                }
            }
            appendLine("  ─────────────────")
            appendLine("  Total Income     ${moneyFormatter.format(pnl.totalIncome)}")
            appendLine()
            appendLine("Other ledger types:")
            breakdown.forEach { row ->
                if (row.type in listOf(
                        LedgerEntryType.SALE_PRODUCT.name,
                        LedgerEntryType.SALE_SERVICE.name,
                        LedgerEntryType.SALE_MIXED.name,
                        LedgerEntryType.VOUCHER_SALE.name,
                    )
                ) {
                    return@forEach
                }
                append("• ").append(row.type).append(": ")
                appendLine(moneyFormatter.format(row.total))
            }
        }
    }

    private fun csvEscape(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n')
        return if (!needsQuotes) value
        else "\"${value.replace("\"", "\"\"")}\""
    }
}
