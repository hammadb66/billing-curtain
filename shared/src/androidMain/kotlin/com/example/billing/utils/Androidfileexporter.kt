// ---- Ye file androidMain me rakhein (package apne project ke mutabiq) ----
package com.example.billing.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** File "Downloads/BillingBackups" folder me save hoti hai. */
class AndroidFileExporter(private val context: Context) : FileExporter {

    override suspend fun saveText(fileName: String, content: String): String? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, if (fileName.endsWith(".json")) "application/json" else "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/BillingBackups")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext null
                val stream = context.contentResolver.openOutputStream(uri) ?: return@withContext null
                stream.use { it.write(content.encodeToByteArray()) }
                "Downloads/BillingBackups/$fileName"
            } else {
                // Purane Android: app ka apna folder (permission nahi chahiye)
                val dir = context.getExternalFilesDir(null) ?: context.filesDir
                val file = File(dir, fileName)
                file.writeText(content)
                file.absolutePath
            }
        } catch (e: Throwable) {
            null
        }
    }
}