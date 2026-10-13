package com.example.billing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.billing.data.BillRepository
import com.example.billing.data.masterItems
import com.example.billing.model.BillingFormState
import com.example.billing.model.ItemState
import com.example.billing.model.RateMemory
import com.example.billing.model.RoomState
import com.example.billing.pdf.PdfPrinter
import com.example.billing.pdf.buildInvoiceRows
import com.example.billing.utils.Branding
import com.example.billing.utils.displayDate
import com.example.billing.utils.formatMoney
import com.example.billing.utils.nowIso
import com.example.billing.utils.sanitizeDecimal
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
    var addItemForRoom by remember { mutableStateOf<RoomState?>(null) }
    var isPrinting by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var previousBalance by remember { mutableStateOf(0.0) }

    LaunchedEffect(Unit) { runCatching { RateMemory.rates = repository.getLastRates() } }

    LaunchedEffect(form.phone) {
        if (form.phone.length >= 7) {
            val info = runCatching { repository.getCustomerInfo(form.phone) }.getOrNull()
            previousBalance = info?.previousBalance ?: 0.0
            if (info != null && form.customer.isBlank()) form.customer = info.name
        } else {
            previousBalance = 0.0
        }
    }

    val filledItems by remember { derivedStateOf { form.rooms.sumOf { r -> r.items.count { it.total > 0 } } } }
    val summary = "${form.rooms.size} Room(s) | $filledItems Item(s)"

    fun printAndSave() {
        if (isPrinting) return
        val problem = validateForm(form)
        if (problem != null) { message = problem; return }

        scope.launch {
            isPrinting = true
            var snackText: String? = null
            try {
                val customer = form.customer.trim()
                val phone = form.phone
                val rooms = form.toRoomDataList()
                val iso = nowIso()

                val discVal = form.discountValue
                val extraVal = form.extraValue
                val paid = form.receivedValue

                val newId = repository.insertBill(customer, phone, rooms, iso, discVal, extraVal, paid)
                form.reset()
                runCatching { RateMemory.rates = repository.getLastRates() }

                try {
                    val printed = printer.print(
                        title = "Invoice_${customer.filter { it.isLetterOrDigit() || it == ' ' }}",
                        rows = buildInvoiceRows(customer, phone, displayDate(iso), rooms, discVal, extraVal, invoiceNo = newId, paidAmount = paid),
                    )
                    snackText = if (printed) "Bill printed and saved successfully" else "Bill saved. Print cancelled."
                } catch (e: Throwable) {
                    snackText = "Bill saved, but print failed. Reprint from History."
                }
            } catch (e: Throwable) {
                message = "Save Error" to (e.message ?: e.toString())
            } finally {
                isPrinting = false
            }
            snackText?.let { snackbar.showSnackbar(it) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color(0xFFF4F6F8),
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val wide = maxWidth >= 840.dp

            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .width(380.dp)
                            .fillMaxHeight()
                            .background(Color.White)
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Billing System", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = AppGreen)
                        Text("by ${Branding.COMPANY}", fontSize = 16.sp, color = Color.Gray)

                        HeaderBlock(form, onOpenHistory, onLogout, userName, isDeveloper, enabled = !isPrinting, previousBalance = previousBalance)
                        HorizontalDivider(color = Color(0xFFEEEEEE))

                        FinancialAdjustmentsBlock(form, enabled = !isPrinting)

                        HorizontalDivider(color = Color(0xFFEEEEEE))
                        AmountBreakdown(form.subtotal, form.discountValue, form.extraValue)
                        val receivedNow = form.receivedValue
                        if (receivedNow != null && form.balance > 0.005) BalanceBanner(receivedNow, form.balance)
                        ActionBlock(
                            total = form.grandTotal,
                            summary = summary,
                            compact = false,
                            enabled = !isPrinting,
                            onPrint = { printAndSave() },
                            onReset = { confirmReset = true },
                        )
                    }
                    LazyColumn(
                        Modifier.weight(1f).fillMaxHeight(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        roomItems(form, onAddItem = { addItemForRoom = it }, onDelete = { roomToDelete = it })
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { Text("Billing System", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AppGreen) }
                        item { Text("by ${Branding.COMPANY}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AppGreen) }
                        item { HeaderBlock(form, onOpenHistory, onLogout, userName, isDeveloper, enabled = !isPrinting, previousBalance = previousBalance) }
                        item { FinancialAdjustmentsBlock(form, enabled = !isPrinting) }
                        roomItems(form, onAddItem = { addItemForRoom = it }, onDelete = { roomToDelete = it })
                    }
                    Surface(shadowElevation = 8.dp, color = Color.White) {
                        Column(Modifier.padding(12.dp)) {
                            AmountBreakdown(form.subtotal, form.discountValue, form.extraValue, Modifier.padding(bottom = 6.dp))
                            val receivedNow = form.receivedValue
                            if (receivedNow != null && form.balance > 0.005) BalanceBanner(receivedNow, form.balance, Modifier.padding(bottom = 6.dp))
                            ActionBlock(
                                total = form.grandTotal,
                                summary = summary,
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
                Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = AppGreen)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Printing & Saving Bill...", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    addItemForRoom?.let { room ->
        AlertDialog(
            onDismissRequest = { addItemForRoom = null },
            title = { Text("Add Item") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    masterItems.forEach { name ->
                        Text(
                            name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    room.addItem(name)
                                    addItemForRoom = null
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addItemForRoom = null }) { Text("Cancel") } },
        )
    }

    message?.let { (title, text) -> MessageDialog(title, text) { message = null } }
    roomToDelete?.let { room ->
        ConfirmDialog("Delete Room", "Are you sure you want to delete this room?", "Confirm", { form.removeRoom(room); roomToDelete = null }, { roomToDelete = null })
    }
    if (confirmReset) {
        ConfirmDialog("Reset", "Do you want to clear all the fields?", "Reset", { form.reset(); confirmReset = false }, { confirmReset = false })
    }
}

@Composable
private fun FinancialAdjustmentsBlock(form: BillingFormState, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Discount, Extra & Payment", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DiscountInput(
                value = form.discount,
                onValueChange = { form.discount = it },
                isPercent = form.discountIsPercent,
                onModeChange = { form.discountIsPercent = it; form.discount = "" },
                computedAmount = form.discountValue,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = form.extraCharges,
                onValueChange = { form.extraCharges = sanitizeDecimal(it) },
                label = { Text("Extra/Misc (Rs)") },
                singleLine = true,
                enabled = enabled,
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = form.received,
            onValueChange = { form.received = sanitizeDecimal(it) },
            label = { Text("Received Amount (Rs)") },
            supportingText = { Text("Blank means no amount received.") },
            singleLine = true,
            enabled = enabled,
            shape = RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun LazyListScope.roomItems(
    form: BillingFormState,
    onAddItem: (RoomState) -> Unit,
    onDelete: (RoomState) -> Unit
) {
    itemsIndexed(items = form.rooms, key = { _, room -> room.id }) { index, room ->
        RoomCard(index, room, onAddItem = { onAddItem(room) }, onDelete = onDelete)
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
    previousBalance: Double = 0.0,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = form.customer,
            onValueChange = { form.customer = it },
            label = { Text("Customer Name") },
            shape = RoundedCornerShape(10.dp),
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.phone,
            onValueChange = { form.phone = it.filter { c -> c.isDigit() } },
            label = { Text("Customer Number") },
            shape = RoundedCornerShape(10.dp),
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        if (previousBalance > 0.0) {
            Text(
                "Old Pending: Rs ${formatMoney(previousBalance)}",
                color = Color(0xFFE65100),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
        Button(onClick = { form.addRoom() }, enabled = enabled, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add New Room", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = onOpenHistory, enabled = enabled, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
            Text("History & Invoices", fontWeight = FontWeight.Bold)
        }

        HorizontalDivider(color = Color(0xFFEEEEEE))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Person, contentDescription = null, tint = AppGreen)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (isDeveloper) "Developer mode" else "Logged in",
                    fontSize = 11.sp,
                    color = if (isDeveloper) Color(0xFFE65100) else Color.Gray,
                )
                Text(userName, fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 13.sp)
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
                Text(if (isDeveloper) "Admin Panel" else "Logout", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ActionBlock(total: Double, summary: String, compact: Boolean, enabled: Boolean, onPrint: () -> Unit, onReset: () -> Unit) {
    if (compact) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("GRAND TOTAL", fontSize = 10.sp, color = AppGreen, fontWeight = FontWeight.Bold)
                Text("Rs ${formatMoney(total)}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(summary, fontSize = 9.sp, color = Color.Gray)
            }
            TextButton(onClick = onReset, enabled = enabled) { Text("Reset", color = Color.Red) }
            Button(onClick = onPrint, enabled = enabled, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF212121))) {
                Text("PRINT", fontWeight = FontWeight.Bold)
            }
        }
    } else {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TotalCard(total)
            Text(summary, fontSize = 12.sp, color = Color.Gray)
            Button(onClick = onPrint, enabled = enabled, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF212121))) {
                Text("PRINT & SAVE BILL", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            TextButton(onClick = onReset, enabled = enabled) { Text("Reset All Fields", color = Color.Red) }
        }
    }
}

@Composable
private fun RoomCard(
    index: Int,
    room: RoomState,
    onAddItem: () -> Unit,
    onDelete: (RoomState) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (index == 0) {
                    Box(Modifier.size(36.dp).background(AppGreen, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Home, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                } else {
                    IconButton(onClick = { onDelete(room) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete room", tint = Color.Red, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = room.name,
                    onValueChange = { room.name = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner -> Box { if (room.name.isEmpty()) Text("Room Name", fontSize = 16.sp, color = Color.Gray); inner() } },
                )
                Text("Rs ${formatMoney(room.total)}", color = AppGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                IconButton(onClick = { room.expanded = !room.expanded }) {
                    Icon(if (room.expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = "Expand")
                }
            }

            if (room.expanded) {
                HorizontalDivider(color = Color(0xFFEEEEEE))
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (room.items.isEmpty()) {
                        Text(
                            "No item in this room",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Item Name", Modifier.weight(3f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                            Text("Qty", Modifier.weight(1.2f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                            Text("Rate", Modifier.weight(1.2f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                            Text("Total", Modifier.weight(1.4f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.End)
                            Spacer(Modifier.width(36.dp))
                        }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color(0xFFF0F0F0))
                        room.items.forEach { item -> ItemRow(item, onDeleteItem = { room.removeItem(item) }) }
                    }

                    TextButton(onClick = onAddItem) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = AppGreen, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add item", color = AppGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: ItemState, onDeleteItem: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.name, Modifier.weight(3f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(4.dp))
        CompactNumberField(
            item.qty,
            { newQty -> item.qty = newQty },
            Modifier.weight(1.2f),
        )
        Spacer(Modifier.width(4.dp))
        CompactNumberField(item.rate, { item.rate = it }, Modifier.weight(1.2f))
        Spacer(Modifier.width(4.dp))
        Text(formatMoney(item.total), Modifier.weight(1.4f), fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
        IconButton(onClick = onDeleteItem, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Remove item", tint = Color.Red, modifier = Modifier.size(16.dp))
        }
    }
}

private fun validateForm(form: BillingFormState): Pair<String, String>? {
    if (form.customer.isBlank()) return "Missing Data" to "Customer name is required"
    if (form.phone.isBlank()) return "Missing Data" to "Customer Number is required"
    if (!form.hasAnyItem) return "Missing Data" to "Enter the quantity and rate for at least one item."

    // Yahan check karte hain aur room/item ka naam dhund kar batate hain
    for (room in form.rooms) {
        for (item in room.items) {
            if (item.isIncomplete) {
                val rName = room.name.ifBlank { "Room" }
                return "Incomplete Item" to "Room \"$rName\" -> \"${item.name}\" qty or rate is blank."
            }
        }
    }

    if (form.discountValue > form.subtotal) return "Discount Error" to "Discount is greater then subtotal"
    val receivedNow = form.receivedValue
    if (receivedNow != null && receivedNow > form.grandTotal + 0.005) return "Received Error" to "Received amount is greater then total"
    return null
}