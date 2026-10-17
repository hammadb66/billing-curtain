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

/** Ek room (Bedroom, Lounge...) aur us ke items. */
@Serializable
data class RoomData(
    val roomName: String = "",
    val items: List<ItemData> = emptyList(),
) {
    val total: Double get() = items.sumOf { it.total }

    val validItems: List<ItemData>
        get() = items.filter { it.qtyValue > 0 && it.rateValue > 0 }
}

/** Database se aaya hua mukammal bill. */
data class Bill(
    val id: Long,
    val customer: String,
    val phone: String,
    val date: String,
    val rooms: List<RoomData>,
    val discount: Double = 0.0,
    val extraCharges: Double = 0.0,
    /** Kitna paisa mila. null = poora mil chuka. */
    val paidAmount: Double? = null,
    /** Invoice number jo customer ko diya gaya, maslan "hammad-05". Purane bills me khali (tab "#id" dikhta hai). */
    val invoiceLabel: String = "",
) {
    /** Screen / PDF / CSV me jo invoice number dikhana hai. */
    val invoiceText: String get() = invoiceLabel.ifBlank { "#$id" }

    /** Discount / extra se PEHLE ka total. */
    val subtotal: Double get() = rooms.sumOf { it.total }

    val grandTotal: Double get() = (subtotal - discount + extraCharges).coerceAtLeast(0.0)

    /** Asal me kitna mila (kabhi grandTotal se zyada nahi). */
    val received: Double get() = (paidAmount ?: grandTotal).coerceIn(0.0, grandTotal)

    /** Baqaya (udhaar). 0 matlab bill paid hai. */
    val balance: Double get() = (grandTotal - received).coerceAtLeast(0.0)

    val isPaid: Boolean get() = balance < 0.005
}