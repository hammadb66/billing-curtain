package com.example.billing.model

import kotlinx.serialization.Serializable

@Serializable
data class ItemData(
    val name: String,
    val qty: String = "",
    val rate: String = "",
) {
    val qtyValue: Double get() = qty.toDoubleOrNull() ?: 0.0
    val rateValue: Double get() = rate.toDoubleOrNull() ?: 0.0
    val total: Double get() = qtyValue * rateValue
}

/** Ek room (Bedroom, Lounge...) aur us ke items. (Flutter ka Room class) */
@Serializable
data class RoomData(
    val roomName: String = "",
    val items: List<ItemData> = emptyList(),
) {
    val total: Double get() = items.sumOf { it.total }

    val validItems: List<ItemData>          // <-- NAYA
        get() = items.filter { it.qtyValue > 0 && it.rateValue > 0 }
}

/** Database se aaya hua mukammal bill. */
data class Bill(
    val id: Long,
    val customer: String,
    val phone: String,
    val date: String,
    val rooms: List<RoomData>,
) {
    val grandTotal: Double get() = rooms.sumOf { it.total }
}
