package com.example.billing.utils

import com.example.billing.model.PurchaseItem
import com.example.billing.model.RoomData
import kotlinx.serialization.Serializable

@Serializable
data class BillBackup(
    val id: Long = 0L,
    val customer: String = "", val phone: String = "", val address: String = "",
    val phone2: String = "", val accountNo: String = "", val date: String = "",
    val rooms: List<RoomData> = emptyList(), val discount: Double = 0.0,
    val extraCharges: Double = 0.0, val paidAmount: Double? = null,
    val invoiceLabel: String = "", val showProduct: Boolean = false,
    val showCategory: Boolean = false, val showSubcategory: Boolean = false,val showGroup: Boolean = false,    // <-- NAYA
    val showSubgroup: Boolean = false, // <-- NAYA
    val salesmanId: Long? = null,
)

@Serializable
data class VendorBackup(
    val id: Long = 0L, val name: String = "", val address: String = "",
    val phone1: String = "", val phone2: String = "", val accountDescription: String = "",
    val createdAt: String = "",
)

@Serializable
data class VendorPaymentBackup(
    val id: Long = 0L, val vendorId: Long = 0L, val amount: Double = 0.0,
    val discount: Double = 0.0, val date: String = "", val note: String = "",
)

@Serializable
data class GroupBackup(val id: Long = 0L, val name: String = "")

@Serializable
data class SubgroupBackup(val id: Long = 0L, val groupId: Long? = null, val name: String = "")

@Serializable
data class CategoryBackup(val id: Long = 0L, val name: String = "", val subgroupId: Long? = null)

@Serializable
data class SubcategoryBackup(val id: Long = 0L, val categoryId: Long? = null, val name: String = "")

@Serializable
data class ProductBackup(
    val id: Long = 0L, val name: String = "",
    val groupId: Long? = null, val subgroupId: Long? = null,
    val categoryId: Long? = null, val subcategoryId: Long? = null,
    val price: Double = 0.0, val salePrice: Double = 0.0,
    val discountPct: Double = 0.0, val openingStock: Double = 0.0, val unit: String = "pcs",
)

@Serializable
data class PurchaseBackup(
    val id: Long = 0L, val vendorId: Long = 0L, val date: String = "",
    val items: List<PurchaseItem> = emptyList(), val total: Double = 0.0,
    val discount: Double = 0.0, val paid: Double = 0.0, val notes: String = "",
)

@Serializable
data class CustomerAccountBackup(
    val id: Long = 0L, val name: String = "", val phone1: String = "",
    val phone2: String = "", val address: String = "", val accountNo: String = "",
)

@Serializable
data class CustomerPaymentBackup(
    val id: Long = 0L, val customerId: Long = 0L, val amount: Double = 0.0,
    val discount: Double = 0.0, val date: String = "", val note: String = "",
)

@Serializable
data class FullBackup(
    val bills: List<BillBackup> = emptyList(),
    val vendors: List<VendorBackup> = emptyList(),
    val vendorPayments: List<VendorPaymentBackup> = emptyList(),
    val groups: List<GroupBackup> = emptyList(),
    val subgroups: List<SubgroupBackup> = emptyList(),
    val categories: List<CategoryBackup> = emptyList(),
    val subcategories: List<SubcategoryBackup> = emptyList(),
    val products: List<ProductBackup> = emptyList(),
    val purchases: List<PurchaseBackup> = emptyList(),
    val customerAccounts: List<CustomerAccountBackup> = emptyList(),
    val customerPayments: List<CustomerPaymentBackup> = emptyList(),
)