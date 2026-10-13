package com.example.billing.utils

import android.content.Context
import android.net.Uri
import com.example.billing.ui.FileImporter
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// Global registry jo Android Activity se result wapas laane me madad karegi
object FilePickerRegistry {
    private var callback: ((Uri?) -> Unit)? = null

    fun setCallback(cb: (Uri?) -> Unit) {
        callback = cb
    }

    fun onFilePicked(uri: Uri?) {
        callback?.invoke(uri)
        callback = null
    }
}

class AndroidFileImporter(
    private val context: Context,
    private val triggerPicker: () -> Unit
) : FileImporter {

    override suspend fun pickJsonFile(): String? = suspendCancellableCoroutine { continuation ->
        FilePickerRegistry.setCallback { uri ->
            if (uri == null) {
                continuation.resume(null)
                return@setCallback
            }
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader().use { it?.readText() }
                continuation.resume(text)
            } catch (e: Throwable) {
                continuation.resume(null)
            }
        }

        // System ka file picker launcher chalayein
        triggerPicker()
    }
}