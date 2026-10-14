// ---- Ye file androidMain me rakhein (package apne project ke mutabiq) ----
package com.example.billing.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** User se pooch kar file save karne ke liye Android exporter. */
class AndroidFileExporter(private val context: Context) : FileExporter {

    override suspend fun saveText(fileName: String, content: String): String? = withContext(Dispatchers.IO) {
        try {
            // User se poochne ke liye system dialog mangwayenge
            val uri = FileSaveRegistry.requestSaveLocation(fileName) ?: return@withContext null

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.encodeToByteArray())
            }
            uri.toString()
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }
}