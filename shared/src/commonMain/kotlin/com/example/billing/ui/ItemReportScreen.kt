package com.example.billing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billing.AppServices
import com.example.billing.data.BillRepository
import com.example.billing.data.ItemReportRow
import com.example.billing.model.Product
import com.example.billing.utils.PlatformBackHandler
import com.example.billing.utils.displayDate
import com.example.billing.utils.epochMillisToIsoDay
import com.example.billing.utils.formatMoney
import com.example.billing.utils.formatQty
import com.example.billing.utils.isoDayToDisplay
import com.example.billing.utils.isoDayToEpochMillis
import com.example.billing.utils.nowIso
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemReportScreen(
    repository: BillRepository,
    onBack: () -> Unit
) {
    PlatformBackHandler { onBack() }
    val scope = rememberCoroutineScope()
    val today = nowIso().take(10)

    var fromDate by remember { mutableStateOf(today) }
    var toDate by remember { mutableStateOf(today) }

    var selectedItem by remember { mutableStateOf<String?>(null) }
    var showItemPicker by remember { mutableStateOf(false) }

    var reportData by remember { mutableStateOf<List<ItemReportRow>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    val services = koinInject<AppServices>()
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    LaunchedEffect(Unit) { products = services.vendors.listProducts() }

    fun loadReport() {
        scope.launch {
            isLoading = true
            reportData = repository.getItemReport(fromDate, toDate, selectedItem)
            isLoading = false
        }
    }

    LaunchedEffect(fromDate, toDate, selectedItem) { loadReport() }

    Scaffold(
        containerColor = Color(0xFFF4F5F7),
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val wide = maxWidth >= 950.dp

            Column(Modifier.fillMaxSize()) {
                // ================== CUSTOM DESKTOP HEADER ==================
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
                            Text("Item Usage Report", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))

                            Spacer(Modifier.weight(1f))

                            // Summary Stats (Only on wide screens)
                            if (wide && reportData.isNotEmpty()) {
                                val totalQty = reportData.sumOf { it.qty }
                                val totalAmount = reportData.sumOf { it.total }
                                Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                                    SummaryStat("TOTAL QUANTITY", formatQty(totalQty), Color(0xFF1E293B))
                                    SummaryStat("TOTAL AMOUNT", "Rs ${formatMoney(totalAmount)}", AppGreen)
                                }
                            }
                        }

                        // Filters Row
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DateSelectorButton("From Date", fromDate, Modifier.width(180.dp)) { showFromPicker = true }
                            DateSelectorButton("To Date", toDate, Modifier.width(180.dp)) { showToPicker = true }

                            Spacer(Modifier.width(8.dp))

                            OutlinedButton(
                                onClick = { showItemPicker = true },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2C3E50))
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = selectedItem ?: "All Items",
                                    modifier = Modifier.weight(1f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Select Item", tint = Color.Gray)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // ================== RESULTS AREA ==================
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    if (isLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AppGreen)
                        }
                    } else if (reportData.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.DateRange, null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                                Spacer(Modifier.height(16.dp))
                                Text("No records found.", color = Color.Gray, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    } else {
                        Column(Modifier.widthIn(max = 1100.dp).fillMaxSize()) {
                            // Mobile Summary (If wide screen is false)
                            if (!wide) {
                                val totalQty = reportData.sumOf { it.qty }
                                val totalAmount = reportData.sumOf { it.total }
                                Row(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Card(
                                        Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = BorderStroke(1.dp, Color(0xFFE0E0E0))
                                    ) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text("TOTAL QTY", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                            Text(formatQty(totalQty), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Card(
                                        Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = BorderStroke(1.dp, Color(0xFFE0E0E0))
                                    ) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text("TOTAL AMOUNT", fontSize = 10.sp, color = AppGreen, fontWeight = FontWeight.Bold)
                                            Text("Rs ${formatMoney(totalAmount)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppGreen)
                                        }
                                    }
                                }
                            }

                            // Data List
                            LazyColumn(
                                contentPadding = PaddingValues(24.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(reportData) { row ->
                                    ReportListRow(row)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ================== DIALOGS ==================
    if (showItemPicker) {
        ItemPickerDialog(
            products = products,
            selectedItem = selectedItem,
            onPick = {
                selectedItem = it
                showItemPicker = false
            },
            onDismiss = { showItemPicker = false }
        )
    }

    if (showFromPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = isoDayToEpochMillis(fromDate))
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { fromDate = epochMillisToIsoDay(it) }; showFromPicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    if (showToPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = isoDayToEpochMillis(toDate))
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { toDate = epochMillisToIsoDay(it) }; showToPicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}

// ================== HELPER COMPOSABLES ==================

@Composable
private fun SummaryStat(label: String, value: String, valueColor: Color) {
    Column {
        Text(label, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Text(value, fontSize = 18.sp, color = valueColor, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun DateSelectorButton(label: String, dateIso: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
        contentPadding = PaddingValues(horizontal = 12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2C3E50))
    ) {
        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp), tint = AppGreen)
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(label, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Text(isoDayToDisplay(dateIso), fontSize = 12.sp, color = Color(0xFF1E293B), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ReportListRow(row: ItemReportRow) {
    Card(
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Side: Item Name & Details
            Column(Modifier.weight(2f)) {
                Text(row.itemName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E293B))
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bill: ${row.billId}", fontSize = 12.sp, color = Color.Gray)
                    Text("  •  ", fontSize = 12.sp, color = Color.Gray)
                    Text("Date: ${displayDate(row.date)}", fontSize = 12.sp, color = Color.Gray)
                }
            }

            // Middle: Quantity & Rate
            Column(Modifier.weight(1.5f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("QTY", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Text("${formatQty(row.qty)} ${row.unit}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.DarkGray)
            }

            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("RATE", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Text("Rs ${formatMoney(row.rate)}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.DarkGray)
            }

            // Right Side: Total Amount
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("TOTAL", fontSize = 10.sp, color = AppGreen, fontWeight = FontWeight.Bold)
                Text("Rs ${formatMoney(row.total)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppGreen)
            }
        }
    }
}

@Composable
private fun ItemPickerDialog(
    products: List<Product>,
    selectedItem: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filteredItems = remember(query, products) {
        products.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Item", fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search item...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    singleLine = true,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen)
                )
                Spacer(Modifier.height(12.dp))

                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    if (query.isBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onPick(null) }.padding(vertical = 4.dp),
                            color = if (selectedItem == null) AppGreen.copy(alpha = 0.1f) else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "All Items",
                                modifier = Modifier.padding(12.dp),
                                fontWeight = FontWeight.Bold,
                                color = if (selectedItem == null) AppGreen else Color.Black
                            )
                        }
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                    }

                    if (filteredItems.isEmpty()) {
                        Text("No item found", color = Color.Gray, modifier = Modifier.padding(12.dp))
                    }

                    filteredItems.forEach { item ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onPick(item.name) }.padding(vertical = 2.dp),
                            color = if (selectedItem == item.name) AppGreen.copy(alpha = 0.1f) else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    item.name,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = if (selectedItem == item.name) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedItem == item.name) AppGreen else Color.Black
                                )
                                Text("(${item.unit})", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        },
        shape = RoundedCornerShape(8.dp),
        containerColor = Color.White
    )
}