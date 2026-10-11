package com.example.billing.pdf

import com.example.billing.model.RoomData
import com.example.billing.utils.formatMoney
import com.example.billing.utils.formatQty
import kotlin.collections.plusAssign

/** Naye bill aur History dono ka PDF isi aik function se banta hai. */
fun buildInvoiceRows(
    customer: String,
    phone: String,
    date: String,
    rooms: List<RoomData>,
): List<PdfRow> {
    val rows = mutableListOf<PdfRow>()
    rows += PdfRow(left = "INVOICE", right = "Date: $date", bold = true, fontSize = 22f)
    rows += PdfRow(kind = RowKind.LINE)
    rows += PdfRow(left = "Customer: $customer", bold = true, fontSize = 14f)
    rows += PdfRow(left = "Customer Number: $phone", bold = true, fontSize = 14f)
    rows += PdfRow(kind = RowKind.SPACE)

    var grand = 0.0
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
        grand += roomTotal
    }

    rows += PdfRow(kind = RowKind.LINE)
    rows += PdfRow(right = "GRAND TOTAL: Rs ${formatMoney(grand)}", bold = true, fontSize = 16f)
    return rows
}
