package com.example.billing.utils

import com.example.billing.model.PurchaseItem
import kotlinx.serialization.Serializable

@Serializable
data class FullBackup(
    val version: Int = 1,
    val bills: List<BillBackup> = emptyList(),
    val vendors: List<VendorBackup> = emptyList(),
    val vendorPayments: List<VendorPaymentBackup> = emptyList(),
    val categories: List<CategoryBackup> = emptyList(),
    val subcategories: List<SubcategoryBackup> = emptyList(),
    val products: List<ProductBackup> = emptyList(),
    val purchases: List<PurchaseBackup> = emptyList()
)

@Serializable
data class VendorBackup(
    val id: Long, val name: String, val address: String, val phone1: String, val phone2: String, val accountDescription: String, val createdAt: String
)

@Serializable
data class VendorPaymentBackup(
    val id: Long, val vendorId: Long, val amount: Double, val discount: Double, val date: String, val note: String
)

@Serializable
data class CategoryBackup(val id: Long, val name: String)

@Serializable
data class SubcategoryBackup(val id: Long, val categoryId: Long, val name: String)

@Serializable
data class ProductBackup(
    val id: Long, val name: String, val categoryId: Long?, val subcategoryId: Long?,
    val price: Double, val salePrice: Double, val openingStock: Double, val unit: String
)

@Serializable
data class PurchaseBackup(
    val id: Long, val vendorId: Long, val date: String, val items: List<PurchaseItem>,
    val total: Double, val discount: Double, val paid: Double, val notes: String
)