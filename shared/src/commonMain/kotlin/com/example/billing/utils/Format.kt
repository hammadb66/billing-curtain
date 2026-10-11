package com.example.billing.utils


import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round
import kotlin.time.Clock
import kotlin.time.Instant

// Flutter ka DecimalTextInputFormatter: sirf digits aur ek dot allowed
private val DECIMAL_REGEX = Regex("^\\d*\\.?\\d*$")

fun isValidDecimal(text: String): Boolean = text.isEmpty() || DECIMAL_REGEX.matches(text)

/** Flutter ka toStringAsFixed(2). Kotlin common me String.format nahi hota, is liye khud likha. */
fun formatMoney(value: Double, decimals: Int = 2): String {
    val negative = value < 0
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val rounded = round(abs(value) * factor).toLong()
    val whole = rounded / factor
    val fraction = (rounded % factor).toString().padStart(decimals, '0')
    val sign = if (negative && rounded != 0L) "-" else ""
    return if (decimals > 0) "$sign$whole.$fraction" else "$sign$whole"
}

/** 3.0 -> "3", 2.5 -> "2.5" */
fun formatQty(value: Double): String =
    if (value == floor(value) && abs(value) < 1e15) value.toLong().toString() else value.toString()

/** CRASH-PROOF ISO Date Generator (Error dialog se bachata hai) */
fun nowIso(): String = Clock.System.now().toString()

fun displayDate(iso: String): String = try {
    Instant.parse(iso).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
} catch (e: Throwable) {
    iso.take(10)
}