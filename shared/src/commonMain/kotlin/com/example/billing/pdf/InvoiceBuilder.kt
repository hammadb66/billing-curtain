package com.example.billing.pdf

import com.example.billing.model.Bill
import com.example.billing.model.RoomData
import com.example.billing.utils.formatMoney
import com.example.billing.utils.Branding
import com.example.billing.utils.formatQty

fun buildInvoiceRows(
    customer: String, phone: String, date: String, rooms: List<RoomData>,
    discount: Double = 0.0, extraCharges: Double = 0.0,
    invoiceNo: String? = null, paidAmount: Double? = null,
    showProduct: Boolean = false,
    showCategory: Boolean = false,
    showSubcategory: Boolean = false,
    showGroup: Boolean = false,
    showSubgroup: Boolean = false,
    previousBill: Bill? = null, previousBalance: Double = 0.0,
): List<PdfRow> {
    val rows = mutableListOf<PdfRow>()

    // ---- SHOP NAME HEADER (Requirement 1) ----
    if (Branding.licensedTo.isNotBlank()) {
        rows += PdfRow(left = Branding.licensedTo, bold = true, fontSize = 20f)
        rows += PdfRow(kind = RowKind.LINE)
    }

    rows += PdfRow(left = "INVOICE", right = "Date: $date", bold = true, fontSize = 20f)
    if (invoiceNo != null) rows += PdfRow(left = "Invoice No: $invoiceNo", bold = true, fontSize = 13f)
    rows += PdfRow(kind = RowKind.LINE)
    rows += PdfRow(left = "Customer: $customer", bold = true, fontSize = 13f)
    rows += PdfRow(left = "Phone: $phone", bold = true, fontSize = 13f)
    rows += PdfRow(kind = RowKind.SPACE)

    // ---- PREVIOUS BILL BOX ----
    if (previousBill != null && previousBalance > 0.005) {
        rows += PdfRow(kind = RowKind.LINE)
        rows += PdfRow(left = "PREVIOUS BILL REF: ${previousBill.invoiceText}", bold = true, fontSize = 13f)
        rows += PdfRow(right = "Previous Balance: Rs ${formatMoney(previousBalance)}", bold = true)
        rows += PdfRow(kind = RowKind.LINE)
    }

    // ---- ITEMS ----
    var subtotal = 0.0
    for (room in rooms) {
        val valid = room.validItems
        if (valid.isEmpty()) continue
        rows += PdfRow(left = room.roomName.ifBlank { "All Items" }, bold = true, fontSize = 13f)
        rows += PdfRow(left = "Item", qty = "Qty", rate = "Rate", right = "Total", bold = true)
        rows += PdfRow(kind = RowKind.LINE)
        var roomTotal = 0.0
        // InvoiceBuilder.kt
        for (item in valid) {
            var displayText = item.displayName(showProduct, showCategory, showSubcategory, showGroup, showSubgroup)

            if (item.productDiscountPct > 0.0) {
                displayText += " (${formatQty(item.productDiscountPct)}% off)"
            }

            // NAYA CALCULATION
            val grossTotal = item.grossTotal
            val prodDiscAmount = grossTotal * (item.productDiscountPct / 100.0)
            val netAfterProdDisc = grossTotal - prodDiscAmount
            val manualDiscAmount = if (item.discountIsPercent) netAfterProdDisc * (item.discountValue / 100.0) else item.discountValue
            val netTotal = (netAfterProdDisc - manualDiscAmount).coerceAtLeast(0.0)

            rows += PdfRow(
                left = displayText,
                qty = "${formatQty(item.qtyValue)} ${item.unit}",
                rate = formatMoney(item.rateValue),
                right = formatMoney(netTotal),
            )

            if (grossTotal > netTotal + 0.005) {
                val totalDisc = grossTotal - netTotal
                rows += PdfRow(
                    left = "   (Discount: - Rs ${formatMoney(totalDisc)})",
                    right = "Was: ${formatMoney(grossTotal)}",
                    fontSize = 9f, bold = false
                )
            }
            roomTotal += netTotal
        }
        rows += PdfRow(kind = RowKind.LINE)
        rows += PdfRow(right = "Section Total: Rs ${formatMoney(roomTotal)}", bold = true)
        rows += PdfRow(kind = RowKind.SPACE)
        subtotal += roomTotal
    }

    // ---- TOTALS ----
    rows += PdfRow(kind = RowKind.LINE)
    rows += PdfRow(right = "Subtotal: Rs ${formatMoney(subtotal)}", bold = true)
    if (discount > 0.0) rows += PdfRow(right = "Discount: - Rs ${formatMoney(discount)}", bold = true)
    if (extraCharges > 0.0) rows += PdfRow(right = "Extra/Misc: + Rs ${formatMoney(extraCharges)}", bold = true)

    val currentTotal = (subtotal - discount + extraCharges).coerceAtLeast(0.0)
    val totalDue = currentTotal + previousBalance

    rows += PdfRow(right = "Current Bill Total: Rs ${formatMoney(currentTotal)}", bold = true)
    if (previousBalance > 0) {
        rows += PdfRow(right = "Previous Balance: Rs ${formatMoney(previousBalance)}", bold = true)
        rows += PdfRow(kind = RowKind.LINE)
        rows += PdfRow(right = "TOTAL PAYABLE: Rs ${formatMoney(totalDue)}", bold = true, fontSize = 16f)
    } else {
        rows += PdfRow(right = "GRAND TOTAL: Rs ${formatMoney(currentTotal)}", bold = true, fontSize = 16f)
    }

    // ---- PAYMENT ----
    val received = (paidAmount ?: totalDue).coerceIn(0.0, totalDue)
    val balance = (totalDue - received).coerceAtLeast(0.0)
    if (balance > 0.005) {
        rows += PdfRow(right = "Received: Rs ${formatMoney(received)}", bold = true)
        rows += PdfRow(right = "BALANCE DUE: Rs ${formatMoney(balance)}", bold = true, fontSize = 16f)
    } else {
        rows += PdfRow(right = "PAID — Rs ${formatMoney(received)}", bold = true, fontSize = 14f)
    }

    if (Branding.SHOW_ON_INVOICE) {
        rows += PdfRow(kind = RowKind.SPACE)
        rows += PdfRow(left = "Software by ${Branding.COMPANY}  |  ${Branding.PHONE}", fontSize = 9f)
    }
    return rows
}