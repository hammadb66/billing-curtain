// ---- Ye file desktopMain me rakhein (package apne project ke mutabiq) ----
package com.example.billing.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** File "Documents/BillingBackups" folder me save hoti hai. */
class DesktopFileExporter : FileExporter {

    override suspend fun saveText(fileName: String, content: String): String? = withContext(Dispatchers.IO) {
        try {
            val dir = File(System.getProperty("user.home"), "Documents/BillingBackups")
            dir.mkdirs()
            val file = File(dir, fileName)
            file.writeText(content, Charsets.UTF_8)
            file.absolutePath
        } catch (e: Throwable) {
            null
        }
    }
}