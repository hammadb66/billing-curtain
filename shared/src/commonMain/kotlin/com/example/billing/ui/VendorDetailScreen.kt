package com.example.billing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.billing.model.*
import com.example.billing.pdf.PdfPrinter
import com.example.billing.pdf.PdfRow
import com.example.billing.pdf.RowKind
import com.example.billing.utils.Branding
import com.example.billing.utils.PlatformBackHandler
import com.example.billing.utils.displayDate
import com.example.billing.utils.epochMillisToIsoDay
import com.example.billing.utils.formatMoney
import com.example.billing.utils.isoDayToDisplay
import com.example.billing.utils.isoDayToEpochMillis
import com.example.billing.utils.nowIso
import com.example.billing.utils.sanitizeDecimal
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorDetailScreen(
    repository: VendorRepository,
    vendorId: Long,
    onBack: () -> Unit,
    printer: PdfPrinter
) {
    PlatformBackHandler { onBack() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var vendor by remember { mutableStateOf<Vendor?>(null) }
    var balance by remember { mutableStateOf(0.0) }
    var payments by remember { mutableStateOf<List<VendorPayment>>(emptyList()) }
    var purchases by remember { mutableStateOf<List<Purchase>>(emptyList()) }
    var showEdit by remember { mutableStateOf(false) }
    var showPayment by remember { mutableStateOf(false) }
    var editingPayment by remember { mutableStateOf<VendorPayment?>(null) }
    var paymentToDelete by remember { mutableStateOf<VendorPayment?>(null) }

    suspend fun reload() {
        vendor = repository.getVendor(vendorId)
        val vwb = repository.listVendors().firstOrNull { it.vendor.id == vendorId }
        balance = vwb?.balance ?: 0.0
        payments = repository.paymentsFor(vendorId)
        purchases = repository.listPurchases(vendorId)
    }
    LaunchedEffect(vendorId) { reload() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color(0xFFF4F5F7),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // ================== HEADER ==================
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF2C3E50)) }
                    Spacer(Modifier.width(12.dp))
                    Text(vendor?.name ?: "Vendor Details", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))

                    Spacer(Modifier.weight(1f))

                    // Actions
                    IconButton(onClick = {
                        scope.launch {
                            val rows = mutableListOf<PdfRow>()
                            rows += PdfRow(left = "VENDOR STATEMENT", bold = true, fontSize = 22f)
                            rows += PdfRow(left = "Date: ${displayDate(nowIso())}", fontSize = 12f)
                            rows += PdfRow(kind = RowKind.LINE)
                            rows += PdfRow(left = "Name: ${vendor?.name}", bold = true, fontSize = 14f)
                            rows += PdfRow(left = "Phone: ${vendor?.phone1}", fontSize = 12f)
                            if (vendor?.address?.isNotBlank() == true) rows += PdfRow(left = "Address: ${vendor?.address}", fontSize = 12f)
                            rows += PdfRow(kind = RowKind.SPACE)
                            rows += PdfRow(right = "CURRENT BALANCE: Rs ${formatMoney(balance)}", bold = true, fontSize = 16f)
                            rows += PdfRow(kind = RowKind.SPACE)

                            rows += PdfRow(left = "--- PURCHASES ---", bold = true, fontSize = 14f)
                            if (purchases.isEmpty()) rows += PdfRow(left = "No purchases found.", fontSize = 12f)
                            else purchases.forEach { pu ->
                                rows += PdfRow(left = "Date: ${displayDate(pu.date)} | Items: ${pu.items.size}", right = "Total: Rs ${formatMoney(pu.total)}", fontSize = 12f)
                                rows += PdfRow(left = "  Paid: Rs ${formatMoney(pu.paid)}", right = "Bal: Rs ${formatMoney(pu.balance)}", fontSize = 11f)
                            }
                            rows += PdfRow(kind = RowKind.SPACE)

                            rows += PdfRow(left = "--- PAYMENTS ---", bold = true, fontSize = 14f)
                            if (payments.isEmpty()) rows += PdfRow(left = "No payments found.", fontSize = 12f)
                            else payments.forEach { p ->
                                rows += PdfRow(left = "Date: ${displayDate(p.date)} | Note: ${p.note.ifBlank { "-" }}", right = "Amount: Rs ${formatMoney(p.amount)}", fontSize = 12f)
                            }

                            rows += PdfRow(kind = RowKind.LINE)
                            rows += PdfRow(left = "Software by ${Branding.COMPANY}  |  Contact: ${Branding.PHONE}", fontSize = 9f)

                            val safeName = vendor?.name?.filter { it.isLetterOrDigit() } ?: "Vendor"
                            printer.print("Vendor_Statement_$safeName", rows)
                        }
                    }) {
                        Icon(Icons.Default.Print, "Print", tint = Color.DarkGray)
                    }

                    IconButton(onClick = { showEdit = true }) { Icon(Icons.Default.Edit, "Edit", tint = Color.DarkGray) }

                    Spacer(Modifier.width(16.dp))
                    Button(
                        onClick = { editingPayment = null; showPayment = true },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppGreen),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(Icons.Default.Payments, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Payment", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ================== MAIN CONTENT ==================
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    Modifier.widthIn(max = 1000.dp).fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        InfoLine("Phone 1", vendor?.phone1.orEmpty())
                                        InfoLine("Phone 2", vendor?.phone2.orEmpty())
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        InfoLine("Address", vendor?.address.orEmpty())
                                        InfoLine("Description", vendor?.accountDescription.orEmpty())
                                    }
                                }
                                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0xFFE0E0E0))
                                val balColor = if (balance > 0.005) DueColor else if (balance < -0.005) PaidColor else Color.Gray
                                Text(
                                    if (balance > 0.005) "Payable Balance: Rs ${formatMoney(balance)}"
                                    else if (balance < -0.005) "Advance Balance: Rs ${formatMoney(-balance)}"
                                    else "Settled",
                                    color = balColor, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                                )
                            }
                        }
                    }

                    item { SectionTitle("Payments (${payments.size})") }
                    if (payments.isEmpty()) item { Text("No payments yet.", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp)) }
                    items(payments) { p ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Rs ${formatMoney(p.amount + p.discount)}", fontWeight = FontWeight.Bold, color = AppGreen, fontSize = 16.sp)
                                    if (p.discount > 0) Text("(Paid Rs ${formatMoney(p.amount)} + Disc Rs ${formatMoney(p.discount)})", fontSize = 11.sp, color = Color(0xFF1565C0), fontWeight = FontWeight.Medium)
                                    Text(displayDate(p.date), fontSize = 12.sp, color = Color.Gray)
                                }
                                if (p.note.isNotBlank()) {
                                    Text(p.note, fontSize = 13.sp, color = Color.DarkGray, modifier = Modifier.weight(1f))
                                } else Spacer(Modifier.weight(1f))

                                IconButton(onClick = { editingPayment = p; showPayment = true }) { Icon(Icons.Default.Edit, "Edit", tint = Color(0xFF1565C0), modifier = Modifier.size(20.dp)) }
                                IconButton(onClick = { paymentToDelete = p }) { Icon(Icons.Default.Delete, "Delete", tint = Color.Red, modifier = Modifier.size(20.dp)) }
                            }
                        }
                    }

                    item { SectionTitle("Purchases (${purchases.size})") }
                    if (purchases.isEmpty()) item { Text("No purchases yet.", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp)) }
                    items(purchases) { pu ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Purchase #${pu.id} • ${displayDate(pu.date)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                                    Text("Total: Rs ${formatMoney(pu.total)}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    pu.items.take(3).forEach { it ->
                                        Text("• ${it.name} — ${it.qty} × ${formatMoney(it.priceValue)}", fontSize = 12.sp, color = Color.DarkGray)
                                    }
                                    if (pu.items.size > 3) Text("+ ${pu.items.size - 3} more items...", fontSize = 11.sp, color = Color.Gray)
                                }

                                HorizontalDivider(color = Color(0xFFF1F5F9))

                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                    Text("Paid: Rs ${formatMoney(pu.paid)}", color = PaidColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    if (pu.discount > 0) Text("Discount: Rs ${formatMoney(pu.discount)}", color = Color(0xFF1565C0), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    if (pu.balance > 0.005) Text("Balance: Rs ${formatMoney(pu.balance)}", color = DueColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }

    if (showEdit && vendor != null) {
        VendorEditDialog(
            initial = vendor,
            onSave = { repository.updateVendor(it); reload() },
            onDismiss = { showEdit = false },
        )
    }

    if (showPayment) {
        PaymentDialog(
            initial = editingPayment,
            onSave = { amount, discount, date, note ->
                if (editingPayment != null) {
                    repository.updatePayment(editingPayment!!.copy(amount = amount, discount = discount, date = date, note = note))
                } else {
                    repository.addPayment(vendorId, amount, discount, date, note)
                }
                reload()
            },
            onDismiss = { showPayment = false; editingPayment = null },
        )
    }

    paymentToDelete?.let { p ->
        ConfirmDialog(
            "Delete Payment",
            "Are you sure you want to delete this payment of Rs ${formatMoney(p.amount)}?",
            "Delete",
            { scope.launch { repository.deletePayment(p.id); paymentToDelete = null; reload() } },
            { paymentToDelete = null }
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    if (value.isBlank()) return
    Row {
        Text("$label: ", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
        Text(value, fontSize = 13.sp, color = Color(0xFF1E293B))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50), modifier = Modifier.padding(top = 8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentDialog(
    initial: VendorPayment?,
    onSave: suspend (Double, Double, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var discount by remember { mutableStateOf(if ((initial?.discount ?: 0.0) > 0) initial!!.discount.toString() else "") }
    var dateIso by remember { mutableStateOf(initial?.date ?: nowIso()) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var busy by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }

    val amountVal = amount.toDoubleOrNull() ?: 0.0
    val discountVal = discount.toDoubleOrNull() ?: 0.0
    val totalCovered = amountVal + discountVal

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (initial == null) "Add Payment" else "Edit Payment", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(amount, { amount = sanitizeDecimal(it) }, label = { Text("Amount Paid (Rs)") },
                    singleLine = true, enabled = !busy, shape = RoundedCornerShape(6.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth())

                OutlinedTextField(discount, { discount = sanitizeDecimal(it) }, label = { Text("Discount (Rs) — optional") },
                    singleLine = true, enabled = !busy, shape = RoundedCornerShape(6.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth())

                if (discountVal > 0) {
                    Text("Total covered: Rs ${formatMoney(totalCovered)}", fontSize = 13.sp, color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)
                }

                OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, Color(0xFFE0E0E0))) {
                    Icon(Icons.Default.DateRange, null, Modifier.size(16.dp), tint = Color.Gray)
                    Spacer(Modifier.width(6.dp))
                    Text("Date: ${isoDayToDisplay(dateIso.take(10))}", color = Color(0xFF2C3E50))
                }
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && totalCovered > 0.0,
                onClick = {
                    scope.launch {
                        busy = true
                        onSave(amountVal, discountVal, dateIso, note)
                        busy = false; onDismiss()
                    }
                },
                shape = RoundedCornerShape(6.dp)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("Save Payment", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(8.dp),
        containerColor = Color.White
    )

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = isoDayToEpochMillis(dateIso.take(10)))
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { dateIso = epochMillisToIsoDay(it) }; showDate = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}