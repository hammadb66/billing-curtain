package com.example.billing.data

import com.example.billing.BillingDatabase
import com.example.billing.auth.randomSaltHex
import com.example.billing.model.Bill
import com.example.billing.model.RoomData
import com.example.billing.sync.RemoteBill
import com.example.billing.utils.BillBackup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.collections.iterator
import kotlin.compareTo
import kotlin.io.iterator
import kotlin.text.iterator
import kotlin.time.Clock

/** Phone number se mila purana customer: naam + us ka purana baqaya. */
class CustomerInfo(val name: String, val previousBalance: Double)

/** Sync ke liye: ek bill ka Firebase se rishta. */
class SyncRow(val billId: Long, val remoteId: String, val pushedAt: Long, val updatedAt: Long)

/** Sync ke liye: delete hua bill jis ka nishan Firebase par lagana baqi hai. */
class PendingDeleteRow(val remoteId: String, val deletedAt: Long)

/**
 * Local database hamesha asal source hai (offline chalta hai). Har tabdili par sath sath
 * "dirty" nishan lagta hai; SyncManager online mode me wo nishan dekh kar Firebase ko bhejta hai.
 * UI ke liye purane functions ke naam/signature wohi hain.
 */
class BillRepository(database: BillingDatabase) {

    private val queries = database.billQueries
    private val syncQ = database.syncQueries
    private val json = Json { ignoreUnknownKeys = true }
    private val roomsSerializer = ListSerializer(RoomData.serializer())

    private companion object {
        /** Invoice number ke kam az kam kitne digits: 2 -> hammad-01 ... hammad-99, phir hammad-100 */
        const val INVOICE_DIGITS = 2
    }

    /** SyncManager yahan apna "sync karo" ka ishara lagata hai. */
    var onChanged: (() -> Unit)? = null

    /**
     * Is laptop ki ghadi aur Firebase server ki ghadi ka farq (millis). SyncManager set karta hai,
     * taake galat date wala laptop "hamesha jeet / hamesha haar" na kare.
     */
    var clockSkew: Long = 0L

    private fun now(): Long = Clock.System.now().toEpochMilliseconds() + clockSkew
    private fun newRemoteId(): String = "b" + randomSaltHex().take(24)

    /** Database ki ek row ko Bill me badalta hai (selectAll aur selectByPhone dono yehi use karte hain). */
    private fun toBill(
        id: Long,
        customer: String,
        phone: String,
        data: String,
        date: String,
        discount: Double,
        extraCharges: Double,
        paidAmount: Double?,
        label: String?,
    ): Bill = Bill(
        id = id,
        customer = customer,
        phone = phone,
        date = date,
        rooms = runCatching { json.decodeFromString(roomsSerializer, data) }.getOrDefault(emptyList()),
        discount = discount,
        extraCharges = extraCharges,
        paidAmount = paidAmount,
        invoiceLabel = label.orEmpty(),
    )

    // ---------- Invoice number: "<user>-<NN>" ----------

    private fun cleanPrefix(raw: String): String = raw.filter { it.isLetterOrDigit() }.take(12).ifBlank { "inv" }

    /** Is user ka agla number: ab tak dekhe gaye (local + Firebase se aaye) sab labels me sab se bada + 1. */
    private fun nextLabel(rawPrefix: String): String {
        val prefix = cleanPrefix(rawPrefix)
        val head = "$prefix-"
        val max = syncQ.selectLabelsLike("$head%").executeAsList()
            .filter { it.startsWith(head, ignoreCase = true) }
            .mapNotNull { it.substring(head.length).takeWhile { c -> c.isDigit() }.toLongOrNull() }
            .maxOrNull() ?: 0L
        return head + (max + 1).toString().padStart(INVOICE_DIGITS, '0')
    }

    suspend fun getAllBills(): List<Bill> = withContext(Dispatchers.Default) {
        queries.selectAll(this@BillRepository::toBill).executeAsList()
    }

    suspend fun getBill(id: Long): Bill? = withContext(Dispatchers.Default) {
        queries.selectById(id, this@BillRepository::toBill).executeAsOneOrNull()
    }

