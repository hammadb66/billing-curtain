package com.example.billing.presentation

import kotlinx.serialization.Serializable

@Serializable
sealed interface BillingRoute {
    @Serializable data object Billing : BillingRoute
    @Serializable data object History : BillingRoute
    @Serializable data object ManageItems : BillingRoute
    @Serializable data object ItemReport : BillingRoute
    @Serializable data class EditBill(val billId: Long) : BillingRoute
}