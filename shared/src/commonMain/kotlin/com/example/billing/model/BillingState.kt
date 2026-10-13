package com.example.billing.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.billing.data.masterItems

private var idCounter = 0L
private fun nextId(): Long = idCounter++

class ItemState(val name: String) {
    var qty by mutableStateOf("")
    var rate by mutableStateOf("")
    val total: Double get() = (qty.toDoubleOrNull() ?: 0.0) * (rate.toDoubleOrNull() ?: 0.0)
}

class RoomState {
    val id: Long = nextId()
    var name by mutableStateOf("")
    var expanded by mutableStateOf(true)
    val items: List<ItemState> = masterItems.map { ItemState(it) }
    val total: Double get() = items.sumOf { it.total }

    fun toRoomData() = RoomData(
        roomName = name.trim(),
        items = items.filter { it.qty.isNotBlank() || it.rate.isNotBlank() }.map { ItemData(it.name, it.qty, it.rate) }
    )
}

class BillingFormState {
    var customer by mutableStateOf("")
    var phone by mutableStateOf("")
    var discount by mutableStateOf("")       // Naya field: Discount
    var extraCharges by mutableStateOf("") // Naya field: Extra Charges
    val rooms = mutableStateListOf(RoomState())

    val grandTotal: Double get() {
        val raw = rooms.sumOf { it.total }
        val disc = discount.toDoubleOrNull() ?: 0.0
        val extra = extraCharges.toDoubleOrNull() ?: 0.0
        return (raw - disc + extra).coerceAtLeast(0.0)
    }

    fun addRoom() { rooms.add(RoomState()) }
    fun removeRoom(room: RoomState) {
        if (rooms.isNotEmpty() && rooms.first() !== room) rooms.remove(room)
    }
    fun reset() {
        customer = ""
        phone = ""
        discount = ""
        extraCharges = ""
        rooms.clear()
        rooms.add(RoomState())
    }
    fun toRoomDataList(): List<RoomData> = rooms.map { it.toRoomData() }.filter { it.items.isNotEmpty() }
}