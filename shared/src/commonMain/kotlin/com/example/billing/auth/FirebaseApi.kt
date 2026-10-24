package com.example.billing.auth

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

data class LicenseInfo(
    val active: Boolean,
    val shopName: String = "",
    val message: String = "",
    val maxDevices: Int = AppConfig.DEFAULT_MAX_DEVICES,
)

data class DeviceRecord(
    val id: String,
    val label: String,
    val firstSeen: Long,
    val lastSeen: Long,
    val blocked: Boolean,
)

sealed interface LicenseFetch {
    data class Found(val info: LicenseInfo) : LicenseFetch
    data object NotFound : LicenseFetch
    data class Failed(val reason: String) : LicenseFetch
}

data class DevSession(val email: String, val idToken: String)

sealed interface DevResult {
    data class Success(val session: DevSession) : DevResult
    data class Failed(val message: String) : DevResult
}

sealed interface DevicesResult {
    data class Ok(val devices: Map<String, DeviceRecord>) : DevicesResult
    data class Failed(val reason: String) : DevicesResult
}

sealed interface ShopsResult {
    data class Ok(val shops: Map<String, LicenseInfo>) : ShopsResult
    data class Failed(val message: String) : ShopsResult
}

/** Firebase se baat karne wala hissa (Realtime Database + Authentication, REST ke zariye). */
class FirebaseApi {
    private val json = Json { ignoreUnknownKeys = true }
    private val db get() = AppConfig.DB_URL

    // =============== Sab laptops ye istemal karte hain ===============

