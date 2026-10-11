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
            )
        }
    }

    suspend fun insertBill(
        customer: String,
        phone: String,
        rooms: List<RoomData>,
        dateIso: String,
    ) {
        withContext(Dispatchers.Default) {
            queries.insertBill(customer, phone, json.encodeToString(roomsSerializer, rooms), dateIso)
        }
    }

    suspend fun updateBill(
        id: Long,
        customer: String,
        phone: String,
        rooms: List<RoomData>,
    ) {
        withContext(Dispatchers.Default) {
            queries.updateBill(customer, phone, json.encodeToString(roomsSerializer, rooms), id)
        }
    }

    suspend fun deleteBill(id: Long) {
        withContext(Dispatchers.Default) { queries.deleteBill(id) }
    }

    suspend fun deleteAll() {
        withContext(Dispatchers.Default) { queries.deleteAll() }
    }
}