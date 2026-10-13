package com.example.billing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import kotlinx.coroutines.launch

private var editIdCounter = 0L

private class EditItem(val name: String, initialQty: String, initialRate: String) {
    var qty by mutableStateOf(initialQty)
    var rate by mutableStateOf(initialRate)
    val total: Double get() = (qty.toDoubleOrNull() ?: 0.0) * (rate.toDoubleOrNull() ?: 0.0)
}

private class EditRoom(initialName: String, initialItems: List<EditItem>) {
    val id: Long = editIdCounter++
    var name by mutableStateOf(initialName)
    val items = mutableStateListOf<EditItem>().apply { addAll(initialItems) }
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
    var discount by remember { mutableStateOf(if (bill.discount > 0.0) bill.discount.toString().removeSuffix(".0") else "") }
    var extraCharges by remember { mutableStateOf(if (bill.extraCharges > 0.0) bill.extraCharges.toString().removeSuffix(".0") else "") }
    var isDirty by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var addItemFor by remember { mutableStateOf<EditRoom?>(null) }
    var isSaving by remember { mutableStateOf(false) }




    val rooms = remember {
        mutableStateListOf<EditRoom>().apply {
            bill.rooms.forEach { r ->
                add(EditRoom(r.roomName, r.items.map { EditItem(it.name, it.qty, it.rate) }))
            }
        }
    }

    fun requestBack() {
        if (isDirty) showDiscard = true else onClose()
    }

    fun save() {
        if (isSaving) return
        if (customer.isBlank()) {
            scope.launch { snackbar.showSnackbar("Customer name is required") }
            return
        }

        val finalRooms = rooms.map { room ->
            RoomData(
                roomName = room.name.trim(),
                items = room.items
                    .filter { it.qty.isNotBlank() || it.rate.isNotBlank() }
                    .map { ItemData(it.name, it.qty, it.rate) },
            )
        }

        val discVal = discount.toDoubleOrNull() ?: 0.0
        val extraVal = extraCharges.toDoubleOrNull() ?: 0.0

        scope.launch {
            isSaving = true
            try {
                // repository update me discount aur extraCharges pass karein
                repository.updateBill(bill.id, customer.trim(), phone, finalRooms, discVal, extraVal)
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
                title = { Text("Edit Bill", fontWeight = FontWeight.Bold) },
                actions = {
                    Button(onClick = { save() }, enabled = !isSaving, shape = RoundedCornerShape(10.dp), modifier = Modifier.padding(end = 8.dp)) {
                        Text("Save Changes")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 900.dp).fillMaxWidth().fillMaxHeight(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Customer Details", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF546E7A))
                        OutlinedTextField(
                            value = customer,
                            onValueChange = { customer = it; isDirty = true },
                            placeholder = { Text("Enter customer name") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it.filter { c -> c.isDigit() }; isDirty = true },
                            placeholder = { Text("Phone Number") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = discount,
                                onValueChange = { discount = it; isDirty = true },
                                label = { Text("Discount (Rs)") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = extraCharges,
                                onValueChange = { extraCharges = it; isDirty = true },
                                label = { Text("Extra Charges (Rs)") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Rooms & Items", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("${rooms.size} rooms added", fontSize = 12.sp, color = Color.Gray)
                        }
                        TextButton(onClick = { rooms.add(EditRoom("New Room", emptyList())); isDirty = true }) {
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
                        onDeleteRoom = { rooms.remove(room); isDirty = true },
                    )
                }
            }
        }
    }

    addItemFor?.let { room ->
        val used = room.items.map { it.name }.toSet()
        val remaining = masterItems.filter { it !in used }
        AlertDialog(
            onDismissRequest = { addItemFor = null },
            title = { Text("Add Item") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    remaining.forEach { name ->
                        Text(name, modifier = Modifier.fillMaxWidth().clickable { room.items.add(EditItem(name, "", "")); isDirty = true; addItemFor = null }.padding(vertical = 12.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addItemFor = null }) { Text("Cancel") } },
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
private fun EditRoomCard(room: EditRoom, onChanged: () -> Unit, onAddItem: () -> Unit, onDeleteRoom: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column {
            Row(Modifier.fillMaxWidth().background(Color(0xFFF9FAFB)).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = room.name,
                    onValueChange = { room.name = it; onChanged() },
                    singleLine = true,
                    textStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                )
                IconButton(onClick = onAddItem) { Icon(Icons.Default.Add, contentDescription = null, tint = AppGreen) }
                IconButton(onClick = onDeleteRoom) { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
            }
            HorizontalDivider(color = Color(0xFFEEEEEE))
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                room.items.forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.name, Modifier.weight(3f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        CompactNumberField(item.qty, { item.qty = it; onChanged() }, Modifier.weight(1.2f))
                        CompactNumberField(item.rate, { item.rate = it; onChanged() }, Modifier.weight(1.2f))
                        Text(formatMoney(item.total), Modifier.weight(1.4f), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppGreen, textAlign = TextAlign.End)
                        IconButton(onClick = { room.items.remove(item); onChanged() }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}