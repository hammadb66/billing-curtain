package com.example.billing.presentation

import kotlinx.serialization.Serializable

@Serializable
sealed interface BillingRoute {
    @Serializable data object Billing : BillingRoute
    @Serializable data object History : BillingRoute
    @Serializable data object ManageItems : BillingRoute
    @Serializable data object ItemReport : BillingRoute
    @Serializable data class EditBill(val billId: Long) : BillingRoute
    // NEW
    @Serializable data object Vendors : BillingRoute
    @Serializable data class VendorDetail(val vendorId: Long) : BillingRoute
    @Serializable data object Products : BillingRoute
    @Serializable data object Categories : BillingRoute
    @Serializable data object Purchases : BillingRoute
    @Serializable data class PurchaseEdit(val purchaseId: Long) : BillingRoute
}