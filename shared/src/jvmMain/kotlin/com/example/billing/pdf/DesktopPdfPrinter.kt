package com.example.billing.pdf

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.printing.PDFPageable
import java.awt.print.PrinterJob

class DesktopPdfPrinter : PdfPrinter {

    override suspend fun print(title: String, rows: List<PdfRow>): Boolean =
        withContext(Dispatchers.IO) {
            buildDocument(rows).use { doc ->
                val job = PrinterJob.getPrinterJob()
                job.jobName = title
                job.setPageable(PDFPageable(doc))
                if (job.printDialog()) {
                    job.print()
                    true
                } else {
                    false
                }
            }
        }

    private fun buildDocument(rows: List<PdfRow>): PDDocument {
        val doc = PDDocument()
        val pageSize = PDRectangle.A4
        val pageW = pageSize.width
        val pageH = pageSize.height
        val margin = PdfLayout.MARGIN

        val regular = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        val bold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)

        var page = PDPage(pageSize)
        doc.addPage(page)
        var stream = PDPageContentStream(doc, page)
        var y = pageH - margin

        for (row in rows) {
            val size = row.fontSize
            val lineHeight = size * 1.7f

            if ((y - lineHeight) < margin){
                stream.close()
                page = PDPage(pageSize)
                doc.addPage(page)
                stream = PDPageContentStream(doc, page)
                y = pageH - margin
            }

            when (row.kind) {
                RowKind.SPACE -> y -= lineHeight
                RowKind.LINE -> {
                    stream.setLineWidth(0.7f)
                    stream.moveTo(margin, y)
                    stream.lineTo(pageW - margin, y)
                    stream.stroke()
                    y -= lineHeight / 2
                }
                RowKind.TEXT -> {
                    val font = if (row.bold) bold else regular
                    val baseline = y - size

                    // NAYA FIX: Left text ko limit me rakhein taake Qty par overlap na ho
                    val maxLeftWidth = PdfLayout.QTY_X - margin - 10f
                    val safeLeftText = fitText(font, size, maxLeftWidth, row.left)

                    stream.drawText(font, size, margin, baseline, safeLeftText)
                    stream.drawText(font, size, PdfLayout.QTY_X, baseline, row.qty)
                    stream.drawText(font, size, PdfLayout.RATE_X, baseline, row.rate)
                    if (row.right.isNotEmpty()) {
                        val text = safe(row.right)
                        val width = font.getStringWidth(text) / 1000f * size
                        stream.drawText(font, size, pageW - margin - width, baseline, text)
                    }
                    y -= lineHeight
                }
            }
        }

        stream.close()
        return doc
    }

    // NAYA FIX: Text ko cut karne ka function
    private fun fitText(font: PDFont, size: Float, maxWidth: Float, text: String): String {
        if (text.isEmpty()) return ""
        var result = safe(text)
        while (font.getStringWidth(result) / 1000f * size > maxWidth && result.length > 3) {
            result = result.dropLast(4) + "..."
        }
        return result
    }

    private fun PDPageContentStream.drawText(font: PDFont, size: Float, x: Float, y: Float, text: String) {
        if (text.isEmpty()) return
        beginText()
        setFont(font, size)
        newLineAtOffset(x, y)
        showText(safe(text))
        endText()
    }

    private fun safe(text: String): String =
        text.map { if (it.code in 32..126 || it.code in 160..255) it else '?' }.joinToString("")
}