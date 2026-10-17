package com.example.billing.sync

import com.example.billing.auth.AppConfig
import com.example.billing.auth.AppSettings
import com.example.billing.auth.SettingKeys
import com.example.billing.data.BillRepository
import com.example.billing.utils.billsToBackupJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

sealed interface SyncStatus {
    /** Offline mode: data sirf is laptop me. */
    data object Disabled : SyncStatus
    data object Syncing : SyncStatus
    /** Online aur sab kuch Firebase ke saath barabar. */
    data object Synced : SyncStatus
    /** Online mode hai magar abhi bhej nahi saka (net nahi / error). Net aate hi khud ho jayega. */
    data class Waiting(val pending: Int, val reason: String) : SyncStatus
}

/** Switch ON karne ka nateeja. */
sealed interface EnableResult {
    /** note != null: online ho gaya lekin sync poora nahi hua (net aate hi khud ho jayega). */
    data class Success(val note: String?) : EnableResult
    data class Error(val message: String) : EnableResult
    /** Is laptop par pehle kisi aur shop ka data hai; naye shop me bhejne se pehle poochna zaroori. */
    data class NeedsConfirm(val oldShop: String) : EnableResult
}

/**
 * Online / Offline ka poora nizam.
 *
 *  OFFLINE: sab kuch sirf local database me (pehle jaisa).
 *  ONLINE : local database + Firebase /bills/{shopCode}. Net na ho to local me save hota rahta hai,
 *           net aate hi khud Firebase par chala jata hai.
 *
 * Firebase kab chheda jata hai (kharcha kam rakhne ke liye):
 *   - bill banne / edit / delete par (sirf badli hui cheezen bhejta hai, aur sirf NAYA data mangta hai)
 *   - app khulte hi ek dafa, aur History ke Refresh button par
 *   - kuch bhejna baqi ho (net nahi tha) to har SYNC_RETRY_SECONDS baad
 * Warna koi request nahi jati.
 *
 * Switch ON  -> purana local data Firebase par jata hai + Firebase ka data local me aata hai.
 * Switch OFF -> aakhri baar Firebase ka naya data local me utar kar offline ho jata hai.
 */
