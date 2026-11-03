package com.example.billing.sync

import com.example.billing.auth.AppConfig
import com.example.billing.auth.AppSettings
import com.example.billing.auth.SettingKeys
import com.example.billing.data.BackupRepository
import com.example.billing.data.BillRepository
import com.example.billing.data.VendorRepository
import com.example.billing.sync.SyncManager.Companion.ROLE_MASTER
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.time.Clock

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

    private val _role = MutableStateFlow<String>(ROLE_MASTER)
    val role: StateFlow<String> = _role.asStateFlow()

    private val _uploadPending = MutableStateFlow(false)
    val uploadPending: StateFlow<Boolean> = _uploadPending.asStateFlow()

    private val _downloadPending = MutableStateFlow(false)
    val downloadPending: StateFlow<Boolean> = _downloadPending.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _lastSyncAt = MutableStateFlow(0L)
    val lastSyncAt: StateFlow<Long> = _lastSyncAt.asStateFlow()

    private val _onlineMode = MutableStateFlow(false)
    val onlineMode: StateFlow<Boolean> = _onlineMode.asStateFlow()

    @Volatile private var currentRole: String = ROLE_MASTER
    @Volatile private var suppressOnChanged: Boolean = false
    @Volatile private var hasUnsyncedLocal: Boolean = false

    private var memberShop: String? = null
    private var skewLoaded = false

    init {
        repo.onChanged = {
            if (!suppressOnChanged) {
                hasUnsyncedLocal = true
                _uploadPending.value = true
                GlobalScope.launch(Dispatchers.IO) {
                    try { settings.put("sync_has_unsynced_local", "1") } catch (_: Exception) {}
                }
                trigger.trySend(Unit)
            }
        }
    }

    // ============ Key settings keys ============

    suspend fun isOnlineMode(): Boolean = settings.get(SettingKeys.SYNC_MODE) == MODE_ONLINE
    suspend fun isMasterRole(): Boolean = currentRole == ROLE_MASTER

    suspend fun setRole(master: Boolean) {
        val r = if (master) ROLE_MASTER else ROLE_VIEWER
        currentRole = r
        settings.put(SYNC_ROLE_KEY, r)
        _role.value = r
        trigger.trySend(Unit)
    }

    private suspend fun loadRole() {
        val saved = settings.get(SYNC_ROLE_KEY) ?: ROLE_MASTER
        currentRole = saved
        _role.value = saved
    }

    private suspend fun loadSkew() {
        if (skewLoaded) return
        repo.clockSkew = settings.get(SettingKeys.SYNC_SKEW)?.toLongOrNull() ?: 0L
        skewLoaded = true
    }

    private suspend fun loadPersistedState() {
        hasUnsyncedLocal = settings.get(KEY_HAS_UNSYNCED) == "1"
        _uploadPending.value = hasUnsyncedLocal
        _lastSyncAt.value = settings.get(KEY_LAST_SYNCED_AT)?.toLongOrNull() ?: 0L
    }

    // SyncManager.kt
    // SyncManager.kt -> refreshCloudSyncState() function ko is se replace karein
    suspend fun refreshCloudSyncState() {
        val shopId = settings.get(SettingKeys.SHOP_ID) ?: return
        try {
            val meta = api.getShopMeta(shopId)
            if (!meta.syncEnabled) {
                // Admin ne Firebase se Sync OFF kar diya hai
                settings.put(SettingKeys.SYNC_MODE, MODE_OFFLINE)
                _onlineMode.value = false
                _status.value = SyncStatus.Disabled
                currentRole = ROLE_MASTER
            } else {
                // Sync ON hai, latest role fetch karein
                currentRole = if (meta.masterDeviceId == deviceId) ROLE_MASTER else ROLE_VIEWER
            }
            _role.value = currentRole
            settings.put(SYNC_ROLE_KEY, currentRole)
        } catch (_: Exception) {
            // Internet issue, ignore karein
        }
    }

    suspend fun getShopMeta(shopId: String) = api.getShopMeta(shopId)
    suspend fun setShopSyncEnabled(shopId: String, enabled: Boolean): String? =
        api.setShopSyncEnabled(shopId, enabled)



    // ============ Enable / Disable ============
    suspend fun enableOnline(): EnableResult = mutex.withLock {
        loadSkew()
        loadRole()
        loadPersistedState()
        val shopId = settings.get(SettingKeys.SHOP_ID)
            ?: return@withLock EnableResult.Error("Shop code set nahi hai.")
        ensureCloud(shopId)?.let { return@withLock EnableResult.Error("Internet/Firebase: $it") }

        // NEW: block going online when admin hasn't enabled sync for this shop
        if (!api.getShopSyncEnabled(shopId)) {
            return@withLock EnableResult.Error(
                "Sync is disabled for this shop. Ask admin to enable it in the Admin Panel."
            )
        }

        settings.put(SettingKeys.SYNC_SHOP, shopId)
        settings.put(SettingKeys.SYNC_MODE, MODE_ONLINE)
        _onlineMode.value = true

        // Initial sync
        refreshPendingState()

// NAYA: Role-aware — MASTER wins, VIEWER always downloads (discards local)
        val err = when {
            currentRole == ROLE_VIEWER -> {
                val hadLocalChanges = hasUnsyncedLocal
                val e = downloadReports(onlyIfEmpty = false)
                if (e == null && hadLocalChanges) {
                    _notice.value = "Viewer mode: Local changes discarded. Master's data loaded."
                }
                e
            }
            hasUnsyncedLocal -> uploadReports()               // Master with local changes → upload
            _downloadPending.value -> downloadReports(onlyIfEmpty = false)  // Master, cloud has updates
            else -> null
        }
        if (err != null) {
            _lastError.value = err
            return@withLock EnableResult.Error(err)
        }
        markSyncSuccess()
        EnableResult.Success("Online. Synced successfully.")
    }

    suspend fun disableOnline(force: Boolean = false): String? = mutex.withLock {
        if (isOnlineMode()) {
            val err = downloadReports(onlyIfEmpty = true)
            if (err != null && !force) return@withLock "Sync fail: $err"
        }
        settings.put(SettingKeys.SYNC_MODE, MODE_OFFLINE)
        _status.value = SyncStatus.Disabled
        _onlineMode.value = false
        null
    }

    // ============ Public sync actions ============
    suspend fun syncNow(): String? = mutex.withLock {
        refreshPendingState()
        doSync()
    }

    suspend fun uploadNow(): String? = mutex.withLock {
        val shopId = settings.get(SettingKeys.SHOP_ID) ?: return@withLock "Shop code missing"
        ensureCloud(shopId)?.let { return@withLock it }
        _lastError.value = null
        _status.value = SyncStatus.Syncing
        val err = uploadReports()
        if (err != null) { _lastError.value = err; _status.value = SyncStatus.Waiting(err); return@withLock err }
        markSyncSuccess()
        _status.value = SyncStatus.Synced
        null
    }

    suspend fun downloadNow(): String? = mutex.withLock {
        val shopId = settings.get(SettingKeys.SHOP_ID) ?: return@withLock "Shop code missing"
        ensureCloud(shopId)?.let { return@withLock it }
        _lastError.value = null
        _status.value = SyncStatus.Syncing
        val err = downloadReports(onlyIfEmpty = false)
        if (err != null) { _lastError.value = err; _status.value = SyncStatus.Waiting(err); return@withLock err }
        markSyncSuccess()
        _status.value = SyncStatus.Synced
        null
    }

    suspend fun forceUpload(): String? = uploadNow()

    // ============ Main loop ============
    suspend fun runLoop() {
        loadSkew()
        loadRole()
        loadPersistedState()
        _onlineMode.value = isOnlineMode()
        _status.value = if (_onlineMode.value) SyncStatus.Waiting("starting") else SyncStatus.Disabled

        while (true) {
            mutex.withLock {
                if (isOnlineMode()) {
                    refreshPendingState()
                    // Only sync if something is pending
                    if (hasUnsyncedLocal || _downloadPending.value) {
                        doSync()
                    } else if (_status.value is SyncStatus.Waiting || _status.value is SyncStatus.Syncing) {
                        _status.value = SyncStatus.Synced
                    }
                } else if (_status.value != SyncStatus.Disabled) {
                    _status.value = SyncStatus.Disabled
                    _onlineMode.value = false
                }
            }
            val waitMs = if (AppConfig.BACKGROUND_PULL_MINUTES > 0)
                AppConfig.BACKGROUND_PULL_MINUTES * 60_000L else null
            if (waitMs == null) trigger.receive() else withTimeoutOrNull(waitMs) { trigger.receive() }
            delay(400)
        }
    }

    private suspend fun doSync(): String? {
        return try {
            _status.value = SyncStatus.Syncing
            val shopId = settings.get(SettingKeys.SHOP_ID) ?: return fail("Shop code missing")
            if (!api.getShopSyncEnabled(shopId)) {
                settings.put(SettingKeys.SYNC_MODE, MODE_OFFLINE)
                _onlineMode.value = false
                _status.value = SyncStatus.Disabled
                return fail("Sync was disabled by admin.")
            }

            ensureCloud(shopId)?.let { return fail(it) }
// NAYA: Role-aware
            val err = when {
                currentRole == ROLE_VIEWER -> {
                    // Viewer: cloud wins always — download (discard local)
                    downloadReports(onlyIfEmpty = false)
                }
                hasUnsyncedLocal -> {
                    // Master with local changes: upload (master wins, no conflict needed)
                    uploadReports()
                }
                _downloadPending.value -> {
                    downloadReports(onlyIfEmpty = false)
                }
                else -> null
            }
            if (err != null) return fail(err)

            markSyncSuccess()
            _status.value = SyncStatus.Synced
            null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { fail(e.message ?: "sync error") }
    }

    private suspend fun fail(reason: String): String {
        _status.value = SyncStatus.Waiting(reason)
        _lastError.value = reason
        return reason
    }

    // ============ Pending-state refresh (API call) ============
    suspend fun refreshPendingState() {
        _onlineMode.value = isOnlineMode()
        // Local pending
        _uploadPending.value = hasUnsyncedLocal
        // Cloud pending
        try {
            val shopId = settings.get(SettingKeys.SHOP_ID) ?: return
            val remote = api.getReports(shopId)
            val cloudGenAt = extractGenAt(remote)
            val lastCloudSeen = settings.get(KEY_LAST_CLOUD_GEN)?.toLongOrNull() ?: 0L
            _downloadPending.value = cloudGenAt > 0 && cloudGenAt > lastCloudSeen
        } catch (_: Exception) {
            // Network error - keep current state
        }
    }

    private fun extractGenAt(remote: String?): Long {
        if (remote == null) return 0L
        return try {
            json.parseToJsonElement(remote).jsonObject["generatedAt"]?.jsonPrimitive?.longOrNull ?: 0L
        } catch (_: Exception) { 0L }
    }

    private suspend fun markSyncSuccess() {
        hasUnsyncedLocal = false
        _uploadPending.value = false
        _downloadPending.value = false
        _lastError.value = null
        _lastSyncAt.value = Clock.System.now().toEpochMilliseconds()
        settings.put(KEY_HAS_UNSYNCED, "0")
        settings.put(KEY_LAST_SYNCED_AT, _lastSyncAt.value.toString())
        // Refresh cloud genAt so downloadPending stays false
        try {
            val shopId = settings.get(SettingKeys.SHOP_ID) ?: return
            val remote = api.getReports(shopId)
            val cloudGenAt = extractGenAt(remote)
            settings.put(KEY_LAST_CLOUD_GEN, cloudGenAt.toString())
        } catch (_: Exception) {}
    }

    // ============ Upload / Download ============
    private suspend fun ensureCloud(shopId: String): String? {
        if (memberShop == shopId) return null
        val r = api.registerMember(shopId)
        if (r.error != null) return r.error
        if (r.serverTs > 0) {
            val skew = r.serverTs - (r.localBefore + r.localAfter) / 2
            repo.clockSkew = skew
            settings.put(SettingKeys.SYNC_SKEW, skew.toString())
        }

        // NAYA: Cloud se Master Device check karein
        val masterId = api.getMasterDevice(shopId)
        if (masterId == null) {
            // Agar koi master set nahi hai, to jo pehli dafa aaya hai wo master ban jaye
            api.setMasterDevice(shopId, deviceId)
            currentRole = ROLE_MASTER
        } else {
            currentRole = if (masterId == deviceId) ROLE_MASTER else ROLE_VIEWER
        }
        _role.value = currentRole
        settings.put(SYNC_ROLE_KEY, currentRole)

        memberShop = shopId
        return null
    }

    private suspend fun uploadReports(): String? {
        val shopId = settings.get(SettingKeys.SHOP_ID) ?: return "Shop code missing"
        for (del in repo.pendingDeletes()) {
            val err = api.deleteBill(shopId, del.remoteId)   // add this method
            if (err != null) return err
            repo.clearPendingDelete(del.remoteId)
        }
        val reports = buildJsonObject {
            put("fullBackup", backupRepo.exportAll())
            put("generatedAt", Clock.System.now().toEpochMilliseconds())
            put("deviceId", deviceId)
        }
        return api.syncReports(shopId, reports.toString())
    }

    private suspend fun downloadReports(onlyIfEmpty: Boolean = true): String? {
        val shopId = settings.get(SettingKeys.SHOP_ID) ?: return "Shop code missing"
        val remote = api.getReports(shopId) ?: return null

        val inner = runCatching {
            json.parseToJsonElement(remote).jsonObject["fullBackup"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        if (inner.isNullOrBlank() || inner.length < 50) return null

        val localCount = repo.countBills()
        if (onlyIfEmpty && localCount > 0L) return null

        suppressOnChanged = true
        try {
            val err = backupRepo.importAll(inner)
            if (err != null) return "Restore failed: $err"
        } finally {
            suppressOnChanged = false
        }
        _dataVersion.update { it + 1 }
        _notice.value = "Cloud data synced."
        return null
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

        // Empty backup upload karo taaki master ko pull karna pade
        val emptyBackup = com.example.billing.utils.FullBackup()
        val emptyJson = json.encodeToString(
            com.example.billing.utils.FullBackup.serializer(), emptyBackup
        )
        val reports = buildJsonObject {
            put("fullBackup", emptyJson)
            put("generatedAt", Clock.System.now().toEpochMilliseconds())
            put("deviceId", deviceId)
        }
        return api.syncReports(shopId, reports.toString())
    }
    suspend fun onShopChanged() {
        memberShop = null
        hasUnsyncedLocal = false
        settings.put(SettingKeys.SYNC_MODE, MODE_OFFLINE)
        settings.put(KEY_HAS_UNSYNCED, "0")
        _uploadPending.value = false
        _downloadPending.value = false
        _lastError.value = null
        _lastSyncAt.value = 0L
        _status.value = SyncStatus.Disabled
        _onlineMode.value = false
    }
    companion object {
        // --- Sync bookkeeping keys (private) ---
        private const val KEY_HAS_UNSYNCED = "sync_has_unsynced_local"
        private const val KEY_LAST_SYNCED_AT = "sync_last_synced_at"
        private const val KEY_LAST_CLOUD_GEN = "sync_last_cloud_gen"

        // --- Public constants ---
        const val MODE_ONLINE = "online"
        const val MODE_OFFLINE = "offline"
        const val ROLE_MASTER = "master"
        const val ROLE_VIEWER = "viewer"
        const val SYNC_ROLE_KEY = "sync_role"
    }
}