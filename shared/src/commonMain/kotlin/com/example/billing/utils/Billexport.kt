package com.example.billing.utils

import com.example.billing.model.Bill
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

fun billsToBackupJson(bills: List<Bill>): String =
    backupJson.encodeToString(ListSerializer(BillBackup.serializer()), bills.map {
        BillBackup(
            id = it.id,
            customer = it.customer,
            phone = it.phone,
            address = it.address,
            phone2 = it.phone2,
            accountNo = it.accountNo,
            date = it.date,
            rooms = it.rooms,
            discount = it.discount,
            extraCharges = it.extraCharges,
            paidAmount = it.paidAmount,
            invoiceLabel = it.invoiceLabel,
            showProduct = it.showProduct,
            showCategory = it.showCategory,
            showSubcategory = it.showSubcategory,
            showGroup = it.showGroup,        // <-- ADD
            showSubgroup = it.showSubgroup,  // <-- ADD
            salesmanId = it.salesmanId,
        )
    })

private val backupJson = Json { prettyPrint = true; encodeDefaults = true }

private fun csvCell(text: String): String =
    if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + text.replace("\"", "\"\"") + "\"" else text

/** Excel me khulne wali CSV. Shuru ka \uFEFF Excel ko batata hai ke Urdu/UTF-8 naam hain. */
fun billsToCsv(bills: List<Bill>): String {
    val sb = StringBuilder("\uFEFF")
    sb.append("Invoice No,Date,Customer,Phone,Subtotal,Discount,Extra,Grand Total,Received,Balance\n")
    for (b in bills) {
        sb.append(
            listOf(
                b.invoiceText,
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