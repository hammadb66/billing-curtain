package com.example.billing.utils

import android.net.Uri
import kotlinx.coroutines.CompletableDeferred

object FileSaveRegistry {
    private var saveCallback: ((String) -> Unit)? = null
    private var currentDeferred: CompletableDeferred<Uri?>? = null

    fun register(callback: (String) -> Unit) {
        saveCallback = callback
    }

    suspend fun requestSaveLocation(defaultName: String): Uri? {
        val deferred = CompletableDeferred<Uri?>()
        currentDeferred = deferred
        saveCallback?.invoke(defaultName)
        return deferred.await()
    }

    fun onFileLocationPicked(uri: Uri?) {
        currentDeferred?.complete(uri)
        currentDeferred = null
    }
}