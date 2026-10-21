package com.example.billing.sync

import com.example.billing.model.Bill
import com.example.billing.model.RoomData
import kotlinx.serialization.Serializable

/**
 * Firebase me ek bill ki shakal:  /bills/{shopId}/{remoteId}
 * deleted = true wala record "qabar ka nishan" (tombstone) hai: dusre laptops ko pata chalta hai ke bill delete hua.
 *
 * serverTs = Firebase SERVER ka waqt jo likhte waqt khud lag jata hai (laptop ki ghadi par bharosa nahi).
 * Naya data dhoondne (incremental pull) ke liye sirf isi ko istemal karte hain.
 */
@Serializable
data class RemoteBill(
    val invoiceNo: Long = 0,
    val customer: String = "",
    val phone: String = "",
    val date: String = "",
    val rooms: List<RoomData> = emptyList(),
    val discount: Double = 0.0,
    val extraCharges: Double = 0.0,
    val paidAmount: Double? = null,
    val updatedAt: Long = 0,
    val deviceId: String = "",
    val deleted: Boolean = false,
    val serverTs: Long = 0,
    /** Invoice number jo customer ko diya gaya (maslan "hammad-05"). Purane bills me khali. */
    val invoiceLabel: String = "",
    // NEW: display options saved with the bill
    val showProduct: Boolean = false,
    val showCategory: Boolean = false,
    val showSubcategory: Boolean = false,
)

fun Bill.toRemote(updatedAt: Long, deviceId: String) = RemoteBill(
    invoiceNo = id,
    customer = customer,
    phone = phone,
    date = date,
    rooms = rooms,
    discount = discount,
    extraCharges = extraCharges,
    paidAmount = paidAmount,
    updatedAt = updatedAt,
    deviceId = deviceId,
    invoiceLabel = invoiceLabel,
    showProduct = showProduct,
    showCategory = showCategory,
    showSubcategory = showSubcategory,
)

fun RemoteBill.toBill() = Bill(
    id = invoiceNo,
    customer = customer,
    phone = phone,
    date = date,
    rooms = rooms,
    discount = discount,
    extraCharges = extraCharges,
    paidAmount = paidAmount,
    invoiceLabel = invoiceLabel,
    showProduct = showProduct,
    showCategory = showCategory,
    showSubcategory = showSubcategory,
)
