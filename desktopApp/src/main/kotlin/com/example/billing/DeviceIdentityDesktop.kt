package com.example.billing

import com.example.billing.auth.DeviceIdentity
import java.io.File
import java.net.InetAddress
import java.util.concurrent.TimeUnit

fun readDeviceIdentity(): DeviceIdentity {
    val os = System.getProperty("os.name") ?: "PC"
    val host = runCatching { InetAddress.getLocalHost().hostName }.getOrNull()
        ?: System.getenv("COMPUTERNAME")
        ?: "unknown-pc"

    val guid = runCatching {
        val process = ProcessBuilder(
            "reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid",
        ).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText()
        process.waitFor(3, TimeUnit.SECONDS)
        text.lines()
            .firstOrNull { it.contains("MachineGuid") }
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.lastOrNull()
    }.getOrNull()

    val fingerprint = if (!guid.isNullOrBlank()) guid else "$host|${System.getProperty("user.name")}|$os"
    return DeviceIdentity(fingerprint, "$host ($os)")
}


fun readPresetShopCode(): String? = runCatching {
    val candidates = listOf(
        File(System.getProperty("user.home"), ".billing_app/shop.code"),
        File(System.getProperty("user.dir"), "shop.code"),
    )
    candidates.firstOrNull { it.exists() }
        ?.readText()
        ?.lines()
        ?.firstOrNull { it.isNotBlank() }
        ?.trim()
}.getOrNull()
