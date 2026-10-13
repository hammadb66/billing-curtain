package com.example.billing.data

import com.example.billing.BillingDatabase
import com.example.billing.model.Bill
import com.example.billing.model.RoomData
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

    /** Har item ka sab se naya rate: { "Fan" -> "1500" }. getAllBills newest-first deta hai, isliye pehla mila hua rate hi naya hai. */
    suspend fun getLastRates(): Map<String, String> {
        val rates = LinkedHashMap<String, String>()
        for (bill in getAllBills()) {
            for (room in bill.rooms) {
                for (item in room.items) {
                    if (item.rateValue > 0.0 && item.name !in rates) rates[item.name] = item.rate
                }
            }
        }
        return rates
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
        // transaction: insert aur id uthana "ek saath" hon, beech me koi aur bill na ghus jaye
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

    suspend fun deleteBill(id: Long) {
        withContext(Dispatchers.Default) { queries.deleteBill(id) }
    }

    suspend fun deleteAll() {
        withContext(Dispatchers.Default) { queries.deleteAll() }
    }
}