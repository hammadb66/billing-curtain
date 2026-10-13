package com.example.billing.utils

import com.example.billing.ui.FileImporter
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

class DesktopFileImporter : FileImporter {
    override suspend fun pickJsonFile(): String? {
        return try {
            val chooser = FileDialog(null as Frame?, "Select Backup JSON", FileDialog.LOAD).apply {
                file = "*.json"
                isVisible = true
            }
            val dir = chooser.directory
            val file = chooser.file
            if (dir != null && file != null) {
                File(dir, file).readText(Charsets.UTF_8)
            } else {
                null
            }
        } catch (e: Throwable) {
            null
        }
    }
}