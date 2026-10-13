package com.example.billing.utils

interface FileImporter {
    suspend fun pickJsonFile(): String?
}