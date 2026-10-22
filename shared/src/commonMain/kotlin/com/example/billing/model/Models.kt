package com.example.billing.model

import kotlinx.serialization.Serializable
data class Salesman(val id: Long, val name: String)

@Serializable
data class ItemData(
    val name: String,
    val qty: String = "",
    val rate: String = "",
    val unit: String = "",
    val productId: Long? = null,
    val productName: String = "",
    val categoryName: String = "",
    val subcategoryName: String = "",
    val displayType: String = "default",
) {
    val qtyValue: Double get() = qty.toDoubleOrNull() ?: 0.0
    val rateValue: Double get() = rate.toDoubleOrNull() ?: 0.0
    val total: Double get() = qtyValue * rateValue

    fun displayName(showProduct: Boolean, showCategory: Boolean, showSubcategory: Boolean): String {
        return when (displayType) {
            "product" -> productName.ifBlank { name }
            "category" -> categoryName.ifBlank { name }
            "subcategory" -> subcategoryName.ifBlank { name }
            else -> when {
                showProduct && productName.isNotBlank() -> productName
                showCategory && categoryName.isNotBlank() -> categoryName
                showSubcategory && subcategoryName.isNotBlank() -> subcategoryName
                else -> name
            }
        }
    }
}

@Serializable
data class MasterItem(val name: String, val unit: String = "pcs")

@Serializable
data class RoomData(
    val roomName: String = "",
    val items: List<ItemData> = emptyList(),
) {
    val total: Double get() = items.sumOf { it.total }
    val validItems: List<ItemData> get() = items.filter { it.qtyValue > 0 && it.rateValue > 0 }
}

data class Bill(
    val id: Long,
    val customer: String,
    val phone: String,
    val address: String = "",      // NAYA
    val phone2: String = "",       // NAYA
    val accountNo: String = "",    // NAYA
    val date: String,
    val rooms: List<RoomData>,
    val discount: Double = 0.0,
    val extraCharges: Double = 0.0,
    val paidAmount: Double? = null,
    val invoiceLabel: String = "",
    val showProduct: Boolean = false,
    val showCategory: Boolean = false,
    val showSubcategory: Boolean = false,
    val salesmanId: Long? = null,
    val salesmanName: String = ""
) {
    val invoiceText: String get() = invoiceLabel.ifBlank { "#$id" }
    val subtotal: Double get() = rooms.sumOf { it.total }
    val grandTotal: Double get() = (subtotal - discount + extraCharges).coerceAtLeast(0.0)
    val received: Double get() = (paidAmount ?: grandTotal).coerceIn(0.0, grandTotal)
    val balance: Double get() = (grandTotal - received).coerceAtLeast(0.0)
    val isPaid: Boolean get() = balance < 0.005
}