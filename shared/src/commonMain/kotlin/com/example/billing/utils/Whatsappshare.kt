package com.example.billing.utils

import com.example.billing.model.Bill

/** Pakistan ka code. Dusre mulk ke liye yahan badal dein. */
const val DEFAULT_COUNTRY_CODE = "92"

/** "0300-1234567" -> "923001234567" (WhatsApp ko country code ke saath number chahiye). */
fun normalizeWhatsAppNumber(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    return when {
        digits.startsWith("00") -> digits.drop(2)
        digits.startsWith("0") -> DEFAULT_COUNTRY_CODE + digits.drop(1)
        else -> digits
    }
}

/** WhatsApp ka link. Android par WhatsApp app khulti hai, PC par browser (WhatsApp Web). */
fun whatsAppUrl(phone: String, message: String): String =
    "https://wa.me/${normalizeWhatsAppNumber(phone)}?text=${urlEncode(message)}"

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
        appendLine("*Baqaya: Rs ${formatMoney(bill.balance)}*")
    } else {
        appendLine("Status: PAID")
    }
    appendLine()
    append("Shukriya!")
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