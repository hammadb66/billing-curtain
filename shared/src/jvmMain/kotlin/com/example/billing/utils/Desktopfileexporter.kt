// ---- Ye file desktopMain me rakhein (package apne project ke mutabiq) ----
package com.example.billing.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Pop-up window khol kar user se poocha jayega ke file kahan save karni hai. */
class DesktopFileExporter : FileExporter {

    override suspend fun saveText(fileName: String, content: String): String? = withContext(Dispatchers.IO) {
        try {
            // System ko batate hain ke Graphical Window kholni hai
            System.setProperty("java.awt.headless", "false")

            // Ek pop-up dialog window
            val frame: Frame? = null
            val dialog = FileDialog(frame, "Save Backup File", FileDialog.SAVE).apply {
                file = fileName
                isVisible = true
            }

            val chosenDir = dialog.directory
            val chosenName = dialog.file

            // Agar user ne "Cancel" nahi kiya aur jagah select ki
            if (chosenDir != null && chosenName != null) {
                val file = File(chosenDir, chosenName)
                file.writeText(content, Charsets.UTF_8)
                file.absolutePath
            } else {
                null // User ne cancel kar diya
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }
}