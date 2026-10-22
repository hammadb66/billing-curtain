package com.example.billing.data

import com.example.billing.sync.RemoteBill
import com.example.billing.BillingDatabase
import com.example.billing.auth.randomSaltHex
import com.example.billing.model.Bill
import com.example.billing.model.RoomData
import com.example.billing.utils.BillBackup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock

class CustomerInfo(val name: String, val previousBalance: Double)
class SyncRow(val billId: Long, val remoteId: String, val pushedAt: Long, val updatedAt: Long)
class PendingDeleteRow(val remoteId: String, val deletedAt: Long)

class BillRepository(database: BillingDatabase) {

    private val queries = database.billQueries
    private val syncQ = database.syncQueries
    private val json = Json { ignoreUnknownKeys = true }
    private val roomsSerializer = ListSerializer(RoomData.serializer())

    private companion object { const val INVOICE_DIGITS = 2 }

    var onChanged: (() -> Unit)? = null
    var clockSkew: Long = 0L

    private fun now(): Long = Clock.System.now().toEpochMilliseconds() + clockSkew
    private fun newRemoteId(): String = "b" + randomSaltHex().take(24)

    private fun toBill(
        id: Long, customer: String, phone: String, address: String, phone2: String, accountNo: String,
        data: String, date: String, discount: Double, extraCharges: Double, paidAmount: Double?,
        showProduct: Long, showCategory: Long, showSubcategory: Long, salesmanId: Long?, label: String?
    ): Bill = Bill(
        id = id, customer = customer, phone = phone, address = address, phone2 = phone2, accountNo = accountNo,
        date = date, rooms = runCatching { json.decodeFromString(roomsSerializer, data) }.getOrDefault(emptyList()),
        discount = discount, extraCharges = extraCharges, paidAmount = paidAmount,
        invoiceLabel = label.orEmpty(), salesmanId = salesmanId,
        showProduct = showProduct != 0L, showCategory = showCategory != 0L, showSubcategory = showSubcategory != 0L,
    )

    private fun cleanPrefix(raw: String): String = raw.filter { it.isLetterOrDigit() }.take(12).ifBlank { "inv" }

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

    suspend fun getCustomerInfo(phone: String): CustomerInfo? = withContext(Dispatchers.Default) {
        val list = queries.selectByPhone(phone, this@BillRepository::toBill).executeAsList()
        if (list.isEmpty()) null else CustomerInfo(list.first().customer, list.sumOf { it.balance })
    }

