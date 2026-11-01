package com.example.billing.model

import kotlinx.serialization.Serializable

data class CustomerAccount(
    val id: Long,
    val name: String,
    val phone1: String = "",
    val phone2: String = "",
    val address: String = "",
    val accountNo: String = ""
)

@Serializable
data class ItemData(
    val name: String,
    val qty: String = "",
    val discount: String = "",           // <-- NAYA
    val discountIsPercent: Boolean = false, // <-- NAYA
    val rate: String = "",
    val unit: String = "",
    val productId: Long? = null,
    val productName: String = "",
    val categoryName: String = "",
    val subcategoryName: String = "",
    val groupName: String = "",
    val subgroupName: String = "",
    val displayType: String = "default",
    val productDiscountPct: Double = 0.0,

) {
    val qtyValue: Double get() = qty.toDoubleOrNull() ?: 0.0
    val rateValue: Double get() = rate.toDoubleOrNull() ?: 0.0
    val discountValue: Double get() = discount.toDoubleOrNull() ?: 0.0
    val grossTotal: Double get() = qtyValue * rateValue


    // NAYA: Product discount calculate karo
    val productDiscountAmount: Double get() = grossTotal * (productDiscountPct / 100.0)
    val netGross: Double get() = grossTotal - productDiscountAmount

    val total: Double get() {
        return if (discountIsPercent)
            (netGross * (1.0 - discountValue / 100.0)).coerceAtLeast(0.0)
        else
            (netGross - discountValue).coerceAtLeast(0.0)
    }
    val netTotal: Double get() = total
    fun displayName(showProduct: Boolean, showCategory: Boolean, showSubcategory: Boolean,showGroup: Boolean,showSubgroup: Boolean): String {
        return when (displayType) {
            "product" -> productName.ifBlank { name }
            "category" -> categoryName.ifBlank { name }
            "subcategory" -> subcategoryName.ifBlank { name }
            "group" -> groupName.ifBlank { name }
            "subgroup" -> subgroupName.ifBlank { name }
            else -> when {
                showProduct && productName.isNotBlank() -> productName
                showCategory && categoryName.isNotBlank() -> categoryName
                showSubcategory && subcategoryName.isNotBlank() -> subcategoryName
                showGroup && groupName.isNotBlank() -> groupName
                showSubgroup && subgroupName.isNotBlank() -> subgroupName
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
    val address: String = "",
    val phone2: String = "",
    val accountNo: String = "",
    val date: String,
    val rooms: List<RoomData>,
    val discount: Double = 0.0,
    val extraCharges: Double = 0.0,
    val paidAmount: Double? = null,
    val invoiceLabel: String = "",
    val showProduct: Boolean = false,
    val showCategory: Boolean = false,
    val showSubcategory: Boolean = false,
    val showGroup: Boolean=false,
    val showSubgroup: Boolean = false,
    val salesmanId: Long? = null,
    val salesmanName: String = ""
) {
    val invoiceText: String get() = invoiceLabel.ifBlank { "#$id" }
    val subtotal: Double get() = rooms.sumOf { it.total }
    val grandTotal: Double get() = (subtotal - discount + extraCharges).coerceAtLeast(0.0)
    val received: Double get() = paidAmount?.coerceIn(0.0, grandTotal) ?: 0.0
    val isFullyPaid: Boolean get() = paidAmount != null && balance < 0.005
    val isUnknown: Boolean get() = paidAmount == null
    val balance: Double get() = (grandTotal - received).coerceAtLeast(0.0)
    val isPaid: Boolean get() = balance < 0.005
}

// ============ 5-LEVEL HIERARCHY MODELS ============
data class Group(val id: Long, val name: String)
data class Subgroup(val id: Long, val groupId: Long?, val name: String)

data class Category(
    val id: Long,
    val name: String,
    val subgroupId: Long? = null,
)

data class Subcategory(val id: Long, val categoryId: Long?, val name: String)

data class Product(
    val id: Long,
    val name: String,
    val groupId: Long? = null,
    val groupName: String = "",
    val subgroupId: Long? = null,
    val subgroupName: String = "",
    val categoryId: Long? = null,
    val categoryName: String = "",
    val subcategoryId: Long? = null,
    val subcategoryName: String = "",
    val price: Double = 0.0,
    val salePrice: Double = 0.0,
    val discountPct: Double = 0.0,
    val openingStock: Double = 0.0,
    val unit: String = "pcs",
    val liveStock: Double? = null,
) {
    val effectiveSaleRate: Double get() = salePrice * (1.0 - discountPct / 100.0)
}