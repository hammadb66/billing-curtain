package com.example.billing.utils

import com.example.billing.model.Bill
import com.example.billing.model.RoomData
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Poore backup ke liye ek bill ki shakal (JSON me save hoti hai). */
@Serializable
data class BillBackup(
    val id: Long,
    val customer: String,
    val phone: String,
    val date: String,
    val rooms: List<RoomData>,
    val discount: Double,
    val extraCharges: Double,
    val paidAmount: Double? = null,
)

private val backupJson = Json { prettyPrint = true; encodeDefaults = true }

fun billsToBackupJson(bills: List<Bill>): String =
    backupJson.encodeToString(
        ListSerializer(BillBackup.serializer()),
        bills.map { BillBackup(it.id, it.customer, it.phone, it.date, it.rooms, it.discount, it.extraCharges, it.paidAmount) },
    )

private fun csvCell(text: String): String =
    if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + text.replace("\"", "\"\"") + "\"" else text

/** Excel me khulne wali CSV. Shuru ka \uFEFF Excel ko batata hai ke Urdu/UTF-8 naam hain. */
fun billsToCsv(bills: List<Bill>): String {
    val sb = StringBuilder("\uFEFF")
    sb.append("Invoice No,Date,Customer,Phone,Subtotal,Discount,Extra,Grand Total,Received,Balance\n")
    for (b in bills) {
        sb.append(
            listOf(
                "#${b.id}",
                displayDate(b.date),
                b.customer,
                b.phone,
                b.subtotal.toPlainAmount(),
                b.discount.toPlainAmount(),
                b.extraCharges.toPlainAmount(),
                b.grandTotal.toPlainAmount(),
                b.received.toPlainAmount(),
                b.balance.toPlainAmount(),
            ).joinToString(",") { csvCell(it) }
        ).append('\n')
    }
    return sb.toString()
}