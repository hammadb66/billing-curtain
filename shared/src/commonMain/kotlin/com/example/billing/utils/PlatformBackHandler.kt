package com.example.billing.utils

import androidx.compose.runtime.Composable

/**
 * Mobile ke "Back" button ke liye. Android me asli BackHandler chalta hai,
 * Desktop par koi back button nahi is liye wahan kuch nahi hota.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
