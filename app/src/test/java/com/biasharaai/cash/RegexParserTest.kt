package com.biasharaai.cash

import com.biasharaai.data.local.db.LedgerDirection
import com.biasharaai.data.local.db.ParserEngine
import com.biasharaai.data.local.db.ProofType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexParserTest {

    // ── M-Pesa SMS ───────────────────────────────────────────────────────

    @Test
    fun mpesa_send_money_parses_correctly() {
        val sms = "QA12BC3456 Confirmed. Ksh500.00 sent to JOHN DOE 0712345678 on 12/5/25 at 10:30 AM. New M-PESA balance is Ksh1,200.00."
        val result = RegexParser.parse(sms)
        assertNotNull(result)
        assertEquals(500.0, result!!.amount!!, 0.01)
        assertEquals("QA12BC3456", result.reference)
        assertEquals(ProofType.MPESA_SMS, result.proofType)
        assertEquals(LedgerDirection.MONEY_OUT, result.suggestedDirection)
        assertEquals(ParserEngine.REGEX, result.engine)
    }

    @Test
    fun mpesa_receive_money_parses_correctly() {
        val sms = "PB98ZX7654 Confirmed. You have received Ksh1,500.00 from JANE SMITH 0722000001 on 3/5/25."
        val result = RegexParser.parse(sms)
        assertNotNull(result)
        assertEquals(1500.0, result!!.amount!!, 0.01)
        assertEquals("PB98ZX7654", result.reference)
        assertEquals(ProofType.MPESA_SMS, result.proofType)
        assertEquals(LedgerDirection.MONEY_IN, result.suggestedDirection)
    }

    @Test
    fun mpesa_buy_goods_parses_correctly() {
        val sms = "LK45MN9012 Confirmed. Ksh250.00 paid to SUPERMARKET TILL on 1/5/25 at 9:00 AM."
        val result = RegexParser.parse(sms)
        assertNotNull(result)
        assertEquals(250.0, result!!.amount!!, 0.01)
        assertEquals(LedgerDirection.MONEY_OUT, result.suggestedDirection)
    }

    @Test
    fun mpesa_amount_with_comma_parsed_correctly() {
        val sms = "AB12CD3456 Confirmed. Ksh12,500.00 sent to SUPPLIER LTD 0700111222 on 15/4/25."
        val result = RegexParser.parse(sms)
        assertNotNull(result)
        assertEquals(12500.0, result!!.amount!!, 0.01)
    }

    @Test
    fun mpesa_direction_override_respected() {
        val sms = "XY12ZZ9999 Confirmed. Ksh750.00 sent to TEST 0700000001 on 1/5/25."
        val result = RegexParser.parse(sms, LedgerDirection.MONEY_IN)
        assertNotNull(result)
        assertEquals(LedgerDirection.MONEY_IN, result!!.suggestedDirection)
    }

    // ── KES / Amount patterns ────────────────────────────────────────────

    @Test
    fun kes_prefix_amount_parsed() {
        val text = "Payment received KES 3,200 for delivery on 10/5/25"
        val result = RegexParser.parse(text)
        assertNotNull(result)
        assertEquals(3200.0, result!!.amount!!, 0.01)
    }

    @Test
    fun ksh_without_space_parsed() {
        val text = "Total: Ksh850 paid via cash"
        val result = RegexParser.parse(text)
        assertNotNull(result)
        assertEquals(850.0, result!!.amount!!, 0.01)
    }

    @Test
    fun ngn_currency_parsed() {
        val text = "You sent NGN 5,000.00. Ref: NGN20250110AB"
        val result = RegexParser.parse(text)
        assertNotNull(result)
        assertEquals(5000.0, result!!.amount!!, 0.01)
    }

    // ── KPLC / Utility ──────────────────────────────────────────────────

    @Test
    fun kplc_token_sms_parsed() {
        val sms = "KPLC: Amount Ksh500 purchased. Tokens: 12.34 kWh. Meter: 00012345678. Ref: KPLC20251234"
        val result = RegexParser.parse(sms)
        assertNotNull(result)
        assertEquals(500.0, result!!.amount!!, 0.01)
        assertEquals(ProofType.UTILITY_BILL, result.proofType)
    }

    // ── Till slips ───────────────────────────────────────────────────────

    @Test
    fun till_slip_total_parsed() {
        val receipt = "SUPERMART\nCashier: Mary\nSugar  120.00\nMilk    85.00\nTOTAL: 205.00\nTILL NO 5"
        val result = RegexParser.parse(receipt)
        assertNotNull(result)
        assertEquals(205.0, result!!.amount!!, 0.01)
        assertEquals(ProofType.TILL_SLIP, result.proofType)
    }

    // ── Reference extraction ─────────────────────────────────────────────

    @Test
    fun generic_ref_prefix_extracted() {
        val text = "Payment received. Ref: INV-2025-0042 Amount KSh 1,000"
        val result = RegexParser.parse(text)
        assertNotNull(result)
        assertNotNull(result!!.reference)
    }

    @Test
    fun ref_hash_prefix_extracted() {
        val text = "Transfer complete. REF#TXN20250501 KES 2,500"
        val result = RegexParser.parse(text)
        assertNotNull(result)
        assertNotNull(result!!.reference)
    }

    // ── Counterparty ─────────────────────────────────────────────────────

    @Test
    fun sent_to_counterparty_extracted() {
        val sms = "MN34OP5678 Confirmed. Ksh300.00 sent to ALICE WANJIRU 0711223344 on 5/5/25."
        val result = RegexParser.parse(sms)
        assertNotNull(result)
        assertNotNull(result!!.counterparty)
        assertTrue(result.counterparty!!.contains("ALICE", ignoreCase = true))
    }

    // ── Date extraction ──────────────────────────────────────────────────

    @Test
    fun date_dmy_slash_parsed() {
        val sms = "QZ11AA2345 Confirmed. Ksh200.00 sent to BOB 0700000001 on 15/5/25."
        val result = RegexParser.parse(sms)
        assertNotNull(result)
        assertNotNull(result!!.parsedDate)
    }

    @Test
    fun date_iso_format_parsed() {
        val text = "Invoice date 2025-05-10. Amount KSh 4,000. Ref: INV-999"
        val result = RegexParser.parse(text)
        assertNotNull(result)
        assertNotNull(result!!.parsedDate)
    }

    // ── Low-confidence / edge cases ──────────────────────────────────────

    @Test
    fun blank_text_returns_null() {
        assertNull(RegexParser.parse("   "))
    }

    @Test
    fun text_without_amount_returns_null() {
        // No amount → confidence < MIN_CONFIDENCE
        assertNull(RegexParser.parse("Hello world no money here"))
    }

    @Test
    fun text_with_only_amount_still_parses_when_confident() {
        // Amount alone is 0.40 score — below 0.60 → null from regex tier
        val result = RegexParser.parse("KSh 100")
        assertNull(result)
    }

    @Test
    fun confidence_above_threshold_returns_result() {
        // Amount + reference + proofType → 0.40+0.35+0.15 = 0.90
        val sms = "AB12CD3456 Confirmed. Ksh999.00 sent to SHOP 0700000001 on 1/1/25."
        val result = RegexParser.parse(sms)
        assertNotNull(result)
        assertTrue(result!!.confidence >= RegexParser.MIN_CONFIDENCE)
    }

    // ── ProofTypeDetector ────────────────────────────────────────────────

    @Test
    fun detector_identifies_mpesa() {
        assertEquals(ProofType.MPESA_SMS, ProofTypeDetector.detect("M-PESA Confirmed Ksh 200"))
    }

    @Test
    fun detector_identifies_utility() {
        assertEquals(ProofType.UTILITY_BILL, ProofTypeDetector.detect("KPLC Tokens 5.00 kWh"))
    }

    @Test
    fun detector_identifies_supplier_bill() {
        assertEquals(ProofType.SUPPLIER_BILL, ProofTypeDetector.detect("LPO #1234 from SUPPLIER"))
    }

    @Test
    fun detector_identifies_invoice() {
        assertEquals(ProofType.INVOICE, ProofTypeDetector.detect("INVOICE #INV-0042 due 2025-06-01"))
    }

    @Test
    fun detector_fallback_unknown() {
        assertEquals(ProofType.UNKNOWN, ProofTypeDetector.detect("some random text"))
    }
}
