package com.example.billing.ui

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
import com.example.billing.data.BillRepository
import com.example.billing.model.Bill
import com.example.billing.model.ItemData
import com.example.billing.model.RoomData
import com.example.billing.utils.PlatformBackHandler
import com.example.billing.utils.formatMoney
import com.example.billing.utils.resolveDiscount
import com.example.billing.utils.sanitizeDecimal
import com.example.billing.utils.sanitizePhone
import com.example.billing.utils.toInputString
import kotlinx.coroutines.launch

private var editIdCounter = 0L

private class EditItem(val name: String, initialQty: String, initialRate: String) {
    var qty by mutableStateOf(initialQty)
    var rate by mutableStateOf(initialRate)
    val total: Double get() = (qty.toDoubleOrNull() ?: 0.0) * (rate.toDoubleOrNull() ?: 0.0)
    val isIncomplete: Boolean get() = (qty.isNotBlank() || rate.isNotBlank()) && total <= 0.0
}

private class EditRoom(initialName: String, initialItems: List<EditItem>) {
    val id: Long = editIdCounter++
    var name by mutableStateOf(initialName)
    val items = mutableStateListOf<EditItem>().apply { addAll(initialItems) }
    val total: Double get() = items.sumOf { it.total }

    /** Wohi item us ke neeche dobara (alag rate ke liye). */
    fun duplicate(item: EditItem) {
        val i = items.indexOf(item)
        val copy = EditItem(item.name, "", "")
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
    // Blank = 0 received (Billing screen jaisa). bill.received purane bills (null = poora mila) ko bhi sahi sambhalta hai.
    var received by remember { mutableStateOf(bill.received.toInputString()) }
    var isDirty by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var addItemFor by remember { mutableStateOf<EditRoom?>(null) }
    var roomToDelete by remember { mutableStateOf<EditRoom?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val rooms = remember {
        mutableStateListOf<EditRoom>().apply {
            bill.rooms.forEach { r ->
                add(EditRoom(r.roomName, r.items.map { EditItem(it.name, it.qty, it.rate) }))
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
        val incompleteItem = rooms.asSequence()
            .flatMap { room -> room.items.map { room to it } }
            .firstOrNull { (_, item) -> item.isIncomplete }

        val problem = when {
            customer.isBlank() -> "Customer name is required"
            rooms.none { r -> r.items.any { it.total > 0.0 } } -> "Enter the quantity and rate for at least one item."
            incompleteItem != null -> {
                val roomName = incompleteItem.first.name.ifBlank { "Room" }
                val itemName = incompleteItem.second.name
                val missing = if (incompleteItem.second.qty.isBlank()) "Qty" else "Rate"
                "Room \"$roomName\" -> \"$itemName\" $missing is missing."
            }
            discountValue > subtotal -> "Discount is greater than subtotal."
            receivedValue > grandTotal + 0.005 -> "Received is greater than total."
            else -> null
        }

        if (problem != null) {
            scope.launch { snackbar.showSnackbar(problem) }
            return
        }

        val finalRooms = rooms
            .map { room ->
                RoomData(
                    roomName = room.name.trim(),
                    items = room.items
                        .filter { it.qty.isNotBlank() || it.rate.isNotBlank() }
                        .map { ItemData(it.name, it.qty, it.rate) },
                )
            }
            .filter { it.items.isNotEmpty() }

        scope.launch {
            isSaving = true
            try {
                repository.updateBill(bill.id, customer.trim(), phone, finalRooms, discountValue, extraValue, receivedValue)
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
        containerColor = Color(0xFFF4F6F8),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { requestBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text("Edit Bill", fontWeight = FontWeight.Bold)
                        Text("#${bill.id}", fontSize = 12.sp, color = Color.Gray)
                    }
                },
            )
        },
        // Chhota bottom bar: left me total, right me chhota Save button
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = Color.White) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("GRAND TOTAL", fontSize = 10.sp, color = AppGreen, fontWeight = FontWeight.Bold)
                        Text("Rs ${formatMoney(grandTotal)}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        if (pending > 0.005) {
                            Text("Pending: Rs ${formatMoney(pending)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DueColor)
                        }
                    }
                    Button(
                        onClick = { save() },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                        modifier = Modifier.height(40.dp),
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 900.dp).fillMaxWidth().fillMaxHeight(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    SectionCard(title = "Customer Details") {
                        OutlinedTextField(
                            value = customer,
                            onValueChange = { customer = it; isDirty = true },
                            label = { Text("Customer Name") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = sanitizePhone(it); isDirty = true },
                            label = { Text("Phone Number") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                            supportingText = { Text("For another country use +code, e.g. +923012345678") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                item {
                    SectionCard(title = "Discount, Extra & Payment") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                shape = RoundedCornerShape(10.dp),
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
                            shape = RoundedCornerShape(10.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Rooms & Items", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("${rooms.size} rooms", fontSize = 12.sp, color = Color.Gray)
                        }
                        TextButton(onClick = { rooms.add(EditRoom("", emptyList())); isDirty = true }) {
                            Icon(Icons.Default.AddCircle, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add Room")
                        }
                    }
                }

                items(rooms, key = { it.id }) { room ->
                    EditRoomCard(
                        room = room,
                        onChanged = { isDirty = true },
                        onAddItem = { addItemFor = room },
                        onDeleteRoom = { roomToDelete = room },
                    )
                }

                // Poora hisaab (bottom bar chhota rakhne ke liye yahan dikhaya)
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

    addItemFor?.let { room ->
        AddItemDialog(
            onPick = { name -> room.items.add(EditItem(name, "", "")); isDirty = true; addItemFor = null },
            onDismiss = { addItemFor = null },
        )
    }

    roomToDelete?.let { room ->
        ConfirmDialog(
            "Delete Room",
            "\"${room.name.ifBlank { "Room" }}\" and all its items will be removed. Are you sure?",
            "Delete",
            { rooms.remove(room); isDirty = true; roomToDelete = null },
            { roomToDelete = null },
        )
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes that will be lost.") },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("Stay") } },
            confirmButton = { TextButton(onClick = { showDiscard = false; onClose() }) { Text("Discard", color = Color.Red) } },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF546E7A))
            content()
        }
    }
}

@Composable
private fun EditRoomCard(room: EditRoom, onChanged: () -> Unit, onAddItem: () -> Unit, onDeleteRoom: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFFF9FAFB)).padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = room.name,
                    onValueChange = { room.name = it; onChanged() },
                    singleLine = true,
                    textStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                    decorationBox = { inner ->
                        Box {
                            if (room.name.isEmpty()) Text("Room name", fontSize = 16.sp, color = Color.Gray)
                            inner()
                        }
                    },
                )
                Text("Rs ${formatMoney(room.total)}", color = AppGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                IconButton(onClick = onDeleteRoom) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete room", tint = Color.Red)
                }
            }
            HorizontalDivider(color = Color(0xFFEEEEEE))

            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (room.items.isEmpty()) {
                    Text("No item in this room", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
                }
                room.items.forEachIndexed { index, item ->
                    key(item) {
                        val occurrence = room.items.take(index + 1).count { it.name == item.name }
                        ItemEntryCard(
                            name = item.name,
                            occurrence = occurrence,
                            qty = item.qty,
                            onQtyChange = { item.qty = it; onChanged() },
                            rate = item.rate,
                            onRateChange = { item.rate = it; onChanged() },
                            total = item.total,
                            isIncomplete = item.isIncomplete,
                            onDuplicate = { room.duplicate(item); onChanged() },
                            onDelete = { room.items.remove(item); onChanged() },
                        )
                    }
                }

                TextButton(onClick = onAddItem) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = AppGreen, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Item", color = AppGreen, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}