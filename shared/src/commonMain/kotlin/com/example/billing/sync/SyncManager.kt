package com.example.billing.sync

import com.example.billing.auth.AppConfig
import com.example.billing.auth.AppSettings
import com.example.billing.auth.SettingKeys
import com.example.billing.data.BackupRepository
import com.example.billing.data.BillRepository
import com.example.billing.data.VendorRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

sealed interface SyncStatus {
    data object Disabled : SyncStatus
    data object Syncing : SyncStatus
    data object Synced : SyncStatus
    data class Waiting(val reason: String) : SyncStatus
}

sealed interface EnableResult {
    data class Success(val note: String?) : EnableResult
    data class Error(val message: String) : EnableResult
}

/**
 * Simplified sync — SIRF reports Firebase par jate hain.
 * 5 reports: SALE, STOCK, PURCHASE, VENDOR, CUSTOMER.
 * Bills alag se sync nahi hote — sab kuch reports me hai.
 */
class SyncManager(
    private val settings: AppSettings,
    private val repo: BillRepository,
    private val vendors: VendorRepository,
    private val backupRepo: BackupRepository,
    private val api: BillCloudApi,
    private val deviceId: String,
) {
    private val mutex = Mutex()
    private val trigger = Channel<Unit>(Channel.CONFLATED)
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Disabled)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _dataVersion = MutableStateFlow(0)
    val dataVersion: StateFlow<Int> = _dataVersion.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private var memberShop: String? = null
    private var skewLoaded = false

    init {
        repo.onChanged = { trigger.trySend(Unit) }
    }

    suspend fun isOnlineMode(): Boolean = settings.get(SettingKeys.SYNC_MODE) == MODE_ONLINE

    private suspend fun loadSkew() {
        if (skewLoaded) return
        repo.clockSkew = settings.get(SettingKeys.SYNC_SKEW)?.toLongOrNull() ?: 0L
        skewLoaded = true
    }

    suspend fun enableOnline(): EnableResult = mutex.withLock {
        loadSkew()
        val shopId = settings.get(SettingKeys.SHOP_ID)
            ?: return@withLock EnableResult.Error("Shop code set nahi hai.")
        ensureCloud(shopId)?.let { return@withLock EnableResult.Error("Internet/Firebase: $it") }
        settings.put(SettingKeys.SYNC_SHOP, shopId)
        settings.put(SettingKeys.SYNC_MODE, MODE_ONLINE)
        EnableResult.Success(uploadReports()?.let { "Online. Report upload pending: $it" })
    }

    suspend fun disableOnline(force: Boolean = false): String? = mutex.withLock {
        if (isOnlineMode()) {
            val err = downloadReports()
            if (err != null && !force) return@withLock "Sync fail: $err"
        }
        settings.put(SettingKeys.SYNC_MODE, MODE_OFFLINE)
        _status.value = SyncStatus.Disabled
        null
    }

    suspend fun syncNow(): String? = mutex.withLock { doSync() }

    suspend fun runLoop() {
        loadSkew()
        _status.value = if (isOnlineMode()) SyncStatus.Waiting("starting") else SyncStatus.Disabled
        while (true) {
            mutex.withLock {
                if (isOnlineMode()) { doSync() } else if (_status.value != SyncStatus.Disabled) _status.value = SyncStatus.Disabled
            }
            val waitMs = if (AppConfig.BACKGROUND_PULL_MINUTES > 0) AppConfig.BACKGROUND_PULL_MINUTES * 60_000L else null
            if (waitMs == null) trigger.receive() else withTimeoutOrNull(waitMs) { trigger.receive() }
            delay(400)
        }
    }

    private suspend fun doSync(): String? {
        return try {
            _status.value = SyncStatus.Syncing
            val shopId = settings.get(SettingKeys.SHOP_ID) ?: return fail("Shop code missing")
            ensureCloud(shopId)?.let { return fail(it) }
            uploadReports()?.let { return fail(it) }
            downloadReports()?.let { return fail(it) }
            _status.value = SyncStatus.Synced
            null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { fail(e.message ?: "sync error") }
    }

    private suspend fun fail(reason: String): String {
        _status.value = SyncStatus.Waiting(reason)
        return reason
    }

    private suspend fun ensureCloud(shopId: String): String? {
        if (memberShop == shopId) return null
        val r = api.registerMember(shopId)
        if (r.error != null) return r.error
        if (r.serverTs > 0) {
            val skew = r.serverTs - (r.localBefore + r.localAfter) / 2
            repo.clockSkew = skew
            settings.put(SettingKeys.SYNC_SKEW, skew.toString())
        }
        memberShop = shopId
        return null
    }

    /** 5 reports build karke Firebase par bhejo */
    private suspend fun uploadReports(): String? {
        val shopId = settings.get(SettingKeys.SHOP_ID) ?: return "Shop code missing"
        val reports = buildFiveReports()
        val body = reports.toString()
        return api.syncReports(shopId, body)
    }

    /** Firebase se reports laao aur local DB me merge karo */
    private suspend fun downloadReports(): String? {
        val shopId = settings.get(SettingKeys.SHOP_ID) ?: return "Shop code missing"
        val remote = api.getReports(shopId) ?: return null

        // Wrapper parse karo -> andar ki FullBackup string nikaalo
        val inner = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(remote)
                .jsonObject["fullBackup"]
                ?.jsonPrimitive
                ?.contentOrNull
        }.getOrNull()

        if (inner.isNullOrBlank() || inner.length < 50) return null

        val localCount = repo.countBills()
        if (localCount == 0L) {
            val err = backupRepo.importAll(inner)
            if (err != null) return "Restore failed: $err"
            _dataVersion.update { it + 1 }
            _notice.value = "Cloud reports restored."
        }
        return null
    }

    /** 5 reports (SALE, STOCK, PURCHASE, VENDOR, CUSTOMER) as one JSON */
    private suspend fun buildFiveReports() = buildJsonObject {
        // Full backup JSON includes all data; report screen reads from same source
        put("fullBackup", backupRepo.exportAll())
        put("generatedAt", kotlin.time.Clock.System.now().toEpochMilliseconds())
        put("deviceId", deviceId)
    }

    suspend fun exportCloudJson(): String? {
        val shopId = settings.get(SettingKeys.SHOP_ID) ?: return null
        if (ensureCloud(shopId) != null) return null
        return api.getReports(shopId)
    }

    fun clearNotice() { _notice.value = null }

    suspend fun clearCloudData(): String? {
        if (!isOnlineMode()) return null
        val shopId = settings.get(SettingKeys.SHOP_ID) ?: return "Shop ID missing"
        return api.deleteShopData(shopId)
    }

    companion object {
        const val MODE_ONLINE = "online"
        const val MODE_OFFLINE = "offline"
    }
}