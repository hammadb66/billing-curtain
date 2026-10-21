package com.example.billing.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import com.example.billing.utils.PlatformBackHandler
import com.example.billing.utils.displayDate
import com.example.billing.utils.formatMoney

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseListScreen(
    repository: VendorRepository,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    PlatformBackHandler { onBack() }
    var purchases by remember { mutableStateOf<List<Purchase>>(emptyList()) }
    LaunchedEffect(Unit) { purchases = repository.listPurchases() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Purchases", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpen(0L) }, containerColor = AppGreen) {
                Icon(Icons.Default.Add, null, tint = Color.White)
            }
        },
        containerColor = Color(0xFFF4F6F8),
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (purchases.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No purchase yet. Tap + to add.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    Modifier.widthIn(max = 900.dp).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(purchases, key = { it.id }) { p ->
                        Card(
                            onClick = { onOpen(p.id) },
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(p.vendorName.ifBlank { "Vendor #${p.vendorId}" }, fontWeight = FontWeight.Bold)
                                    Text(displayDate(p.date), fontSize = 12.sp, color = Color.Gray)
                                }
                                Text("${p.items.size} item(s)  •  Payable Rs ${formatMoney(p.payable)}", fontSize = 13.sp)
                                if (p.discount > 0) {
                                    Text("Discount: Rs ${formatMoney(p.discount)}", fontSize = 12.sp, color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Paid: Rs ${formatMoney(p.paid)}", fontSize = 12.sp, color = PaidColor)
                                    if (p.balance > 0.005) Text("Balance: Rs ${formatMoney(p.balance)}", fontSize = 12.sp, color = DueColor, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}