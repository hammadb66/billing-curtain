package com.example.billing.sync

import com.example.billing.model.Bill
import com.example.billing.model.RoomData
import kotlinx.serialization.Serializable


@Serializable
data class RemoteBill(
    val invoiceNo: Long = 0,
    val customer: String = "",
    val phone: String = "",
    val address: String = "",     // NAYA
    val phone2: String = "",      // NAYA
    val accountNo: String = "",   // NAYA
    val date: String = "",
    val rooms: List<RoomData> = emptyList(),
    val discount: Double = 0.0,
    val extraCharges: Double = 0.0,
    val paidAmount: Double? = null,
    val updatedAt: Long = 0,
    val deviceId: String = "",
    val deleted: Boolean = false,
    val serverTs: Long = 0,
    val invoiceLabel: String = "",
    val showProduct: Boolean = false,
    val showCategory: Boolean = false,
    val showSubcategory: Boolean = false,
    val showGroup: Boolean = false,    // <-- NAYA
    val showSubgroup: Boolean = false, // <-- NAYA
    val salesmanId: Long? = null,
)

fun Bill.toRemote(updatedAt: Long, deviceId: String) = RemoteBill(
    invoiceNo = id, customer = customer, phone = phone, address = address, phone2 = phone2, accountNo = accountNo,
    date = date, rooms = rooms, discount = discount, extraCharges = extraCharges, paidAmount = paidAmount,
    updatedAt = updatedAt, deviceId = deviceId, invoiceLabel = invoiceLabel,
    showProduct = showProduct, showCategory = showCategory, showSubcategory = showSubcategory,  showGroup = showGroup, showSubgroup = showSubgroup, salesmanId = salesmanId,
)

fun RemoteBill.toBill() = Bill(
    id = invoiceNo, customer = customer, phone = phone, address = address, phone2 = phone2, accountNo = accountNo,
    date = date, rooms = rooms, discount = discount, extraCharges = extraCharges, paidAmount = paidAmount,
    invoiceLabel = invoiceLabel, showProduct = showProduct, showCategory = showCategory, showSubcategory = showSubcategory,  showGroup = showGroup, showSubgroup = showSubgroup, salesmanId = salesmanId,
)