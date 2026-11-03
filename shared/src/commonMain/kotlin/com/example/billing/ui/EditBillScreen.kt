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

private class EditItem(
    val name: String, val initialQty: String, initialRate: String, val unit: String,
    val productId: Long? = null, val productName: String = "", val categoryName: String = "",
    val subcategoryName: String = "", val groupName: String = "", val subgroupName: String = "",
    val initialDisplayType: String = "default", initialDiscount: String = "",
    initialDiscountIsPercent: Boolean = false, val productDiscountPct: Double = 0.0,
) {
    var qty by mutableStateOf(initialQty)
    var rate by mutableStateOf(initialRate)
    var discount by mutableStateOf(initialDiscount)
    var discountIsPercent by mutableStateOf(initialDiscountIsPercent)
    var displayType by mutableStateOf(initialDisplayType)

    val grossTotal: Double get() = (qty.toDoubleOrNull() ?: 0.0) * (rate.toDoubleOrNull() ?: 0.0)
    val productDiscountAmount: Double get() = grossTotal * (productDiscountPct / 100.0)
    val netGross: Double get() = grossTotal - productDiscountAmount
    val total: Double
        get() {
            val d = discount.toDoubleOrNull() ?: 0.0
            return if (discountIsPercent) (netGross * (1.0 - d / 100.0)).coerceAtLeast(0.0) else (netGross - d).coerceAtLeast(0.0)
        }
    val isIncomplete: Boolean get() = (qty.isNotBlank() || rate.isNotBlank()) && grossTotal <= 0.0
}

