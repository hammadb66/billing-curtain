package com.example.billing.utils

import kotlin.math.roundToLong

/** Sirf digits aur ZYADA SE ZYADA ek '.' rakhta hai. "12.3.4" -> "12.34" */
fun sanitizeDecimal(input: String): String {
    val sb = StringBuilder()
    var dotSeen = false
    for (c in input) {
        when {
            c.isDigit() -> sb.append(c)
            c == '.' && !dotSeen -> { sb.append(c); dotSeen = true }
        }
    }
    return sb.toString()
}

/** Phone field ke liye: digits aur sirf SHURU ka '+'. "+971 50-123" -> "+97150123" */
fun sanitizePhone(input: String): String {
    val trimmed = input.trim()
    val digits = trimmed.filter { it.isDigit() }
    return if (trimmed.startsWith("+")) "+$digits" else digits
}

/** 0.0 -> ""  |  1500.0 -> "1500"  |  10000000.0 -> "10000000" (1.0E7 NAHI)  |  99.5 -> "99.5" */
fun Double.toInputString(): String {
    if (this <= 0.0) return ""
    val paisa = (this * 100).roundToLong()
    val whole = paisa / 100
    val frac = (paisa % 100).toInt()
    return if (frac == 0) whole.toString()
    else "$whole.${frac.toString().padStart(2, '0').trimEnd('0')}"
}

/** CSV jaisi jagah ke liye: 0 bhi "0" likhta hai (toInputString khali deta hai). */
fun Double.toPlainAmount(): String = if (this <= 0.0) "0" else toInputString()

/**
 * Discount hamesha Rs me wapas deta hai.
 * isPercent = true ho to input ko % maan kar subtotal se Rs nikalta hai (10% of 2000 = 200).
 */
fun resolveDiscount(subtotal: Double, input: String, isPercent: Boolean): Double {
    val raw = input.toDoubleOrNull() ?: 0.0
    if (!isPercent) return raw
    val pct = raw.coerceIn(0.0, 100.0)
    return ((subtotal * pct / 100.0) * 100).roundToLong() / 100.0
}