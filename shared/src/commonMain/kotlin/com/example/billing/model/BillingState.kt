package com.example.billing.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.billing.utils.resolveDiscount

private var idCounter = 0L
private fun nextId(): Long = idCounter++

class ItemState(val name: String) {
    var qty by mutableStateOf("")
    var rate by mutableStateOf("")
    val total: Double get() = (qty.toDoubleOrNull() ?: 0.0) * (rate.toDoubleOrNull() ?: 0.0)

    /** Kuch likha hai (qty ya rate) lekin total 0 hai -> adhura item. */
    val isIncomplete: Boolean get() = (qty.isNotBlank() || rate.isNotBlank()) && total <= 0.0
}

class RoomState {
    val id: Long = nextId()
    var name by mutableStateOf("")
    var expanded by mutableStateOf(true)
    val items = mutableStateListOf<ItemState>()
    val total: Double get() = items.sumOf { it.total }

    fun addItem(name: String) {
        items.add(ItemState(name))
    }

    /** Wohi item us ke neeche dobara (alag rate ke liye). */
    fun duplicateItem(item: ItemState) {
        val i = items.indexOf(item)
        if (i >= 0) items.add(i + 1, ItemState(item.name)) else items.add(ItemState(item.name))
    }

    fun removeItem(item: ItemState) {
        items.remove(item)
    }

    fun toRoomData() = RoomData(
        roomName = name.trim(),
        items = items.filter { it.qty.isNotBlank() || it.rate.isNotBlank() }.map { ItemData(it.name, it.qty, it.rate) }
    )
}

class BillingFormState {
    var customer by mutableStateOf("")
    var phone by mutableStateOf("")
    var discount by mutableStateOf("")
    var discountIsPercent by mutableStateOf(false)   // false = Rs, true = %
    var extraCharges by mutableStateOf("")
    var received by mutableStateOf("")               // khali = 0 mila
    val rooms = mutableStateListOf(RoomState())

    val subtotal: Double get() = rooms.sumOf { it.total }

    /** Discount hamesha Rs me (agar % likha ho to subtotal se Rs nikal leta hai). */
    val discountValue: Double get() = resolveDiscount(subtotal, discount, discountIsPercent)
    val extraValue: Double get() = extraCharges.toDoubleOrNull() ?: 0.0
    val grandTotal: Double get() = (subtotal - discountValue + extraValue).coerceAtLeast(0.0)

    /** Blank = 0 mila. Koi amount likhi ho to wohi. */
    val receivedValue: Double get() = received.toDoubleOrNull() ?: 0.0
    val balance: Double get() = (grandTotal - receivedValue).coerceAtLeast(0.0)

    val hasAnyItem: Boolean get() = rooms.any { r -> r.items.any { it.total > 0.0 } }
    val hasIncompleteItem: Boolean get() = rooms.any { r -> r.items.any { it.isIncomplete } }

    fun addRoom() { rooms.add(RoomState()) }
    fun removeRoom(room: RoomState) {
        if (rooms.isNotEmpty() && rooms.first() !== room) rooms.remove(room)
    }
    fun reset() {
        customer = ""
        phone = ""
        discount = ""
        discountIsPercent = false
        extraCharges = ""
        received = ""
        rooms.clear()
        rooms.add(RoomState())
    }
    fun toRoomDataList(): List<RoomData> = rooms.map { it.toRoomData() }.filter { it.items.isNotEmpty() }
}