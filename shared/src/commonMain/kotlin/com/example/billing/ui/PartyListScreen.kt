package com.example.billing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billing.data.VendorRepository
import com.example.billing.model.CustomerAccount
import com.example.billing.model.Vendor
import com.example.billing.model.VendorWithBalance
import com.example.billing.utils.PlatformBackHandler
import com.example.billing.utils.formatMoney
import com.example.billing.utils.sanitizePhone
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyListScreen(
    repository: VendorRepository,
    onBack: () -> Unit,
    onOpenParty: (Long, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
    var vendors by remember { mutableStateOf<List<VendorWithBalance>>(emptyList()) }
    var customers by remember { mutableStateOf<List<CustomerAccount>>(emptyList()) }
    var customerBalances by remember { mutableStateOf<Map<Long, Double>>(emptyMap()) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddVendor by remember { mutableStateOf(false) }
    var showAddCustomer by remember { mutableStateOf(false) }

    suspend fun reload() {
        vendors = repository.listVendors()
        customers = repository.listCustomerAccounts()
        customerBalances = customers.associate { c -> c.id to repository.getCustomerBalance(c.id) }
    }
    LaunchedEffect(Unit) { reload() }

    PlatformBackHandler { onBack() }

    Scaffold(
        containerColor = Color(0xFFF4F5F7),
        topBar = {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF2C3E50))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Vendors / Customers", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = { if (selectedTab == 0) showAddVendor = true else showAddCustomer = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AppGreen),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (selectedTab == 0) "New Vendor" else "New Customer", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    TabRow(selectedTabIndex = selectedTab, containerColor = Color.White) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Vendors (${vendors.size})") })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Customers (${customers.size})") })
                    }
                    OutlinedTextField(
                        value = searchQuery, onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by name...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true, shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (selectedTab == 0) {
                VendorListContent(vendors, searchQuery, onOpenParty)
            } else {
                CustomerListContent(customers, customerBalances, searchQuery, onOpenParty)
            }
        }
    }

    if (showAddVendor) {
        VendorEditDialog(
            initial = null,
            onSave = { v ->
                repository.addVendor(v.name, v.address, v.phone1, v.phone2, v.accountDescription)
                reload()
            },
            onDismiss = { showAddVendor = false }
        )
    }
    if (showAddCustomer) {
        CustomerAccountDialog(
            initial = null,
            onSave = { acc ->
                scope.launch {
                    repository.addCustomerAccount(acc.name, acc.phone1, acc.phone2, acc.address, acc.accountNo)
                    reload()
                    showAddCustomer = false
                }
            },
            onDismiss = { showAddCustomer = false }
        )
    }
}

@Composable
private fun CustomerListContent(
    customers: List<CustomerAccount>,
    balances: Map<Long, Double>,
    query: String,
    onOpen: (Long, String) -> Unit
) {
    val filtered = customers.filter { query.isBlank() || it.name.contains(query, true) || it.phone1.contains(query) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (filtered.isEmpty()) {
            item { Text("No customers yet. Tap New Customer to add.", color = Color.Gray, fontSize = 14.sp) }
        }
        items(filtered) { c ->
            val balance = balances[c.id] ?: 0.0
            Card(
                onClick = { onOpen(c.id, "customer") },
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFE0E0E0))
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(AppGreen.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                        Text(c.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = AppGreen, fontSize = 18.sp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Phone: ${c.phone1.ifBlank { "-" }}", fontSize = 12.sp, color = Color.Gray)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            when {
                                balance > 0.005 -> "Due: Rs ${formatMoney(balance)}"
                                balance < -0.005 -> "Advance: Rs ${formatMoney(-balance)}"
                                else -> "Settled"
                            },
                            color = when {
                                balance > 0.005 -> Color(0xFFE65100)
                                balance < -0.005 -> AppGreen
                                else -> Color.Gray
                            },
                            fontWeight = FontWeight.Bold, fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VendorListContent(
    vendors: List<VendorWithBalance>,
    query: String,
    onOpen: (Long, String) -> Unit
) {
    val filtered = vendors.filter { query.isBlank() || it.vendor.name.contains(query, true) || it.vendor.phone1.contains(query) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (filtered.isEmpty()) {
            item { Text("No vendors yet. Tap New Vendor to add.", color = Color.Gray, fontSize = 14.sp) }
        }
        items(filtered) { vwb ->
            val balance = vwb.balance
            Card(
                onClick = { onOpen(vwb.vendor.id, "vendor") },
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFE0E0E0))
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(Color(0xFF1565C0).copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                        Text(vwb.vendor.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Color(0xFF1565C0), fontSize = 18.sp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(vwb.vendor.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Phone: ${vwb.vendor.phone1.ifBlank { "-" }}", fontSize = 12.sp, color = Color.Gray)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            when {
                                balance > 0.005 -> "Payable: Rs ${formatMoney(balance)}"
                                balance < -0.005 -> "Advance: Rs ${formatMoney(-balance)}"
                                else -> "Settled"
                            },
                            color = when {
                                balance > 0.005 -> Color(0xFFE65100)
                                balance < -0.005 -> AppGreen
                                else -> Color.Gray
                            },
                            fontWeight = FontWeight.Bold, fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

// ---- Reuse existing VendorEditDialog + CustomerAccountDialog (copied from original files) ----
// [Unchanged from original — keep them as is]

@Composable
fun VendorEditDialog(
    initial: Vendor?,
    onSave: suspend (Vendor) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var phone1 by remember { mutableStateOf(initial?.phone1 ?: "") }
    var phone2 by remember { mutableStateOf(initial?.phone2 ?: "") }
    var desc by remember { mutableStateOf(initial?.accountDescription ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (initial == null) "New Vendor" else "Edit Vendor", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name *", fontSize = 12.sp) }, singleLine = true, enabled = !busy, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone1, { phone1 = sanitizePhone(it) }, label = { Text("Phone 1", fontSize = 12.sp) }, singleLine = true, enabled = !busy, shape = RoundedCornerShape(6.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone2, { phone2 = sanitizePhone(it) }, label = { Text("Phone 2", fontSize = 12.sp) }, singleLine = true, enabled = !busy, shape = RoundedCornerShape(6.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(address, { address = it }, label = { Text("Address", fontSize = 12.sp) }, singleLine = true, enabled = !busy, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(desc, { desc = it }, label = { Text("Account / Description", fontSize = 12.sp) }, minLines = 2, enabled = !busy, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = Color.Red, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && name.isNotBlank(),
                onClick = {
                    scope.launch {
                        busy = true; error = null
                        try {
                            onSave(Vendor(id = initial?.id ?: 0L, name = name.trim(), address = address.trim(), phone1 = phone1.trim(), phone2 = phone2.trim(), accountDescription = desc.trim()))
                            onDismiss()
                        } catch (e: Exception) { error = e.message }
                        busy = false
                    }
                },
                shape = RoundedCornerShape(6.dp)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("Save Vendor", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(8.dp), containerColor = Color.White
    )
}