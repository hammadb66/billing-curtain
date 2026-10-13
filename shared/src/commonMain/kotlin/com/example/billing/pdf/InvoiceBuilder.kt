package com.example.billing.pdf

import com.example.billing.model.RoomData
import com.example.billing.utils.formatMoney
import com.example.billing.utils.Branding
import com.example.billing.utils.formatQty

fun buildInvoiceRows(
    customer: String,
    phone: String,
    date: String,
    rooms: List<RoomData>,
    discount: Double = 0.0,
    extraCharges: Double = 0.0,
): List<PdfRow> {
    val rows = mutableListOf<PdfRow>()
    if (Branding.licensedTo.isNotBlank()) {
        rows += PdfRow(left = Branding.licensedTo, bold = true, fontSize = 18f)
    }
    rows += PdfRow(left = "INVOICE", right = "Date: $date", bold = true, fontSize = 22f)
    rows += PdfRow(kind = RowKind.LINE)
    rows += PdfRow(left = "Customer Name: $customer", bold = true, fontSize = 14f)
    rows += PdfRow(left = "Customer Number: $phone", bold = true, fontSize = 14f)
    rows += PdfRow(kind = RowKind.SPACE)

    var subtotal = 0.0
    for (room in rooms) {
        val valid = room.validItems
        if (valid.isEmpty()) continue

        rows += PdfRow(left = "Room: ${room.roomName.ifBlank { "Room" }}", bold = true, fontSize = 14f)
        rows += PdfRow(left = "Item", qty = "Qty", rate = "Rate", right = "Total", bold = true)
        rows += PdfRow(kind = RowKind.LINE)
        var roomTotal = 0.0
        for (item in valid) {
            roomTotal += item.total
            rows += PdfRow(
                left = item.name,
                qty = formatQty(item.qtyValue),
                rate = formatMoney(item.rateValue),
                right = formatMoney(item.total),
            )
        }
        rows += PdfRow(kind = RowKind.LINE)
        rows += PdfRow(right = "Room Total: Rs ${formatMoney(roomTotal)}", bold = true)
        rows += PdfRow(kind = RowKind.SPACE)
        subtotal += roomTotal
    }

    rows += PdfRow(kind = RowKind.LINE)
    rows += PdfRow(right = "Subtotal: Rs ${formatMoney(subtotal)}", bold = true)

    if (discount > 0.0) {
        rows += PdfRow(right = "Discount: - Rs ${formatMoney(discount)}", bold = true)
    }
    if (extraCharges > 0.0) {
        rows += PdfRow(right = "Extra/Misc: + Rs ${formatMoney(extraCharges)}", bold = true)
    }

    val grandTotal = (subtotal - discount + extraCharges).coerceAtLeast(0.0)
    rows += PdfRow(right = "GRAND TOTAL: Rs ${formatMoney(grandTotal)}", bold = true, fontSize = 16f)

    if (Branding.SHOW_ON_INVOICE) {
        rows += PdfRow(kind = RowKind.SPACE)
        rows += PdfRow(left = "Software by ${Branding.COMPANY}  |  Contact: ${Branding.PHONE}", fontSize = 9f)
    }
    return rows
}