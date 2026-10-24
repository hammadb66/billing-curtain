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
import androidx.compose.material.icons.filled.*
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
import com.example.billing.model.*
import com.example.billing.utils.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private var editIdCounter = 0L
private class EditItem(val name: String, val initialQty: String, initialRate: String, val unit: String, val productId: Long? = null, val productName: String = "", val categoryName: String = "", val subcategoryName: String = "", val initialDisplayType: String = "default") {
    var qty by mutableStateOf(initialQty); var rate by mutableStateOf(initialRate); var displayType by mutableStateOf(initialDisplayType)
    val total: Double get() = (qty.toDoubleOrNull() ?: 0.0) * (rate.toDoubleOrNull() ?: 0.0)
    val isIncomplete: Boolean get() = (qty.isNotBlank() || rate.isNotBlank()) && total <= 0.0
}
private class EditRoom(initialName: String, initialItems: List<EditItem>) {
    val id: Long = editIdCounter++; var name by mutableStateOf(initialName)
    val items = mutableStateListOf<EditItem>().apply { addAll(initialItems) }
    val total: Double get() = items.sumOf { it.total }
    fun duplicate(item: EditItem) { val i = items.indexOf(item); val copy = EditItem(item.name, "", "", item.unit, item.productId, item.productName, item.categoryName, item.subcategoryName, item.displayType); if (i >= 0) items.add(i + 1, copy) else items.add(copy) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBillScreen(bill: Bill, repository: BillRepository, onClose: () -> Unit) {
    val scope = rememberCoroutineScope(); val snackbar = remember { SnackbarHostState() }
    var customer by remember { mutableStateOf(bill.customer) }; var phone by remember { mutableStateOf(bill.phone) }
    var phone2 by remember { mutableStateOf(bill.phone2) }; var address by remember { mutableStateOf(bill.address) }; var accountNo by remember { mutableStateOf(bill.accountNo) }
    var discount by remember { mutableStateOf(bill.discount.toInputString()) }; var extraCharges by remember { mutableStateOf(bill.extraCharges.toInputString()) }
    var discountIsPercent by remember { mutableStateOf(false) }; var received by remember { mutableStateOf(bill.received.toInputString()) }
    var isDirty by remember { mutableStateOf(false) }; var showDiscard by remember { mutableStateOf(false) }
    var addItemFor by remember { mutableStateOf<EditRoom?>(null) }; var roomToDelete by remember { mutableStateOf<EditRoom?>(null) }; var isSaving by remember { mutableStateOf(false) }

    val services = koinInject<AppServices>(); var masterItems by remember { mutableStateOf<List<MasterItem>>(emptyList()) }
    var accounts by remember { mutableStateOf<List<CustomerAccount>>(emptyList()) }
    var selectedAccount by remember { mutableStateOf<CustomerAccount?>(null) }
    var showManageCustomers by remember { mutableStateOf(false) }

    var showProduct by remember { mutableStateOf(bill.showProduct) }; var showCategory by remember { mutableStateOf(bill.showCategory) }; var showSubcategory by remember { mutableStateOf(bill.showSubcategory) }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }; var productStocks by remember { mutableStateOf<Map<Long, Double>>(emptyMap()) }

    LaunchedEffect(Unit) {
        val allProds = services.vendors.listProductsWithStock(); products = allProds; productStocks = allProds.associate { it.id to (it.liveStock ?: it.openingStock) }; masterItems = services.settings.getMasterItems()
        accounts = services.vendors.listCustomerAccounts()
        selectedAccount = accounts.firstOrNull { it.id == bill.salesmanId }
    }

    val rooms = remember { mutableStateListOf<EditRoom>().apply { bill.rooms.forEach { r -> add(EditRoom(r.roomName, r.items.map { EditItem(it.name, it.qty, it.rate, it.unit, it.productId, it.productName, it.categoryName, it.subcategoryName, it.displayType) })) } } }
    val subtotal = rooms.sumOf { it.total }; val discountValue = resolveDiscount(subtotal, discount, discountIsPercent); val extraValue = extraCharges.toDoubleOrNull() ?: 0.0
    val grandTotal = (subtotal - discountValue + extraValue).coerceAtLeast(0.0); val receivedValue = received.toDoubleOrNull() ?: 0.0; val pending = (grandTotal - receivedValue).coerceAtLeast(0.0)

    fun requestBack() { if (isDirty) showDiscard = true else onClose() }
    fun save() {
        if (isSaving) return
        val incompleteItem = rooms.asSequence().flatMap { room -> room.items.map { room to it } }.firstOrNull { (_, item) -> item.isIncomplete }
        val problem = when {
            customer.isBlank() -> "Customer name is required"
            rooms.none { r -> r.items.any { it.total > 0.0 } } -> "Enter the quantity and rate for at least one item."
            incompleteItem != null -> { val s = incompleteItem.first.name.ifBlank { "All Items" }; val i = incompleteItem.second.name; val m = if (incompleteItem.second.qty.isBlank()) "Qty" else "Rate"; "\"$s\" -> \"$i\" $m is missing." }
            discountValue > subtotal -> "Discount is greater than subtotal."; receivedValue > grandTotal + 0.005 -> "Received is greater than total."; else -> null
        }
        if (problem != null) { scope.launch { snackbar.showSnackbar(problem) }; return }
        val finalRooms = rooms.map { room -> RoomData(room.name.trim(), room.items.filter { it.qty.isNotBlank() || it.rate.isNotBlank() }.map { ItemData(it.name, it.qty, it.rate, it.unit, it.productId, it.productName, it.categoryName, it.subcategoryName, it.displayType) }) }.filter { it.items.isNotEmpty() }
        scope.launch {
            isSaving = true
            try {
                repository.updateBill(bill.id, customer.trim(), phone, address, phone2, accountNo, finalRooms, discountValue, extraValue, receivedValue, selectedAccount?.id, showProduct, showCategory, showSubcategory)
                onClose()
            } catch (e: Exception) { isSaving = false; snackbar.showSnackbar("Save failed: ${e.message}") }
        }
    }
    PlatformBackHandler { requestBack() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) }, containerColor = Color(0xFFF4F5F7),
        topBar = { Surface(color = Color.White, shadowElevation = 1.dp) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { requestBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF2C3E50)) }; Spacer(Modifier.width(8.dp)); Text("Edit Bill", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50)); Text(" • ${bill.invoiceText}", fontSize = 14.sp, color = Color.Gray) } } },
        bottomBar = { Surface(color = Color.White, shadowElevation = 4.dp) { Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { Column { Text("GRAND TOTAL", fontSize = 10.sp, color = AppGreen, fontWeight = FontWeight.Bold); Text("Rs ${formatMoney(grandTotal)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A)) }; if (pending > 0.005) { Column { Text("PENDING", fontSize = 10.sp, color = DueColor, fontWeight = FontWeight.Bold); Text("Rs ${formatMoney(pending)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DueColor) } } }; Button(onClick = { save() }, enabled = !isSaving, shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = AppGreen), modifier = Modifier.height(44.dp).width(140.dp)) { if (isSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White) else Text("SAVE CHANGES", fontWeight = FontWeight.Bold, fontSize = 14.sp) } } } },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 1000.dp).fillMaxWidth().fillMaxHeight(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    SectionCard(title = "Customer Details") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CustomerSelector(
                                accounts = accounts,
                                selected = selectedAccount,
                                onSelect = {
                                    selectedAccount = it
                                    if (it != null) {
                                        customer = it.name
                                        phone = it.phone1
                                        phone2 = it.phone2
                                        address = it.address
                                        accountNo = it.accountNo
                                    }
                                    isDirty = true
                                },
                                onManageCustomers = { showManageCustomers = true },
                                onEdit = { showManageCustomers = true },
                                onDelete = { showManageCustomers = true }
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                OutlinedTextField(value = customer, onValueChange = { customer = it; isDirty = true }, label = { Text("Customer Name") }, leadingIcon = { Icon(Icons.Default.Person, null) }, singleLine = true, shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f))
                                OutlinedTextField(value = phone, onValueChange = { phone = sanitizePhone(it); isDirty = true }, label = { Text("Phone Number 1") }, leadingIcon = { Icon(Icons.Default.Phone, null) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                OutlinedTextField(value = phone2, onValueChange = { phone2 = sanitizePhone(it); isDirty = true }, label = { Text("Phone Number 2") }, leadingIcon = { Icon(Icons.Default.Phone, null) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f))
                                OutlinedTextField(value = address, onValueChange = { address = it; isDirty = true }, label = { Text("Address") }, leadingIcon = { Icon(Icons.Default.Home, null) }, singleLine = true, shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f))
                            }
                            OutlinedTextField(value = accountNo, onValueChange = { accountNo = it; isDirty = true }, label = { Text("Account No") }, leadingIcon = { Icon(Icons.Default.AccountBox, null) }, singleLine = true, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                item { SectionCard(title = "Discount, Extra & Payment") { Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { DiscountInput(discount, { discount = it; isDirty = true }, discountIsPercent, { discountIsPercent = it; discount = ""; isDirty = true }, discountValue, Modifier.weight(1f)); OutlinedTextField(extraCharges, { extraCharges = sanitizeDecimal(it); isDirty = true }, label = { Text("Extra/Misc (Rs)") }, singleLine = true, shape = RoundedCornerShape(6.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f)) }; if (discountValue > subtotal) Text("Discount is greater than subtotal.", color = Color.Red, fontSize = 12.sp); OutlinedTextField(received, { received = sanitizeDecimal(it); isDirty = true }, label = { Text("Received Amount (Rs)") }, supportingText = { Text("Blank = nothing received. Tap Full if fully paid.") }, trailingIcon = { TextButton(onClick = { received = grandTotal.toInputString(); isDirty = true }, enabled = grandTotal > 0.0, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Full", fontWeight = FontWeight.Bold) } }, singleLine = true, shape = RoundedCornerShape(6.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()) } }
                item { Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text("Sections & Items", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50)); Text("${rooms.size} section(s)", fontSize = 12.sp, color = Color.Gray) }; Button(onClick = { rooms.add(EditRoom("", emptyList())); isDirty = true }, shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50))) { Icon(Icons.Default.AddCircle, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Add Section", fontSize = 13.sp) } } }
                items(rooms, key = { it.id }) { room -> EditRoomCard(room, productStocks, { isDirty = true }, { addItemFor = room }, { roomToDelete = room }) }
                item { SectionCard(title = "Summary") { AmountBreakdown(subtotal, discountValue, extraValue, grandTotal = grandTotal, received = receivedValue) } }
            }
        }
    }
    LaunchedEffect(addItemFor) { if (addItemFor != null) { products = services.vendors.listProductsWithStock(); productStocks = products.associate { it.id to (it.liveStock ?: it.openingStock) } } }
    addItemFor?.let { room -> AddItemDialog(products, { p -> room.items.add(EditItem(p.name, "", "", p.unit, p.id, p.name, p.categoryName, p.subcategoryName)); isDirty = true; addItemFor = null }, { addItemFor = null }, { addItemFor = null }) }
    roomToDelete?.let { room -> ConfirmDialog("Delete Section", "\"${room.name.ifBlank { "All Items" }}\" and all its items will be removed. Are you sure?", "Delete", { rooms.remove(room); isDirty = true; roomToDelete = null }, { roomToDelete = null }) }
    if (showDiscard) AlertDialog(onDismissRequest = { showDiscard = false }, title = { Text("Discard changes?", fontWeight = FontWeight.Bold) }, text = { Text("You have unsaved changes that will be lost.") }, dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("Stay") } }, confirmButton = { TextButton(onClick = { showDiscard = false; onClose() }) { Text("Discard", color = Color.Red, fontWeight = FontWeight.Bold) } })

    if (showManageCustomers) {
        CustomerManageDialog(services = services, onClose = {
            showManageCustomers = false
            scope.launch {
                accounts = services.vendors.listCustomerAccounts()
                // NAYA FIX: Agar koi customer pehle se select tha, toh uski nayi detail (naya naam) utha lo
                if (selectedAccount != null) {
                    selectedAccount = accounts.firstOrNull { it.id == selectedAccount?.id }
                }
            }
        })
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFFE0E0E0)), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569)); content() } }
}

