package com.example.billing.auth

import kotlin.time.Clock

private val SHOP_ID_REGEX = Regex("^[A-Za-z0-9_-]{3,40}$")

fun isValidShopId(id: String): Boolean = SHOP_ID_REGEX.matches(id)

sealed interface LicenseState {
    /** Is laptop par abhi shop code dala hi nahi gaya */
    data object NeedActivation : LicenseState

    /** Chalne ki ijazat hai. offline = true matlab internet nahi tha, purana saved "ON" istemal hua */
    data class Allowed(val offline: Boolean) : LicenseState

    data class Blocked(val message: String) : LicenseState
}

/** Internet par ek dafa poochne ka nateeja */
private sealed interface Online {
    data object Ok : Online
    data object NotFound : Online
    data class Denied(val message: String) : Online
    data class Unreachable(val reason: String) : Online
}

/**
 * Software chalne ki ijazat ka faisla yahan hota hai.
 *
 *  1. Internet se poochho: shop ON hai? Ye laptop darj hai / jagah baqi hai / block to nahi?
 *  2. Jawab na aaye -> aakhri saved jawab (jis par mohar lagi ho) dekho:
 *       ON aur taaza (OFFLINE_GRACE_DAYS ke andar) -> chalne do, warna band.
 */
class LicenseManager(
    private val settings: AppSettings,
    private val firebase: FirebaseApi,
    private val device: DeviceIdentity,
) {
    /** Is laptop ki pehchan (asli machine ID nahi bheji jati, uska hash bheja jata hai) */
    val deviceId: String = sha256Hex("${AppConfig.APP_SALT}:${device.fingerprint}").take(20)

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    suspend fun currentShopId(): String? = settings.get(SettingKeys.SHOP_ID)

    // ---------- local saved status (mohar ke saath) ----------

    private fun sign(shopId: String, ok: Boolean, at: Long, message: String): String =
        sha256Hex("${AppConfig.LOCAL_SECRET}|$deviceId|$shopId|$ok|$at|$message")

    private suspend fun saveResult(shopId: String, ok: Boolean, message: String) {
        val at = nowMillis()
        settings.put(SettingKeys.LIC_OK, ok.toString())
        settings.put(SettingKeys.LIC_CHECKED_AT, at.toString())
        settings.put(SettingKeys.LIC_MESSAGE, message)
        settings.put(SettingKeys.LIC_SIG, sign(shopId, ok, at, message))
    }

    // ---------- internet wala check ----------

    private suspend fun checkOnline(shopId: String): Online {
        val lic = when (val r = firebase.fetchLicense(shopId)) {
            is LicenseFetch.Found -> r.info
            LicenseFetch.NotFound -> return Online.NotFound
            is LicenseFetch.Failed -> return Online.Unreachable("license: ${r.reason}")
        }
        if (!lic.active) return Online.Denied(lic.message.ifBlank { DEFAULT_BLOCK_MESSAGE })

        val devices = when (val d = firebase.fetchDevices(shopId)) {
            is DevicesResult.Ok -> d.devices
            is DevicesResult.Failed -> return Online.Unreachable(d.reason)
        }
        val now = nowMillis()
        val shortId = deviceId.take(8)

        val mine = devices[deviceId]
        if (mine != null) {
            if (mine.blocked) {
                return Online.Denied(
                    "Is laptop ko chalane ki ijazat nahi hai (band hai ya laptops ki had poori hai). " +
                        "Developer se rabta karein.\n(Device: $shortId)"
                )
            }
            firebase.touchDevice(shopId, deviceId, now)
            return Online.Ok
        }

        val used = devices.values.count { !it.blocked }
        if (used >= lic.maxDevices) {
            // Koshish ka record rakh lo (BLOCKED ke taur par), taake developer ko admin panel me nazar aaye
            firebase.registerDevice(shopId, deviceId, device.label, now, blocked = true)
            return Online.Denied(
                "Is dukaan ke laptops ki had (${lic.maxDevices}) poori ho chuki hai. " +
                    "Naya laptop lagane ke liye developer se rabta karein.\n(Device: $shortId)"
            )
        }
        val problem = firebase.registerDevice(shopId, deviceId, device.label, now)
        return if (problem == null) Online.Ok else Online.Unreachable("laptop darj nahi hua: $problem")
    }

    // ---------- software khulte waqt ----------

    suspend fun evaluate(): LicenseState {
        val shopId = currentShopId() ?: return LicenseState.NeedActivation
        return when (val r = checkOnline(shopId)) {
            Online.Ok -> {
                saveResult(shopId, true, "")
                LicenseState.Allowed(offline = false)
            }
            is Online.Denied -> {
                saveResult(shopId, false, r.message)
                LicenseState.Blocked(r.message)
            }
            Online.NotFound -> {
                saveResult(shopId, false, NOT_FOUND_MESSAGE)
                LicenseState.Blocked(NOT_FOUND_MESSAGE)
            }
            is Online.Unreachable -> offlineDecision(shopId, r.reason)
        }
    }

    private suspend fun offlineDecision(shopId: String, reason: String): LicenseState {
        val ok = settings.get(SettingKeys.LIC_OK)?.toBooleanStrictOrNull()
        val at = settings.get(SettingKeys.LIC_CHECKED_AT)?.toLongOrNull()
        val message = settings.get(SettingKeys.LIC_MESSAGE) ?: ""
        val sig = settings.get(SettingKeys.LIC_SIG)

        // Mohar na mile ya ghalat ho = kisi ne database me chhed chhaad ki, ya kabhi verify hi nahi hua
        if (ok == null || at == null || sig == null || sig != sign(shopId, ok, at, message)) {
            return LicenseState.Blocked(
                "License verify nahi ho saka. Internet se connect kar ke ek dafa dobara try karein.\n\n" +
                    "Wajah: $reason"
            )
        }
        if (!ok) return LicenseState.Blocked(message.ifBlank { DEFAULT_BLOCK_MESSAGE })

        val graceMillis = AppConfig.OFFLINE_GRACE_DAYS * 24L * 60L * 60L * 1000L
        val age = nowMillis() - at
        return if (age in 0..graceMillis) {
            LicenseState.Allowed(offline = true)
        } else {
            LicenseState.Blocked("Kaafi din se license verify nahi hua. Internet se connect kar ke dobara try karein.")
        }
    }

    /**
     * Software chalte hue periodic check. Sirf tab Blocked deta hai jab Firebase khud saaf
     * "band" bataye. Net na ho to kuch nahi karta (user ka kaam nahi rukta).
     */
    suspend fun recheck(): LicenseState.Blocked? {
        val shopId = currentShopId() ?: return null
        return when (val r = checkOnline(shopId)) {
            Online.Ok -> {
                saveResult(shopId, true, "")
                null
            }
            is Online.Denied -> {
                saveResult(shopId, false, r.message)
                LicenseState.Blocked(r.message)
            }
            Online.NotFound -> {
                saveResult(shopId, false, NOT_FOUND_MESSAGE)
                LicenseState.Blocked(NOT_FOUND_MESSAGE)
            }
            is Online.Unreachable -> null
        }
    }

    /** Shop code laga kar activate karna. null = kamyab, warna error ka text */
    suspend fun activate(rawCode: String): String? {
        val code = rawCode.trim()
        if (!isValidShopId(code)) return "Shop code 3 se 40 harf ka ho (sirf a-z, A-Z, 0-9, _ ya -)"
        return when (val r = checkOnline(code)) {
            Online.Ok -> {
                settings.put(SettingKeys.SHOP_ID, code)
                saveResult(code, true, "")
                null
            }
            is Online.Denied -> r.message
            Online.NotFound -> "Ye Shop Code nahi mila. Dobara check karein."
            is Online.Unreachable -> "Internet check karein aur dobara try karein.\n(Wajah: ${r.reason})"
        }
    }

    companion object {
        const val DEFAULT_BLOCK_MESSAGE =
            "Aap ki subscription khatam ho gayi hai. Barae meherbani developer se rabta karein."
        const val NOT_FOUND_MESSAGE =
            "Is shop ka license nahi mila. Barae meherbani developer se rabta karein."
    }
}
