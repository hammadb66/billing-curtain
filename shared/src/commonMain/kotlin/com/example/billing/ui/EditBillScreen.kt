package com.example.billing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billing.AppServices
import com.example.billing.data.BillRepository
import com.example.billing.model.Bill
import com.example.billing.model.ItemData
import com.example.billing.model.MasterItem
import com.example.billing.model.Product
import com.example.billing.model.RoomData
import com.example.billing.utils.PlatformBackHandler
import com.example.billing.utils.formatMoney
import com.example.billing.utils.resolveDiscount
import com.example.billing.utils.sanitizeDecimal
import com.example.billing.utils.sanitizePhone
import com.example.billing.utils.toInputString
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private var editIdCounter = 0L

private class EditItem(
    val name: String,
    val initialQty: String,
    initialRate: String,
    val unit: String,
    val productId: Long? = null,
    val productName: String = "",
    val categoryName: String = "",
    val subcategoryName: String = "",
    val initialDisplayType: String = "default",
) {
    var qty by mutableStateOf(initialQty)
    var rate by mutableStateOf(initialRate)
    var displayType by mutableStateOf(initialDisplayType)

    val total: Double get() = (qty.toDoubleOrNull() ?: 0.0) * (rate.toDoubleOrNull() ?: 0.0)
    val isIncomplete: Boolean get() = (qty.isNotBlank() || rate.isNotBlank()) && total <= 0.0
}

private class EditRoom(initialName: String, initialItems: List<EditItem>) {
    val id: Long = editIdCounter++
    var name by mutableStateOf(initialName)
    val items = mutableStateListOf<EditItem>().apply { addAll(initialItems) }
    val total: Double get() = items.sumOf { it.total }

