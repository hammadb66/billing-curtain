package com.example.billing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billing.data.BillRepository
import com.example.billing.model.Bill
import com.example.billing.model.RoomData
import com.example.billing.pdf.PdfPrinter
import com.example.billing.pdf.buildInvoiceRows
import com.example.billing.utils.PlatformBackHandler
import com.example.billing.utils.displayDate
import com.example.billing.utils.formatMoney
import com.example.billing.utils.formatQty
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    repository: BillRepository,
    printer: PdfPrinter,
    onBack: () -> Unit,
    onEdit: (Bill) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var bills by remember { mutableStateOf<List<Bill>?>(null) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var billToDelete by remember { mutableStateOf<Bill?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    fun reload() {
        scope.launch { bills = repository.getAllBills() }
    }

    LaunchedEffect(Unit) { reload() }
    LaunchedEffect(searching) { if (searching) focusRequester.requestFocus() }

    PlatformBackHandler { onBack() }

    val filtered = remember(bills, query) {
        bills.orEmpty().filter {
            query.isBlank() ||
                    it.customer.contains(query, ignoreCase = true) ||
                    it.phone.contains(query, ignoreCase = true)
        }
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
                        Text("Invoice History Dashboard", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                },
                actions = {
                    IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                        Icon(if (searching) Icons.Default.Close else Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { confirmClearAll = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete all", tint = Color.Red)
                    }
                },
            )
        },
        containerColor = Color(0xFFF4F6F8),
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val list = bills
            when {
                list == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = AppGreen)
                filtered.isEmpty() -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No records found.", color = Color.Gray, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                else -> LazyColumn(
                    Modifier.widthIn(max = 900.dp).fillMaxWidth().fillMaxHeight(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(filtered, key = { it.id }) { bill ->
                        ModernBillCard(
                            bill = bill,
                            expanded = expandedId == bill.id,
                            onToggle = { expandedId = if (expandedId == bill.id) null else bill.id },
                            onDelete = { billToDelete = bill },
                            onEdit = { onEdit(bill) },
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
                                                discount = bill.discount,       // Yahan pass hoga[cite: 26, 27]
                                                extraCharges = bill.extraCharges // Yahan pass hoga[cite: 26, 27]
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

    billToDelete?.let { bill ->
        ConfirmDialog("Delete Bill", "Are you sure you want to delete this specific bill?", "Delete", {
            billToDelete = null
            scope.launch { repository.deleteBill(bill.id); reload() }
        }, { billToDelete = null })
    }

    if (confirmClearAll) {
        ConfirmDialog("Clear History?", "This will delete all saved bills permanently.", "Clear All", {
            confirmClearAll = false
            scope.launch { repository.deleteAll(); reload() }
        }, { confirmClearAll = false })
    }
}

@Composable
private fun ModernBillCard(bill: Bill, expanded: Boolean, onToggle: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit, onPrint: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).background(AppGreen.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = AppGreen)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(bill.customer, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF1E293B))
                    Spacer(Modifier.size(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.Gray)
                        Spacer(Modifier.width(4.dp))
                        Text(bill.phone.ifBlank { "No Number" }, fontSize = 12.sp, color = Color.Gray)
                        Spacer(Modifier.width(8.dp))
                        Text("•  ${displayDate(bill.date)}", fontSize = 12.sp, color = Color.Gray)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Rs ${formatMoney(bill.grandTotal)}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppGreen)
                    Spacer(Modifier.size(4.dp))
                    Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.Gray)
                }
            }

            if (expanded) {
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = onEdit, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit Bill", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onPrint,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Print Bill", fontWeight = FontWeight.Bold)
                    }
                }

                bill.rooms.forEach { room -> RoomTableCard(room) }

                Row(
                    Modifier.fillMaxWidth().background(Color(0xFFF8FAFC), RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("GRAND TOTAL", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Gray)
                    Text("Rs ${formatMoney(bill.grandTotal)}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFF0F172A))
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
            Text("ITEM NAME", Modifier.weight(4f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Text("QTY", Modifier.weight(2f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, textAlign = TextAlign.Center)
            Text("TOTAL", Modifier.weight(3f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, textAlign = TextAlign.End)
        }
        HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = Color(0xFFF1F5F9))
        items.forEach { item ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(item.name, Modifier.weight(4f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(formatQty(item.qtyValue), Modifier.weight(2f), fontSize = 13.sp, textAlign = TextAlign.Center, color = Color.DarkGray)
                Text("Rs ${formatMoney(item.total)}", Modifier.weight(3f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), textAlign = TextAlign.End)
            }
        }
        Spacer(Modifier.size(4.dp))
    }
}