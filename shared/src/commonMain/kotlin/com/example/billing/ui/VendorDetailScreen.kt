package com.example.billing.ui

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
        topBar = {
            TopAppBar(
                title = { Text(vendor?.name ?: "Vendor", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                actions = {

                    IconButton(onClick = {
                        scope.launch {
                            val rows = mutableListOf<PdfRow>()

                            // Header
                            rows += PdfRow(left = "VENDOR STATEMENT", bold = true, fontSize = 22f)
                            rows += PdfRow(left = "Date: ${displayDate(nowIso())}", fontSize = 12f)
                            rows += PdfRow(kind = RowKind.LINE)

                            // Vendor Info
                            rows += PdfRow(left = "Name: ${vendor?.name}", bold = true, fontSize = 14f)
                            rows += PdfRow(left = "Phone: ${vendor?.phone1}", fontSize = 12f)
                            if (vendor?.address?.isNotBlank() == true) {
                                rows += PdfRow(left = "Address: ${vendor?.address}", fontSize = 12f)
                            }
                            rows += PdfRow(kind = RowKind.SPACE)

                            // Current Balance
                            rows += PdfRow(right = "CURRENT BALANCE: Rs ${formatMoney(balance)}", bold = true, fontSize = 16f)
                            rows += PdfRow(kind = RowKind.SPACE)

                            // Purchases History
                            rows += PdfRow(left = "--- PURCHASES ---", bold = true, fontSize = 14f)
                            if (purchases.isEmpty()) {
                                rows += PdfRow(left = "No purchases found.", fontSize = 12f)
                            } else {
                                purchases.forEach { pu ->
                                    rows += PdfRow(
                                        left = "Date: ${displayDate(pu.date)} | Items: ${pu.items.size}",
                                        right = "Total: Rs ${formatMoney(pu.total)}",
                                        fontSize = 12f
                                    )
                                    val details = buildString {
                                        append("  Paid: Rs ${formatMoney(pu.paid)}")
                                        if (pu.discount > 0) append("  |  Disc: Rs ${formatMoney(pu.discount)}")
                                    }
                                    rows += PdfRow(
                                        left = details,
                                        right = "Bal: Rs ${formatMoney(pu.balance)}",
                                        fontSize = 11f
                                    )
                                }
                            }
                            rows += PdfRow(kind = RowKind.SPACE)

                            // Payments History
                            rows += PdfRow(left = "--- PAYMENTS ---", bold = true, fontSize = 14f)
                            if (payments.isEmpty()) {
                                rows += PdfRow(left = "No payments found.", fontSize = 12f)
                            } else {
                                payments.forEach { p ->
                                    rows += PdfRow(
                                        left = "Date: ${displayDate(p.date)} | Note: ${p.note.ifBlank { "-" }}",
                                        right = "Amount: Rs ${formatMoney(p.amount)}",
                                        fontSize = 12f
                                    )
                                    if (p.discount > 0) {
                                        rows += PdfRow(
                                            left = "  Discount: Rs ${formatMoney(p.discount)}",
                                            right = "Covered: Rs ${formatMoney(p.amount + p.discount)}",
                                            fontSize = 11f
                                        )
                                    }
                                }
                            }

                            // Footer
                            rows += PdfRow(kind = RowKind.LINE)
                            rows += PdfRow(left = "Software by ${Branding.COMPANY}  |  Contact: ${Branding.PHONE}", fontSize = 9f)

                            // File name safely create karein (spaces hata kar)
                            val safeName = vendor?.name?.filter { it.isLetterOrDigit() } ?: "Vendor"
                            printer.print("Vendor_Statement_$safeName", rows)
                        }
                    }) {
                        Icon(Icons.Default.Print, "Print")
                    }
                    IconButton(onClick = { showEdit = true }) { Icon(Icons.Default.Edit, "Edit") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editingPayment = null; showPayment = true },
                containerColor = AppGreen,
                content = { Icon(Icons.Default.Payments, null, tint = Color.White); Spacer(Modifier.width(8.dp)); Text("Payment", color = Color.White) }
            )
        },
        containerColor = Color(0xFFF4F6F8),
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 900.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            InfoLine("Phone 1", vendor?.phone1.orEmpty())
                            InfoLine("Phone 2", vendor?.phone2.orEmpty())
                            InfoLine("Address", vendor?.address.orEmpty())
                            InfoLine("Description", vendor?.accountDescription.orEmpty())
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            val balColor = if (balance > 0.005) DueColor else if (balance < -0.005) PaidColor else Color.Gray
                            Text(
                                if (balance > 0.005) "Payable: Rs ${formatMoney(balance)}"
                                else if (balance < -0.005) "Advance: Rs ${formatMoney(-balance)}"
                                else "Settled",
                                color = balColor, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                            )
                        }
                    }
                }

                item { SectionTitle("Payments (${payments.size})") }
                if (payments.isEmpty()) item { Text("No payments yet.", color = Color.Gray, fontSize = 13.sp) }
                items(payments) { p ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {

                            Column(Modifier.weight(1f)) {
                                // Total covered (amount + discount) prominently
                                Text(
                                    "Rs ${formatMoney(p.amount + p.discount)}",
                                    fontWeight = FontWeight.Bold,
                                    color = AppGreen,
                                )
                                // Breakdown agar discount hai
                                if (p.discount > 0) {
                                    Text(
                                        "(Paid Rs ${formatMoney(p.amount)} + Disc Rs ${formatMoney(p.discount)})",
                                        fontSize = 11.sp,
                                        color = Color(0xFF1565C0),
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                                Text(displayDate(p.date), fontSize = 11.sp, color = Color.Gray)
                                if (p.note.isNotBlank()) Text(p.note, fontSize = 12.sp)
                            }

                            IconButton(onClick = { editingPayment = p; showPayment = true }) {
                                Icon(Icons.Default.Edit, "Edit", tint = Color(0xFF1565C0))
                            }
                            IconButton(onClick = { paymentToDelete = p }) { Icon(Icons.Default.Delete, "Delete", tint = Color.Red) }
                        }
                    }
                }

                item { SectionTitle("Purchases (${purchases.size})") }
                if (purchases.isEmpty()) item { Text("No purchases yet.", color = Color.Gray, fontSize = 13.sp) }
                items(purchases) { pu ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Purchase #${pu.id} • ${displayDate(pu.date)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            pu.items.take(3).forEach { it ->
                                Text("• ${it.name} — ${it.qty} × ${formatMoney(it.priceValue)}", fontSize = 12.sp, color = Color.DarkGray)
                            }
                            if (pu.items.size > 3) Text("+ ${pu.items.size - 3} more", fontSize = 11.sp, color = Color.Gray)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total: Rs ${formatMoney(pu.total)}", fontWeight = FontWeight.Bold)
                                Text("Paid: Rs ${formatMoney(pu.paid)}", color = PaidColor)
                            }
                            if (pu.discount > 0) {
                                Text("Discount: Rs ${formatMoney(pu.discount)}", color = Color(0xFF1565C0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            if (pu.balance > 0.005) Text("Balance: Rs ${formatMoney(pu.balance)}", color = DueColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                item { Spacer(Modifier.height(60.dp)) }
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
    Row { Text("$label: ", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(90.dp)); Text(value, fontSize = 13.sp) }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B), modifier = Modifier.padding(top = 4.dp))
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
        title = { Text(if (initial == null) "Add Payment" else "Edit Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(amount, { amount = sanitizeDecimal(it) }, label = { Text("Amount Paid (Rs)") },
                    singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth())

                OutlinedTextField(discount, { discount = sanitizeDecimal(it) }, label = { Text("Discount (Rs) — optional") },
                    singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth())

                if (discountVal > 0) {
                    Text("Total covered: Rs ${formatMoney(totalCovered)}",
                        fontSize = 12.sp, color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)
                }

                OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.DateRange, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                    Text("Date: ${isoDayToDisplay(dateIso.take(10))}")
                }
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && totalCovered > 0.0, onClick = {
                scope.launch {
                    busy = true
                    onSave(amountVal, discountVal, dateIso, note)
                    busy = false; onDismiss()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = isoDayToEpochMillis(dateIso.take(10)))
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let { dateIso = epochMillisToIsoDay(it) }
                showDate = false
            }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}