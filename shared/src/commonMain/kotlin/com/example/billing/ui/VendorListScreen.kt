package com.example.billing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.billing.model.Vendor
import com.example.billing.model.VendorWithBalance
import com.example.billing.utils.PlatformBackHandler
import com.example.billing.utils.formatMoney
import com.example.billing.utils.sanitizePhone
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorListScreen(
    repository: VendorRepository,
    onBack: () -> Unit,
    onOpenVendor: (Long) -> Unit,
    onAddVendor: () -> Unit,
) {
    PlatformBackHandler { onBack() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var vendors by remember { mutableStateOf<List<VendorWithBalance>>(emptyList()) }
    var editing by remember { mutableStateOf<Vendor?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    suspend fun reload() { vendors = repository.listVendors() }
    LaunchedEffect(Unit) { reload() }

    val filtered = remember(vendors, query) {
        vendors.filter { query.isBlank() || it.vendor.name.contains(query, true) || it.vendor.phone1.contains(query) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Vendors", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color(0xFF1E293B))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editing = null; showDialog = true; onAddVendor() },
                containerColor = AppGreen,
            ) { Icon(Icons.Default.Add, null, tint = Color.White) }
        },
        containerColor = Color(0xFFF4F6F8),
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 900.dp).fillMaxSize()) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Search vendor...") },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No vendor yet. Tap + to add.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filtered, key = { it.vendor.id }) { vwb ->
                            VendorRow(
                                vwb = vwb,
                                onClick = { onOpenVendor(vwb.vendor.id) },
                                onEdit = { editing = vwb.vendor; showDialog = true },
                                onDelete = {
                                    scope.launch {
                                        val purchases = repository.listPurchases(vwb.vendor.id)
                                        if (purchases.isNotEmpty()) {
                                            snackbar.showSnackbar("Cannot delete! This vendor has purchases.")
                                        } else {
                                            repository.deleteVendor(vwb.vendor.id)
                                            reload()
                                            snackbar.showSnackbar("Vendor deleted.")
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

    if (showDialog) {
        VendorEditDialog(
            initial = editing,
            onSave = { vendor ->
                if (vendor.id == 0L) {
                    repository.addVendor(vendor.name, vendor.address, vendor.phone1, vendor.phone2, vendor.accountDescription)
                } else repository.updateVendor(vendor)
                reload()
            },
            onDismiss = { showDialog = false; editing = null },
        )
    }
}

@Composable
private fun VendorRow(
    vwb: VendorWithBalance,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val v = vwb.vendor
    val balColor = if (vwb.balance > 0.005) DueColor else if (vwb.balance < -0.005) PaidColor else Color.Gray
    val balLabel = when {
        vwb.balance > 0.005 -> "Payable: Rs ${formatMoney(vwb.balance)}"
        vwb.balance < -0.005 -> "Advance: Rs ${formatMoney(-vwb.balance)}"
        else -> "Settled"
    }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(AppGreen.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Text(v.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = AppGreen, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(v.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (v.phone1.isNotBlank()) Text(v.phone1, fontSize = 12.sp, color = Color.Gray)
                Text(balLabel, fontSize = 13.sp, color = balColor, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit", tint = Color(0xFF1565C0)) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = Color.Red) }
        }
    }
}

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
        title = { Text(if (initial == null) "New Vendor" else "Edit Vendor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name *") }, singleLine = true,
                    enabled = !busy, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(address, { address = it }, label = { Text("Address") }, singleLine = true,
                    enabled = !busy, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone1, { phone1 = sanitizePhone(it) }, label = { Text("Phone 1") },
                    singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone2, { phone2 = sanitizePhone(it) }, label = { Text("Phone 2") },
                    singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(desc, { desc = it }, label = { Text("Account / Description") },
                    minLines = 2, enabled = !busy, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = Color.Red, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && name.isNotBlank(), onClick = {
                scope.launch {
                    busy = true; error = null
                    try {
                        onSave(Vendor(
                            id = initial?.id ?: 0L, name = name.trim(), address = address.trim(),
                            phone1 = phone1.trim(), phone2 = phone2.trim(), accountDescription = desc.trim(),
                        ))
                        onDismiss()
                    } catch (e: Exception) { error = e.message }
                    busy = false
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}