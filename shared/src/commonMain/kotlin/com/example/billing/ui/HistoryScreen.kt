package com.example.billing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billing.data.BillRepository
import com.example.billing.model.Bill
import com.example.billing.model.RoomData
import com.example.billing.pdf.PdfPrinter
import com.example.billing.pdf.buildInvoiceRows
import com.example.billing.utils.*
import kotlinx.coroutines.launch

interface FileImporter {
    suspend fun pickJsonFile(): String?
}

private enum class DateFilter { ALL, TODAY, MONTH, CUSTOM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    repository: BillRepository,
    printer: PdfPrinter,
    onBack: () -> Unit,
    onEdit: (Bill) -> Unit,
    exporter: FileExporter? = null,
    importer: FileImporter? = null,
    isDeveloper: Boolean = false,
    syncVersion: Int = 0,
    cloudExport: (suspend () -> String?)? = null,
    onlineMode: Boolean = false,
    onRefresh: (suspend () -> String?)? = null,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current

    var bills by remember { mutableStateOf<List<Bill>?>(null) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var billToDelete by remember { mutableStateOf<Bill?>(null) }
    var billToReceive by remember { mutableStateOf<Bill?>(null) }
    var billToWhatsApp by remember { mutableStateOf<Bill?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    var dateFilter by remember { mutableStateOf(DateFilter.ALL) }
    var customDay by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var onlyDue by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    fun reload() {
        scope.launch {
            try {
                bills = repository.getAllBills()
            } catch (e: Throwable) {
                bills = emptyList()
                snackbar.showSnackbar("Load failed: ${e.message ?: e}")
            }
        }
    }

    fun runExport(fileName: String, content: String) {
        val ex = exporter
        if (ex == null) {
            scope.launch { snackbar.showSnackbar("Export setup missing: pass exporter = ... to HistoryScreen") }
            return
        }
        scope.launch {
            val where = ex.saveText(fileName, content)
            snackbar.showSnackbar(if (where != null) "Saved: $where" else "Export failed")
        }
    }

    fun runImport() {
        val imp = importer
        if (imp == null) {
            scope.launch { snackbar.showSnackbar("Couldn't setup File importer") }
            return
        }
        scope.launch {
            val text = imp.pickJsonFile()
            if (!text.isNullOrBlank()) {
                val added = repository.importFromJson(text)
                when {
                    added == null -> snackbar.showSnackbar("Import failed. Check the file format.")
                    added == 0 -> snackbar.showSnackbar("Nothing new to import (these bills already exist).")
                    else -> {
                        reload()
                        snackbar.showSnackbar("$added bills imported.")
                    }
                }
            }
        }
    }

    LaunchedEffect(syncVersion) { reload() }
    LaunchedEffect(searching) { if (searching) focusRequester.requestFocus() }

    PlatformBackHandler { onBack() }

    val todayDay = nowIso().take(10)

    val filtered = remember(bills, query, dateFilter, customDay, onlyDue, todayDay) {
        val month = todayDay.take(7)
        bills.orEmpty().filter { bill ->
            val textOk = query.isBlank() ||
                    bill.customer.contains(query, ignoreCase = true) ||
                    bill.phone.contains(query, ignoreCase = true) ||
                    bill.invoiceText.contains(query, ignoreCase = true)
            val day = bill.date.take(10)
            val dateOk = when (dateFilter) {
                DateFilter.ALL -> true
                DateFilter.TODAY -> day == todayDay
                DateFilter.MONTH -> day.startsWith(month)
                DateFilter.CUSTOM -> customDay != null && day == customDay
            }
            val dueOk = !onlyDue || bill.balance > 0.005
            textOk && dateOk && dueOk
        }
    }

    val customLabel = customDay?.let { isoDayToDisplay(it) } ?: "-"
    val periodLabel = when (dateFilter) {
        DateFilter.ALL -> "ALL"
        DateFilter.TODAY -> "TODAY"
        DateFilter.MONTH -> "THIS MONTH"
        DateFilter.CUSTOM -> customLabel
    }
    val periodFile = when (dateFilter) {
        DateFilter.ALL -> "ALL"
        DateFilter.TODAY -> todayDay
        DateFilter.MONTH -> todayDay.take(7)
        DateFilter.CUSTOM -> customDay ?: "custom"
    }
    val caption = buildString {
        append(
            when (dateFilter) {
                DateFilter.ALL -> "SHOWING: ALL BILLS"
                DateFilter.TODAY -> "SHOWING: TODAY (${isoDayToDisplay(todayDay)})"
                DateFilter.MONTH -> "SHOWING: THIS MONTH (${todayDay.drop(5).take(2)}-${todayDay.take(4)})"
                DateFilter.CUSTOM -> "SHOWING: SELECTED DATE ($customLabel)"
            }
        )
        if (onlyDue) append("  •  PENDING PAYMENT")
        if (query.isNotBlank()) append("  •  Search: \"$query\"")
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color(0xFFF4F5F7), // Desktop light gray
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val wide = maxWidth >= 950.dp

            Column(Modifier.fillMaxSize()) {
                // ================== CUSTOM DESKTOP TOP BAR ==================
                Surface(color = Color.White, shadowElevation = 1.dp) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF2C3E50))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text("Invoice History", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))

                            Spacer(Modifier.weight(1f))

                            // Stats (Only on wide screens)
                            if (wide && bills != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    SummaryStat("BILLS", filtered.size.toString(), Color(0xFF1E293B))
                                    SummaryStat("SALE", "Rs ${formatMoney(filtered.sumOf { it.grandTotal })}", AppGreen)
                                    SummaryStat("PENDING", "Rs ${formatMoney(filtered.sumOf { it.balance })}", DueColor)
                                    Spacer(Modifier.width(16.dp))
                                    VerticalDivider(Modifier.height(24.dp), color = Color.LightGray)
                                    Spacer(Modifier.width(16.dp))
                                }
                            }