class SyncManager(
    private val settings: AppSettings,
    private val repo: BillRepository,
    private val api: BillCloudApi,
    private val deviceId: String,
) {
    private val mutex = Mutex()
    private val trigger = Channel<Unit>(Channel.CONFLATED)

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Disabled)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** Jab Firebase se local data badle to ye barhta hai (screens reload kar len). */
    private val _dataVersion = MutableStateFlow(0)
    val dataVersion: StateFlow<Int> = _dataVersion.asStateFlow()

    /** Kabhi kabhi ka paigham (maslan "2 bills par dusre laptop ki tabdili aayi"). */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** Is session me is laptop ko shop ka member likh chuke? (rules isi se darwaza kholte hain) */
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

    // ------------------------------------------------------------------
    // Admin panel ka switch
    // ------------------------------------------------------------------

    /**
     * confirmForeign = true: is laptop par jo purane shop ka data hai wo naye shop me bhejne ki ijazat.
     */
    suspend fun enableOnline(confirmForeign: Boolean = false): EnableResult {
        return mutex.withLock {
            loadSkew()
            val shopId = settings.get(SettingKeys.SHOP_ID)
            if (shopId.isNullOrBlank()) return@withLock EnableResult.Error("Pehle is laptop ka shop code set karo.")

            val owner = settings.get(SettingKeys.SYNC_SHOP)
            if (owner != null && owner != shopId) {
                if (repo.countBills() > 0 && !confirmForeign) return@withLock EnableResult.NeedsConfirm(owner)
                // Purane shop ka sync hisaab saaf (bills local me rehte hain, naye shop ke liye "naye" ban jate hain)
                repo.resetSyncState()
                settings.put(SettingKeys.syncCursor(shopId), "0")
            }

            // Net / Firebase / login / license na ho to switch ON hone hi na do
            ensureCloud(shopId)?.let { return@withLock EnableResult.Error("Internet / Firebase masla: $it") }

            settings.put(SettingKeys.SYNC_SHOP, shopId)
            settings.put(SettingKeys.SYNC_MODE, MODE_ONLINE)
            EnableResult.Success(
                doSync()?.let { "Online ho gaya lekin sync poora nahi hua (net aate hi khud ho jayega): $it" }
            )
        }
    }

    /**
     * null = kamyab (offline ho gaya, Firebase ka naya data local me aa chuka).
     * force = true: sync fail ho tab bhi offline kar do (Firebase ka naya data local me nahi aayega).
     */
    suspend fun disableOnline(force: Boolean = false): String? {
        return mutex.withLock {
            if (isOnlineMode()) {
                val err = doSync()
                if (err != null && !force && isOnlineMode()) {
                    return@withLock "Offline karne se pehle Firebase ka data utarna zaroori hai, lekin sync fail hua: $err"
                }
            }
            settings.put(SettingKeys.SYNC_MODE, MODE_OFFLINE)
            _status.value = SyncStatus.Disabled
            null
        }
    }

    /**
     * Shop code badalne ke baad bulayen. Agar online mode me tha to foran offline kar deta hai
     * (warna purane shop ke bills naye shop me mil jate). true = offline kar diya.
     */
    suspend fun handleShopChange(): Boolean = mutex.withLock {
        val shopId = settings.get(SettingKeys.SHOP_ID)
        val owner = settings.get(SettingKeys.SYNC_SHOP)
        if (isOnlineMode() && owner != null && owner != shopId) {
            settings.put(SettingKeys.SYNC_MODE, MODE_OFFLINE)
            _status.value = SyncStatus.Disabled
            memberShop = null
            true
        } else {
            false
        }
    }

    /** Abhi foran sync (Sync Now / History ka Refresh). null = kamyab. */
    suspend fun syncNow(): String? = mutex.withLock { doSync() }

    // ------------------------------------------------------------------
    // Background loop (license Allowed hone ke baad ek dafa chalayen)
    // ------------------------------------------------------------------

    suspend fun runLoop() {
        loadSkew()
        _status.value = if (isOnlineMode()) SyncStatus.Waiting(repo.pendingCount(), "starting") else SyncStatus.Disabled
        while (true) {
            // Mode lock ke ANDAR dekhte hain (switch OFF hone ke baad purana sync na chale).
            val pending = mutex.withLock {
                if (isOnlineMode()) {
                    doSync()
                    repo.pendingCount()
                } else {
                    if (_status.value != SyncStatus.Disabled) _status.value = SyncStatus.Disabled
                    0
                }
            }
            val waitMs: Long? = when {
                pending > 0 -> AppConfig.SYNC_RETRY_SECONDS * 1000L
                AppConfig.BACKGROUND_PULL_MINUTES > 0 -> AppConfig.BACKGROUND_PULL_MINUTES * 60_000L
                else -> null
            }
            if (waitMs == null) trigger.receive() else withTimeoutOrNull(waitMs) { trigger.receive() }
            delay(400) // chhoti chhoti tabdiliyan ek saath bhejne ke liye
        }
    }

    // ------------------------------------------------------------------
    // Firebase se export
    // ------------------------------------------------------------------

    /** Is shop ka saara data Firebase se, backup JSON ki shakal me. null = net / error. */
    suspend fun exportCloudJson(): String? {
        val shopId = settings.get(SettingKeys.SHOP_ID)
        if (shopId.isNullOrBlank()) return null
        if (ensureCloud(shopId) != null) return null
        val list = api.listBills(shopId) as? CloudList.Ok ?: return null
        val bills = list.bills.values
            .filter { !it.deleted }
            .sortedByDescending { it.invoiceNo }
            .map { it.toBill() }
        return billsToBackupJson(bills)
    }

    // ------------------------------------------------------------------
    // Asal sync
    // ------------------------------------------------------------------

    private suspend fun fail(reason: String): String {
        _status.value = SyncStatus.Waiting(repo.pendingCount(), reason)
        return reason
    }

    /**
     * Firebase login + is laptop ko shop ka member likhna (har session me ek dafa).
     * null = tayyar, warna wajah. Isi jawab se ghadi ka farq bhi nikalta hai.
     */
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

    private suspend fun doSync(): String? {
        return try {
            syncInner()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(e.message ?: "sync error")
        }
    }

    /** items ko chhote chhote gutthon me ek saath bhejta hai. null = sab theek, warna pehli galti. */
    private suspend fun <T> inParallel(items: List<T>, block: suspend (T) -> String?): String? {
        for (chunk in items.chunked(PUSH_PARALLEL)) {
            val errors = coroutineScope { chunk.map { async { block(it) } }.awaitAll() }
            errors.firstOrNull { it != null }?.let { return it }
        }
        return null
    }

    /** null = kamyab, warna wajah. Hamesha mutex ke andar chalayen. */
    private suspend fun syncInner(): String? {
        loadSkew()
        val shopId = settings.get(SettingKeys.SHOP_ID)
        if (shopId.isNullOrBlank()) return fail("Shop code set nahi hai")

        // Shop code online mode me badal gaya to sync rok do (purane shop ka data naye me na mile)
        val owner = settings.get(SettingKeys.SYNC_SHOP)
        if (owner != null && owner != shopId) {
            settings.put(SettingKeys.SYNC_MODE, MODE_OFFLINE)
            _status.value = SyncStatus.Disabled
            memberShop = null
            return "Shop code badal gaya, is liye online sync band kar diya. Admin Panel se dobara ON karein."
        }

        _status.value = SyncStatus.Syncing
        ensureCloud(shopId)?.let { return fail(it) }

        // 1. Delete hue bills ka nishan Firebase par lagao
        inParallel(repo.pendingDeletes()) { d ->
            val tombstone = RemoteBill(deleted = true, updatedAt = d.deletedAt, deviceId = deviceId)
            val res = api.putBill(shopId, d.remoteId, tombstone)
            if (res.error == null) repo.clearPendingDelete(d.remoteId)
            res.error
        }?.let { return fail(it) }

        // 2. Firebase se sirf NAYA data (pehli dafa sab)
        val cursorKey = SettingKeys.syncCursor(shopId)
        val cursor = settings.get(cursorKey)?.toLongOrNull() ?: 0L
        val remote: Map<String, RemoteBill> = when (val r = api.listBills(shopId, cursor)) {
            is CloudList.Ok -> r.bills
            is CloudList.Failed -> return fail(r.reason)
        }

        // 3. Purane bills ko jodo (duplicate se bachte hue). Ye sirf pehli (poori) download par mumkin hai.
        if (cursor == 0L) repo.adoptUnmapped(remote)

        // 4. Firebase ki tabdiliyan local me lao (conflict ho to payment milti hai, baqi me naya jeetta hai)
        val merge = repo.mergeRemote(remote)
        if (merge.changed) _dataVersion.update { it + 1 }
        if (merge.overwritten > 0) {
            _notice.value = "${merge.overwritten} bill me dusre laptop ki nayi tabdili aayi, is liye is laptop ki wo tabdili replace ho gayi."
        }
        val newCursor = remote.values.maxOfOrNull { it.serverTs } ?: 0L
        if (newCursor > cursor) settings.put(cursorKey, newCursor.toString())

        // 5. Local tabdiliyan Firebase par bhejo
        inParallel(repo.dirtyRows()) { row ->
            val bill = repo.getBill(row.billId) ?: return@inParallel null
            val res = api.putBill(shopId, row.remoteId, bill.toRemote(row.updatedAt, deviceId))
            if (res.error == null) repo.markPushed(row.billId, row.updatedAt)
            res.error
        }?.let { return fail(it) }

        _status.value = SyncStatus.Synced
        return null
    }

    fun clearNotice() {
        _notice.value = null
    }

    companion object {
        const val MODE_ONLINE = "online"
        const val MODE_OFFLINE = "offline"
        private const val PUSH_PARALLEL = 6
    }
}
