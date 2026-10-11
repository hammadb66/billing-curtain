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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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

    var bills by remember { mutableStateOf<List<Bill>?>(null) }   // null = abhi load ho raha hai
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var billToDelete by remember { mutableStateOf<Bill?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    fun reload() {
        scope.launch { bills = repository.getAllBills() }
    }

    // Screen khulte hi bills load (Edit se wapas aane par bhi dobara load hota hai)
    LaunchedEffect(Unit) { bills = repository.getAllBills() }
    LaunchedEffect(searching) { if (searching) focusRequester.requestFocus() }

    PlatformBackHandler { onBack() }

    // Search: naam ya number me jo bhi milay
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
                            placeholder = { Text("Search name or number...") },
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
                        Text("Invoice History", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) query = ""
                    }) {
                        Icon(
                            if (searching) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search",
                        )
                    }
                    IconButton(onClick = { confirmClearAll = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete all", tint = Color.Red)
                    }
                },
            )
        },
        containerColor = Color(0xFFF5F7F9),
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val list = bills
            when {
                list == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                filtered.isEmpty() -> Text("No records found.", Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    // Computer par bohat chori na ho: 900dp tak
                    Modifier.widthIn(max = 900.dp).fillMaxWidth().fillMaxHeight(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filtered, key = { it.id }) { bill ->
                        BillCard(
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
                                                bill.customer,
                                                bill.phone,
                                                displayDate(bill.date),
                                                bill.rooms,
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
        ConfirmDialog(
            title = "Delete Bill",
            message = "Are you sure you want to delete this specific bill?",
            confirmText = "Delete",
            onConfirm = {
                billToDelete = null
                scope.launch { repository.deleteBill(bill.id); reload() }
            },
            onDismiss = { billToDelete = null },
        )
    }

    if (confirmClearAll) {
        ConfirmDialog(
            title = "Clear History?",
            message = "This will delete all saved bills permanently.",
            confirmText = "Clear All",
            onConfirm = {
                confirmClearAll = false
                scope.launch { repository.deleteAll(); reload() }
            },
            onDismiss = { confirmClearAll = false },
        )
    }
}

@Composable
private fun BillCard(
    bill: Bill,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onPrint: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(15.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            // ---- Upar wali patti (tap karo to khule/band) ----
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(bill.customer, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.size(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.Gray,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(bill.phone.ifBlank { "No Number" }, fontSize = 13.sp, color = Color.DarkGray)
                    }
                    Text(
                        "${displayDate(bill.date)} • Rs ${formatMoney(bill.grandTotal)}",
                        fontSize = 12.sp,
                        color = Color.Gray,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                )
            }

            if (expanded) {
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit Bill")
                    }
                    Button(
                        onClick = onPrint,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                    ) { Text("Print Bill") }
                }

                bill.rooms.forEach { room -> RoomTable(room) }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFECEFF1), RoundedCornerShape(bottomStart = 15.dp, bottomEnd = 15.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("GRAND TOTAL", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text(
                        "Rs ${formatMoney(bill.grandTotal)}",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = Color(0xFF1565C0),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomTable(room: RoomData) {
    val items = room.validItems
    if (items.isEmpty()) return

    Column(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(8.dp)),
    ) {
        Text(
            "ROOM: ${room.roomName.ifBlank { "ROOM" }.uppercase()}",
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFECEFF1), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF546E7A),
        )
        Row(Modifier.fillMaxWidth().padding(12.dp, 10.dp, 12.dp, 4.dp)) {
            Text("ITEM", Modifier.weight(4f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
            Text("QTY", Modifier.weight(2f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray, textAlign = TextAlign.Center)
            Text("TOTAL", Modifier.weight(3f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray, textAlign = TextAlign.End)
        }
        HorizontalDivider(Modifier.padding(horizontal = 12.dp))
        items.forEach { item ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(item.name, Modifier.weight(4f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(formatQty(item.qtyValue), Modifier.weight(2f), fontSize = 15.sp, textAlign = TextAlign.Center)
                Text(
                    "Rs ${formatMoney(item.total)}",
                    Modifier.weight(3f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1565C0),
                    textAlign = TextAlign.End,
                )
            }
        }
        Spacer(Modifier.size(8.dp))
    }
}