                            // Search Bar
                            if (searching) {
                                CompactSearchField(
                                    value = query, onValueChange = { query = it },
                                    placeholder = "Search Name...",
                                    modifier = Modifier.width(350.dp)
                                )
//                                OutlinedTextField(
//                                    value = query,
//                                    onValueChange = { query = it },
//                                    placeholder = { Text("Search customer/invoice...", fontSize = 13.sp) },
//                                    singleLine = true,
//                                    shape = RoundedCornerShape(6.dp),
//                                    modifier = Modifier.width(250.dp).height(44.dp).focusRequester(focusRequester),
//                                )
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = { searching = false; query = "" }) {
                                    Icon(Icons.Default.Close, "Close search")
                                }
                            } else {
                                IconButton(onClick = { searching = true }) {
                                    Icon(Icons.Default.Search, "Search", tint = Color.DarkGray)
                                }
                            }

                            // Refresh Button
                            if (onRefresh != null) {
                                if (refreshing) {
                                    CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    IconButton(onClick = {
                                        scope.launch {
                                            refreshing = true
                                            val err = onRefresh()
                                            refreshing = false
                                            reload()
                                            if (err != null) snackbar.showSnackbar("Refresh fail: $err")
                                        }
                                    }) {
                                        Icon(Icons.Default.Refresh, "Refresh", tint = Color.DarkGray)
                                    }
                                }
                            }

                            // Menu (Export/Import/Delete)
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, "More", tint = Color.DarkGray)
                                }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    if (isDeveloper) {
                                        DropdownMenuItem(
                                            text = { Text("Export CSV (Visible List)") },
                                            leadingIcon = { Icon(Icons.Outlined.FileDownload, null) },
                                            onClick = {
                                                showMenu = false
                                                runExport("bills_$periodFile.csv", billsToCsv(filtered))
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("JSON Export (Backup)") },
                                            leadingIcon = { Icon(Icons.Outlined.Save, null) },
                                            onClick = {
                                                showMenu = false
                                                scope.launch {
                                                    val content = cloudExport?.invoke() ?: bills?.let { billsToBackupJson(it) }
                                                    if (content != null) runExport("billing_backup_$todayDay.json", content)
                                                }
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("JSON Import (Restore)") },
                                            leadingIcon = { Icon(Icons.Outlined.Upload, null) },
                                            onClick = { showMenu = false; runImport() },
                                        )
                                        HorizontalDivider()
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Delete All History", color = Color.Red) },
                                        leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = Color.Red) },
                                        onClick = { showMenu = false; confirmClearAll = true },
                                    )
                                }
                            }
                        }

                        // Filter Row
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(selected = dateFilter == DateFilter.ALL, onClick = { dateFilter = DateFilter.ALL }, label = { Text("All") })
                            FilterChip(selected = dateFilter == DateFilter.TODAY, onClick = { dateFilter = DateFilter.TODAY }, label = { Text("Today") })
                            FilterChip(selected = dateFilter == DateFilter.MONTH, onClick = { dateFilter = DateFilter.MONTH }, label = { Text("This Month") })
                            FilterChip(
                                selected = dateFilter == DateFilter.CUSTOM,
                                onClick = { showDatePicker = true },
                                label = { Text(if (dateFilter == DateFilter.CUSTOM) customLabel else "Calendar") },
                                leadingIcon = { Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp)) },
                            )
                            FilterChip(
                                selected = onlyDue,
                                onClick = { onlyDue = !onlyDue },
                                label = { Text("Only Pending") },
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(caption, fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // ================== MAIN LIST AREA ==================
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 1100.dp).fillMaxSize()) {
                        if (wide && bills != null) {
                            // Mobile stats hidden on wide screens (they're in the header)
                        } else if (bills != null) {
                            // Mobile Stats
                            Row(
                                Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SummaryStat("BILLS", filtered.size.toString(), Color(0xFF1E293B), Modifier.weight(1f))
                                SummaryStat("SALE", "Rs ${formatMoney(filtered.sumOf { it.grandTotal })}", AppGreen, Modifier.weight(1.4f))
                                SummaryStat("DUE", "Rs ${formatMoney(filtered.sumOf { it.balance })}", DueColor, Modifier.weight(1.2f))
                            }
                        }

                        val list = bills
                        when {
                            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = AppGreen)
                            }
                            filtered.isEmpty() -> Column(
                                Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Outlined.Receipt, null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                                Spacer(Modifier.height(16.dp))
                                Text("No invoices found", color = Color.Gray, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            }
                            else -> LazyColumn(
                                Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(24.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(filtered, key = { it.id }) { bill ->
                                    ModernBillCard(
                                        bill = bill,
                                        expanded = expandedId == bill.id,
                                        onToggle = { expandedId = if (expandedId == bill.id) null else bill.id },
                                        onDelete = { billToDelete = bill },
                                        onEdit = { onEdit(bill) },
                                        onReceive = { billToReceive = bill },
                                        onWhatsApp = { billToWhatsApp = bill },
                                        onPrint = {
                                            scope.launch {
                                                try {
                                                    printer.print(
                                                        title = "Invoice_${bill.customer.filter { it.isLetterOrDigit() || it == ' ' }}",
                                                        rows = buildInvoiceRows(
                                                            customer = bill.customer, phone = bill.phone, date = displayDate(bill.date),
                                                            rooms = bill.rooms, discount = bill.discount, extraCharges = bill.extraCharges,
                                                            invoiceNo = bill.invoiceText, paidAmount = bill.paidAmount,
                                                            showProduct = bill.showProduct, showCategory = bill.showCategory, showSubcategory = bill.showSubcategory,
                                                            showGroup = bill.showGroup,      // <-- NAYA
                                                            showSubgroup = bill.showSubgroup // <-- NAYA
                                                        ),
                                                    )
                                                } catch (e: Throwable) {
                                                    snackbar.showSnackbar("Print failed: ${e.message ?: e}")
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ================== DIALOGS & BOTTOM SHEETS ==================
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = isoDayToEpochMillis(customDay ?: todayDay))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        customDay = epochMillisToIsoDay(it)
                        dateFilter = DateFilter.CUSTOM
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }

    billToWhatsApp?.let { bill ->
        WhatsAppSendDialog(
            customerName = bill.customer,
            savedPhone = bill.phone,
            onDismiss = { billToWhatsApp = null },
            onSend = { number, saveOnBill ->
                billToWhatsApp = null
                try {
                    uriHandler.openUri(whatsAppUrlFor(number, buildBillMessage(bill)))
                } catch (e: Throwable) {
                    scope.launch { snackbar.showSnackbar("WhatsApp could not be opened.") }
                }
                if (saveOnBill) {
                    scope.launch {
                        try {
                            repository.updatePhone(bill.id, toStoredPhone(number))
                            reload()
                        } catch (e: Throwable) {
                            snackbar.showSnackbar("Could not save the number: ${e.message ?: e}")
                        }
                    }
                }
            },
        )
    }

    billToDelete?.let { bill ->
        ConfirmDialog("Delete Bill", "Do you want to delete ${bill.invoiceText} (${bill.customer})?", "Delete", {
            billToDelete = null
            scope.launch { repository.deleteBill(bill.id); reload() }
        }, { billToDelete = null })
    }

    billToReceive?.let { bill ->
        ReceivePaymentDialog(
            bill = bill,
            onConfirm = { amount ->
                billToReceive = null
                scope.launch {
                    val newPaid = (bill.received + amount).coerceAtMost(bill.grandTotal)
                    try {
                        repository.updatePaid(bill.id, newPaid)
                        reload()
                        snackbar.showSnackbar("Rs ${formatMoney(amount)} received")
                    } catch (e: Throwable) {
                        snackbar.showSnackbar("Save failed: ${e.message ?: e}")
                    }
                }
            },
            onDismiss = { billToReceive = null },
        )
    }

    if (confirmClearAll) {
        ConfirmDialog(
            "Clear History?",
            if (onlineMode) {
                "ONLINE mode: this will delete ALL bills from this laptop AND from Firebase, so they will disappear from every laptop of this shop. Take a JSON backup first."
            } else {
                "This will delete all saved bills permanently."
            },
            "Clear All", {
                confirmClearAll = false
                scope.launch { repository.deleteAll(); reload() }
            }, { confirmClearAll = false })
    }
}

// ================== HELPER COMPOSABLES ==================

@Composable
private fun SummaryStat(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Text(value, fontSize = 16.sp, color = valueColor, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun ReceivePaymentDialog(bill: Bill, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(bill.balance.toInputString()) }
    val amount = text.toDoubleOrNull() ?: 0.0
    val valid = amount > 0.0 && amount <= bill.balance + 0.005

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Receive Payment", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${bill.customer}  •  Bill ${bill.invoiceText}", fontSize = 13.sp, color = Color.Gray)
                Text("Pending: Rs ${formatMoney(bill.balance)}", color = DueColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = sanitizeDecimal(it) },
                    label = { Text("Received Amount (Rs)") },
                    singleLine = true,
                    isError = text.isNotBlank() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(amount) }, enabled = valid) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ModernBillCard(
    bill: Bill,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onPrint: () -> Unit,
    onWhatsApp: () -> Unit,
    onReceive: () -> Unit,
) {
    val pending = bill.isUnknown || bill.balance > 0.005
    val statusColor = when {
        bill.isUnknown -> Color.Gray
        pending        -> DueColor
        else           -> PaidColor
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            BoxWithConstraints {
                val isWide = maxWidth > 600.dp
                if (isWide) {
                    // Desktop Layout (Wahi purana)
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(40.dp).background(statusColor.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                            Text(bill.customer.trim().take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = statusColor)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(2f)) {
                            Text(bill.customer, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E293B), maxLines = 1)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Phone, null, modifier = Modifier.size(12.dp), tint = Color.Gray)
                                Spacer(Modifier.width(4.dp))
                                Text(bill.phone.ifBlank { "No Number" }, fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        Column(Modifier.weight(1.5f)) {
                            Text(bill.invoiceText, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.DarkGray)
                            Text(displayDate(bill.date), fontSize = 12.sp, color = Color.Gray)
                        }
                        Column(Modifier.weight(1.5f), horizontalAlignment = Alignment.Start) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (bill.isUnknown) AmountTag("—", Color.Gray)
                                else if (pending) AmountTag("Pending", DueColor)
                                else AmountTag("PAID", PaidColor)
                            }
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Rs ${formatMoney(bill.grandTotal)}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                        }
                    }
                } else {
                    // Mobile Layout (Stacked)
                    Column(
                        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).background(statusColor.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                Text(bill.customer.trim().take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = statusColor)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(bill.customer, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E293B))
                                Text(bill.phone.ifBlank { "No Number" }, fontSize = 12.sp, color = Color.Gray)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Rs ${formatMoney(bill.grandTotal)}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                            }
                        }
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text(bill.invoiceText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                Text(displayDate(bill.date), fontSize = 11.sp, color = Color.Gray)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (bill.isUnknown) AmountTag("—", Color.Gray)
                                else if (pending) AmountTag("Pending", DueColor)
                                else AmountTag("PAID", PaidColor)
                            }
                        }
                    }
                }
            }

            // Detail part (Remains same)
            if (expanded) {
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    bill.rooms.forEach { room ->
                        RoomTableCard(room, bill.showProduct, bill.showCategory, bill.showSubcategory,bill.showGroup,bill.showSubgroup)
                    }
                    Column(Modifier.fillMaxWidth().background(Color(0xFFF8FAFC), RoundedCornerShape(6.dp)).padding(16.dp)) {
                        AmountBreakdown(bill.subtotal, bill.discount, bill.extraCharges, grandTotal = bill.grandTotal, received = bill.received)                    }
                }
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (pending) Button(onClick = onReceive, shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = DueColor), modifier = Modifier.weight(1f).height(40.dp)) { Text("Receive", fontSize = 12.sp) }
                    OutlinedButton(onClick = onEdit, shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f).height(40.dp)) { Text("Edit", fontSize = 12.sp) }
                    Button(onClick = onPrint, shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)), modifier = Modifier.weight(1f).height(40.dp)) { Text("Print", fontSize = 12.sp) }
                    Button(onClick = onWhatsApp, shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)), modifier = Modifier.weight(1f).height(40.dp)) { Text("WA", fontSize = 12.sp) }
                    OutlinedButton(onClick = onDelete, shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, Color.Red), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red), modifier = Modifier.size(40.dp), contentPadding = PaddingValues(0.dp)) { Icon(Icons.Default.Delete, null, Modifier.size(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RoomTableCard(
    room: RoomData,
    showProduct: Boolean,
    showCategory: Boolean,
    showSubcategory: Boolean,
    showGroup:Boolean,
    showSubGroup:Boolean
) {
    val items = room.validItems
    if (items.isEmpty()) return

    Column(
        Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
    ) {
        // Room header
        Text(
            room.roomName.ifBlank { "ALL ITEMS" }.uppercase(),
            modifier = Modifier.fillMaxWidth().background(Color(0xFFF8FAFC), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569),
        )

        // Column headers
        Row(Modifier.fillMaxWidth().padding(12.dp, 8.dp, 12.dp, 4.dp)) {
            Text("ITEM", Modifier.weight(3.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Text("QTY", Modifier.weight(1.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, textAlign = TextAlign.Center)
            Text("RATE", Modifier.weight(2f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, textAlign = TextAlign.End)
            Text("TOTAL", Modifier.weight(2.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, textAlign = TextAlign.End)
        }

        HorizontalDivider(color = Color(0xFFF1F5F9))

        items.forEach { item ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(item.displayName(showProduct, showCategory, showSubcategory,showGroup,showSubGroup), Modifier.weight(3.5f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(formatQty(item.qtyValue), Modifier.weight(1.5f), fontSize = 13.sp, textAlign = TextAlign.Center, color = Color.DarkGray)
                Text(formatMoney(item.rateValue), Modifier.weight(2f), fontSize = 13.sp, textAlign = TextAlign.End, color = Color.DarkGray)
                Text(formatMoney(item.total), Modifier.weight(2.5f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), textAlign = TextAlign.End)
            }
        }
    }
}