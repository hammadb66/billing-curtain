package com.example.billing

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.billing.data.BillRepository
import com.example.billing.data.DriverFactory
import com.example.billing.pdf.DesktopPdfPrinter
import java.util.logging.Level
import java.util.logging.Logger
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import com.example.billing.utils.DesktopFileExporter
import com.example.billing.utils.DesktopFileImporter

private val pdfBoxLoggers =
    listOf("org.apache.pdfbox", "org.apache.fontbox").map { Logger.getLogger(it).apply { level = Level.OFF } }

fun main() = application {
    val services = remember { createAppServices(DriverFactory(), readDeviceIdentity()) }

    val presetShop = remember { readPresetShopCode() }
    val printer = remember { DesktopPdfPrinter() }
    val icon = remember {
        useResource("icon.png") { BitmapPainter(loadImageBitmap(it)) }
    }
    val exporter = remember { DesktopFileExporter() }
    val fileImporter = remember { DesktopFileImporter() }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Billing System",
        icon = icon,
        state = rememberWindowState(width = 1200.dp, height = 800.dp),
    ) {
        App(services, printer, presetShop, exporter = exporter, exporter_importer = fileImporter)
    }
}
