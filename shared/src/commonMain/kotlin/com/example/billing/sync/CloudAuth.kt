package com.example.billing.sync

import com.example.billing.auth.AppConfig
import com.example.billing.auth.httpCall
import com.example.billing.auth.sha256Hex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Clock

/**
 * Har laptop ka apna Firebase login (Authentication -> Email/Password, jo developer login ke liye pehle se ON hai).
 * Email/password laptop ke deviceId se khud ban jate hain, user ko kuch likhna nahi padta.
 * Bills ke rules sirf is login wale (aur license ON + laptop block na ho) ko data dete hain.
 * Token 1 ghante ka hota hai; khatam hone se pehle khud naya le leta hai.
 */
class CloudAuth(private val deviceId: String) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var idToken: String? = null
    private var expiresAt = 0L

    /** Firebase user id (rules me auth.uid). Token milne ke baad hi sahi hota hai. */
    var uid: String? = null
        private set

    /** Aakhri nakami ki wajah (token na mile to). */
    var lastError: String? = null
        private set

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()
    private val email get() = "d-$deviceId@devices.example.com"
    private val password get() = sha256Hex("${AppConfig.APP_SALT}|cloud|$deviceId").take(32)

    /** null = login nahi ho saka (wajah lastError me). forceNew = purana token chhod kar naya lo. */
    suspend fun token(forceNew: Boolean = false): String? = mutex.withLock {
        val t = idToken
        if (!forceNew && t != null && now() < expiresAt - 120_000L) return@withLock t
        idToken = null
        val fresh = login()
        idToken = fresh
        fresh
    }

    private suspend fun login(): String? {
        val signIn = call("signInWithPassword")
        if (signIn.token != null) return accept(signIn)
        // Pehli dafa: is laptop ka account abhi bana nahi
        val notFound = signIn.code.let {
            it.contains("EMAIL_NOT_FOUND") || it.contains("INVALID_LOGIN_CREDENTIALS") || it.contains("INVALID_PASSWORD")
        }
        if (!notFound) {
            lastError = signIn.problem()
            return null
        }
        val signUp = call("signUp")
        if (signUp.token != null) return accept(signUp)
        lastError = if (signUp.code.contains("EMAIL_EXISTS")) {
            "Is laptop ka Firebase login match nahi hua (kya APP_SALT badal diya gaya?)"
        } else {
            signUp.problem()
        }
        return null
    }

    private fun accept(r: Reply): String? {
        uid = r.uid
        expiresAt = now() + r.expiresInSec * 1000L
        lastError = null
        return r.token
    }

    private class Reply(
        val token: String?,
        val uid: String?,
        val expiresInSec: Long,
        val code: String,
        val http: Int,
        val net: String?,
    ) {
        fun problem(): String = when {
            net != null -> net
            code.contains("ADMIN_ONLY_OPERATION") ->
                "Firebase Console -> Authentication -> Settings -> User actions me 'Enable create (sign-up)' ON karein."
            code.contains("OPERATION_NOT_ALLOWED") -> "Firebase Authentication me Email/Password sign-in ON karo."
            code.contains("TOO_MANY") -> "Bohat koshishen, thodi der baad try karein."
            else -> "Firebase login fail (HTTP $http $code)"
        }
    }

    private suspend fun call(action: String): Reply {
        return try {
            val body = buildJsonObject {
                put("email", email)
                put("password", password)
                put("returnSecureToken", true)
            }.toString()
            val res = httpCall(
                "POST",
                "https://identitytoolkit.googleapis.com/v1/accounts:$action?key=${AppConfig.API_KEY}",
                body,
                timeoutMs = 15_000,
            )
            val obj = runCatching { json.parseToJsonElement(res.body).jsonObject }.getOrNull()
            if (res.code == 200 && obj != null) {
                Reply(
                    token = obj["idToken"]?.jsonPrimitive?.contentOrNull,
                    uid = obj["localId"]?.jsonPrimitive?.contentOrNull,
                    expiresInSec = obj["expiresIn"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3600L,
                    code = "",
                    http = 200,
                    net = null,
                )
            } else {
                val msg = obj?.get("error")?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull ?: ""
                Reply(null, null, 0, msg, res.code, null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Reply(null, null, 0, "", 0, e.message ?: "network error")
        }
    }
}
