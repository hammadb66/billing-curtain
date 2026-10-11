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

    val total: Double
        get() = (qty.toDoubleOrNull() ?: 0.0) * (rate.toDoubleOrNull() ?: 0.0)
}

class RoomState {
    val id: Long = nextId()          // LazyColumn ko "key" chahiye hoti hai
    var name by mutableStateOf("")
    var expanded by mutableStateOf(true)

    // Har naye room ko master list se bilkul taaza items milte hain
    val items: List<ItemState> = masterItems.map { ItemState(it) }

    val total: Double get() = items.sumOf { it.total }

    /** Sirf wo items jin me qty ya rate bhari ho (database/PDF ke liye) */
    fun toRoomData() = RoomData(
        roomName = name.trim(),
        items = items
            .filter { it.qty.isNotBlank() || it.rate.isNotBlank() }
            .map { ItemData(it.name, it.qty, it.rate) },
    )
}

class BillingFormState {
    var customer by mutableStateOf("")
    var phone by mutableStateOf("")
    val rooms = mutableStateListOf(RoomState())

    // Ye property khud-ba-khud update hoti hai: koi calculateGrandTotal() nahi chahiye!
    val grandTotal: Double get() = rooms.sumOf { it.total }

    fun addRoom() { rooms.add(RoomState()) }

    fun removeRoom(room: RoomState) {
        // Pehla room delete nahi hota (Flutter jaisa)
        if (rooms.isNotEmpty() && rooms.first() !== room) rooms.remove(room)
    }

    fun reset() {
        customer = ""
        phone = ""
        rooms.clear()
        rooms.add(RoomState())
    }

    fun toRoomDataList(): List<RoomData> =
        rooms.map { it.toRoomData() }.filter { it.items.isNotEmpty() }
}
