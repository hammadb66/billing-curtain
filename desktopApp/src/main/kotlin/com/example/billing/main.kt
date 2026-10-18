package com.example.billing

import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
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
    var showExitDialog by remember { mutableStateOf(false) }

    Window(
        onCloseRequest ={
            showExitDialog = true
        },
        title = "Billing System",
        icon = icon,
        state = rememberWindowState(width = 1200.dp, height = 800.dp),
    ) {
        App(services, printer, presetShop, exporter = exporter, exporter_importer = fileImporter)


        // Desktop Exit Confirmation Dialog
        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text("Exit App?", color = Color.Black) },
                text = { Text("Do you really want to exit the billing system?", color = Color.DarkGray) },
                confirmButton = {
                    TextButton(onClick = {
                        showExitDialog = false
                        // Desktop App ko safely band karne ka tareeqa
                        exitApplication()
                    }) { Text("Exit", color = Color.Red) }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) { Text("Cancel", color = Color.Black) }
                }
            )
        }

    }
}
