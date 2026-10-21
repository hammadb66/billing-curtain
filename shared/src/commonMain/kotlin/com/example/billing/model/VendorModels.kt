package com.example.billing.model

import kotlinx.serialization.Serializable

// ============ VENDOR ============
data class Vendor(
    val id: Long,
    val name: String,
    val address: String = "",
    val phone1: String = "",
    val phone2: String = "",
    val accountDescription: String = "",
    val createdAt: String = "",
)

data class VendorWithBalance(
    val vendor: Vendor,
    val balance: Double, // + = hum vendor ko dene hain, - = vendor hum ko dene hain (advance)
)

data class VendorPayment(
    val id: Long,
    val vendorId: Long,
    val amount: Double,
    val discount: Double = 0.0,
    val date: String,
    val note: String = "",
) {
    val totalCovered: Double get() = amount + discount
}

// ============ CATEGORY / SUBCATEGORY ============
data class Category(val id: Long, val name: String)
data class Subcategory(val id: Long, val categoryId: Long, val name: String)

// ============ PRODUCT ============
data class Product(
    val id: Long,
    val name: String,
    val categoryId: Long? = null,
    val categoryName: String = "",
    val subcategoryId: Long? = null,
    val subcategoryName: String = "",
    val price: Double = 0.0,
    val salePrice: Double = 0.0,
    val openingStock: Double = 0.0,
    val unit: String = "pcs",
    val liveStock: Double? = null,
    )

// ============ PURCHASE ============
@Serializable
data class PurchaseItem(
    val productId: Long? = null,
    val name: String = "",
    val qty: String = "",
    val price: String = "",
    val unit: String = "pcs",
) {
    val qtyValue: Double get() = qty.toDoubleOrNull() ?: 0.0
    val priceValue: Double get() = price.toDoubleOrNull() ?: 0.0
    val total: Double get() = qtyValue * priceValue
}

data class Purchase(
    val id: Long,
    val vendorId: Long,
    val vendorName: String,
    val date: String,
    val items: List<PurchaseItem>,
    val total: Double,          // items total
    val discount: Double = 0.0,
    val paid: Double,
    val notes: String = "",
) {
    val payable: Double get() = (total - discount).coerceAtLeast(0.0)
    val balance: Double get() = (payable - paid).coerceAtLeast(0.0)
}