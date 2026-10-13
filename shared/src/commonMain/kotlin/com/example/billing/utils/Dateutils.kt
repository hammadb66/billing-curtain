package com.example.billing.utils

/*
 * Calendar (DatePicker) millisecond deta hai, aur hamari bills "2026-10-13T..." jaisi ISO date me hain.
 * Ye helpers dono ko aapas me badalte hain. Koi extra library nahi chahiye.
 */

private const val MILLIS_PER_DAY = 86_400_000L

/** "2026-10-13" -> "13-10-2026" */
fun isoDayToDisplay(day: String): String {
    val p = day.take(10).split("-")
    return if (p.size == 3) "${p[2]}-${p[1]}-${p[0]}" else day
}

/** "2026-10-13" -> us din ki UTC midnight (millis). Calendar ko initial date dene ke liye. */
fun isoDayToEpochMillis(day: String): Long? {
    val p = day.take(10).split("-")
    if (p.size != 3) return null
    val y = p[0].toIntOrNull() ?: return null
    val m = p[1].toIntOrNull() ?: return null
    val d = p[2].toIntOrNull() ?: return null
    return daysFromCivil(y, m, d) * MILLIS_PER_DAY
}

/** Calendar ke millis -> "2026-10-13" */
fun epochMillisToIsoDay(millis: Long): String {
    val (y, m, d) = civilFromDays(millis.floorDiv(MILLIS_PER_DAY))
    return "${y.toString().padStart(4, '0')}-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
}

private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    val y = (if (month <= 2) year - 1 else year).toLong()
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val mp = (if (month > 2) month - 3 else month + 9).toLong()
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}

private fun civilFromDays(daysSinceEpoch: Long): Triple<Int, Int, Int> {
    val z = daysSinceEpoch + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val year = if (m <= 2) y + 1 else y
    return Triple(year.toInt(), m.toInt(), d.toInt())
}