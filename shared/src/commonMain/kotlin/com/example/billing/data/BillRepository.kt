package com.example.billing.data

import com.example.billing.BillingDatabase
import com.example.billing.model.Bill
import com.example.billing.model.RoomData
import com.example.billing.utils.BillBackup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Phone number se mila purana customer: naam + us ka purana baqaya. */
class CustomerInfo(val name: String, val previousBalance: Double)

class BillRepository(database: BillingDatabase) {

    private val queries = database.billQueries
    private val json = Json { ignoreUnknownKeys = true }
    private val roomsSerializer = ListSerializer(RoomData.serializer())

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
    ): Bill = Bill(
        id = id,
        customer = customer,
        phone = phone,
        date = date,
        rooms = runCatching { json.decodeFromString(roomsSerializer, data) }.getOrDefault(emptyList()),
        discount = discount,
        extraCharges = extraCharges,
        paidAmount = paidAmount,
    )

    suspend fun getAllBills(): List<Bill> = withContext(Dispatchers.Default) {
        queries.selectAll(this@BillRepository::toBill).executeAsList()
    }

    /** Naam + purana baqaya. Is phone ka koi bill nahi to null. */
    suspend fun getCustomerInfo(phone: String): CustomerInfo? = withContext(Dispatchers.Default) {
        val list = queries.selectByPhone(phone, this@BillRepository::toBill).executeAsList()
        if (list.isEmpty()) null else CustomerInfo(list.first().customer, list.sumOf { it.balance })
    }

    /** Naye bill ka number (id) wapas deta hai, taake PDF me "Invoice #45" likh sakein. */
    suspend fun insertBill(
        customer: String,
        phone: String,
        rooms: List<RoomData>,
        dateIso: String,
        discount: Double,
        extraCharges: Double,
        paidAmount: Double?,
    ): Long = withContext(Dispatchers.Default) {
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
            queries.lastInsertedId().executeAsOne()
        }
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
            queries.updateBill(
                customer,
                phone,
                json.encodeToString(roomsSerializer, rooms),
                discount,
                extraCharges,
                paidAmount,
                id,
            )
        }
    }

    /** Baqaya wasool hone par "kitna mila" update karta hai. */
    suspend fun updatePaid(id: Long, paidAmount: Double?) {
        withContext(Dispatchers.Default) { queries.updatePaid(paidAmount, id) }
    }

    /** Sirf phone number badalta hai (WhatsApp dialog ke "save this number" ke liye). */
    suspend fun updatePhone(id: Long, phone: String) {
        withContext(Dispatchers.Default) { queries.updatePhone(phone, id) }
    }

    suspend fun deleteBill(id: Long) {
        withContext(Dispatchers.Default) { queries.deleteBill(id) }
    }

    suspend fun deleteAll() {
        withContext(Dispatchers.Default) { queries.deleteAll() }
    }

    private fun signature(date: String, phone: String, customer: String, subtotal: Double, discount: Double, extra: Double) =
        "$date|$phone|$customer|$subtotal|$discount|$extra"

    /**
     * JSON backup se bills wapas laata hai. Jo bill pehle se maujood ho (same date, phone, naam, total)
     * usay dobara nahi dalta, taake ek hi file do dafa import karne se duplicates na banen.
     * Wapas: kitne NAYE bills aaye. null = file kharab / format ghalat.
     */
    suspend fun importFromJson(jsonString: String): Int? = withContext(Dispatchers.Default) {
        try {
            val backupList = json.decodeFromString(ListSerializer(BillBackup.serializer()), jsonString)
            val existing = queries.selectAll(this@BillRepository::toBill).executeAsList()
                .map { signature(it.date, it.phone, it.customer, it.subtotal, it.discount, it.extraCharges) }
                .toHashSet()
            var added = 0
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
                    added++
                }
            }
            added
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}