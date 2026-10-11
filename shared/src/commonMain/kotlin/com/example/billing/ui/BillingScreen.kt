package com.example.billing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.billing.data.BillRepository
import com.example.billing.model.BillingFormState
import com.example.billing.model.ItemState
import com.example.billing.model.RoomState
import com.example.billing.pdf.PdfPrinter
import com.example.billing.pdf.buildInvoiceRows
import com.example.billing.utils.displayDate
import com.example.billing.utils.formatMoney
import com.example.billing.utils.nowIso
import kotlinx.coroutines.launch

@Composable
fun BillingScreen(
    form: BillingFormState,
    repository: BillRepository,
    printer: PdfPrinter,
    onOpenHistory: () -> Unit,
    onLogout: () -> Unit,
    userName: String,
    isDeveloper: Boolean,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var message by remember { mutableStateOf<Pair<String, String>?>(null) }
    var roomToDelete by remember { mutableStateOf<RoomState?>(null) }
    var isPrinting by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    fun printAndSave() {
        if (isPrinting) return

        val problem = validateForm(form)
        if (problem != null) {
            message = problem; return
        }

        scope.launch {
            isPrinting = true
            var snackText: String? = null

            try {
                val customer = form.customer.trim()
                val phone = form.phone
                val rooms = form.toRoomDataList()
                val iso = nowIso()

                repository.insertBill(customer, phone, rooms, iso)
                form.reset()   // save ho gaya, ab form saaf

                try {
                    val printed = printer.print(
                        title = "Invoice_${customer.filter { it.isLetterOrDigit() || it == ' ' }}",
                        rows = buildInvoiceRows(customer, phone, displayDate(iso), rooms),
                    )
                    snackText = if (printed) {
                        "Bill printed and saved successfully"
                    } else {
                        "Bill saved. Print cancel hua, History se dobara print kar sakte hain."
                    }
                } catch (e: Throwable) {
                    snackText = "Bill saved, but print failed. History se dobara print karein."
                }
            } catch (e: Throwable) {
                message = "Save Error" to (e.message ?: e.toString())
            } finally {
                isPrinting = false          // spinner foran band
            }

            snackText?.let { snackbar.showSnackbar(it) }   // snackbar baad me
        }
    }

    Scaffold(
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown &&
                (event.isCtrlPressed || event.isMetaPressed) &&
                event.key == Key.R
            ) {
                if (!isPrinting) confirmReset = true
                true
            } else {
                false
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color(0xFFEEEEEE),
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val wide = maxWidth >= 840.dp

            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .width(340.dp)
                            .fillMaxHeight()
                            .background(Color.White)
                            .padding(20.dp),
                    ) {
                        Text(
                            "Billing System",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppGreen,
                        )
                        Spacer(Modifier.height(20.dp))

                        Column(
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        ) {
                            HeaderBlock(form, onOpenHistory, onLogout, userName, isDeveloper, enabled = !isPrinting)
                        }

                        ActionBlock(
                            total = form.grandTotal,
                            compact = false,
                            enabled = !isPrinting,
                            onPrint = { printAndSave() },
                            onReset = { confirmReset = true },
                        )
                    }
                    LazyColumn(
                        Modifier.weight(1f).fillMaxHeight(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        roomItems(form) { roomToDelete = it }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Text(
                                "Billing System",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppGreen,
                            )
                        }
                        item { HeaderBlock(form, onOpenHistory, onLogout, userName, isDeveloper, enabled = !isPrinting) }
                        roomItems(form) { roomToDelete = it }
                    }
                    Surface(shadowElevation = 8.dp, color = Color.White) {
                        Column(Modifier.padding(12.dp)) {
                            ActionBlock(
                                total = form.grandTotal,
                                compact = true,
                                enabled = !isPrinting,
                                onPrint = { printAndSave() },
                                onReset = { confirmReset = true },
                            )
                        }
                    }
                }
            }

            if (isPrinting) {
                Dialog(
                    onDismissRequest = {},
                    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = AppGreen)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Printing & Saving Bill...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Please wait a moment",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }

    message?.let { (title, text) -> MessageDialog(title, text) { message = null } }

    roomToDelete?.let { room ->
        ConfirmDialog(
            title = "Delete Room",
            message = "Are you sure you want to delete this room?",
            confirmText = "Confirm",
            onConfirm = { form.removeRoom(room); roomToDelete = null },
            onDismiss = { roomToDelete = null },
        )

    }
    if (confirmReset) {
        ConfirmDialog(
            title = "Reset",
            message = "Saari entries clear ho jayengi. Pakka?",
            confirmText = "Reset",
            onConfirm = { form.reset(); confirmReset = false },
            onDismiss = { confirmReset = false },
        )
    }
}

private fun LazyListScope.roomItems(form: BillingFormState, onDelete: (RoomState) -> Unit) {
    itemsIndexed(
        items = form.rooms,
        key = { _, room -> room.id }
    ) { index, room ->
        RoomCard(index, room, onDelete)
    }
}

