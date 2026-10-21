package com.example.billing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billing.data.VendorRepository
import com.example.billing.model.Purchase
import com.example.billing.utils.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

private enum class PurchaseDateFilter { ALL, TODAY, WEEK, MONTH, CUSTOM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseListScreen(
    repository: VendorRepository,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    PlatformBackHandler { onBack() }
    var purchases by remember { mutableStateOf<List<Purchase>>(emptyList()) }
    var query by remember { mutableStateOf("") }

    var dateFilter by remember { mutableStateOf(PurchaseDateFilter.ALL) }
    var customDay by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { purchases = repository.listPurchases() }

    val today = nowIso().take(10)

    val filtered = remember(purchases, query, dateFilter, customDay, today) {
        purchases.filter { p ->
            val matchQuery = query.isBlank() ||
                    p.vendorName.contains(query, ignoreCase = true) ||
                    p.items.any { it.name.contains(query, ignoreCase = true) }

            val day = p.date.take(10)
            val matchDate = when (dateFilter) {
                PurchaseDateFilter.ALL -> true
                PurchaseDateFilter.TODAY -> day == today
                PurchaseDateFilter.WEEK -> {
                    val start = LocalDate.parse(today).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
                    day >= start
                }
                PurchaseDateFilter.MONTH -> day.startsWith(today.take(7))
                PurchaseDateFilter.CUSTOM -> customDay != null && day == customDay
            }
            matchQuery && matchDate
        }
    }

    Scaffold(
        containerColor = Color(0xFFF4F5F7),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

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
                        Text("Purchases", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))

                        Spacer(Modifier.weight(1f))

                        // Add Button in Header
                        Button(
                            onClick = { onOpen(0L) },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppGreen),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("New Purchase", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Filters Row
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            placeholder = { Text("Search vendor or product...", fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.width(300.dp).height(44.dp),
                        )

                        Row(
                            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(selected = dateFilter == PurchaseDateFilter.ALL, onClick = { dateFilter = PurchaseDateFilter.ALL }, label = { Text("All") })
                            FilterChip(selected = dateFilter == PurchaseDateFilter.TODAY, onClick = { dateFilter = PurchaseDateFilter.TODAY }, label = { Text("Today") })
                            FilterChip(selected = dateFilter == PurchaseDateFilter.WEEK, onClick = { dateFilter = PurchaseDateFilter.WEEK }, label = { Text("This Week") })
                            FilterChip(selected = dateFilter == PurchaseDateFilter.MONTH, onClick = { dateFilter = PurchaseDateFilter.MONTH }, label = { Text("This Month") })
                            FilterChip(
                                selected = dateFilter == PurchaseDateFilter.CUSTOM,
                                onClick = { showDatePicker = true },
                                label = { Text(if (dateFilter == PurchaseDateFilter.CUSTOM) customDay?.let { isoDayToDisplay(it) } ?: "Date" else "Calendar") },
                                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ================== LIST AREA ==================
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No purchase found.", color = Color.Gray, fontSize = 15.sp)
                    }
                } else {
                    LazyColumn(
                        Modifier.widthIn(max = 1100.dp).fillMaxSize(),
                        contentPadding = PaddingValues(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(filtered, key = { it.id }) { p ->
                            Card(
                                onClick = { onOpen(p.id) },
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(2f)) {
                                        Text(p.vendorName.ifBlank { "Vendor #${p.vendorId}" }, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E293B))
                                        Text("${p.items.size} item(s)  •  ${displayDate(p.date)}", fontSize = 12.sp, color = Color.Gray)
                                    }

                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                        Text("PAYABLE", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                        Text("Rs ${formatMoney(p.payable)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                    }

                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                        Text("PAID", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                        Text("Rs ${formatMoney(p.paid)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PaidColor)
                                    }

                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                        Text("BALANCE", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                        if (p.balance > 0.005) {
                                            Text("Rs ${formatMoney(p.balance)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DueColor)
                                        } else {
                                            Text("Settled", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PaidColor)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = isoDayToEpochMillis(customDay ?: today))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        customDay = epochMillisToIsoDay(it)
                        dateFilter = PurchaseDateFilter.CUSTOM
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}