    /** Naam + purana baqaya. Is phone ka koi bill nahi to null. */
    suspend fun getCustomerInfo(phone: String): CustomerInfo? = withContext(Dispatchers.Default) {
        val list = queries.selectByPhone(phone, this@BillRepository::toBill).executeAsList()
        if (list.isEmpty()) null else CustomerInfo(list.first().customer, list.sumOf { it.balance })
    }

    /**
     * Naye bill ka invoice number (maslan "hammad-05") wapas deta hai, taake PDF me likh sakein.
     * invoicePrefix = login user ka naam; har user ka apna 01, 02, 03... chalta hai.
     */
    suspend fun insertBill(
        customer: String,
        phone: String,
        rooms: List<RoomData>,
        dateIso: String,
        discount: Double,
        extraCharges: Double,
        paidAmount: Double?,
        invoicePrefix: String,
    ): String {
        val label = withContext(Dispatchers.Default) {
            queries.transactionWithResult {
                queries.insertBill(
                    customer,
                    phone,
                    json.encodeToString(roomsSerializer, rooms),
                    dateIso,
                    discount,
                    extraCharges,
                    paidAmount,
                )
                val newId = queries.lastInsertedId().executeAsOne()
                syncQ.insertSync(newId, newRemoteId(), 1L, 0L, now())
                val label = nextLabel(invoicePrefix)
                syncQ.setLabel(newId, label)
                label
            }
        }
        onChanged?.invoke()
        return label
    }

    suspend fun updateBill(
        id: Long,
        customer: String,
        phone: String,
        rooms: List<RoomData>,
        discount: Double,
        extraCharges: Double,
        paidAmount: Double?,
    ) {
        withContext(Dispatchers.Default) {
            queries.transaction {
                queries.updateBill(
                    customer,
                    phone,
                    json.encodeToString(roomsSerializer, rooms),
                    discount,
                    extraCharges,
                    paidAmount,
                    id,
                )
                syncQ.markDirty(now(), id)
            }
        }
        onChanged?.invoke()
    }

    /** Baqaya wasool hone par "kitna mila" update karta hai. */
    suspend fun updatePaid(id: Long, paidAmount: Double?) {
        withContext(Dispatchers.Default) {
            queries.transaction {
                queries.updatePaid(paidAmount, id)
                syncQ.markDirty(now(), id)
            }
        }
        onChanged?.invoke()
    }

    /** Sirf phone number badalta hai (WhatsApp dialog ke "save this number" ke liye). */
    suspend fun updatePhone(id: Long, phone: String) {
        withContext(Dispatchers.Default) {
            queries.transaction {
                queries.updatePhone(phone, id)
                syncQ.markDirty(now(), id)
            }
        }
        onChanged?.invoke()
    }

    suspend fun deleteBill(id: Long) {
        withContext(Dispatchers.Default) {
            queries.transaction {
                val row = syncQ.selectSyncByBill(id).executeAsOneOrNull()
                // Jo kabhi Firebase par gaya hi nahi, us ka nishan lagane ki zaroorat nahi
                if (row != null && row.pushedAt > 0) syncQ.insertPendingDelete(row.remoteId, now())
                syncQ.deleteSync(id)
                syncQ.deleteLabel(id)
                queries.deleteBill(id)
            }
        }
        onChanged?.invoke()
    }

    suspend fun deleteAll() {
        withContext(Dispatchers.Default) {
            queries.transaction {
                syncQ.queuePendingDeleteAll(now())
                syncQ.deleteAllSync()
                syncQ.deleteAllLabels()
                queries.deleteAll()
            }
        }
        onChanged?.invoke()
    }

    private fun signature(date: String, phone: String, customer: String, subtotal: Double, discount: Double, extra: Double) =
        "$date|$phone|$customer|$subtotal|$discount|$extra"

