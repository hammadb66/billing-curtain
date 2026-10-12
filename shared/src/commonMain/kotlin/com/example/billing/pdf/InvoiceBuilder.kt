package com.example.billing.pdf

import com.example.billing.model.RoomData
import com.example.billing.utils.formatMoney
import com.example.billing.utils.Branding
import com.example.billing.utils.formatQty

/** Naye bill aur History dono ka PDF isi aik function se banta hai. */
fun buildInvoiceRows(
    customer: String,
    phone: String,
    date: String,
    rooms: List<RoomData>,
): List<PdfRow> {
    val rows = mutableListOf<PdfRow>()
    // Dukaan ka naam bill ke sab se upar (license ke shopName se)
    if (Branding.licensedTo.isNotBlank()) {
        rows += PdfRow(left = Branding.licensedTo, bold = true, fontSize = 18f)
    }
    rows += PdfRow(left = "INVOICE", right = "Date: $date", bold = true, fontSize = 22f)
    rows += PdfRow(kind = RowKind.LINE)
    rows += PdfRow(left = "Customer Name: $customer", bold = true, fontSize = 14f)
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

    // Chhota sa "Software by ..." (band karna ho to Branding.SHOW_ON_INVOICE = false)
    if (Branding.SHOW_ON_INVOICE) {
        rows += PdfRow(kind = RowKind.SPACE)
        rows += PdfRow(left = "Software by ${Branding.COMPANY}  |  Contact: ${Branding.PHONE}", fontSize = 9f)
    }
    return rows
}
