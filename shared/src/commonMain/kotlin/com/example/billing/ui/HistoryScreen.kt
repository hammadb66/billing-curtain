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
import com.example.billing.utils.FileExporter
import com.example.billing.utils.PlatformBackHandler
import com.example.billing.utils.billsToBackupJson
import com.example.billing.utils.billsToCsv
import com.example.billing.utils.buildBillMessage
import com.example.billing.utils.displayDate
import com.example.billing.utils.epochMillisToIsoDay
import com.example.billing.utils.formatMoney
import com.example.billing.utils.formatQty
import com.example.billing.utils.isoDayToDisplay
import com.example.billing.utils.isoDayToEpochMillis
import com.example.billing.utils.nowIso
import com.example.billing.utils.sanitizeDecimal
import com.example.billing.utils.toInputString
import com.example.billing.utils.toStoredPhone
import com.example.billing.utils.whatsAppUrlFor
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
    // true = developer login: Export / Backup / Import JSON dikhte hain.
    // false = normal user: sirf "Delete all" ka icon.
    isDeveloper: Boolean = false,
    // Online sync: Firebase se data aaye to list refresh, aur online mode me export Firebase se.
    syncVersion: Int = 0,
    cloudExport: (suspend () -> String?)? = null,
    // Online mode ON hai? (delete-all ka icon normal user se hata diya jata hai, kyunke wo sab laptops se delete karta hai)
    onlineMode: Boolean = false,
    // Refresh button: Firebase se naya data mangwata hai. null = button nahi. Wapas: error ka text ya null.
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

    // Search + Date filter + Pending filter, TEENO ek saath chalte hain
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
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    if (searching) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search customer or number...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        )
                    } else {
                        Text("Invoice History", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                },
                actions = {
                    if (onRefresh != null) {
                        if (refreshing) {
                            CircularProgressIndicator(
                                Modifier.padding(horizontal = 12.dp).size(22.dp),
                                strokeWidth = 2.dp,
                            )
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
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        }
                    }
                    IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                        Icon(if (searching) Icons.Default.Close else Icons.Default.Search, contentDescription = "Search")
                    }
                    if (isDeveloper) {
                        // Developer: poora menu (Export / Backup / Import / Delete all)
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Export CSV (Only visible list)") },
                                    onClick = {
                                        showMenu = false
                                        runExport("bills_$periodFile.csv", billsToCsv(filtered))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("JSON Export (Backup Create)") },
                                    onClick = {
                                        showMenu = false
                                        scope.launch {
                                            val content = cloudExport?.invoke() ?: bills?.let { billsToBackupJson(it) }
                                            if (content != null) runExport("billing_backup_$todayDay.json", content)
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("JSON Import (Backup Restore)") },
                                    onClick = { showMenu = false; runImport() },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Delete all History", color = Color.Red) },
                                    onClick = { showMenu = false; confirmClearAll = true },
                                )
                            }
                        }
                    } else {
                        // Normal user: sirf Delete all ka icon
                        IconButton(onClick = { confirmClearAll = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete all history", tint = Color.Red)
                        }
                    }
                },
            )
        },
        containerColor = Color(0xFFF4F6F8),
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 900.dp).fillMaxSize()) {

                if (bills != null) {
                    HistorySummary(
                        count = filtered.size,
                        sale = filtered.sumOf { it.grandTotal },
                        due = filtered.sumOf { it.balance },
                        periodLabel = periodLabel,
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(selected = dateFilter == DateFilter.ALL, onClick = { dateFilter = DateFilter.ALL }, label = { Text("ALL") })
                        FilterChip(selected = dateFilter == DateFilter.TODAY, onClick = { dateFilter = DateFilter.TODAY }, label = { Text("Today") })
                        FilterChip(selected = dateFilter == DateFilter.MONTH, onClick = { dateFilter = DateFilter.MONTH }, label = { Text("This Month") })
                        FilterChip(
                            selected = dateFilter == DateFilter.CUSTOM,
                            onClick = { showDatePicker = true },
                            label = { Text(if (dateFilter == DateFilter.CUSTOM) customLabel else "Calendar") },
                            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                        FilterChip(
                            selected = onlyDue,
                            onClick = { onlyDue = !onlyDue },
                            label = { Text("Pending Payment") },
                        )
                    }
                    Text(
                        caption,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val list = bills
                    when {
                        list == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = AppGreen)
                        filtered.isEmpty() -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("No records found.", color = Color.Gray, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                        else -> LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
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
                                                        customer = bill.customer,
                                                        phone = bill.phone,
                                                        date = displayDate(bill.date),
                                                        rooms = bill.rooms,
                                                        discount = bill.discount,
                                                        extraCharges = bill.extraCharges,
                                                        invoiceNo = bill.invoiceText,
                                                        paidAmount = bill.paidAmount,
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

    // ---------- Calendar ----------
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = isoDayToEpochMillis(customDay ?: todayDay)
        )
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
        ) {
            DatePicker(state = pickerState)
        }
    }

    // ---------- WhatsApp: kis number par bhejna hai ----------
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

    // ---------- Dialogs ----------
    billToDelete?.let { bill ->
        ConfirmDialog("Delete Bill", "Do you want to delete ${bill.invoiceText} (${bill.customer}) bill?", "Delete", {
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
                "ONLINE mode: this will delete ALL bills from this laptop AND from Firebase, so they will disappear " +
                    "from every laptop of this shop. Take a JSON backup first."
            } else {
                "This will delete all saved bills permanently."
            },
            "Clear All", {
            confirmClearAll = false
            scope.launch { repository.deleteAll(); reload() }
        }, { confirmClearAll = false })
    }
}

@Composable
private fun ReceivePaymentDialog(bill: Bill, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(bill.balance.toInputString()) }
    val amount = text.toDoubleOrNull() ?: 0.0
    val valid = amount > 0.0 && amount <= bill.balance + 0.005

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Payment Receive") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${bill.customer}  •  Bill ${bill.invoiceText}", fontSize = 13.sp, color = Color.Gray)
                Text("Pending: Rs ${formatMoney(bill.balance)}", color = DueColor, fontWeight = FontWeight.Bold)
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
        confirmButton = { TextButton(onClick = { onConfirm(amount) }, enabled = valid) { Text("Received Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun HistorySummary(count: Int, sale: Double, due: Double, periodLabel: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatCard("BILLS", count.toString(), Color(0xFF1E293B), Modifier.weight(1f))
        StatCard("SALE • $periodLabel", "Rs ${formatMoney(sale)}", AppGreen, Modifier.weight(1.4f))
        StatCard("PENDING", "Rs ${formatMoney(due)}", DueColor, Modifier.weight(1.2f))
    }
}

@Composable
private fun StatCard(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(label, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.size(2.dp))
            Text(value, fontSize = 16.sp, color = valueColor, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
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
    val pending = bill.balance > 0.005
    val statusColor = if (pending) DueColor else PaidColor

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            // ---------- Header ----------
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Gol avatar: naam ka pehla harf, rang = paid (hara) / pending (narangi)
                Box(Modifier.size(44.dp).background(statusColor.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(
                        bill.customer.trim().take(1).uppercase().ifBlank { "?" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = statusColor,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(bill.customer, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B), maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.Gray)
                        Spacer(Modifier.width(4.dp))
                        Text(bill.phone.ifBlank { "No Number" }, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                    }
                    Text("${bill.invoiceText}  •  ${displayDate(bill.date)}", fontSize = 12.sp, color = Color.Gray)

                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (pending) AmountTag("Pending Rs ${formatMoney(bill.balance)}", DueColor)
                        else AmountTag("PAID", PaidColor)
                        if (bill.discount > 0.0) AmountTag("Disc - ${formatMoney(bill.discount)}", DiscountColor)
                        if (bill.extraCharges > 0.0) AmountTag("Extra + ${formatMoney(bill.extraCharges)}", ExtraColor)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Rs ${formatMoney(bill.grandTotal)}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                    Spacer(Modifier.size(4.dp))
                    Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.Gray)
                }
            }

            // ---------- Detail ----------
            if (expanded) {
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(Modifier.size(6.dp))

                bill.rooms.forEach { room -> RoomTableCard(room) }

                Column(
                    Modifier.fillMaxWidth().padding(top = 8.dp).background(Color(0xFFF8FAFC)).padding(16.dp),
                ) {
                    AmountBreakdown(
                        subtotal = bill.subtotal,
                        discount = bill.discount,
                        extra = bill.extraCharges,
                        grandTotal = bill.grandTotal,
                        received = bill.paidAmount,
                    )
                }

                if (pending) {
                    Button(
                        onClick = onReceive,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DueColor),
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    ) {
                        Text("Payment Receive  (Pending Rs ${formatMoney(bill.balance)})", fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text("Edit", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1) }
                    Button(
                        onClick = onPrint,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier.weight(1f),
                    ) { Text("Print", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1) }
                    Button(
                        onClick = onWhatsApp,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        modifier = Modifier.weight(1.3f),
                    ) { Text("WhatsApp", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1) }
                    OutlinedButton(
                        onClick = onDelete,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color.Red),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(width = 44.dp, height = 40.dp),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete bill", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomTableCard(room: RoomData) {
    val items = room.validItems
    if (items.isEmpty()) return

    Column(
        Modifier.padding(horizontal = 14.dp, vertical = 6.dp).fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
    ) {
        Text(
            "ROOM: ${room.roomName.ifBlank { "ROOM" }.uppercase()}",
            modifier = Modifier.fillMaxWidth().background(Color(0xFFF1F5F9), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF475569),
        )
        Row(Modifier.fillMaxWidth().padding(12.dp, 8.dp, 12.dp, 4.dp)) {
            Text("ITEM", Modifier.weight(3.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Text("QTY", Modifier.weight(1.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, textAlign = TextAlign.Center)
            Text("RATE", Modifier.weight(2f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, textAlign = TextAlign.End)
            Text("TOTAL", Modifier.weight(2.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, textAlign = TextAlign.End)
        }
        HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = Color(0xFFF1F5F9))
        items.forEach { item ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(item.name, Modifier.weight(3.5f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(formatQty(item.qtyValue), Modifier.weight(1.5f), fontSize = 13.sp, textAlign = TextAlign.Center, color = Color.DarkGray)
                Text(formatMoney(item.rateValue), Modifier.weight(2f), fontSize = 13.sp, textAlign = TextAlign.End, color = Color.DarkGray)
                Text(formatMoney(item.total), Modifier.weight(2.5f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), textAlign = TextAlign.End)
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = Color(0xFFF1F5F9))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Room Total", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
            Text("Rs ${formatMoney(items.sumOf { it.total })}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppGreen)
        }
    }
}