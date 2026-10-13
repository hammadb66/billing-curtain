package com.example.billing.data

import com.example.billing.model.Bill
import com.example.billing.model.RoomData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import com.example.billing.BillingDatabase

class BillRepository(database: BillingDatabase) {

    private val queries = database.billQueries
    private val json = Json { ignoreUnknownKeys = true }
    private val roomsSerializer = ListSerializer(RoomData.serializer())

    suspend fun getAllBills(): List<Bill> = withContext(Dispatchers.Default) {
        queries.selectAll().executeAsList().map { row ->
            Bill(
                id = row.id,
                customer = row.customer,
                phone = row.phone,
                date = row.date,
                rooms = runCatching { json.decodeFromString(roomsSerializer, row.data_) }
                    .getOrDefault(emptyList()),
                discount = row.discount,       // <-- Yeh yahan add hoga[cite: 32, 34]
                extraCharges = row.extraCharges // <-- Yeh yahan add hoga[cite: 32, 34]
            )
        }
    }

    suspend fun insertBill(
        customer: String,
        phone: String,
        rooms: List<RoomData>,
        dateIso: String,
        discount: Double,       // <-- Naya parameter
        extraCharges: Double,   // <-- Naya parameter
    ) {
        withContext(Dispatchers.Default) {
            queries.insertBill(
                customer,
                phone,
                json.encodeToString(roomsSerializer, rooms),
                dateIso,
                discount,
                extraCharges
            )
        }
    }

    suspend fun updateBill(
        id: Long,
        customer: String,
        phone: String,
        rooms: List<RoomData>,
        discount: Double,       // <-- Naya parameter[cite: 29]
        extraCharges: Double,   // <-- Naya parameter[cite: 29]
    ) {
        withContext(Dispatchers.Default) {
            queries.updateBill(
                customer,
                phone,
                json.encodeToString(roomsSerializer, rooms),
                discount,
                extraCharges,
                id
            )
        }
    }

    suspend fun deleteBill(id: Long) {
        withContext(Dispatchers.Default) { queries.deleteBill(id) }
    }

    suspend fun deleteAll() {
        withContext(Dispatchers.Default) { queries.deleteAll() }
    }
}