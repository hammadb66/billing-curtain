package com.example.billing.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.billing.utils.resolveDiscount

private var idCounter = 0L
private fun nextId(): Long = idCounter++

class ItemState(val name: String, var unit: String = "") {
    var qty by mutableStateOf("")
    var rate by mutableStateOf("")
    var stock: Double = 0.0 // NAYA: Stock store karne ke liye
    // NEW
    var productId: Long? = null
    var productName by mutableStateOf("")
    var categoryName by mutableStateOf("")
    var subcategoryName by mutableStateOf("")
    var displayType by mutableStateOf("default") // "default"|"category"|"subcategory"|"product"

    val total: Double get() = (qty.toDoubleOrNull() ?: 0.0) * (rate.toDoubleOrNull() ?: 0.0)

    /** Kuch likha hai (qty ya rate) lekin total 0 hai -> adhura item. */
    val isIncomplete: Boolean get() = (qty.isNotBlank() || rate.isNotBlank()) && total <= 0.0


    fun displayName(showProduct: Boolean, showCategory: Boolean, showSubcategory: Boolean): String =
        when (displayType) {
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

class RoomState {
    val id: Long = nextId()
    var name by mutableStateOf("")
    var expanded by mutableStateOf(true)
    val items = mutableStateListOf<ItemState>()
    val total: Double get() = items.sumOf { it.total }

    // addItem ko update karo taake extra data le sake
    fun addProduct(product: Product,stock: Double) {
        val item = ItemState(product.name, product.unit)
        item.productId = product.id
        item.productName = product.name
        item.categoryName = product.categoryName
        item.subcategoryName = product.subcategoryName
        item.stock = stock
        items.add(item)
    }

    /** Wohi item us ke neeche dobara (alag rate ke liye). */
    fun duplicateItem(item: ItemState) {
        val i = items.indexOf(item)
        val copy = ItemState(item.name, item.unit).apply {
            productId = item.productId
            productName = item.productName
            categoryName = item.categoryName
            subcategoryName = item.subcategoryName
            displayType = item.displayType
        }
        if (i >= 0) items.add(i + 1, copy) else items.add(copy)
    }

    fun removeItem(item: ItemState) {
        items.remove(item)
    }

    fun toRoomData() = RoomData(
        roomName = name.trim(),
        items = items
            .filter { it.qty.isNotBlank() || it.rate.isNotBlank() }
            .map {
                ItemData(
                    name = it.name, qty = it.qty, rate = it.rate, unit = it.unit,
                    productId = it.productId, productName = it.productName,
                    categoryName = it.categoryName, subcategoryName = it.subcategoryName,
                    displayType = it.displayType,
                )
            }
    )
}

class BillingFormState {
    var customer by mutableStateOf("")
    var phone by mutableStateOf("")
    var discount by mutableStateOf("")

    // NEW: display checkboxes
    var showProduct by mutableStateOf(true)
    var showCategory by mutableStateOf(false)
    var showSubcategory by mutableStateOf(false)
    var discountIsPercent by mutableStateOf(false)   // false = Rs, true = %
    var extraCharges by mutableStateOf("")
    var received by mutableStateOf("")               // khali = 0 mila
    val rooms = mutableStateListOf(RoomState())

    val subtotal: Double get() = rooms.sumOf { it.total }

    /** Discount hamesha Rs me (agar % likha ho to subtotal se Rs nikal leta hai). */
    val discountValue: Double get() = resolveDiscount(subtotal, discount, discountIsPercent)
    val extraValue: Double get() = extraCharges.toDoubleOrNull() ?: 0.0
    val grandTotal: Double get() = (subtotal - discountValue + extraValue).coerceAtLeast(0.0)

    /** Blank = 0 mila. Koi amount likhi ho to wohi. */
    val receivedValue: Double get() = received.toDoubleOrNull() ?: 0.0
    val balance: Double get() = (grandTotal - receivedValue).coerceAtLeast(0.0)

    val hasAnyItem: Boolean get() = rooms.any { r -> r.items.any { it.total > 0.0 } }
    val hasIncompleteItem: Boolean get() = rooms.any { r -> r.items.any { it.isIncomplete } }

    fun addRoom() {
        rooms.add(RoomState())
    }

    fun removeRoom(room: RoomState) {
        if (rooms.isNotEmpty() && rooms.first() !== room) rooms.remove(room)
    }

    fun reset() {
        customer = ""
        phone = ""
        discount = ""
        discountIsPercent = false
        extraCharges = ""
        received = ""
        rooms.clear()
        rooms.add(RoomState())
    }

    fun toRoomDataList(): List<RoomData> = rooms.map { it.toRoomData() }.filter { it.items.isNotEmpty() }
}