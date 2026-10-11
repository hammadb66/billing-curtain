package com.example.billing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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

// ---- Edit screen ki apni chhoti state classes ----
private var editIdCounter = 0L

private class EditItem(val name: String, initialQty: String, initialRate: String) {
    var qty by mutableStateOf(initialQty)
    var rate by mutableStateOf(initialRate)
    val total: Double
        get() = (qty.toDoubleOrNull() ?: 0.0) * (rate.toDoubleOrNull() ?: 0.0)
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
    var isDirty by remember { mutableStateOf(false) }          // koi tabdeeli hui?
    var showDiscard by remember { mutableStateOf(false) }
    var addItemFor by remember { mutableStateOf<EditRoom?>(null) }
    var isSaving by remember { mutableStateOf(false) }          // <-- NAYI LINE
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
        if (isSaving) return          // pehle se save ho raha hai to dobara mat chalo

        if (customer.isBlank()) {
            scope.launch { snackbar.showSnackbar("Customer name is required") }
            return
        }

        // Har item check karo: agar kuch bhara hai to qty aur rate dono 0 se zyada hon
        for (room in rooms) {
            val roomName = room.name.ifBlank { "Room" }
            for (item in room.items) {
                if (item.qty.isBlank() && item.rate.isBlank()) continue
                val qty = item.qty.toDoubleOrNull() ?: 0.0
                val rate = item.rate.toDoubleOrNull() ?: 0.0
                if (qty <= 0 || rate <= 0) {
                    scope.launch {
                        snackbar.showSnackbar("$roomName → ${item.name}: Qty aur Rate dono 0 se zyada hone chahiye")
                    }
                    return
                }
            }
        }

        val finalRooms = rooms.map { room ->
            RoomData(
                roomName = room.name.trim(),
                // Khali items (na qty na rate) save nahi hote
                items = room.items
                    .filter { it.qty.isNotBlank() || it.rate.isNotBlank() }
                    .map { ItemData(it.name, it.qty, it.rate) },
            )
        }

        scope.launch {
            isSaving = true
            try {
                repository.updateBill(bill.id, customer.trim(), phone, finalRooms)
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
        containerColor = Color(0xFFF9FAFB),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { requestBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Edit Bill", fontWeight = FontWeight.Bold) },
                actions = {
                    Button(
                        onClick = { save() },
                        enabled = !isSaving,
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Save") }
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
                // ---- Customer details ----
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Customer Details",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF607D8B),
                        )
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
                    }
                }

                // ---- Rooms header ----
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Rooms & Items", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("${rooms.size} rooms added", fontSize = 12.sp, color = Color.Gray)
                        }
                        TextButton(onClick = {
                            rooms.add(EditRoom("New Room", emptyList()))
                            isDirty = true
                        }) {
                            Icon(Icons.Default.AddCircle, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add Room")
                        }
                    }
                }

                // ---- Har room ka card ----
                items(rooms, key = { it.id }) { room ->
                    EditRoomCard(
                        room = room,
                        onChanged = { isDirty = true },
                        onAddItem = {
                            val used = room.items.map { it.name }.toSet()
                            if (masterItems.all { it in used }) {
                                scope.launch { snackbar.showSnackbar("All items are already in this room") }
                            } else {
                                addItemFor = room
                            }
                        },
                        onDeleteRoom = { rooms.remove(room); isDirty = true },
                    )
                }
            }
        }
    }

    // ---- Item add karne ka dialog ----
    addItemFor?.let { room ->
        val used = room.items.map { it.name }.toSet()
        val remaining = masterItems.filter { it !in used }
        AlertDialog(
            onDismissRequest = { addItemFor = null },
            title = { Text("Add Item") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    remaining.forEach { name ->
                        Text(
                            name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    room.items.add(EditItem(name, "", ""))
                                    isDirty = true
                                    addItemFor = null
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addItemFor = null }) { Text("Cancel") } },
        )
    }

    // ---- "Tabdeelian zaya ho jayengi" dialog ----
    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes that will be lost.") },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("Stay") } },
            confirmButton = {
                TextButton(onClick = { showDiscard = false; onClose() }) {
                    Text("Discard", color = Color.Red)
                }
            },
        )
    }
}

@Composable
private fun EditRoomCard(
    room: EditRoom,
    onChanged: () -> Unit,
    onAddItem: () -> Unit,
    onDeleteRoom: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.border(1.dp, Color(0x33808080), RoundedCornerShape(16.dp)),
    ) {
        Column {
            // Room header
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF9FAFB))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = room.name,
                    onValueChange = { room.name = it; onChanged() },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                    decorationBox = { inner ->
                        Box {
                            if (room.name.isEmpty()) Text("Room Name", color = Color.Gray)
                            inner()
                        }
                    },
                )
                IconButton(onClick = onAddItem) {
                    Icon(Icons.Default.Add, contentDescription = "Add item", tint = AppGreen)
                }
                IconButton(onClick = onDeleteRoom) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete room", tint = Color.Red)
                }
            }
            HorizontalDivider()

            if (room.items.isEmpty()) {
                Text(
                    "No items added yet",
                    modifier = Modifier.padding(20.dp),
                    color = Color.Gray,
                )
            } else {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("ITEM", Modifier.weight(3f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF607D8B))
                        Spacer(Modifier.width(6.dp))
                        Text("QTY", Modifier.weight(1.2f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF607D8B))
                        Spacer(Modifier.width(6.dp))
                        Text("RATE", Modifier.weight(1.2f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF607D8B))
                        Spacer(Modifier.width(6.dp))
                        Text("TOTAL", Modifier.weight(1.4f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF607D8B), textAlign = TextAlign.End)
                        Spacer(Modifier.width(40.dp))
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    room.items.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(item.name, Modifier.weight(3f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.width(6.dp))
                            CompactNumberField(item.qty, { item.qty = it; onChanged() }, Modifier.weight(1.2f))
                            Spacer(Modifier.width(6.dp))
                            CompactNumberField(item.rate, { item.rate = it; onChanged() }, Modifier.weight(1.2f))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                formatMoney(item.total),
                                Modifier.weight(1.4f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppGreen,
                                textAlign = TextAlign.End,
                            )
                            IconButton(
                                onClick = { room.items.remove(item); onChanged() },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove item",
                                    tint = Color.Red,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
