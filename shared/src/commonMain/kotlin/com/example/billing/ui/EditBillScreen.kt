package com.example.billing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billing.data.BillRepository
import com.example.billing.data.masterItems
import com.example.billing.model.Bill
import com.example.billing.model.ItemData
import com.example.billing.model.RoomData
import com.example.billing.utils.PlatformBackHandler
import com.example.billing.utils.formatMoney
import com.example.billing.utils.resolveDiscount
import com.example.billing.utils.sanitizeDecimal
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
    // toInputString: 10000000.0 ko "1.0E7" nahi, "10000000" banata hai
    var discount by remember { mutableStateOf(bill.discount.toInputString()) }
    var extraCharges by remember { mutableStateOf(bill.extraCharges.toInputString()) }
    var discountIsPercent by remember { mutableStateOf(false) }
    // Received: khali = poora paisa mila. "0" = kuch nahi mila (poora udhaar)
    var received by remember { mutableStateOf(bill.paidAmount?.let { if (it <= 0.0) "0" else it.toInputString() } ?: "") }
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

    // Live hisaab: kuch bhi badle, ye khud dobara calculate hota hai
    val subtotal = rooms.sumOf { it.total }
    val discountValue = resolveDiscount(subtotal, discount, discountIsPercent)
    val extraValue = extraCharges.toDoubleOrNull() ?: 0.0
    val grandTotal = (subtotal - discountValue + extraValue).coerceAtLeast(0.0)
    val receivedValue = received.toDoubleOrNull()   // null = poora mila

    fun requestBack() {
        if (isDirty) showDiscard = true else onClose()
    }

    fun save() {
        if (isSaving) return

        val problem = when {
            customer.isBlank() -> "Customer name is required"
            rooms.none { r -> r.items.any { it.total > 0.0 } } -> "Kam az kam ek item ki qty aur rate daalein"
            rooms.any { r -> r.items.any { it.isIncomplete } } -> "Laal item me sirf qty ya sirf rate hai. Dono daalein ya item delete karein."
            discountValue > subtotal -> "Discount subtotal se zyada nahi ho sakta"
            receivedValue != null && receivedValue > grandTotal + 0.005 -> "Received total se zyada nahi ho sakta"
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
            .filter { it.items.isNotEmpty() } // khali room save nahi hoga (Billing screen jaisa)

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
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = Color.White) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    AmountBreakdown(
                        subtotal = subtotal,
                        discount = discountValue,
                        extra = extraValue,
                        grandTotal = grandTotal,
                        received = receivedValue,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { save() },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text("Save Changes", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ---------- Customer ----------
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
                            onValueChange = { phone = it.filter { c -> c.isDigit() }; isDirty = true },
                            label = { Text("Phone Number") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // ---------- Discount / Extra ----------
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
                            Text("Discount subtotal se zyada hai!", color = Color.Red, fontSize = 12.sp)
                        }
                        OutlinedTextField(
                            value = received,
                            onValueChange = { received = sanitizeDecimal(it); isDirty = true },
                            label = { Text("Received / Jitne paise mile (Rs)") },
                            supportingText = { Text("Khali = poora paisa mila. 0 = kuch nahi mila.") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // ---------- Rooms header ----------
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
            }
        }
    }

    // ---------- Dialogs ----------
    addItemFor?.let { room ->
        val used = room.items.map { it.name }.toSet()
        val remaining = masterItems.filter { it !in used }
        AlertDialog(
            onDismissRequest = { addItemFor = null },
            title = { Text("Add Item") },
            text = {
                if (remaining.isEmpty()) {
                    Text("Is room me saare items pehle se add hain.")
                } else {
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        remaining.forEach { name ->
                            Text(
                                name,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { room.items.add(EditItem(name, "", "")); isDirty = true; addItemFor = null }
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addItemFor = null }) { Text("Cancel") } },
        )
    }

    roomToDelete?.let { room ->
        ConfirmDialog(
            "Delete Room",
            "\"${room.name.ifBlank { "Room" }}\" aur is ke saare items delete ho jayenge. Pakka?",
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

/** Safed card + chhota title + andar ka content (customer card, discount card dono yehi use karte hain). */
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
            // Room ka header: naam + room total + delete
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
                            if (room.name.isEmpty()) Text("Room ka naam", fontSize = 16.sp, color = Color.Gray)
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

            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (room.items.isEmpty()) {
                    Text(
                        "Is room me abhi koi item nahi.",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    // Column ke naam (Billing screen jaisa)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Item", Modifier.weight(3f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                        Text("Qty", Modifier.weight(1.2f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                        Text("Rate", Modifier.weight(1.2f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                        Text("Total", Modifier.weight(1.4f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.End)
                        Spacer(Modifier.width(36.dp)) // delete icon ki jagah
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color(0xFFF0F0F0))

                    room.items.forEach { item ->
                        // Adhura item (sirf qty ya sirf rate) halka laal dikhega
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (item.isIncomplete) Color(0xFFFFEBEE) else Color.Transparent, RoundedCornerShape(8.dp))
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(item.name, Modifier.weight(3f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            CompactNumberField(item.qty, { item.qty = it; onChanged() }, Modifier.weight(1.2f))
                            CompactNumberField(item.rate, { item.rate = it; onChanged() }, Modifier.weight(1.2f))
                            Text(formatMoney(item.total), Modifier.weight(1.4f), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppGreen, textAlign = TextAlign.End)
                            IconButton(onClick = { room.items.remove(item); onChanged() }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove item", tint = Color.Red, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Item add karne ka bara, wazeh button
                TextButton(onClick = onAddItem) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = AppGreen, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Item Add Karein", color = AppGreen, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}