private class EditRoom(initialName: String, initialItems: List<EditItem>) {
    val id: Long = editIdCounter++
    var name by mutableStateOf(initialName)
    val items = mutableStateListOf<EditItem>().apply { addAll(initialItems) }
    val total: Double get() = items.sumOf { it.total }
    fun duplicate(item: EditItem) {
        val i = items.indexOf(item)
        val copy = EditItem(item.name, "", "", item.unit, item.productId, item.productName, item.categoryName, item.subcategoryName, item.groupName, item.subgroupName, item.displayType, item.discount, item.discountIsPercent, item.productDiscountPct)
        if (i >= 0) items.add(i + 1, copy) else items.add(copy)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBillScreen(bill: Bill, repository: BillRepository, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var customer by remember { mutableStateOf(bill.customer) }
    var phone by remember { mutableStateOf(bill.phone) }
    var phone2 by remember { mutableStateOf(bill.phone2) }
    var address by remember { mutableStateOf(bill.address) }
    var accountNo by remember { mutableStateOf(bill.accountNo) }
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
    var accounts by remember { mutableStateOf<List<CustomerAccount>>(emptyList()) }
    var selectedAccount by remember { mutableStateOf<CustomerAccount?>(null) }
    var showManageCustomers by remember { mutableStateOf(false) }

    var showProduct by remember { mutableStateOf(bill.showProduct) }
    var showCategory by remember { mutableStateOf(bill.showCategory) }
    var showSubcategory by remember { mutableStateOf(bill.showSubcategory) }
    var showGroup by remember { mutableStateOf(bill.showGroup) }
    var showSubgroup by remember { mutableStateOf(bill.showSubgroup) }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var productStocks by remember { mutableStateOf<Map<Long, Double>>(emptyMap()) }

    LaunchedEffect(Unit) {
        val allProds = services.vendors.listProductsWithStock(); products = allProds; productStocks = allProds.associate { it.id to (it.liveStock ?: it.openingStock) }; masterItems = services.settings.getMasterItems()
        accounts = services.vendors.listCustomerAccounts()
        selectedAccount = accounts.firstOrNull { it.id == bill.salesmanId }
    }

    val rooms = remember {
        mutableStateListOf<EditRoom>().apply {
            bill.rooms.forEach { r -> add(EditRoom(r.roomName, r.items.map { EditItem(it.name, it.qty, it.rate, it.unit, it.productId, it.productName, it.categoryName, it.subcategoryName, it.groupName, it.subgroupName, it.displayType, it.discount, it.discountIsPercent, it.productDiscountPct) })) }
        }
    }

    fun setDisplayOption(option: String) {
        showProduct = option == "product"; showCategory = option == "category"; showSubcategory = option == "subcategory"; showGroup = option == "group"; showSubgroup = option == "subgroup"; isDirty = true
    }

    val subtotal = rooms.sumOf { it.total }
    val discountValue = resolveDiscount(subtotal, discount, discountIsPercent)
    val extraValue = extraCharges.toDoubleOrNull() ?: 0.0
    val grandTotal = (subtotal - discountValue + extraValue).coerceAtLeast(0.0)
    val receivedValue = received.toDoubleOrNull() ?: 0.0
    val pending = (grandTotal - receivedValue).coerceAtLeast(0.0)

    fun requestBack() { if (isDirty) showDiscard = true else onClose() }

    fun save() {
        if (isSaving) return
        val incompleteItem = rooms.asSequence().flatMap { room -> room.items.map { room to it } }.firstOrNull { (_, item) -> item.isIncomplete }
        val problem = when {
            customer.isBlank() -> "Customer name is required"
            rooms.none { r -> r.items.any { it.total > 0.0 } } -> "Enter the quantity and rate for at least one item."
            incompleteItem != null -> {
                val s = incompleteItem.first.name.ifBlank { "All Items" }
                val i = incompleteItem.second.name
                val m = if (incompleteItem.second.qty.isBlank()) "Qty" else "Rate"; "\"$s\" -> \"$i\" $m is missing."
            }
            discountValue > subtotal -> "Discount is greater than subtotal."
            receivedValue > grandTotal + 0.005 -> "Received is greater than total."
            else -> null
        }
        if (problem != null) { scope.launch { snackbar.showSnackbar(problem) }; return }
        val finalRooms = rooms.map { room ->
            RoomData(room.name.trim(), room.items.filter { it.qty.isNotBlank() || it.rate.isNotBlank() }.map {
                ItemData(name = it.name, qty = it.qty, discount = it.discount, discountIsPercent = it.discountIsPercent, rate = it.rate, unit = it.unit, productId = it.productId, productName = it.productName, categoryName = it.categoryName, subcategoryName = it.subcategoryName, groupName = it.groupName, subgroupName = it.subgroupName, displayType = it.displayType, productDiscountPct = it.productDiscountPct)
            })
        }.filter { it.items.isNotEmpty() }
        scope.launch {
            isSaving = true
            try {
                repository.updateBill(bill.id, customer.trim(), phone, address, phone2, accountNo, finalRooms, discountValue, extraValue, receivedValue, selectedAccount?.id, showProduct, showCategory, showSubcategory, showGroup, showSubgroup)
                onClose()
            } catch (e: Exception) { isSaving = false; snackbar.showSnackbar("Save failed: ${e.message}") }
        }
    }
    PlatformBackHandler { requestBack() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) }, containerColor = AppTheme.Bg,
        topBar = {
            Surface(color = AppTheme.Surface, shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { requestBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppTheme.TextPrimary) }
                    Spacer(Modifier.width(8.dp))
                    Text("Edit Bill", fontSize = 20.sp, fontWeight = FontWeight.Black, color = AppTheme.TextPrimary)
                    Text(" • ${bill.invoiceText}", fontSize = 14.sp, color = AppTheme.TextSecondary)
                }
            }
        },
        bottomBar = {
            Surface(color = AppTheme.Surface, shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column {
                            Text("GRAND TOTAL", fontSize = 10.sp, color = AppGreen, fontWeight = FontWeight.Bold)
                            Text("Rs ${formatMoney(grandTotal)}", fontSize = 20.sp, fontWeight = FontWeight.Black, color = AppTheme.TextPrimary)
                        }
                        if (pending > 0.005) {
                            Column {
                                Text("PENDING", fontSize = 10.sp, color = DueColor, fontWeight = FontWeight.Bold)
                                Text("Rs ${formatMoney(pending)}", fontSize = 20.sp, fontWeight = FontWeight.Black, color = DueColor)
                            }
                        }
                    }
                    Button(onClick = { save() }, enabled = !isSaving, shape = RoundedCornerShape(AppTheme.RadiusSm), colors = ButtonDefaults.buttonColors(containerColor = AppGreen), modifier = Modifier.height(44.dp).width(160.dp)) {
                        if (isSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White) else Text("SAVE CHANGES", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 1000.dp).fillMaxWidth().fillMaxHeight(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    SectionCard(title = "Customer Details", icon = Icons.Default.Person) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CustomerSelector(accounts = accounts, selected = selectedAccount, onSelect = {
                                selectedAccount = it
                                if (it != null) { customer = it.name; phone = it.phone1; phone2 = it.phone2; address = it.address; accountNo = it.accountNo }
                                isDirty = true
                            })
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                OutlinedTextField(value = customer, onValueChange = { customer = it; isDirty = true }, label = { Text("Customer Name") }, leadingIcon = { Icon(Icons.Default.Person, null, tint = AppTheme.TextMuted) }, singleLine = true, shape = RoundedCornerShape(AppTheme.RadiusSm), modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen, unfocusedBorderColor = AppTheme.Border))
                                OutlinedTextField(value = phone, onValueChange = { phone = sanitizePhone(it); isDirty = true }, label = { Text("Phone Number 1") }, leadingIcon = { Icon(Icons.Default.Phone, null, tint = AppTheme.TextMuted) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), shape = RoundedCornerShape(AppTheme.RadiusSm), modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen, unfocusedBorderColor = AppTheme.Border))
                            }
                        }
                    }
                }
                item {
                    SectionCard(title = "Discount, Extra & Payment", icon = Icons.Default.Payment) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            DiscountInput(discount, { discount = it; isDirty = true }, discountIsPercent, { discountIsPercent = it; discount = ""; isDirty = true }, discountValue, Modifier.weight(1f))
                            OutlinedTextField(extraCharges, { extraCharges = sanitizeDecimal(it); isDirty = true }, label = { Text("Extra/Misc (Rs)") }, singleLine = true, shape = RoundedCornerShape(AppTheme.RadiusSm), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen, unfocusedBorderColor = AppTheme.Border))
                        }
                        if (discountValue > subtotal) Text("Discount is greater than subtotal.", color = AppTheme.Danger, fontSize = 12.sp)
                        OutlinedTextField(received, { received = sanitizeDecimal(it); isDirty = true }, label = { Text("Received Amount (Rs)") }, supportingText = { Text("Blank = nothing received. Tap Full if fully paid.") }, trailingIcon = { TextButton(onClick = { received = grandTotal.toInputString(); isDirty = true }, enabled = grandTotal > 0.0, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Full", fontWeight = FontWeight.Bold, color = AppGreen) } }, singleLine = true, shape = RoundedCornerShape(AppTheme.RadiusSm), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen, unfocusedBorderColor = AppTheme.Border))
                    }
                }
                item {
                    SectionCard(title = "Invoice Display Options", icon = Icons.Default.Tune) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Which name should appear on the invoice for each item?", fontSize = 12.sp, color = AppTheme.TextSecondary)
                            Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = showProduct, onClick = { setDisplayOption("product") }, colors = RadioButtonDefaults.colors(selectedColor = AppGreen)); Text("Show Product Name", fontSize = 13.sp) }
                            Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = showCategory, onClick = { setDisplayOption("category") }, colors = RadioButtonDefaults.colors(selectedColor = AppGreen)); Text("Show Category", fontSize = 13.sp) }
                            Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = showSubcategory, onClick = { setDisplayOption("subcategory") }, colors = RadioButtonDefaults.colors(selectedColor = AppGreen)); Text("Show Subcategory", fontSize = 13.sp) }
                            Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = showGroup, onClick = { setDisplayOption("group") }, colors = RadioButtonDefaults.colors(selectedColor = AppGreen)); Text("Show Group", fontSize = 13.sp) }
                            Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = showSubgroup, onClick = { setDisplayOption("subgroup") }, colors = RadioButtonDefaults.colors(selectedColor = AppGreen)); Text("Show Subgroup", fontSize = 13.sp) }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Text("Sections & Items", fontSize = 18.sp, fontWeight = FontWeight.Black, color = AppTheme.TextPrimary); Text("${rooms.size} section(s)", fontSize = 12.sp, color = AppTheme.TextSecondary) }
                        Button(onClick = { rooms.add(EditRoom("", emptyList())); isDirty = true }, shape = RoundedCornerShape(AppTheme.RadiusSm), colors = ButtonDefaults.buttonColors(containerColor = AppTheme.TextPrimary)) { Icon(Icons.Default.AddCircle, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Add Section", fontSize = 13.sp) }
                    }
                }
                items(rooms, key = { it.id }) { room -> EditRoomCard(room, productStocks, { isDirty = true }, { addItemFor = room }, { roomToDelete = room }) }
                item {
                    SectionCard(title = "Summary", icon = Icons.Default.Summarize) { AmountBreakdown(subtotal, discountValue, extraValue, grandTotal = grandTotal, received = receivedValue) }
                }
            }
        }
    }
    LaunchedEffect(addItemFor) {
        if (addItemFor != null) { products = services.vendors.listProductsWithStock(); productStocks = products.associate { it.id to (it.liveStock ?: it.openingStock) } }
    }
    addItemFor?.let { room ->
        AddItemDialog(products, { p -> room.items.add(EditItem(p.name, "", "", p.unit, p.id, p.name, p.categoryName, p.subcategoryName, p.groupName, p.subgroupName, "default", "", false, p.discountPct)); isDirty = true; addItemFor = null }, { addItemFor = null }, { addItemFor = null })
    }
    roomToDelete?.let { room ->
        ConfirmDialog("Delete Section", "\"${room.name.ifBlank { "All Items" }}\" and all its items will be removed. Are you sure?", "Delete", { rooms.remove(room); isDirty = true; roomToDelete = null }, { roomToDelete = null })
    }
    if (showDiscard) AlertDialog(
        onDismissRequest = { showDiscard = false },
        title = { Text("Discard changes?", fontWeight = FontWeight.Bold) },
        text = { Text("You have unsaved changes that will be lost.", color = AppTheme.TextSecondary) },
        dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("Stay") } },
        confirmButton = { TextButton(onClick = { showDiscard = false; onClose() }) { Text("Discard", color = AppTheme.Danger, fontWeight = FontWeight.Bold) } },
        shape = RoundedCornerShape(AppTheme.RadiusLg), containerColor = AppTheme.Surface
    )
    if (showManageCustomers) {
        CustomerManageDialog(services = services, onClose = {
            showManageCustomers = false
            scope.launch { accounts = services.vendors.listCustomerAccounts(); if (selectedAccount != null) selectedAccount = accounts.firstOrNull { it.id == selectedAccount?.id } }
        })
    }
}