    /**
     * JSON backup se bills wapas laata hai. Jo bill pehle se maujood ho (same date, phone, naam, total)
     * usay dobara nahi dalta, taake ek hi file do dafa import karne se duplicates na banen.
     * Naye bills online mode me khud Firebase par chale jate hain.
     * Wapas: kitne NAYE bills aaye. null = file kharab / format ghalat.
     */
    suspend fun importFromJson(jsonString: String): Int? {
        val added = withContext(Dispatchers.Default) {
            try {
                val backupList = json.decodeFromString(ListSerializer(BillBackup.serializer()), jsonString)
                val existing = queries.selectAll(this@BillRepository::toBill).executeAsList()
                    .map { signature(it.date, it.phone, it.customer, it.subtotal, it.discount, it.extraCharges) }
                    .toHashSet()
                var count = 0
                queries.transaction {
                    for (b in backupList) {
                        val sig = signature(b.date, b.phone, b.customer, b.rooms.sumOf { it.total }, b.discount, b.extraCharges)
                        if (!existing.add(sig)) continue
                        queries.insertBill(
                            b.customer,
                            b.phone,
                            json.encodeToString(roomsSerializer, b.rooms),
                            b.date,
                            b.discount,
                            b.extraCharges,
                            b.paidAmount,
                        )
                        val newId = queries.lastInsertedId().executeAsOne()
                        syncQ.insertSync(newId, newRemoteId(), 1L, 0L, now())
                        if (b.invoiceLabel.isNotBlank()) syncQ.setLabel(newId, b.invoiceLabel)
                        count++
                    }
                }
                count
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        if (added != null && added > 0) onChanged?.invoke()
        return added
    }

    // =====================================================================
    //  Sync ke liye (sirf SyncManager istemal karta hai)
    // =====================================================================

    suspend fun countBills(): Long = withContext(Dispatchers.Default) { queries.countBills().executeAsOne() }

    /** Shop badalne par: purane shop ka sync hisaab saaf (bills local me rehte hain). */
    suspend fun resetSyncState() {
        withContext(Dispatchers.Default) {
            queries.transaction {
                syncQ.deleteAllSync()
                syncQ.deleteAllPendingDeletes()
            }
        }
    }

    suspend fun pendingCount(): Int = withContext(Dispatchers.Default) {
        (syncQ.countDirty().executeAsOne() + syncQ.countPendingDelete().executeAsOne()).toInt()
    }

    suspend fun dirtyRows(): List<SyncRow> = withContext(Dispatchers.Default) {
        syncQ.selectDirty().executeAsList().map { SyncRow(it.billId, it.remoteId, it.pushedAt, it.updatedAt) }
    }

    suspend fun pendingDeletes(): List<PendingDeleteRow> = withContext(Dispatchers.Default) {
        syncQ.selectPendingDeletes().executeAsList().map { PendingDeleteRow(it.remoteId, it.deletedAt) }
    }

    suspend fun clearPendingDelete(remoteId: String) {
        withContext(Dispatchers.Default) { syncQ.deletePendingDelete(remoteId) }
    }

    /** Push kamyab: dirty hata do (agar beech me bill dobara edit hua to dirty rehta hai). */
    suspend fun markPushed(billId: Long, updatedAtAtPush: Long) {
        withContext(Dispatchers.Default) { syncQ.markClean(now(), billId, updatedAtAtPush) }
    }

    /**
     * Purane bills (jab sync nahi tha) ko Firebase se jodta hai. Agar Firebase par bilkul wahi bill
     * pehle se hai (same date, phone, naam, total) to usi se jod deta hai, warna naya bill banane ke liye dirty.
     */
    suspend fun adoptUnmapped(remote: Map<String, RemoteBill>) {
        withContext(Dispatchers.Default) {
            val unmapped = syncQ.selectUnmappedIds().executeAsList()
            if (unmapped.isEmpty()) return@withContext
            val taken = syncQ.selectAllSync().executeAsList().map { it.remoteId }.toHashSet()
            val bySig = HashMap<String, String>()
            for ((rid, r) in remote) {
                if (r.deleted || rid in taken) continue
                val sig = signature(r.date, r.phone, r.customer, r.rooms.sumOf { it.total }, r.discount, r.extraCharges)
                if (sig !in bySig) bySig[sig] = rid
            }
            queries.transaction {
                for (id in unmapped) {
                    val bill = queries.selectById(id, this@BillRepository::toBill).executeAsOneOrNull() ?: continue
                    val sig = signature(bill.date, bill.phone, bill.customer, bill.subtotal, bill.discount, bill.extraCharges)
                    val match = bySig.remove(sig)
                    if (match != null) {
                        val rm = remote.getValue(match)
                        syncQ.insertSync(id, match, 0L, now(), rm.updatedAt)
                        if (rm.invoiceLabel.isNotBlank() && syncQ.selectLabel(id).executeAsOneOrNull() == null) {
                            syncQ.setLabel(id, rm.invoiceLabel)
                        }
                    } else {
                        syncQ.insertSync(id, newRemoteId(), 1L, 0L, now())
                    }
                }
            }
        }
    }

    /** Merge ka nateeja: changed = local data badla; overwritten = kitne bills me is laptop ki tabdili dusre ki wajah se replace hui. */
    class MergeResult(val changed: Boolean, val overwritten: Int)

    /**
     * Baqaya (paidAmount) milana: null = poora mil chuka. Dono me se zyada wasooli rakhte hain,
     * taake do laptops par ek hi bill ki payment lete waqt koi payment gum na ho.
     */
    private fun mergePaid(a: Double?, b: Double?, grandTotal: Double): Double? {
        if (a == null || b == null) return null
        val m = maxOf(a, b)
        return if (m >= grandTotal - 0.005) null else m
    }

    /**
     * Firebase ka data local me milata hai (jis ka updatedAt naya ho wohi jeetta hai).
     * Naya bill -> local me dalta hai (invoice number wohi rakhne ki koshish), badla hua -> update,
     * delete nishan -> local se hata deta hai.
     * Agar is laptop par bhi us bill ki bhejna-baqi tabdili thi aur Firebase wala naya hai:
     *   - sirf payment ka farq ho -> zyada wasooli rakh kar dobara bhejne ke liye dirty (koi payment gum nahi)
     *   - warna Firebase wala jeetta hai (overwritten me ginti hoti hai).
     */
    suspend fun mergeRemote(remote: Map<String, RemoteBill>): MergeResult = withContext(Dispatchers.Default) {
        var changed = false
        var overwritten = 0
        val rows = syncQ.selectAllSync().executeAsList().associateBy { it.remoteId }
        queries.transaction {
            for ((rid, r) in remote) {
                val row = rows[rid]
                if (row == null) {
                    if (r.deleted) continue
                    // Label wale bills me local id ka koi matlab nahi (nayi id); purane bills me wohi number rakhne ki koshish
                    val wanted = if (r.invoiceLabel.isNotBlank()) null
                    else r.invoiceNo.takeIf { it > 0 && queries.selectById(it).executeAsOneOrNull() == null }
                    val roomsJson = json.encodeToString(roomsSerializer, r.rooms)
                    val newId: Long
                    if (wanted != null) {
                        queries.insertBillWithId(wanted, r.customer, r.phone, roomsJson, r.date, r.discount, r.extraCharges, r.paidAmount)
                        newId = wanted
                    } else {
                        queries.insertBill(r.customer, r.phone, roomsJson, r.date, r.discount, r.extraCharges, r.paidAmount)
                        newId = queries.lastInsertedId().executeAsOne()
                    }
                    syncQ.insertSync(newId, rid, 0L, now(), r.updatedAt)
                    if (r.invoiceLabel.isNotBlank()) syncQ.setLabel(newId, r.invoiceLabel)
                    changed = true
                } else if (r.updatedAt > row.updatedAt) {
                    if (r.deleted) {
                        queries.deleteBill(row.billId)
                        syncQ.deleteSync(row.billId)
                        syncQ.deleteLabel(row.billId)
                        changed = true
                        continue
                    }
                    if (row.dirty == 1L) {
                        // Is laptop par bhi unsent tabdili hai: dono ka hisaab
                        val local = queries.selectById(row.billId, this@BillRepository::toBill).executeAsOneOrNull()
                        val sameExceptPaid = local != null &&
                            local.customer == r.customer && local.phone == r.phone &&
                            local.rooms == r.rooms && local.discount == r.discount &&
                            local.extraCharges == r.extraCharges
                        if (sameExceptPaid && local != null) {
                            val merged = mergePaid(local.paidAmount, r.paidAmount, local.grandTotal)
                            queries.updatePaid(merged, row.billId)
                            // local ko Firebase wale se naya kar do taake agle push me ye jeete
                            syncQ.insertSync(row.billId, rid, 0L, now(), r.updatedAt)
                            changed = true
                            continue
                        }
                        overwritten++
                    }
                    queries.updateBill(
                        r.customer,
                        r.phone,
                        json.encodeToString(roomsSerializer, r.rooms),
                        r.discount,
                        r.extraCharges,
                        r.paidAmount,
                        row.billId,
                    )
                    syncQ.insertSync(row.billId, rid, 0L, now(), r.updatedAt)
                    if (r.invoiceLabel.isNotBlank() && syncQ.selectLabel(row.billId).executeAsOneOrNull() == null) {
                        syncQ.setLabel(row.billId, r.invoiceLabel)
                    }
                    changed = true
                }
            }
        }
        MergeResult(changed, overwritten)
    }

    /** Jab master item ka naam ya unit change ho, to purane bills mein bhi update karo */
    suspend fun updateItemInBills(oldName: String, newName: String, newUnit: String) {
        withContext(Dispatchers.Default) {
            val allBills = queries.selectAll(this@BillRepository::toBill).executeAsList()
            var changedCount = 0

            queries.transaction {
                for (bill in allBills) {
                    var billChanged = false
                    val updatedRooms = bill.rooms.map { room ->
                        val updatedItems = room.items.map { item ->
                            if (item.name.equals(oldName, ignoreCase = true)) {
                                billChanged = true
                                item.copy(name = newName, unit = newUnit) // Naam aur unit update
                            } else item
                        }
                        room.copy(items = updatedItems)
                    }

                    if (billChanged) {
                        queries.updateBill(
                            bill.customer, bill.phone,
                            json.encodeToString(roomsSerializer, updatedRooms),
                            bill.discount, bill.extraCharges, bill.paidAmount, bill.id
                        )
                        syncQ.markDirty(now(), bill.id) // Sync ke liye nishan lagaya
                        changedCount++
                    }
                }
            }
            if (changedCount > 0) onChanged?.invoke()
        }
    }

    /** Check karta hai ke yeh item kisi bhi bill mein use hua hai ya nahi */
    suspend fun isItemUsedInAnyBill(itemName: String): Boolean = withContext(Dispatchers.Default) {
        val allBills = queries.selectAll(this@BillRepository::toBill).executeAsList()
        allBills.any { bill ->
            bill.rooms.any { room ->
                room.items.any { it.name.equals(itemName, ignoreCase = true) }
            }
        }
    }

    /** Item Report ke liye data nikalta hai */
    suspend fun getItemReport(fromDate: String, toDate: String, filterItemName: String?): List<ItemReportRow> = withContext(Dispatchers.Default) {
        val allBills = queries.selectAll(this@BillRepository::toBill).executeAsList()
        val result = mutableListOf<ItemReportRow>()

        // Date filter (YYYY-MM-DD format mein compare hoga)
        val filteredBills = allBills.filter { it.date.take(10) in fromDate..toDate }

        for (bill in filteredBills) {
            for (room in bill.rooms) {
                for (item in room.items) {
                    if (filterItemName == null || item.name.equals(filterItemName, ignoreCase = true)) {
                        if (item.qtyValue > 0.0) {
                            result.add(
                                ItemReportRow(
                                    date = bill.date.take(10),
                                    billId = bill.invoiceText,
                                    customer = bill.customer,
                                    itemName = item.name,
                                    unit = item.unit,
                                    qty = item.qtyValue,
                                    rate = item.rateValue,
                                    total = item.total
                                )
                            )
                        }
                    }
                }
            }
        }
        result.sortedByDescending { it.date }
    }


}



// BillRepository.kt ke bilkul end mein (class ke bahar)
data class ItemReportRow(
    val date: String,
    val billId: String,
    val customer: String,
    val itemName: String,
    val unit: String,
    val qty: Double,
    val rate: Double,
    val total: Double
)