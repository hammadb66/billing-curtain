package com.example.billing.pdf

enum class RowKind { TEXT, LINE, SPACE }

data class PdfRow(
    val left: String = "",
    val qty: String = "",
    val rate: String = "",
    val right: String = "",
    val bold: Boolean = false,
    val fontSize: Float = 11f,
    val kind: RowKind = RowKind.TEXT,
)

object PdfLayout {
    const val PAGE_WIDTH = 595f   // A4 points
    const val PAGE_HEIGHT = 842f
    const val MARGIN = 40f
    const val QTY_X = 300f
    const val RATE_X = 370f
}

/** Printer ka "contract". Android aur Desktop apni apni implementation dete hain. */
interface PdfPrinter {
    suspend fun print(title: String, rows: List<PdfRow>): Boolean
}
