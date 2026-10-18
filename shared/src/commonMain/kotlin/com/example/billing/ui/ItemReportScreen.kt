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
import com.example.billing.data.BillRepository
import com.example.billing.data.ItemReportRow
import com.example.billing.model.MasterItem
import com.example.billing.utils.PlatformBackHandler
import com.example.billing.utils.displayDate
import com.example.billing.utils.epochMillisToIsoDay
import com.example.billing.utils.formatMoney
import com.example.billing.utils.formatQty
import com.example.billing.utils.isoDayToDisplay
import com.example.billing.utils.isoDayToEpochMillis
import com.example.billing.utils.nowIso
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemReportScreen(
    repository: BillRepository,
    masterItems: List<MasterItem>,
    onBack: () -> Unit
) {
    PlatformBackHandler { onBack() }
    val scope = rememberCoroutineScope()
    val today = nowIso().take(10)

    var fromDate by remember { mutableStateOf(today) }
    var toDate by remember { mutableStateOf(today) }

    // Selected item state (null matlab All Items)
    var selectedItem by remember { mutableStateOf<String?>(null) }
    var showItemPicker by remember { mutableStateOf(false) }

    var reportData by remember { mutableStateOf<List<ItemReportRow>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    fun loadReport() {
        scope.launch {
            isLoading = true
            reportData = repository.getItemReport(fromDate, toDate, selectedItem)
            isLoading = false
        }
    }

    // Jab bhi date ya item change ho, report load karo
    LaunchedEffect(fromDate, toDate, selectedItem) { loadReport() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Item Usage Report", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF4F6F8)
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {

            // --- Filters Card ---
            Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    // Date Range
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateSelectorButton("From", fromDate, Modifier.weight(1f)) { showFromPicker = true }
                        DateSelectorButton("To", toDate, Modifier.weight(1f)) { showToPicker = true }
                    }

                    // Item Selector Button (Dropdown jesa kaam karega)
                    OutlinedButton(
                        onClick = { showItemPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1E293B))
                    ) {
                        Text(
                            text = selectedItem ?: "All Items",
                            modifier = Modifier.weight(1f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Select Item", tint = Color.Gray)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- Results ---
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppGreen)
                }
            } else if (reportData.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No records found for this period.", color = Color.Gray)
                    }
                }
            } else {
                // Premium Summary Card
                val totalQty = reportData.sumOf { it.qty }
                val totalAmount = reportData.sumOf { it.total }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AppGreen),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("TOTAL QUANTITY", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                            Text(formatQty(totalQty), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("TOTAL AMOUNT", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                            Text("Rs ${formatMoney(totalAmount)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(reportData) { row ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(1.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(row.itemName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
                                    Text("Rs ${formatMoney(row.total)}", fontWeight = FontWeight.Bold, color = AppGreen)
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Bill: ${row.billId}", fontSize = 12.sp, color = Color.Gray)
                                    Text("Date: ${displayDate(row.date)}", fontSize = 12.sp, color = Color.Gray)
                                }
                                Spacer(Modifier.height(6.dp))
                                Box(Modifier.fillMaxWidth().background(Color(0xFFF8FAFC), RoundedCornerShape(6.dp)).padding(8.dp)) {
                                    Text("Qty: ${formatQty(row.qty)} ${row.unit}  @ Rs ${formatMoney(row.rate)}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.DarkGray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Item Picker Dialog (Search ke sath) ---
    if (showItemPicker) {
        ItemPickerDialog(
            masterItems = masterItems,
            selectedItem = selectedItem,
            onPick = {
                selectedItem = it
                showItemPicker = false
            },
            onDismiss = { showItemPicker = false }
        )
    }

    // Date Pickers
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

// Helper Composable for Date Selector
@Composable
private fun DateSelectorButton(label: String, dateIso: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp), tint = AppGreen)
        Spacer(Modifier.width(4.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(label, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Text(isoDayToDisplay(dateIso), fontSize = 12.sp, color = Color(0xFF1E293B), fontWeight = FontWeight.SemiBold)
        }
    }
}

// Helper Composable for Item Picker with Search
@Composable
private fun ItemPickerDialog(
    masterItems: List<MasterItem>,
    selectedItem: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filteredItems = remember(query, masterItems) {
        masterItems.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Item for Report", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                // Search Bar inside Dialog
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search item...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen)
                )
                Spacer(Modifier.height(12.dp))

                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {

                    // "All Items" Option
                    if (query.isBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onPick(null) },
                            color = if (selectedItem == null) AppGreen.copy(alpha = 0.1f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
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

                    // Filtered List
                    if (filteredItems.isEmpty()) {
                        Text("No item found", color = Color.Gray, modifier = Modifier.padding(12.dp))
                    }

                    filteredItems.forEach { item ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onPick(item.name) },
                            color = if (selectedItem == item.name) AppGreen.copy(alpha = 0.1f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
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
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}