    suspend fun insertBill(
        customer: String, phone: String, address: String, phone2: String, accountNo: String,
        rooms: List<RoomData>, dateIso: String, discount: Double, extraCharges: Double, paidAmount: Double?, salesmanId: Long?,
        invoicePrefix: String, showProduct: Boolean = false, showCategory: Boolean = false, showSubcategory: Boolean = false,
    ): String {
        val label = withContext(Dispatchers.Default) {
            queries.transactionWithResult {
                queries.insertBill(
                    customer, phone, address, phone2, accountNo, json.encodeToString(roomsSerializer, rooms), dateIso,
                    discount, extraCharges, paidAmount,
                    if (showProduct) 1L else 0L, if (showCategory) 1L else 0L, if (showSubcategory) 1L else 0L, salesmanId
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
        id: Long, customer: String, phone: String, address: String, phone2: String, accountNo: String,
        rooms: List<RoomData>, discount: Double, extraCharges: Double, paidAmount: Double?, salesmanId: Long?,
        showProduct: Boolean = false, showCategory: Boolean = false, showSubcategory: Boolean = false,
    ) {
        withContext(Dispatchers.Default) {
            queries.transaction {
                queries.updateBill(
                    customer, phone, address, phone2, accountNo, json.encodeToString(roomsSerializer, rooms),
                    discount, extraCharges, paidAmount,
                    if (showProduct) 1L else 0L, if (showCategory) 1L else 0L, if (showSubcategory) 1L else 0L,
                    salesmanId, id
                )
                syncQ.markDirty(now(), id)
            }
        }
        onChanged?.invoke()
    }

    suspend fun updatePaid(id: Long, paidAmount: Double?) {
        withContext(Dispatchers.Default) {
            queries.transaction { queries.updatePaid(paidAmount, id); syncQ.markDirty(now(), id) }
        }
        onChanged?.invoke()
    }

    suspend fun updatePhone(id: Long, phone: String) {
        withContext(Dispatchers.Default) {
            queries.transaction { queries.updatePhone(phone, id); syncQ.markDirty(now(), id) }
        }
        onChanged?.invoke()
    }

    suspend fun deleteBill(id: Long) {
        withContext(Dispatchers.Default) {
            queries.transaction {
                val row = syncQ.selectSyncByBill(id).executeAsOneOrNull()
                if (row != null && row.pushedAt > 0) syncQ.insertPendingDelete(row.remoteId, now())
                syncQ.deleteSync(id); syncQ.deleteLabel(id); queries.deleteBill(id)
            }
        }
        onChanged?.invoke()
    }

    suspend fun deleteAll() {
        withContext(Dispatchers.Default) {
            queries.transaction {
                syncQ.queuePendingDeleteAll(now()); syncQ.deleteAllSync(); syncQ.deleteAllLabels(); queries.deleteAll()
            }
        }
        onChanged?.invoke()
    }

    private fun signature(date: String, phone: String, customer: String, subtotal: Double, discount: Double, extra: Double) =
        "$date|$phone|$customer|$subtotal|$discount|$extra"

    suspend fun importFromJson(jsonString: String): Int? {
        val added = withContext(Dispatchers.Default) {
            try {
                val backupList = json.decodeFromString(ListSerializer(BillBackup.serializer()), jsonString)
                val existing = queries.selectAll(this@BillRepository::toBill).executeAsList()
                    .map { signature(it.date, it.phone, it.customer, it.subtotal, it.discount, it.extraCharges) }.toHashSet()
                var count = 0
                queries.transaction {
                    for (b in backupList) {
                        val sig = signature(b.date, b.phone, b.customer, b.rooms.sumOf { it.total }, b.discount, b.extraCharges)
                        if (!existing.add(sig)) continue
                        queries.insertBill(
                            b.customer, b.phone, b.address, b.phone2, b.accountNo, json.encodeToString(roomsSerializer, b.rooms),
                            b.date, b.discount, b.extraCharges, b.paidAmount,
                            if (b.showProduct) 1L else 0L, if (b.showCategory) 1L else 0L, if (b.showSubcategory) 1L else 0L, b.salesmanId
                        )
                        val newId = queries.lastInsertedId().executeAsOne()
                        syncQ.insertSync(newId, newRemoteId(), 1L, 0L, now())
                        if (b.invoiceLabel.isNotBlank()) syncQ.setLabel(newId, b.invoiceLabel)
                        count++
                    }
                }
                count
            } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
        }
        if (added != null && added > 0) onChanged?.invoke()
        return added
    }

    suspend fun countBills(): Long = withContext(Dispatchers.Default) { queries.countBills().executeAsOne() }

    suspend fun resetSyncState() {
        withContext(Dispatchers.Default) { queries.transaction { syncQ.deleteAllSync(); syncQ.deleteAllPendingDeletes() } }
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

    suspend fun markPushed(billId: Long, updatedAtAtPush: Long) {
        withContext(Dispatchers.Default) { syncQ.markClean(now(), billId, updatedAtAtPush) }
    }

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
                        if (rm.invoiceLabel.isNotBlank() && syncQ.selectLabel(id).executeAsOneOrNull() == null) syncQ.setLabel(id, rm.invoiceLabel)
                    } else syncQ.insertSync(id, newRemoteId(), 1L, 0L, now())
                }
            }
        }
    }

    class MergeResult(val changed: Boolean, val overwritten: Int)

    private fun mergePaid(a: Double?, b: Double?, grandTotal: Double): Double? {
        if (a == null || b == null) return null
        val m = maxOf(a, b)
        return if (m >= grandTotal - 0.005) null else m
    }

    suspend fun mergeRemote(remote: Map<String, RemoteBill>): MergeResult = withContext(Dispatchers.Default) {
        var changed = false; var overwritten = 0
        val rows = syncQ.selectAllSync().executeAsList().associateBy { it.remoteId }
        queries.transaction {
            for ((rid, r) in remote) {
                val row = rows[rid]
                val sp = if (r.showProduct) 1L else 0L; val sc = if (r.showCategory) 1L else 0L; val ss = if (r.showSubcategory) 1L else 0L
                if (row == null) {
                    if (r.deleted) continue
                    val wanted = if (r.invoiceLabel.isNotBlank()) null else r.invoiceNo.takeIf { it > 0 && queries.selectById(it).executeAsOneOrNull() == null }
                    val roomsJson = json.encodeToString(roomsSerializer, r.rooms)
                    val newId: Long
                    if (wanted != null) {
                        queries.insertBillWithId(wanted, r.customer, r.phone, r.address, r.phone2, r.accountNo, roomsJson, r.date, r.discount, r.extraCharges, r.paidAmount, sp, sc, ss, r.salesmanId)
                        newId = wanted
                    } else {
                        queries.insertBill(r.customer, r.phone, r.address, r.phone2, r.accountNo, roomsJson, r.date, r.discount, r.extraCharges, r.paidAmount, sp, sc, ss, r.salesmanId)
                        newId = queries.lastInsertedId().executeAsOne()
                    }
                    syncQ.insertSync(newId, rid, 0L, now(), r.updatedAt)
                    if (r.invoiceLabel.isNotBlank()) syncQ.setLabel(newId, r.invoiceLabel)
                    changed = true
                } else if (r.updatedAt > row.updatedAt) {
                    if (r.deleted) {
                        queries.deleteBill(row.billId); syncQ.deleteSync(row.billId); syncQ.deleteLabel(row.billId); changed = true; continue
                    }
                    if (row.dirty == 1L) {
                        val local = queries.selectById(row.billId, this@BillRepository::toBill).executeAsOneOrNull()
                        val sameExceptPaid = local != null && local.customer == r.customer && local.phone == r.phone && local.rooms == r.rooms && local.discount == r.discount && local.extraCharges == r.extraCharges
                        if (sameExceptPaid && local != null) {
                            val merged = mergePaid(local.paidAmount, r.paidAmount, local.grandTotal)
                            queries.updatePaid(merged, row.billId)
                            syncQ.insertSync(row.billId, rid, 0L, now(), r.updatedAt); changed = true; continue
                        }
                        overwritten++
                    }
                    queries.updateBill(r.customer, r.phone, r.address, r.phone2, r.accountNo, json.encodeToString(roomsSerializer, r.rooms), r.discount, r.extraCharges, r.paidAmount, sp, sc, ss, r.salesmanId, row.billId)
                    syncQ.insertSync(row.billId, rid, 0L, now(), r.updatedAt)
                    if (r.invoiceLabel.isNotBlank() && syncQ.selectLabel(row.billId).executeAsOneOrNull() == null) syncQ.setLabel(row.billId, r.invoiceLabel)
                    changed = true
                }
            }
        }
        MergeResult(changed, overwritten)
    }

    suspend fun updateItemInBills(oldName: String, newName: String, newUnit: String) {
        withContext(Dispatchers.Default) {
            val allBills = queries.selectAll(this@BillRepository::toBill).executeAsList()
            var changedCount = 0
            queries.transaction {
                for (bill in allBills) {
                    var billChanged = false
                    val updatedRooms = bill.rooms.map { room ->
                        val updatedItems = room.items.map { item ->
                            if (item.name.equals(oldName, ignoreCase = true)) { billChanged = true; item.copy(name = newName, unit = newUnit) } else item
                        }
                        room.copy(items = updatedItems)
                    }
                    if (billChanged) {
                        queries.updateBill(bill.customer, bill.phone, bill.address, bill.phone2, bill.accountNo, json.encodeToString(roomsSerializer, updatedRooms), bill.discount, bill.extraCharges, bill.paidAmount, if (bill.showProduct) 1L else 0L, if (bill.showCategory) 1L else 0L, if (bill.showSubcategory) 1L else 0L, bill.salesmanId, bill.id)
                        syncQ.markDirty(now(), bill.id); changedCount++
                    }
                }
            }
            if (changedCount > 0) onChanged?.invoke()
        }
    }

    suspend fun isItemUsedInAnyBill(itemName: String): Boolean = withContext(Dispatchers.Default) {
        val allBills = queries.selectAll(this@BillRepository::toBill).executeAsList()
        allBills.any { bill -> bill.rooms.any { room -> room.items.any { it.name.equals(itemName, ignoreCase = true) } } }
    }

    suspend fun getItemReport(fromDate: String, toDate: String, filterItemName: String?): List<ItemReportRow> = withContext(Dispatchers.Default) {
        val allBills = queries.selectAll(this@BillRepository::toBill).executeAsList()
        val result = mutableListOf<ItemReportRow>()
        val filteredBills = allBills.filter { it.date.take(10) in fromDate..toDate }
        for (bill in filteredBills) {
            for (room in bill.rooms) {
                for (item in room.items) {
                    if (filterItemName == null || item.name.equals(filterItemName, ignoreCase = true)) {
                        if (item.qtyValue > 0.0) {
                            result.add(ItemReportRow(bill.date.take(10), bill.invoiceText, bill.customer, item.name, item.unit, item.qtyValue, item.rateValue, item.total))
                        }
                    }
                }
            }
        }
        result.sortedByDescending { it.date }
    }
}

data class ItemReportRow(val date: String, val billId: String, val customer: String, val itemName: String, val unit: String, val qty: Double, val rate: Double, val total: Double)