    fun duplicate(item: EditItem) {
        val i = items.indexOf(item)
        val copy = EditItem(
            item.name, "", "", item.unit,
            item.productId, item.productName, item.categoryName, item.subcategoryName, item.displayType
        )
        if (i >= 0) items.add(i + 1, copy) else items.add(copy)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBillScreen(
    bill: Bill,
    repository: BillRepository,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var customer by remember { mutableStateOf(bill.customer) }
    var phone by remember { mutableStateOf(bill.phone) }
    var discount by remember { mutableStateOf(bill.discount.toInputString()) }
    var extraCharges by remember { mutableStateOf(bill.extraCharges.toInputString()) }
    var discountIsPercent by remember { mutableStateOf(false) }
    var received by remember { mutableStateOf(bill.received.toInputString()) }
    var isDirty by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var addItemFor by remember { mutableStateOf<EditRoom?>(null) }
    var roomToDelete by remember { mutableStateOf<EditRoom?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val services = koinInject<AppServices>()
    var masterItems by remember { mutableStateOf<List<MasterItem>>(emptyList()) }
    var showProduct by remember { mutableStateOf(bill.showProduct) }
    var showCategory by remember { mutableStateOf(bill.showCategory) }
    var showSubcategory by remember { mutableStateOf(bill.showSubcategory) }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var productStocks by remember { mutableStateOf<Map<Long, Double>>(emptyMap()) }

    LaunchedEffect(Unit) {
        val allProds = services.vendors.listProductsWithStock()
        products = allProds
        productStocks = allProds.associate { it.id to (it.liveStock ?: it.openingStock) }
        masterItems = services.settings.getMasterItems()
    }

    val rooms = remember {
        mutableStateListOf<EditRoom>().apply {
            bill.rooms.forEach { r ->
                add(EditRoom(r.roomName, r.items.map {
                    EditItem(
                        name = it.name, initialQty = it.qty, initialRate = it.rate, unit = it.unit,
                        productId = it.productId, productName = it.productName,
                        categoryName = it.categoryName, subcategoryName = it.subcategoryName,
                        initialDisplayType = it.displayType,
                    )
                }))
            }
        }
    }

    val subtotal = rooms.sumOf { it.total }
    val discountValue = resolveDiscount(subtotal, discount, discountIsPercent)
    val extraValue = extraCharges.toDoubleOrNull() ?: 0.0
    val grandTotal = (subtotal - discountValue + extraValue).coerceAtLeast(0.0)
    val receivedValue = received.toDoubleOrNull() ?: 0.0
    val pending = (grandTotal - receivedValue).coerceAtLeast(0.0)

    fun requestBack() {
        if (isDirty) showDiscard = true else onClose()
    }

    fun save() {
        if (isSaving) return
        val incompleteItem = rooms.asSequence().flatMap { room -> room.items.map { room to it } }
            .firstOrNull { (_, item) -> item.isIncomplete }

        val problem = when {
            customer.isBlank() -> "Customer name is required"
            rooms.none { r -> r.items.any { it.total > 0.0 } } -> "Enter the quantity and rate for at least one item."
            incompleteItem != null -> {
                val sectionName = incompleteItem.first.name.ifBlank { "All Items" }
                val itemName = incompleteItem.second.name
                val missing = if (incompleteItem.second.qty.isBlank()) "Qty" else "Rate"
                "\"$sectionName\" -> \"$itemName\" $missing is missing."
            }
            discountValue > subtotal -> "Discount is greater than subtotal."
            receivedValue > grandTotal + 0.005 -> "Received is greater than total."
            else -> null
        }

        if (problem != null) {
            scope.launch { snackbar.showSnackbar(problem) }
            return
        }

        val finalRooms = rooms.map { room ->
            RoomData(
                roomName = room.name.trim(),
                items = room.items.filter { it.qty.isNotBlank() || it.rate.isNotBlank() }.map {
                    ItemData(
                        name = it.name, qty = it.qty, rate = it.rate, unit = it.unit,
                        productId = it.productId, productName = it.productName,
                        categoryName = it.categoryName, subcategoryName = it.subcategoryName,
                        displayType = it.displayType,
                    )
                },
            )
        }.filter { it.items.isNotEmpty() }

        scope.launch {
            isSaving = true
            try {
                repository.updateBill(
                    bill.id, customer.trim(), phone, finalRooms,
                    discountValue, extraValue, receivedValue,
                    showProduct, showCategory, showSubcategory
                )
                onClose()
            } catch (e: Exception) {
                isSaving = false
                snackbar.showSnackbar("Save failed: ${e.message}")
            }
        }
    }

    PlatformBackHandler { requestBack() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color(0xFFF4F5F7),
        topBar = {
            // Custom Desktop Header
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { requestBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF2C3E50))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Edit Bill", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
                    Text(" • ${bill.invoiceText}", fontSize = 14.sp, color = Color.Gray)
                }
            }
        },
        bottomBar = {
            // Custom Desktop Footer
            Surface(color = Color.White, shadowElevation = 4.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column {
                            Text("GRAND TOTAL", fontSize = 10.sp, color = AppGreen, fontWeight = FontWeight.Bold)
                            Text("Rs ${formatMoney(grandTotal)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        }
                        if (pending > 0.005) {
                            Column {
                                Text("PENDING", fontSize = 10.sp, color = DueColor, fontWeight = FontWeight.Bold)
                                Text("Rs ${formatMoney(pending)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DueColor)
                            }
                        }
                    }

                    Button(
                        onClick = { save() },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppGreen),
                        modifier = Modifier.height(44.dp).width(140.dp),
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text("SAVE CHANGES", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 1000.dp).fillMaxWidth().fillMaxHeight(),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    SectionCard(title = "Customer Details") {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedTextField(
                                value = customer,
                                onValueChange = { customer = it; isDirty = true },
                                label = { Text("Customer Name") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = sanitizePhone(it); isDirty = true },
                                label = { Text("Phone Number") },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                item {
                    SectionCard(title = "Discount, Extra & Payment") {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            DiscountInput(
                                value = discount,
                                onValueChange = { discount = it; isDirty = true },
                                isPercent = discountIsPercent,
                                onModeChange = { discountIsPercent = it; discount = ""; isDirty = true },
                                computedAmount = discountValue,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = extraCharges,
                                onValueChange = { extraCharges = sanitizeDecimal(it); isDirty = true },
                                label = { Text("Extra/Misc (Rs)") },
                                singleLine = true,
                                shape = RoundedCornerShape(6.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (discountValue > subtotal) {
                            Text("Discount is greater than subtotal.", color = Color.Red, fontSize = 12.sp)
                        }
                        OutlinedTextField(
                            value = received,
                            onValueChange = { received = sanitizeDecimal(it); isDirty = true },
                            label = { Text("Received Amount (Rs)") },
                            supportingText = { Text("Blank = nothing received. Tap Full if fully paid.") },
                            trailingIcon = {
                                TextButton(
                                    onClick = { received = grandTotal.toInputString(); isDirty = true },
                                    enabled = grandTotal > 0.0,
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                ) { Text("Full", fontWeight = FontWeight.Bold) }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                item {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Sections & Items", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
                            Text("${rooms.size} section(s)", fontSize = 12.sp, color = Color.Gray)
                        }
                        Button(
                            onClick = { rooms.add(EditRoom("", emptyList())); isDirty = true },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50))
                        ) {
                            Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Section", fontSize = 13.sp)
                        }
                    }
                }

                items(rooms, key = { it.id }) { room ->
                    EditRoomCard(
                        room = room,
                        productStocks = productStocks,
                        onChanged = { isDirty = true },
                        onAddItem = { addItemFor = room },
                        onDeleteRoom = { roomToDelete = room },
                    )
                }

                item {
                    SectionCard(title = "Summary") {
                        AmountBreakdown(
                            subtotal = subtotal,
                            discount = discountValue,
                            extra = extraValue,
                            grandTotal = grandTotal,
                            received = receivedValue,
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(addItemFor) {
        if (addItemFor != null) {
            products = services.vendors.listProductsWithStock()
            productStocks = products.associate { it.id to (it.liveStock ?: it.openingStock) }
        }
    }

    addItemFor?.let { room ->
        AddItemDialog(
            products = products,
            onPick = { p ->
                room.items.add(EditItem(p.name, "", "", p.unit, p.id, p.name, p.categoryName, p.subcategoryName))
                isDirty = true; addItemFor = null
            },
            onManageProducts = { addItemFor = null },
            onDismiss = { addItemFor = null },
        )
    }

    roomToDelete?.let { room ->
        ConfirmDialog(
            "Delete Section",
            "\"${room.name.ifBlank { "All Items" }}\" and all its items will be removed. Are you sure?",
            "Delete",
            { rooms.remove(room); isDirty = true; roomToDelete = null },
            { roomToDelete = null },
        )
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Discard changes?", fontWeight = FontWeight.Bold) },
            text = { Text("You have unsaved changes that will be lost.") },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("Stay") } },
            confirmButton = {
                TextButton(onClick = { showDiscard = false; onClose() }) {
                    Text("Discard", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
            content()
        }
    }
}

@Composable
private fun EditRoomCard(
    room: EditRoom,
    productStocks: Map<Long, Double>,
    onChanged: () -> Unit,
    onAddItem: () -> Unit,
    onDeleteRoom: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Header
            Row(
                Modifier.fillMaxWidth().background(Color(0xFFF8FAFC), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = room.name,
                    onValueChange = { room.name = it; onChanged() },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF2C3E50)
                    ),
                    modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                    decorationBox = { inner ->
                        Box {
                            if (room.name.isEmpty()) Text("All Items", fontSize = 16.sp, color = Color.Gray)
                            inner()
                        }
                    },
                )
                Text("Rs ${formatMoney(room.total)}", color = AppGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                IconButton(onClick = onDeleteRoom) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete section", tint = Color.Red, modifier = Modifier.size(20.dp))
                }
            }
            HorizontalDivider(color = Color(0xFFE0E0E0))

            // Items Body
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (room.items.isEmpty()) {
                    Text(
                        "No items added yet",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                room.items.forEachIndexed { index, item ->
                    key(item) {
                        val occurrence = room.items.take(index + 1).count { it.name == item.name }
                        val liveStock = productStocks[item.productId] ?: 0.0
                        val availableStock = liveStock + (item.initialQty.toDoubleOrNull() ?: 0.0)
                        ItemEntryCard(
                            name = item.name,
                            occurrence = occurrence,
                            qty = item.qty,
                            onQtyChange = { item.qty = it; onChanged() },
                            rate = item.rate,
                            onRateChange = { item.rate = it; onChanged() },
                            displayType = item.displayType,
                            onDisplayTypeChange = { item.displayType = it; onChanged() },
                            productName = item.productName,
                            categoryName = item.categoryName,
                            subcategoryName = item.subcategoryName,
                            total = item.total,
                            isIncomplete = item.isIncomplete,
                            unit = item.unit,
                            stock = availableStock,
                            onDuplicate = { room.duplicate(item); onChanged() },
                            onDelete = { room.items.remove(item); onChanged() },
                        )
                    }
                }

                // Add Item Button
                TextButton(
                    onClick = onAddItem,
                    modifier = Modifier.padding(top = 4.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = AppGreen)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Item", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}