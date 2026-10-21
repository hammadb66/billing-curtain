package com.example.billing.ui

import androidx.compose.foundation.BorderStroke
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
        containerColor = Color(0xFFF4F5F7),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // ================== HEADER ==================
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF2C3E50)) }
                        Spacer(Modifier.width(12.dp))
                        Text("Vendors", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))

                        Spacer(Modifier.weight(1f))

                        Button(
                            onClick = { editing = null; showDialog = true; onAddVendor() },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppGreen),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("New Vendor", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            placeholder = { Text("Search vendor...", fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.width(350.dp).height(44.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ================== LIST ==================
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No vendor yet. Tap + New Vendor to add.", color = Color.Gray, fontSize = 15.sp)
                    }
                } else {
                    LazyColumn(
                        Modifier.widthIn(max = 1000.dp).fillMaxSize(),
                        contentPadding = PaddingValues(24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
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
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(AppGreen.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Text(v.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = AppGreen, fontSize = 18.sp)
            }
            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(2f)) {
                Text(v.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E293B))
                if (v.phone1.isNotBlank()) Text(v.phone1, fontSize = 12.sp, color = Color.Gray)
            }

            Column(Modifier.weight(1.5f), horizontalAlignment = Alignment.End) {
                Text(balLabel, fontSize = 14.sp, color = balColor, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.width(24.dp))

            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit", tint = Color(0xFF1565C0), modifier = Modifier.size(20.dp)) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = Color.Red, modifier = Modifier.size(20.dp)) }
            }
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
        title = { Text(if (initial == null) "New Vendor" else "Edit Vendor", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name *", fontSize = 12.sp) }, singleLine = true,
                    enabled = !busy, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(address, { address = it }, label = { Text("Address", fontSize = 12.sp) }, singleLine = true,
                    enabled = !busy, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone1, { phone1 = sanitizePhone(it) }, label = { Text("Phone 1", fontSize = 12.sp) },
                    singleLine = true, enabled = !busy, shape = RoundedCornerShape(6.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone2, { phone2 = sanitizePhone(it) }, label = { Text("Phone 2", fontSize = 12.sp) },
                    singleLine = true, enabled = !busy, shape = RoundedCornerShape(6.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(desc, { desc = it }, label = { Text("Account / Description", fontSize = 12.sp) },
                    minLines = 2, enabled = !busy, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth())
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
                            onSave(Vendor(
                                id = initial?.id ?: 0L, name = name.trim(), address = address.trim(),
                                phone1 = phone1.trim(), phone2 = phone2.trim(), accountDescription = desc.trim(),
                            ))
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
        shape = RoundedCornerShape(8.dp),
        containerColor = Color.White
    )
}