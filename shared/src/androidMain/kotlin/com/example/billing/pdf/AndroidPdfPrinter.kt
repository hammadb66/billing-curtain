package com.example.billing.pdf

import android.app.Activity
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream

class AndroidPdfPrinter(private val activity: Activity) : PdfPrinter {

    override suspend fun print(title: String, rows: List<PdfRow>): Boolean {
        val bytes = withContext(Dispatchers.Default) { renderPdf(rows) }
        withContext(Dispatchers.Main) { startPrintJob(title, bytes) }
        return true
    }

    private fun startPrintJob(title: String, bytes: ByteArray) {
        val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager

        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?,
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder("$title.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?,
            ) {
                try {
                    FileOutputStream(destination!!.fileDescriptor).use { it.write(bytes) }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }

        printManager.print(title, adapter, null)
    }

    private fun renderPdf(rows: List<PdfRow>): ByteArray {
        val pageW = PdfLayout.PAGE_WIDTH.toInt()
        val pageH = PdfLayout.PAGE_HEIGHT.toInt()
        val margin = PdfLayout.MARGIN

        val doc = PdfDocument()
        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNumber).create())
        var y = margin

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val linePaint = Paint().apply { strokeWidth = 0.7f }

        fun newPage() {
            doc.finishPage(page)
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNumber).create())
            y = margin
        }

        for (row in rows) {
            val size = row.fontSize
            val lineHeight = size * 1.7f
            if (y + lineHeight > pageH - margin) newPage()
            val canvas = page.canvas

            when (row.kind) {
                RowKind.SPACE -> y += lineHeight
                RowKind.LINE -> {
                    canvas.drawLine(margin, y, pageW - margin, y, linePaint)
                    y += lineHeight / 2
                }
                RowKind.TEXT -> {
                    paint.textSize = size
                    paint.typeface = if (row.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    val baseline = y + size

                    paint.textAlign = Paint.Align.LEFT

                    // NAYA FIX: Left text ko limit me rakhein
                    val maxLeftWidth = PdfLayout.QTY_X - margin - 10f
                    val safeLeftText = fitText(row.left, paint, maxLeftWidth)

                    if (safeLeftText.isNotEmpty()) canvas.drawText(safeLeftText, margin, baseline, paint)
                    if (row.qty.isNotEmpty()) canvas.drawText(row.qty, PdfLayout.QTY_X, baseline, paint)
                    if (row.rate.isNotEmpty()) canvas.drawText(row.rate, PdfLayout.RATE_X, baseline, paint)

                    paint.textAlign = Paint.Align.RIGHT
                    if (row.right.isNotEmpty()) canvas.drawText(row.right, pageW - margin, baseline, paint)

                    y += lineHeight
                }
            }
        }

        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    // NAYA FIX: Text ko cut karne ka function Android ke liye
    private fun fitText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "...") > maxWidth) {
            end--
        }
        return text.substring(0, end) + "..."
    }
}