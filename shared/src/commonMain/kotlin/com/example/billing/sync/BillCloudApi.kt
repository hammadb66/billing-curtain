package com.example.billing.sync

import com.example.billing.auth.AppConfig
import com.example.billing.auth.httpCall
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.time.Clock

sealed interface CloudList {
    data class Ok(val bills: Map<String, RemoteBill>) : CloudList
    data class Failed(val reason: String) : CloudList
}

class PutResult(val error: String?, val serverTs: Long = 0, val localBefore: Long = 0, val localAfter: Long = 0)

class BillCloudApi(private val auth: CloudAuth, private val deviceId: String) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val db get() = AppConfig.DB_URL

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    private class Resp(val code: Int, val body: String, val error: String?)

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

    suspend fun syncReports(shopId: String, reportsJson: String): String? {
        return try {
            val res = request("PUT", "reports/$shopId", body = reportsJson)
            if (res.code == 200) null else "HTTP ${res.code}"
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { e.message ?: "network error" }
    }

    suspend fun getReports(shopId: String): String? {
        return try {
            val res = request("GET", "reports/$shopId")
            if (res.code == 200 && res.body.isNotBlank()) res.body else null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { null }
    }

    suspend fun deleteShopData(shopId: String): String? {
        return try {
            val res1 = request("DELETE", "reports/$shopId")
            val res2 = request("DELETE", "bills/$shopId")
            val res3 = request("DELETE", "shopData/$shopId")
            if (res1.code == 200 && res2.code == 200 && res3.code == 200) null
            else "Failed (reports: ${res1.code}, bills: ${res2.code}, data: ${res3.code})"
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { e.message ?: "network error" }
    }

    private companion object {
        val SERVER_TIME = buildJsonObject { put(".sv", "timestamp") }
    }

    // FIXED: JSON String encode crash fixed
    suspend fun setMasterDevice(shopId: String, deviceId: String): String? {
        return try {
            // Direct JSON string quotation marks ke sath
            val body = "\"$deviceId\""
            val res = request("PUT", "shops/$shopId/meta/masterDeviceId", body = body)
            if (res.code == 200) null else "HTTP ${res.code}"
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { e.message ?: "network error" }
    }

    suspend fun getMasterDevice(shopId: String): String? {
        return try {
            val res = request("GET", "shops/$shopId/meta")
            if (res.code == 200 && res.body.isNotBlank()) {
                json.parseToJsonElement(res.body).jsonObject["masterDeviceId"]?.jsonPrimitive?.contentOrNull
            } else null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { null }
    }

    @Serializable
    data class ShopMeta(val syncEnabled: Boolean = false, val masterDeviceId: String? = null)

    suspend fun getShopMeta(shopId: String): ShopMeta {
        return try {
            val res = request("GET", "shops/$shopId/meta")
            if (res.code == 200 && res.body.isNotBlank() && res.body != "null") {
                val obj = json.parseToJsonElement(res.body).jsonObject
                ShopMeta(
                    syncEnabled = obj["syncEnabled"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
                    masterDeviceId = obj["masterDeviceId"]?.jsonPrimitive?.contentOrNull
                )
            } else ShopMeta()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { ShopMeta() }
    }

    suspend fun getShopSyncEnabled(shopId: String): Boolean = getShopMeta(shopId).syncEnabled

    suspend fun setShopSyncEnabled(shopId: String, enabled: Boolean): String? {
        return try {
            val res = request("PUT", "shops/$shopId/meta/syncEnabled", body = enabled.toString())
            if (res.code == 200) null else "HTTP ${res.code}"
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { e.message ?: "network error" }
    }

    // In BillCloudApi.kt
    suspend fun deleteBill(shopId: String, remoteId: String): String? {
        return try {
            val res = request("DELETE", "bills/$shopId/$remoteId")
            if (res.code == 200) null else "HTTP ${res.code}"
        } catch (e: Exception) { e.message }
    }
}