package com.example.billing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
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
import com.example.billing.AppServices
import com.example.billing.data.VendorRepository
import com.example.billing.model.Purchase
import com.example.billing.utils.*
import kotlinx.datetime.*
import org.koin.compose.koinInject

private enum class PurchaseDateFilter { ALL, TODAY, WEEK, MONTH, CUSTOM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseListScreen(repository: VendorRepository, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    PlatformBackHandler { onBack() }
    var purchases by remember { mutableStateOf<List<Purchase>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var dateFilter by remember { mutableStateOf(PurchaseDateFilter.ALL) }
    var customDay by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val dataVersion = koinInject<AppServices>().sync.dataVersion.collectAsState().value
    LaunchedEffect(Unit) { purchases = repository.listPurchases() }
    LaunchedEffect(dataVersion) { purchases = repository.listPurchases() }
    val today = nowIso().take(10)

    val filtered = remember(purchases, query, dateFilter, customDay, today) {
        purchases.filter { p ->
            val matchQuery = query.isBlank() || p.vendorName.contains(query, ignoreCase = true) || p.items.any { it.name.contains(query, ignoreCase = true) }
            val day = p.date.take(10)
            val matchDate = when (dateFilter) {
                PurchaseDateFilter.ALL -> true
                PurchaseDateFilter.TODAY -> day == today
                PurchaseDateFilter.WEEK -> { val todayDate = LocalDate.parse(today); val startOfWeek = todayDate.minus(todayDate.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY).toString(); day >= startOfWeek }
                PurchaseDateFilter.MONTH -> day.startsWith(today.take(7))
                PurchaseDateFilter.CUSTOM -> customDay != null && day == customDay
            }
            matchQuery && matchDate
        }
    }

    Scaffold(containerColor = AppTheme.Bg) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Surface(color = AppTheme.Surface, shadowElevation = 2.dp) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppTheme.TextPrimary) }
                        Spacer(Modifier.width(12.dp))
                        Text("Purchases", fontSize = 20.sp, fontWeight = FontWeight.Black, color = AppTheme.TextPrimary)
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { onOpen(0L) }, shape = RoundedCornerShape(AppTheme.RadiusSm), colors = ButtonDefaults.buttonColors(containerColor = AppGreen), modifier = Modifier.height(40.dp)) { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("New Purchase", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    }
                    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                        val isWide = maxWidth > 600.dp
                        if (isWide) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                CompactSearchField(value = query, onValueChange = { query = it }, placeholder = "Search Here...", modifier = Modifier.width(350.dp))
                                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected = dateFilter == PurchaseDateFilter.ALL, onClick = { dateFilter = PurchaseDateFilter.ALL }, label = { Text("All") })
                                    FilterChip(selected = dateFilter == PurchaseDateFilter.TODAY, onClick = { dateFilter = PurchaseDateFilter.TODAY }, label = { Text("Today") })
                                    FilterChip(selected = dateFilter == PurchaseDateFilter.WEEK, onClick = { dateFilter = PurchaseDateFilter.WEEK }, label = { Text("This Week") })
                                    FilterChip(selected = dateFilter == PurchaseDateFilter.MONTH, onClick = { dateFilter = PurchaseDateFilter.MONTH }, label = { Text("This Month") })
                                    FilterChip(selected = dateFilter == PurchaseDateFilter.CUSTOM, onClick = { showDatePicker = true }, label = { Text(if (dateFilter == PurchaseDateFilter.CUSTOM) customDay?.let { isoDayToDisplay(it) } ?: "Date" else "Calendar") }, leadingIcon = { Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp)) })
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CompactSearchField(value = query, onValueChange = { query = it }, placeholder = "Search vendor or product...", modifier = Modifier.fillMaxWidth().height(44.dp))
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected = dateFilter == PurchaseDateFilter.ALL, onClick = { dateFilter = PurchaseDateFilter.ALL }, label = { Text("All") })
                                    FilterChip(selected = dateFilter == PurchaseDateFilter.TODAY, onClick = { dateFilter = PurchaseDateFilter.TODAY }, label = { Text("Today") })
                                    FilterChip(selected = dateFilter == PurchaseDateFilter.WEEK, onClick = { dateFilter = PurchaseDateFilter.WEEK }, label = { Text("This Week") })
                                    FilterChip(selected = dateFilter == PurchaseDateFilter.MONTH, onClick = { dateFilter = PurchaseDateFilter.MONTH }, label = { Text("This Month") })
                                    FilterChip(selected = dateFilter == PurchaseDateFilter.CUSTOM, onClick = { showDatePicker = true }, label = { Text(if (dateFilter == PurchaseDateFilter.CUSTOM) customDay?.let { isoDayToDisplay(it) } ?: "Date" else "Calendar") }, leadingIcon = { Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp)) })
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No purchase found.", color = AppTheme.TextMuted, fontSize = 15.sp) }
                } else {
                    LazyColumn(Modifier.widthIn(max = 1100.dp).fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(filtered, key = { it.id }) { p ->
                            Card(onClick = { onOpen(p.id) }, colors = CardDefaults.cardColors(containerColor = AppTheme.Surface), shape = RoundedCornerShape(AppTheme.RadiusMd), border = BorderStroke(1.dp, AppTheme.Border), elevation = CardDefaults.cardElevation(0.dp), modifier = Modifier.fillMaxWidth()) {
                                BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
                                    val isWide = maxWidth > 500.dp
                                    if (isWide) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(2f)) { Text(p.vendorName.ifBlank { "Vendor #${p.vendorId}" }, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AppTheme.TextPrimary); Text("${p.items.size} item(s)  •  ${displayDate(p.date)}", fontSize = 12.sp, color = AppTheme.TextSecondary) }
                                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) { Text("PAYABLE", fontSize = 10.sp, color = AppTheme.TextSecondary, fontWeight = FontWeight.Bold); Text("Rs ${formatMoney(p.payable)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppTheme.TextPrimary) }
                                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) { Text("PAID", fontSize = 10.sp, color = AppTheme.TextSecondary, fontWeight = FontWeight.Bold); Text("Rs ${formatMoney(p.paid)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PaidColor) }
                                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) { Text("BALANCE", fontSize = 10.sp, color = AppTheme.TextSecondary, fontWeight = FontWeight.Bold); if (p.balance > 0.005) Text("Rs ${formatMoney(p.balance)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DueColor) else Text("Settled", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PaidColor) }
                                        }
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(p.vendorName.ifBlank { "Vendor #${p.vendorId}" }, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AppTheme.TextPrimary)
                                            Text("${p.items.size} item(s)  •  ${displayDate(p.date)}", fontSize = 12.sp, color = AppTheme.TextSecondary)
                                            HorizontalDivider(color = AppTheme.Border)
                                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                Column { Text("PAYABLE", fontSize = 10.sp, color = AppTheme.TextSecondary, fontWeight = FontWeight.Bold); Text("Rs ${formatMoney(p.payable)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppTheme.TextPrimary) }
                                                Column { Text("PAID", fontSize = 10.sp, color = AppTheme.TextSecondary, fontWeight = FontWeight.Bold); Text("Rs ${formatMoney(p.paid)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PaidColor) }
                                                Column(horizontalAlignment = Alignment.End) { Text("BALANCE", fontSize = 10.sp, color = AppTheme.TextSecondary, fontWeight = FontWeight.Bold); if (p.balance > 0.005) Text("Rs ${formatMoney(p.balance)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DueColor) else Text("Settled", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PaidColor) }
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
    }
    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = isoDayToEpochMillis(customDay ?: today))
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { customDay = epochMillisToIsoDay(it); dateFilter = PurchaseDateFilter.CUSTOM }; showDatePicker = false }) { Text("OK") } }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }) { DatePicker(state = state) }
    }
}