package com.example.billing.utils

import com.example.billing.model.Bill

/** Pakistan ka code. Dusre mulk ke liye yahan badal dein. */
const val DEFAULT_COUNTRY_CODE = "92"

/** Dialog me dikhne wale mulk (label, code). "Other" dialog khud add karta hai. */
class WaCountry(val label: String, val code: String)

val waCountries = listOf(
    WaCountry("PK", "92"),
    WaCountry("UAE", "971"),
    WaCountry("UK", "44"),
    WaCountry("US/CA", "1"),
    WaCountry("KSA", "966"),
    WaCountry("Oman", "968"),
    WaCountry("Qatar", "974"),
)

/**
 * Bill par likha number -> WhatsApp ka number (country code ke saath, bina '+').
 *   "0300-1234567"    -> "923001234567"   (0 se shuru = Pakistan)
 *   "+971501234567"   -> "971501234567"   (+ ho to country code maujood hai)
 *   "00447911123456"  -> "447911123456"
 *   "971501234567"    -> "971501234567"
 */
fun normalizeWhatsAppNumber(raw: String): String {
    val trimmed = raw.trim()
    val digits = trimmed.filter { it.isDigit() }
    return when {
        trimmed.startsWith("+") -> digits
        digits.startsWith("00") -> digits.drop(2)
        digits.startsWith("0") -> DEFAULT_COUNTRY_CODE + digits.drop(1)
        digits.length == 10 && digits.startsWith("3") -> DEFAULT_COUNTRY_CODE + digits
        else -> digits
    }
}

/** WhatsApp number sahi lagta hai? (E.164: 8 se 15 digit; Pakistan ke liye 92 + 3xxxxxxxxx) */
fun isValidWhatsAppNumber(e164: String): Boolean {
    if (e164.length !in 8..15 || e164.any { !it.isDigit() }) return false
    if (e164.startsWith("92")) return e164.length == 12 && e164[2] == '3'
    return true
}

/** Mulk chun kar likha hua local number -> poora number. ("UAE", "050 123 4567") -> "971501234567" */
fun buildNumberFromCountry(countryCode: String, local: String): String {
    val digits = local.filter { it.isDigit() }.trimStart('0')
    // Agar user ne country code samet poora number likh diya ho to dobara na lagayen
    if (digits.startsWith(countryCode) && digits.length >= countryCode.length + 7) return digits
    return countryCode + digits
}

/** "Other" ke liye: user poora number country code ke saath likhta hai. */
fun buildNumberFromFull(raw: String): String {
    val d = raw.filter { it.isDigit() }
    return if (d.startsWith("00")) d.drop(2) else d
}

/** Bill me wapas save karne ke liye: Pakistan ka number "03xx..." me, baqi "+971..." me. */
fun toStoredPhone(e164: String): String =
    if (e164.startsWith("92") && e164.length == 12) "0" + e164.drop(2) else "+$e164"

/** Number (E.164, bina +) aur message se WhatsApp ka link. Android par app khulti hai, PC par WhatsApp Web. */
fun whatsAppUrlFor(e164: String, message: String): String =
    "https://wa.me/$e164?text=${urlEncode(message)}"

/** Purani call ke liye: bill par likha number seedha use karta hai. */
fun whatsAppUrl(phone: String, message: String): String =
    whatsAppUrlFor(normalizeWhatsAppNumber(phone), message)

/** Bill ka chhota text summary (WhatsApp *star* se bold hota hai). */
fun buildBillMessage(bill: Bill): String = buildString {
    if (Branding.licensedTo.isNotBlank()) appendLine("*${Branding.licensedTo}*")
    appendLine("Invoice #${bill.id}")
    appendLine("Date: ${displayDate(bill.date)}")
    appendLine("Customer: ${bill.customer}")
    appendLine()

    for (room in bill.rooms) {
        val valid = room.validItems
        if (valid.isEmpty()) continue
        appendLine("${room.roomName.ifBlank { "Room" }}: Rs ${formatMoney(valid.sumOf { it.total })}")
    }
    appendLine()

    if (bill.discount > 0.0 || bill.extraCharges > 0.0) {
        appendLine("Subtotal: Rs ${formatMoney(bill.subtotal)}")
        if (bill.discount > 0.0) appendLine("Discount: - Rs ${formatMoney(bill.discount)}")
        if (bill.extraCharges > 0.0) appendLine("Extra/Misc: + Rs ${formatMoney(bill.extraCharges)}")
    }
    appendLine("*GRAND TOTAL: Rs ${formatMoney(bill.grandTotal)}*")

    if (!bill.isPaid) {
        appendLine("Received: Rs ${formatMoney(bill.received)}")
        appendLine("*Pending: Rs ${formatMoney(bill.balance)}*")
    } else {
        appendLine("Status: PAID")
    }
    appendLine()
    append("Thank you!")
}

private const val HEX = "0123456789ABCDEF"

/** URL me bhejne ke liye text ko %XX me badalta hai (Urdu/space/newline sab safe). */
private fun urlEncode(text: String): String {
    val sb = StringBuilder()
    for (b in text.encodeToByteArray()) {
        val v = b.toInt() and 0xFF
        val ch = v.toChar()
        if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
            sb.append(ch)
        } else {
            sb.append('%').append(HEX[v shr 4]).append(HEX[v and 15])
        }
    }
    return sb.toString()
}