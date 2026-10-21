package com.example.billing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billing.data.VendorRepository
import com.example.billing.model.*
import com.example.billing.pdf.PdfPrinter
import com.example.billing.pdf.PdfRow
import com.example.billing.pdf.RowKind
import com.example.billing.utils.PlatformBackHandler
import com.example.billing.utils.epochMillisToIsoDay
import com.example.billing.utils.formatMoney
import com.example.billing.utils.isoDayToDisplay
import com.example.billing.utils.isoDayToEpochMillis
import com.example.billing.utils.nowIso
import com.example.billing.utils.sanitizeDecimal
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseEditScreen(
    repository: VendorRepository,
    purchaseId: Long,
    onClose: () -> Unit,
    printer: PdfPrinter
) {
    PlatformBackHandler { onClose() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var vendors by remember { mutableStateOf<List<VendorWithBalance>>(emptyList()) }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var vendorId by remember { mutableStateOf<Long?>(null) }
    var dateIso by remember { mutableStateOf(nowIso()) }
    var paid by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<PurchaseItem>>(emptyList()) }
    var showDate by remember { mutableStateOf(false) }
    var showProductPicker by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(purchaseId == 0L) }
    var discount by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        vendors = repository.listVendors()
        products = repository.listProducts()
        if (purchaseId != 0L) {
            val p = repository.getPurchase(purchaseId)
            if (p != null) {
                vendorId = p.vendorId
                dateIso = p.date
                paid = p.paid.toString()
                discount = if (p.discount > 0) p.discount.toString() else ""
                notes = p.notes
                items = p.items
            }
            loaded = true
        }
    }

    val total = items.sumOf { it.total }
    val discountVal = discount.toDoubleOrNull() ?: 0.0
    val payable = (total - discountVal).coerceAtLeast(0.0)
    val paidVal = paid.toDoubleOrNull() ?: 0.0
    val balance = (payable - paidVal).coerceAtLeast(0.0)
    val selectedVendor = vendors.firstOrNull { it.vendor.id == vendorId }

    fun save() {
        if (vendorId == null || items.isEmpty()) {
            scope.launch { snackbar.showSnackbar("Vendor aur kam az kam 1 item zaroori.") }
            return
        }
        scope.launch {
            isSaving = true
            try {
                repository.savePurchase(
                    Purchase(
                        id = purchaseId, vendorId = vendorId!!, vendorName = selectedVendor?.vendor?.name ?: "",
                        date = dateIso, items = items, total = total, discount = discountVal,
                        paid = paidVal, notes = notes,
                    )
                )
                onClose()
            } catch (e: Exception) {
                snackbar.showSnackbar("Save fail: ${e.message}")
            }
            isSaving = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color(0xFFF4F5F7),
    ) { padding ->
        if (!loaded) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppGreen) }
            return@Scaffold
        }

        Column(Modifier.padding(padding).fillMaxSize()) {
            // ================== HEADER ==================
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF2C3E50)) }
                    Spacer(Modifier.width(12.dp))
                    Text(if (purchaseId == 0L) "New Purchase" else "Edit Purchase", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))

                    Spacer(Modifier.weight(1f))

                    if (purchaseId != 0L) {
                        var showDeleteConfirm by remember { mutableStateOf(false) }
                        IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red) }
                        if (showDeleteConfirm) {
                            AlertDialog(
                                onDismissRequest = { showDeleteConfirm = false },
                                title = { Text("Delete Purchase?", fontWeight = FontWeight.Bold) },
                                text = { Text("This will permanently delete this purchase.") },
                                confirmButton = { TextButton(onClick = { scope.launch { repository.deletePurchase(purchaseId); onClose() } }) { Text("Delete", color = Color.Red, fontWeight = FontWeight.Bold) } },
                                dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = {
                        scope.launch {
                            val rows = mutableListOf<PdfRow>()
                            rows += PdfRow(left = "PURCHASE INVOICE", bold = true, fontSize = 20f)
                            rows += PdfRow(left = "Vendor: ${selectedVendor?.vendor?.name}")
                            rows += PdfRow(left = "Date: ${isoDayToDisplay(dateIso.take(10))}")
                            rows += PdfRow(kind = RowKind.LINE)
                            items.forEach { i -> rows += PdfRow(left = "${i.name} (${i.qty} ${i.unit} x ${i.price})", right = formatMoney(i.total)) }
                            rows += PdfRow(kind = RowKind.LINE)
                            rows += PdfRow(right = "Items Total: Rs ${formatMoney(total)}", bold = true)
                            if (discountVal > 0) rows += PdfRow(right = "Discount: - Rs ${formatMoney(discountVal)}", bold = true)
                            rows += PdfRow(right = "Grand Total: Rs ${formatMoney(payable)}", bold = true, fontSize = 16f)
                            rows += PdfRow(right = "Paid: Rs ${formatMoney(paidVal)}", bold = true)
                            if (balance > 0) rows += PdfRow(right = "Balance: Rs ${formatMoney(balance)}", bold = true, fontSize = 16f)
                            printer.print("Purchase_${purchaseId}", rows)
                        }
                    }) { Icon(Icons.Default.Print, "Print", tint = Color.DarkGray) }
                }
            }

            // ================== MAIN CONTENT ==================
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    Modifier.widthIn(max = 900.dp).fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        SectionCard(title = "Vendor Details") {
                            VendorPicker(vendors, vendorId) { vendorId = it }
                            if (selectedVendor != null && selectedVendor.balance > 0.005) {
                                Text("Previous Balance: Rs ${formatMoney(selectedVendor.balance)}", color = DueColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { showDate = true },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFFE0E0E0))
                            ) {
                                Icon(Icons.Default.DateRange, null, Modifier.size(16.dp), tint = Color.Gray)
                                Spacer(Modifier.width(6.dp))
                                Text("Date: ${isoDayToDisplay(dateIso.take(10))}", color = Color(0xFF2C3E50))
                            }
                        }
                    }

                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Purchase Items (${items.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF2C3E50))
                            Button(
                                onClick = { showProductPicker = true },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add Product", fontSize = 13.sp)
                            }
                        }
                    }

                    items(items.size) { index ->
                        val item = items[index]
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.name, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E293B))
                                    IconButton(onClick = { items = items.toMutableList().also { it.removeAt(index) } }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Delete, "Remove", tint = Color.Red, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = item.qty,
                                        onValueChange = { v -> items = items.toMutableList().also { it[index] = item.copy(qty = sanitizeDecimal(v)) } },
                                        label = { Text("Qty (${item.unit})", fontSize = 12.sp) }, singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedTextField(
                                        value = item.price,
                                        onValueChange = { v -> items = items.toMutableList().also { it[index] = item.copy(price = sanitizeDecimal(v)) } },
                                        label = { Text("Price", fontSize = 12.sp) }, singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1f),
                                    )
                                    Column(Modifier.width(120.dp), horizontalAlignment = Alignment.End) {
                                        Text("TOTAL", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                        Text("Rs ${formatMoney(item.total)}", fontWeight = FontWeight.Bold, color = AppGreen, fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        SectionCard(title = "Payment Summary") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Items Total", fontWeight = FontWeight.Bold, color = Color.Gray)
                                Text("Rs ${formatMoney(total)}", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            }
                            OutlinedTextField(
                                value = discount, onValueChange = { discount = sanitizeDecimal(it) },
                                label = { Text("Discount (Rs)") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (discountVal > 0) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Payable", fontWeight = FontWeight.Bold, color = AppGreen)
                                    Text("Rs ${formatMoney(payable)}", fontWeight = FontWeight.Bold, color = AppGreen)
                                }
                            }
                            OutlinedTextField(
                                value = paid, onValueChange = { paid = sanitizeDecimal(it) },
                                label = { Text("Paid Amount") }, singleLine = true,
                                trailingIcon = { TextButton(onClick = { paid = payable.toString() }) { Text("Full", fontWeight = FontWeight.Bold) } },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (balance > 0.005) {
                                Text("Balance: Rs ${formatMoney(balance)}", color = DueColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            OutlinedTextField(
                                notes, { notes = it },
                                label = { Text("Notes") },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }

            // ================== FOOTER (SAVE) ==================
            Surface(color = Color.White, shadowElevation = 4.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        Column {
                            Text("GRAND TOTAL", fontSize = 10.sp, color = AppGreen, fontWeight = FontWeight.Bold)
                            Text("Rs ${formatMoney(payable)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        }
                        if (balance > 0.005) {
                            Column {
                                Text("BALANCE DUE", fontSize = 10.sp, color = DueColor, fontWeight = FontWeight.Bold)
                                Text("Rs ${formatMoney(balance)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DueColor)
                            }
                        }
                    }
                    Button(
                        onClick = { save() },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppGreen),
                        modifier = Modifier.height(44.dp).width(160.dp),
                    ) {
                        if (isSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        else Text("SAVE PURCHASE", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = isoDayToEpochMillis(dateIso.take(10)))
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { dateIso = epochMillisToIsoDay(it) }; showDate = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }

    if (showProductPicker) {
        ProductPickerForPurchase(products) { p ->
            if (p != null) {
                items = items + PurchaseItem(
                    productId = p.id, name = p.name, qty = "", price = p.price.toString(), unit = p.unit,
                )
            }
            showProductPicker = false
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VendorPicker(vendors: List<VendorWithBalance>, selectedId: Long?, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = vendors.firstOrNull { it.vendor.id == selectedId }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.vendor?.name ?: "Select Vendor",
            onValueChange = {},
            readOnly = true,
            label = { Text("Vendor *") },
            shape = RoundedCornerShape(6.dp),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            vendors.forEach { v ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(v.vendor.name, fontWeight = FontWeight.Bold)
                            if (v.balance > 0.005) Text("Bal: Rs ${formatMoney(v.balance)}", fontSize = 11.sp, color = DueColor)
                        }
                    },
                    onClick = { onSelect(v.vendor.id); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun ProductPickerForPurchase(products: List<Product>, onPick: (Product?) -> Unit) {
    var query by remember { mutableStateOf("") }
    val list = products.filter { query.isBlank() || it.name.contains(query, true) }
    AlertDialog(
        onDismissRequest = { onPick(null) },
        title = { Text("Select Product", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    query, { query = it },
                    placeholder = { Text("Search...") },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
                    singleLine = true, shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.heightIn(max = 350.dp)) {
                    items(list.size) { idx ->
                        val p = list[idx]
                        Card(
                            onClick = { onPick(p) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(p.name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                Text("Rs ${formatMoney(p.price)} / ${p.unit}", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { onPick(null) }) { Text("Cancel") } },
        shape = RoundedCornerShape(8.dp),
        containerColor = Color.White
    )
}