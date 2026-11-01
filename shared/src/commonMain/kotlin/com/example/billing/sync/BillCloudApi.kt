package com.example.billing.sync

import com.example.billing.auth.AppConfig
import com.example.billing.auth.httpCall
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.time.Clock

sealed interface CloudList {
    data class Ok(val bills: Map<String, RemoteBill>) : CloudList
    data class Failed(val reason: String) : CloudList
}

/** error == null matlab kamyab. serverTs = Firebase ne jo server waqt likha (0 = pata nahi). */
class PutResult(val error: String?, val serverTs: Long = 0, val localBefore: Long = 0, val localAfter: Long = 0)

/**
 * Firebase Realtime Database se bills ka lena dena (REST). Har request is laptop ke login token ke saath jati hai.
 * Net na ho to Failed / error text wapas deta hai.
 */
class BillCloudApi(private val auth: CloudAuth, private val deviceId: String) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val db get() = AppConfig.DB_URL

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    private class Resp(val code: Int, val body: String, val error: String?)

    /** Token laga kar request. Token purana nikla (401) to ek dafa naya le kar dobara. */
    private suspend fun request(method: String, path: String, query: String = "", body: String? = null, timeoutMs: Int = 15_000): Resp {
        var forceNew = false
        repeat(2) {
            val token = auth.token(forceNew) ?: return Resp(0, "", auth.lastError ?: "Firebase login fail")
            val res = httpCall(method, "$db/$path.json?auth=$token$query", body, timeoutMs)
            if (res.code != 401) return Resp(res.code, res.body, null)
            forceNew = true
        }
        return Resp(401, "", "Permission denied (license band hai, ye laptop block hai, ya Firebase rules sahi publish nahi hue)")
    }

    private fun httpProblem(code: Int, what: String): String = when (code) {
        400 -> "$what HTTP 400 (Rules me bills ke andar \".indexOn\": [\"serverTs\"] publish kiya?)"
        else -> "$what HTTP $code"
    }

    /**
     * Is shop ke bills. since = 0 -> SAARE (pehli dafa), warna sirf wo jin ka serverTs >= since (naye / badle hue).
     * Delete wale nishan bhi aate hain.
     */
    suspend fun listBills(shopId: String, since: Long = 0L): CloudList {
        return try {
            val query = if (since > 0) "&orderBy=%22serverTs%22&startAt=$since" else ""
            val res = request("GET", "bills/$shopId", query, timeoutMs = 30_000)
            if (res.error != null) return CloudList.Failed(res.error)
            if (res.code != 200) return CloudList.Failed(httpProblem(res.code, "bills"))
            val element = json.parseToJsonElement(res.body)
            if (element !is JsonObject) {
                CloudList.Ok(emptyMap())
            } else {
                val map = HashMap<String, RemoteBill>()
                for ((key, value) in element.entries) {
                    val bill = runCatching { json.decodeFromJsonElement(RemoteBill.serializer(), value) }.getOrNull()
                    if (bill != null) map[key] = bill
                }
                CloudList.Ok(map)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CloudList.Failed(e.message ?: "network error")
        }
    }

    /** Bill likhna / badalna. serverTs Firebase khud lagata hai. */
    suspend fun putBill(shopId: String, remoteId: String, bill: RemoteBill): PutResult {
        return try {
            val obj = json.encodeToJsonElement(RemoteBill.serializer(), bill).jsonObject
            val body = JsonObject(obj + ("serverTs" to SERVER_TIME)).toString()
            val before = now()
            val res = request("PUT", "bills/$shopId/$remoteId", body = body)
            val after = now()
            when {
                res.error != null -> PutResult(res.error)
                res.code != 200 -> PutResult(httpProblem(res.code, "bill"))
                else -> PutResult(null, readTs(res.body, "serverTs"), before, after)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PutResult(e.message ?: "network error")
        }
    }

    /**
     * Is laptop ko is shop ka "member" likhna (rules isi se bills ka darwaza kholte hain, aur sirf tab jab
     * license ON ho aur laptop block na ho). Jawab me Firebase ka server waqt bhi milta hai (ghadi ka farq nikalne ke liye).
     */
    suspend fun registerMember(shopId: String): PutResult {
        return try {
            val uid = auth.uid ?: auth.token()?.let { auth.uid } ?: return PutResult(auth.lastError ?: "Firebase login fail")
            val body = buildJsonObject {
                put("deviceId", deviceId)
                put("at", SERVER_TIME)
            }.toString()
            val before = now()
            val res = request("PUT", "members/$shopId/$uid", body = body)
            val after = now()
            when {
                res.error != null -> PutResult(res.error)
                res.code != 200 -> PutResult(httpProblem(res.code, "members") + " (Rules me members section publish kiya?)")
                else -> PutResult(null, readTs(res.body, "at"), before, after)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PutResult(e.message ?: "network error")
        }
    }

    private fun readTs(body: String, key: String): Long =
        runCatching { json.parseToJsonElement(body).jsonObject[key]?.jsonPrimitive?.longOrNull }.getOrNull() ?: 0L
    /** Saare reports ek saath Firebase pe bhejna (Requirement 5) */
    suspend fun syncReports(shopId: String, reportsJson: String): String? {
        return try {
            val res = request("PUT", "reports/$shopId", body = reportsJson)
            if (res.code == 200) null else "HTTP ${res.code}"
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { e.message ?: "network error" }
    }

    /** Firebase se reports lena */
    suspend fun getReports(shopId: String): String? {
        return try {
            val res = request("GET", "reports/$shopId")
            if (res.code == 200 && res.body.isNotBlank()) res.body else null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { null }
    }

    /** Is shop ka online data (Bills aur Master Data) delete karne ke liye. */
    suspend fun deleteShopData(shopId: String): String? {
        return try {
            val res1 = request("DELETE", "reports/$shopId")   // <-- ADD: actual data
            val res2 = request("DELETE", "bills/$shopId")     // purana, safety
            val res3 = request("DELETE", "shopData/$shopId")
            if (res1.code == 200 && res2.code == 200 && res3.code == 200) null
            else "Failed (reports: ${res1.code}, bills: ${res2.code}, data: ${res3.code})"
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { e.message ?: "network error" }
    }
    private companion object {
        /** Firebase ko kehna: yahan apna server waqt likh do. */
        val SERVER_TIME = buildJsonObject { put(".sv", "timestamp") }
    }
}
