package com.example.billing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.example.billing.data.DriverFactory
import com.example.billing.pdf.AndroidPdfPrinter
import com.example.billing.utils.AndroidFileExporter
import com.example.billing.utils.AndroidFileImporter
import com.example.billing.utils.FilePickerRegistry
import com.example.billing.utils.FileSaveRegistry

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val services = (application as BillingApplication).services

        val printer = AndroidPdfPrinter(this)

        val jsonPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            FilePickerRegistry.onFilePicked(uri)
        }

        // User se save location lene ke liye launcher
        val saveDocumentLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            FileSaveRegistry.onFileLocationPicked(uri)
        }

        FileSaveRegistry.register { defaultName ->
            saveDocumentLauncher.launch(defaultName) // Yahan "input =" hata diya gaya hai
        }

        setContent {
            App(
                services = services,
                printer = printer,
                exporter = AndroidFileExporter(this),
                exporter_importer = AndroidFileImporter(this) {
                    jsonPickerLauncher.launch("application/json")
                }
            )
        }
    }
}