@Composable
private fun EditRoomCard(room: EditRoom, productStocks: Map<Long, Double>, onChanged: () -> Unit, onAddItem: () -> Unit, onDeleteRoom: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFFE0E0E0)), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().background(Color(0xFFF8FAFC), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(room.name, { room.name = it; onChanged() }, singleLine = true, textStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF2C3E50)), modifier = Modifier.weight(1f).padding(vertical = 14.dp), decorationBox = { inner -> Box { if (room.name.isEmpty()) Text("All Items", fontSize = 16.sp, color = Color.Gray); inner() } })
                Text("Rs ${formatMoney(room.total)}", color = AppGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                IconButton(onClick = onDeleteRoom) { Icon(Icons.Default.Delete, "Delete section", tint = Color.Red, modifier = Modifier.size(20.dp)) }
            }
            HorizontalDivider(color = Color(0xFFE0E0E0))
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (room.items.isEmpty()) Text("No items added yet", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                room.items.forEachIndexed { index, item ->
                    key(item) {
                        val occurrence = room.items.take(index + 1).count { it.name == item.name }
                        val liveStock = productStocks[item.productId] ?: 0.0; val availableStock = liveStock + (item.initialQty.toDoubleOrNull() ?: 0.0)
                        ItemEntryCard(item.name, occurrence, item.qty, { item.qty = it; onChanged() }, item.rate, { item.rate = it; onChanged() }, item.total, item.isIncomplete, item.unit, item.displayType, { item.displayType = it; onChanged() }, item.productName, item.categoryName, item.subcategoryName, availableStock, { room.duplicate(item); onChanged() }, { room.items.remove(item); onChanged() })
                    }
                }
                TextButton(onClick = onAddItem, modifier = Modifier.padding(top = 4.dp), colors = ButtonDefaults.textButtonColors(contentColor = AppGreen)) { Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add Item", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            }
        }
    }
}