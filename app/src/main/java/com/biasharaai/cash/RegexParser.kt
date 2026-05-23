package com.biasharaai.cash

import com.biasharaai.data.local.db.LedgerDirection
import com.biasharaai.data.local.db.LedgerEntryType
import com.biasharaai.data.local.db.ParserEngine
import com.biasharaai.data.local.db.ProofType
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Zero-network, zero-AI regex tier of the 3-tier cash parser.
 *
 * Returns null when confidence is below MIN_CONFIDENCE so higher tiers can escalate.
 */
object RegexParser {

    const val MIN_CONFIDENCE = 0.60f

    // ── Amount patterns ─────────────────────────────────────────────────

    private val AMOUNT_PATTERNS = listOf(
        // FCFA 1 234 / XAF 1234 / 1,234 FCFA
        Regex("""(?:FCFA|XAF|CFA)\s*([\d\s,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        // KSh 1,234.56 / Ksh1234 / KES 500
        Regex("""(?:KSh|Ksh|KES)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        // NGN / ₦
        Regex("""(?:NGN|₦)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        // ETB / Birr
        Regex("""(?:ETB|Birr)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        // Amount: 1,234.56
        Regex("""(?:Amount|Amt)[:\s]+([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        // Total 1234.00 (till slips)
        Regex("""Total[:\s]+([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        // bare number with currency suffix e.g. "1,234 KES"
        Regex("""([\d\s,]+(?:\.\d{1,2})?)\s*(?:FCFA|XAF|CFA|KES|KSh|NGN|ETB)""", RegexOption.IGNORE_CASE),
    )

    // ── Reference patterns ──────────────────────────────────────────────

    private val MPESA_REF = Regex("""([A-Z0-9]{10})\s+Confirmed""", RegexOption.IGNORE_CASE)
    private val TRANSACTION_ID_REF = Regex("""Transaction\s*(?:ID|Id|No|Number)?[:\s#]+([A-Z0-9]{6,24})""", RegexOption.IGNORE_CASE)
    private val MTN_REF = Regex("""Txn\s*ID[:\s#]+([A-Z0-9]{6,24})""", RegexOption.IGNORE_CASE)
    private val GENERIC_REF = Regex("""(?:Ref|Reference|REF)[.:\s#]+([A-Z0-9\-/]{4,20})""", RegexOption.IGNORE_CASE)

    // ── Date patterns ───────────────────────────────────────────────────

    // SimpleDateFormat is not thread-safe; use ThreadLocal so each thread gets its own instances.
    private val DATE_FORMATS: ThreadLocal<List<SimpleDateFormat>> = ThreadLocal.withInitial {
        listOf(
            SimpleDateFormat("d/M/yy", Locale.US),
            SimpleDateFormat("d/M/yyyy", Locale.US),
            SimpleDateFormat("dd-MM-yyyy", Locale.US),
            SimpleDateFormat("dd.MM.yyyy", Locale.US),
            SimpleDateFormat("d MMM yyyy", Locale.US),
            SimpleDateFormat("d MMM yy", Locale.US),
            SimpleDateFormat("yyyy-MM-dd", Locale.US),
        )
    }

    private val DATE_PATTERN = Regex(
        """(\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}|\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+\d{2,4}|\d{4}-\d{2}-\d{2})""",
        RegexOption.IGNORE_CASE,
    )

    // ── Confirmation signatures ─────────────────────────────────────────

    private val MPESA_CONFIRMED = Regex(
        """[A-Z0-9]{10}\s+Confirmed\.\s+(?:Ksh|KES)\s*([\d,]+(?:\.\d{2})?)""",
        RegexOption.IGNORE_CASE,
    )

    private val AIRTEL_CONFIRMED = Regex(
        """(?:AIRTEL MONEY|AIRTELMONEY).*?(?:KES|KSh)\s*([\d,]+(?:\.\d{2})?).*?Transaction\s+ID[:\s]+([A-Z0-9]{8,16})""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    private val MTN_MOMO = Regex(
        """(?:MTN\s+(?:MoMo|Mobile\s+Money)|MTN).*?(?:FCFA|XAF|CFA|UGX|RWF|GHS)\s*([\d\s,]+(?:\.\d{1,2})?).*?(?:Txn\s*ID|Transaction\s*ID|Ref)[:\s#]+([A-Z0-9]{6,24})""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    private val ORANGE_MONEY = Regex(
        """(?:Orange\s+Money|OrangeMoney|ORANGE).*?(?:FCFA|XAF|CFA)\s*([\d\s,]+(?:\.\d{1,2})?).*?(?:Transaction\s*ID|Txn\s*ID|Ref)[:\s#]+([A-Z0-9]{6,24})""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    private val TILL_TOTAL = Regex(
        """(?:TOTAL|Total\s+Due)[:\s]+([\d,]+(?:\.\d{2})?)""",
        RegexOption.IGNORE_CASE,
    )

    // KPLC token purchase SMS
    private val KPLC_AMOUNT = Regex(
        """(?:Amount|Ksh)\s*([\d,]+(?:\.\d{2})?).*?(?:tokens?|units?|kWh)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    // ── Counterparty ────────────────────────────────────────────────────

    // "sent to JOHN DOE 0712345678" / "paid by JANE SMITH"
    private val SENT_TO = Regex("""sent\s+to\s+([A-Z ]{2,30})\s+\d""", RegexOption.IGNORE_CASE)
    private val RECEIVED_FROM = Regex("""(?:received\s+from|paid\s+by)\s+([A-Z ]{2,30})""", RegexOption.IGNORE_CASE)
    private val FROM_PATTERN = Regex("""from\s+([A-Za-z][A-Za-z ]{1,28})\s""")

    // ── Direction hints ─────────────────────────────────────────────────

    private val OUT_KEYWORDS = setOf("SENT", "PAID TO", "PAYMENT TO", "PURCHASE", "WITHDRAW", "BOUGHT")
    private val IN_KEYWORDS  = setOf("RECEIVED", "DEPOSIT", "CREDIT", "YOU HAVE RECEIVED", "PAID BY")

    // ── Public API ──────────────────────────────────────────────────────

    fun parse(rawText: String, direction: LedgerDirection? = null): ParsedFields? {
        if (rawText.isBlank()) return null
        val text = rawText.trim()
        val upper = text.uppercase()

        val proofType = ProofTypeDetector.detect(text)
        val amount = extractAmount(text, proofType)
        val reference = extractReference(text, proofType)
        val counterparty = extractCounterparty(text)
        val parsedDate = extractDate(text)

        val confidence = computeConfidence(amount, reference, counterparty, proofType, parsedDate, upper)
        if (confidence < MIN_CONFIDENCE) return null

        val dir = direction ?: inferDirection(upper)

        return ParsedFields(
            amount = amount,
            reference = reference,
            counterparty = counterparty,
            proofType = proofType,
            parsedDate = parsedDate,
            confidence = confidence,
            engine = ParserEngine.REGEX,
            suggestedDirection = dir,
            suggestedType = inferType(proofType, dir),
        )
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private fun extractAmount(text: String, proofType: ProofType): Double? {
        val patterns: List<Regex> = when (proofType) {
            ProofType.MPESA_SMS -> listOf(MPESA_CONFIRMED) + AMOUNT_PATTERNS
            ProofType.MOBILE_MONEY_SMS -> listOf(MTN_MOMO, ORANGE_MONEY, AIRTEL_CONFIRMED) + AMOUNT_PATTERNS
            ProofType.UTILITY_BILL -> listOf(KPLC_AMOUNT) + AMOUNT_PATTERNS
            ProofType.TILL_SLIP -> listOf(TILL_TOTAL) + AMOUNT_PATTERNS
            else -> AMOUNT_PATTERNS
        }
        for (pattern in patterns) {
            val m = pattern.find(text) ?: continue
            val raw = (m.groupValues.getOrNull(1) ?: m.value)
                .replace(",", "")
                .replace(" ", "")
            return raw.toDoubleOrNull()?.takeIf { it > 0 }
        }
        return null
    }

    private fun extractReference(text: String, proofType: ProofType): String? {
        val patterns = when (proofType) {
            ProofType.MPESA_SMS -> listOf(MPESA_REF, TRANSACTION_ID_REF, GENERIC_REF)
            ProofType.MOBILE_MONEY_SMS -> listOf(MTN_REF, TRANSACTION_ID_REF, GENERIC_REF)
            else -> listOf(TRANSACTION_ID_REF, MTN_REF, GENERIC_REF)
        }
        for (p in patterns) {
            val m = p.find(text) ?: continue
            val ref = m.groupValues.getOrNull(1)?.trim()?.uppercase()
            if (!ref.isNullOrBlank()) return ref
        }
        return null
    }

    private fun extractCounterparty(text: String): String? {
        for (p in listOf(SENT_TO, RECEIVED_FROM, FROM_PATTERN)) {
            val m = p.find(text) ?: continue
            val name = m.groupValues.getOrNull(1)?.trim()
            if (!name.isNullOrBlank() && name.length >= 2) return name
        }
        return null
    }

    private fun extractDate(text: String): Long? {
        val m = DATE_PATTERN.find(text) ?: return null
        val dateStr = m.value
        val formats = DATE_FORMATS.get() ?: return null
        for (fmt in formats) {
            runCatching { fmt.parse(dateStr)?.time }
                .getOrNull()
                ?.let { return it }
        }
        return null
    }

    private fun computeConfidence(
        amount: Double?,
        reference: String?,
        counterparty: String?,
        proofType: ProofType,
        parsedDate: Long?,
        upper: String,
    ): Float {
        var score = 0f
        if (amount != null) score += 0.40f
        if (reference != null) score += 0.35f
        if (counterparty != null) score += 0.10f
        if (proofType != ProofType.UNKNOWN) score += 0.20f
        if (parsedDate != null) score += 0.10f
        if (hasTransactionCue(upper)) score += 0.20f
        return score.coerceIn(0f, 1f)
    }

    private fun hasTransactionCue(upper: String): Boolean =
        listOf("PAYMENT", "PAID", "RECEIVED", "SENT", "CASH", "DELIVERY", "PURCHASE", "TOTAL")
            .any { upper.contains(it) }

    private fun inferDirection(upper: String): LedgerDirection {
        if (OUT_KEYWORDS.any { upper.contains(it) }) return LedgerDirection.MONEY_OUT
        if (IN_KEYWORDS.any { upper.contains(it) }) return LedgerDirection.MONEY_IN
        return LedgerDirection.MONEY_IN
    }

    private fun inferType(proofType: ProofType, direction: LedgerDirection): LedgerEntryType =
        when {
            proofType == ProofType.SUPPLIER_BILL -> LedgerEntryType.STOCK_PURCHASE
            proofType == ProofType.INVOICE -> LedgerEntryType.SALE_PRODUCT
            proofType == ProofType.UTILITY_BILL -> LedgerEntryType.EXPENSE
            direction == LedgerDirection.MONEY_OUT -> LedgerEntryType.EXPENSE
            else -> LedgerEntryType.OTHER_INCOME
        }
}
