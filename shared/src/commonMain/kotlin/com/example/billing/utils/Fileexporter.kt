package com.example.billing.utils

/**
 * File save karne ka "wada". Common code sirf ye interface jaanta hai;
 * Android aur Desktop apna apna tareeqa (platform/ folder wali files) dete hain.
 */
interface FileExporter {
    /** Kamyab ho to "kahan save hua" ka text, warna null. */
    suspend fun saveText(fileName: String, content: String): String?
}