    suspend fun fetchLicense(shopId: String): LicenseFetch = try {
        val res = httpCall("GET", "$db/licenses/$shopId.json")
        if (res.code != 200) {
            LicenseFetch.Failed("HTTP ${res.code}")
        } else {
            val element = json.parseToJsonElement(res.body)
            if (element is JsonNull) LicenseFetch.NotFound
            else LicenseFetch.Found(element.toLicenseInfo())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val msg = e.message ?: ""
        val friendlyMsg = when {
            msg.contains("firebase", ignoreCase = true) || msg.contains(
                "http",
                ignoreCase = true
            ) -> "No internet connection. Please check your connection and try again."

            msg.contains("timeout", ignoreCase = true) -> "Connection timed out. Please try again."

            else -> msg
        }
        LicenseFetch.Failed(friendlyMsg)
    }

    suspend fun fetchDevices(shopId: String): DevicesResult = try {
        val res = httpCall("GET", "$db/devices/$shopId.json")
        if (res.code != 200) {
            DevicesResult.Failed("devices HTTP ${res.code} (Did you publish the devices section in the Firebase Rules?)")
        } else {
            DevicesResult.Ok(parseDevices(json.parseToJsonElement(res.body)))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        DevicesResult.Failed(e.message ?: "network error")
    }

    suspend fun registerDevice(
        shopId: String,
        deviceId: String,
        label: String,
        now: Long,
        blocked: Boolean = false,
    ): String? = try {
        val body = buildJsonObject {
            put("label", label.take(60))
            put("firstSeen", now)
            put("lastSeen", now)
            put("blocked", blocked)
        }.toString()
        val res = httpCall("PUT", "$db/devices/$shopId/$deviceId.json", body)
        if (res.code == 200) null else "HTTP ${res.code}"
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        e.message ?: "network error"
    }

    suspend fun touchDevice(shopId: String, deviceId: String, now: Long) {
        try {
            httpCall("PUT", "$db/devices/$shopId/$deviceId/lastSeen.json", now.toString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // koi baat nahi
        }
    }

    // =============== Developer login ===============

    suspend fun developerSignIn(email: String, password: String): DevResult = try {
        val body = buildJsonObject {
            put("email", email)
            put("password", password)
            put("returnSecureToken", true)
        }.toString()
        val res = httpCall(
            "POST",
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${AppConfig.API_KEY}",
            body,
        )
        if (res.code == 200) {
            val obj = json.parseToJsonElement(res.body).jsonObject
            val token = obj["idToken"]?.jsonPrimitive?.contentOrNull ?: ""
            val mail = obj["email"]?.jsonPrimitive?.contentOrNull ?: ""
            if (token.isEmpty()) DevResult.Failed("Login failed.")
            // NAYA FIX: Firebase se check karein ke ye developer allowed hai ya nahi
            else if (!isDeveloperAllowed(mail)) DevResult.Failed("This is not a developer account (or account is removed).")
            else DevResult.Success(DevSession(mail, token))
        } else {
            val code = runCatching {
                json.parseToJsonElement(res.body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
            }.getOrNull() ?: ""
            DevResult.Failed(
                when {
                    code.contains("TOO_MANY") -> "Extensive efforts. Try again after a while."
                    code.contains("INVALID") || code.contains("EMAIL_NOT_FOUND") || code.contains("PASSWORD") -> "The email or password is incorrect."

                    else -> "Login failed (HTTP ${res.code} $code)"
                }
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        DevResult.Failed("Check your internet connection (internet is required for developer login).")
    }

    // NAYA FUNCTION: Firebase se developers ki list check karega
    // NAYA FUNCTION: Firebase se developers ki list check karega (Ab har tarah ke format ko parh lega)
    // NAYA FUNCTION: Firebase se developers ki list check karega (Har format aur case-insensitive)
    private suspend fun isDeveloperAllowed(email: String): Boolean {
        // 1. Main dev (AppConfig se) hamesha allowed hai (backup ke liye)
        if (email.equals(AppConfig.DEV_EMAIL, ignoreCase = true)) return true

        // 2. Firebase Database se check karein
        return try {
            val res = httpCall("GET", "$db/devs.json")
            if (res.code == 200) {
                val element = json.parseToJsonElement(res.body)

                // Email ko lowercase aur dot ko comma mein badal lein
                val formattedEmail = email.lowercase().replace('.', ',')

                when (element) {
                    // Agar Firebase me array/list ki tarah save hai (Purana tarika)
                    is JsonArray -> {
                        element.any { it.jsonPrimitive.contentOrNull?.lowercase() == email.lowercase() }
                    }
                    // Agar Firebase me object (keys) ki tarah save hai (Naya tarika)
                    is JsonObject -> {
                        // Check karein ke kya formattedEmail (comma wala) database mein majood hai?
                        // Ya kya kisi value ke andar email majood hai?
                        element.keys.any { it.lowercase() == formattedEmail } ||
                                element.values.any { it.jsonPrimitive.contentOrNull?.lowercase() == email.lowercase() }
                    }
                    else -> false
                }
            } else false
        } catch (e: Exception) {
            false
        }
    }

    // =============== Sirf developer (token ke saath) ===============

    suspend fun listLicenses(idToken: String): ShopsResult = try {
        val res = httpCall("GET", "$db/licenses.json?auth=$idToken")
        if (res.code != 200) {
            ShopsResult.Failed("Couldn't find the shops (HTTP ${res.code}). Check Firebase rules and developer login.")
        } else {
            val element = json.parseToJsonElement(res.body)
            if (element is JsonObject) {
                val map =
                    element.entries.filter { it.value is JsonObject }.associate { it.key to it.value.toLicenseInfo() }
                ShopsResult.Ok(map)
            } else {
                ShopsResult.Ok(emptyMap())
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ShopsResult.Failed("Check the internet.")
    }

    suspend fun listAllDevices(idToken: String): Map<String, Map<String, DeviceRecord>>? = try {
        val res = httpCall("GET", "$db/devices.json?auth=$idToken")
        if (res.code != 200) {
            null
        } else {
            val element = json.parseToJsonElement(res.body)
            if (element is JsonObject) {
                element.entries.associate { (shopId, devices) -> shopId to parseDevices(devices) }
            } else {
                emptyMap()
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    suspend fun putLicense(idToken: String, shopId: String, info: LicenseInfo): String? = try {
        val body = buildJsonObject {
            put("active", info.active)
            put("shopName", info.shopName)
            put("message", info.message)
            put("maxDevices", info.maxDevices)
        }.toString()
        val res = httpCall("PUT", "$db/licenses/$shopId.json?auth=$idToken", body)
        if (res.code == 200) null else "Could not saved (HTTP ${res.code}). Log in as developer again"
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        "Check the internet."
    }

    suspend fun deleteShop(idToken: String, shopId: String, deleteBills: Boolean = false): String? = try {
        val nodes = if (deleteBills) listOf("licenses", "devices", "members", "bills") else listOf(
            "licenses", "devices", "members"
        )
        val codes = nodes.map { node ->
            httpCall("DELETE", "$db/$node/$shopId.json?auth=$idToken", timeoutMs = 30_000).code
        }
        if (codes.all { it == 200 }) null else "Could not delete (HTTP ${codes.joinToString(" / ")}). Rules me members/bills ka developer write publish kiya?"
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        "Check the internet."
    }

    suspend fun setDeviceBlocked(idToken: String, shopId: String, deviceId: String, blocked: Boolean): String? = try {
        val res = httpCall("PUT", "$db/devices/$shopId/$deviceId/blocked.json?auth=$idToken", blocked.toString())
        if (res.code == 200) null else "Could not Save (HTTP ${res.code})"
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        "Check the internet."
    }

    suspend fun removeDevice(idToken: String, shopId: String, deviceId: String): String? = try {
        val res = httpCall("DELETE", "$db/devices/$shopId/$deviceId.json?auth=$idToken")
        if (res.code == 200) null else "Could not delete (HTTP ${res.code})"
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        "Check the internet."
    }

    // =============== parsing ===============

    private fun parseDevices(element: JsonElement): Map<String, DeviceRecord> {
        if (element !is JsonObject) return emptyMap()
        return element.entries.filter { it.value is JsonObject }.associate { (id, value) -> id to value.toDevice(id) }
    }

    private fun JsonElement.toDevice(id: String): DeviceRecord {
        val o = jsonObject
        return DeviceRecord(
            id = id,
            label = o["label"]?.jsonPrimitive?.contentOrNull ?: "?",
            firstSeen = o["firstSeen"]?.jsonPrimitive?.longOrNull ?: 0L,
            lastSeen = o["lastSeen"]?.jsonPrimitive?.longOrNull ?: 0L,
            blocked = o["blocked"]?.jsonPrimitive?.booleanOrNull ?: false,
        )
    }

    private fun JsonElement.toLicenseInfo(): LicenseInfo {
        val o = jsonObject
        val activePrim = o["active"]?.jsonPrimitive
        val active = activePrim?.booleanOrNull ?: (activePrim?.contentOrNull == "true")
        return LicenseInfo(
            active = active,
            shopName = o["shopName"]?.jsonPrimitive?.contentOrNull ?: "",
            message = o["message"]?.jsonPrimitive?.contentOrNull ?: "",
            maxDevices = o["maxDevices"]?.jsonPrimitive?.intOrNull ?: AppConfig.DEFAULT_MAX_DEVICES,
        )
    }
}