@Composable
private fun HeaderBlock(
    form: BillingFormState,
    onOpenHistory: () -> Unit,
    onLogout: () -> Unit,
    userName: String,
    isDeveloper: Boolean,
    enabled: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = form.customer,
            onValueChange = { form.customer = it },
            label = { Text("Customer Name") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.phone,
            onValueChange = { form.phone = it.filter { c -> c.isDigit() } },
            label = { Text("Customer Number") },
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { form.addRoom() },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add New Room")
        }
        OutlinedButton(
            onClick = onOpenHistory,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("History")
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Person, contentDescription = null, tint = AppGreen)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text("Logged in", fontSize = 11.sp, color = Color.Gray)
                Text(userName, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            val accent = if (isDeveloper) AppGreen else Color.Red
            OutlinedButton(
                onClick = onLogout,
                enabled = enabled,
                border = BorderStroke(1.dp, if (enabled) accent else Color.LightGray),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(if (isDeveloper) "Admin Panel" else "Logout", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ActionBlock(
    total: Double,
    compact: Boolean,
    enabled: Boolean,
    onPrint: () -> Unit,
    onReset: () -> Unit,
) {
    if (compact) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("GRAND TOTAL", fontSize = 11.sp, color = AppGreen)
                Text("Rs ${formatMoney(total)}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onReset, enabled = enabled) { Text("Reset", color = Color.Red) }
            Button(
                onClick = onPrint,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF212121)),
            ) { Text("PRINT & SAVE", fontWeight = FontWeight.Bold) }
        }
    } else {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TotalCard(total)
            Button(
                onClick = onPrint,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF212121)),
            ) { Text("PRINT & SAVE", fontWeight = FontWeight.Bold) }
            TextButton(onClick = onReset, enabled = enabled) { Text("Reset All Fields", color = Color.Red) }
        }
    }
}

@Composable
private fun RoomCard(index: Int, room: RoomState, onDelete: (RoomState) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (index == 0) {
                    Box(
                        Modifier.padding(4.dp).size(40.dp).background(AppGreen, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Home, contentDescription = null, tint = Color.White) }
                } else {
                    IconButton(onClick = { onDelete(room) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete room", tint = Color.Red)
                    }
                }
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = room.name,
                    onValueChange = { room.name = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (room.name.isEmpty()) {
                                Text("Room Name (e.g. Bedroom)", fontSize = 18.sp, color = Color.Gray)
                            }
                            inner()
                        }
                    },
                )
                Text(
                    "Rs ${formatMoney(room.total)}",
                    color = AppGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                IconButton(onClick = { room.expanded = !room.expanded }) {
                    Icon(
                        if (room.expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                    )
                }
            }

            if (room.expanded) {
                HorizontalDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Item Name", Modifier.weight(3f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.width(6.dp))
                        Text("Qty", Modifier.weight(1.2f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.width(6.dp))
                        Text("Rate", Modifier.weight(1.2f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Total",
                            Modifier.weight(1.4f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.End,
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    room.items.forEach { item -> ItemRow(item) }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: ItemState) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.name, Modifier.weight(3f), fontSize = 14.sp)
        Spacer(Modifier.width(6.dp))
        CompactNumberField(item.qty, { item.qty = it }, Modifier.weight(1.2f))
        Spacer(Modifier.width(6.dp))
        CompactNumberField(item.rate, { item.rate = it }, Modifier.weight(1.2f))
        Spacer(Modifier.width(6.dp))
        Text(
            formatMoney(item.total),
            Modifier.weight(1.4f),
            fontSize = 14.sp,
            textAlign = TextAlign.End,
        )
    }
}

private fun validateForm(form: BillingFormState): Pair<String, String>? {
    if (form.customer.isBlank()) return "Missing Data" to "Customer name is required"
    if (form.phone.isBlank()) return "Missing Data" to "Customer Number is required"

    var hasValidItem = false
    val problems = mutableListOf<String>()

    for (room in form.rooms) {
        val roomName = room.name.ifBlank { "Room" }
        for (item in room.items) {
            // Dono khali = user ne is item ko chhoda hai, ignore karo
            if (item.qty.isBlank() && item.rate.isBlank()) continue

            val qty = item.qty.toDoubleOrNull() ?: 0.0
            val rate = item.rate.toDoubleOrNull() ?: 0.0

            if (qty > 0 && rate > 0) {
                hasValidItem = true
            } else {
                val missing = when {
                    qty <= 0 && rate <= 0 -> "Qty & Rate"
                    qty <= 0 -> "Qty"
                    else -> "Rate"
                }
                problems += "Room: $roomName → Item: ${item.name} → $missing (0 se zyada hona chahiye)"
            }
        }
    }

    if (problems.isNotEmpty()) return "Incomplete Items" to problems.joinToString("\n")
    if (!hasValidItem) return "No Data Entered" to "Please enter at least one item to print."
    return null
}