@Composable
private fun SectionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = AppTheme.Surface), shape = RoundedCornerShape(AppTheme.RadiusLg), border = BorderStroke(1.dp, AppTheme.Border), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PremiumSectionHeader(title, icon, AppGreen)
            HorizontalDivider(color = AppTheme.Border)
            content()
        }
    }
}

@Composable
private fun EditRoomCard(room: EditRoom, productStocks: Map<Long, Double>, onChanged: () -> Unit, onAddItem: () -> Unit, onDeleteRoom: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = AppTheme.Surface), shape = RoundedCornerShape(AppTheme.RadiusMd), border = BorderStroke(1.dp, AppTheme.Border), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().background(AppTheme.SurfaceAlt, RoundedCornerShape(topStart = AppTheme.RadiusMd, topEnd = AppTheme.RadiusMd)).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(room.name, { room.name = it; onChanged() }, singleLine = true, textStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppTheme.TextPrimary), modifier = Modifier.weight(1f).padding(vertical = 14.dp), decorationBox = { inner -> Box { if (room.name.isEmpty()) Text("All Items", fontSize = 16.sp, color = AppTheme.TextMuted); inner() } })
                Text("Rs ${formatMoney(room.total)}", color = AppGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                IconButton(onClick = onDeleteRoom) { Icon(Icons.Default.Delete, "Delete section", tint = AppTheme.Danger, modifier = Modifier.size(20.dp)) }
            }
            HorizontalDivider(color = AppTheme.Border)
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (room.items.isEmpty()) Text("No items added yet", fontSize = 13.sp, color = AppTheme.TextMuted, modifier = Modifier.padding(vertical = 8.dp))
                room.items.forEachIndexed { index, item ->
                    key(item) {
                        val occurrence = room.items.take(index + 1).count { it.name == item.name }
                        val liveStock = productStocks[item.productId] ?: 0.0
                        val availableStock = liveStock + (item.initialQty.toDoubleOrNull() ?: 0.0)
                        ItemEntryCard(name = item.name, occurrence = occurrence, qty = item.qty, discount = item.discount, discountIsPercent = item.discountIsPercent, onDiscountChange = { item.discount = it; onChanged() }, onQtyChange = { item.qty = it; onChanged() }, rate = item.rate, onRateChange = { item.rate = it; onChanged() }, total = item.total, isIncomplete = item.isIncomplete, unit = item.unit, displayType = item.displayType, onDisplayTypeChange = { item.displayType = it; onChanged() }, productName = item.productName, categoryName = item.categoryName, subcategoryName = item.subcategoryName, groupName = item.groupName, subgroupName = item.subgroupName, stock = availableStock, productDiscountPct = item.productDiscountPct, onDuplicate = { room.duplicate(item); onChanged() }, onDelete = { room.items.remove(item); onChanged() })
                    }
                }
                TextButton(onClick = onAddItem, modifier = Modifier.padding(top = 4.dp), colors = ButtonDefaults.textButtonColors(contentColor = AppGreen)) { Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add Item", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            }
        }
    }
}