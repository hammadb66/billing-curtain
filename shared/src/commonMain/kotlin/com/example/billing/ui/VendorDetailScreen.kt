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
        balance = repository.getVendorBalance(vendorId)
        payments = repository.paymentsFor(vendorId)
        purchases = repository.listPurchases(vendorId)
    }
    LaunchedEffect(vendorId) { reload() }

    Scaffold(containerColor = Color(0xFFF4F5F7), snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF2C3E50)) }
                    Spacer(Modifier.width(12.dp))
                    Text(vendor?.name ?: "Vendor Details", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        scope.launch {
                            val rows = mutableListOf<PdfRow>()
                            if (Branding.licensedTo.isNotBlank()) rows += PdfRow(left = Branding.licensedTo, bold = true, fontSize = 20f)
                            rows += PdfRow(left = "VENDOR STATEMENT", bold = true, fontSize = 18f)
                            rows += PdfRow(left = "Date: ${displayDate(nowIso())}", fontSize = 12f)
                            rows += PdfRow(kind = RowKind.LINE)
                            rows += PdfRow(left = "Name: ${vendor?.name}", bold = true, fontSize = 14f)
                            rows += PdfRow(left = "Phone: ${vendor?.phone1}", fontSize = 12f)
                            if (vendor?.address?.isNotBlank() == true) rows += PdfRow(left = "Address: ${vendor?.address}", fontSize = 12f)
                            rows += PdfRow(kind = RowKind.SPACE)

                            rows += PdfRow(left = "--- PURCHASES ---", bold = true, fontSize = 14f)
                            var totalPurchases = 0.0
                            var totalPaid = 0.0
                            if (purchases.isEmpty()) rows += PdfRow(left = "No purchases.", fontSize = 12f)
                            else purchases.forEach { pu ->
                                rows += PdfRow(left = "Date: ${displayDate(pu.date)} | Pur #${pu.id}", right = "Total: Rs ${formatMoney(pu.payable)}", fontSize = 12f)
                                totalPurchases += pu.payable
                                totalPaid += pu.paid
                            }
                            rows += PdfRow(kind = RowKind.SPACE)
                            rows += PdfRow(left = "--- PAYMENTS ---", bold = true, fontSize = 14f)
                            if (payments.isEmpty()) rows += PdfRow(left = "No separate payments.", fontSize = 12f)
                            else payments.forEach { p ->
                                rows += PdfRow(left = "Date: ${displayDate(p.date)} | ${p.note.ifBlank { "-" }}", right = "Amount: Rs ${formatMoney(p.amount)}", fontSize = 12f)
                                totalPaid += p.amount
                            }
                            rows += PdfRow(kind = RowKind.LINE)
                            rows += PdfRow(right = "Total Purchases: Rs ${formatMoney(totalPurchases)}", bold = true)
                            rows += PdfRow(right = "Total Paid: Rs ${formatMoney(totalPaid)}", bold = true)
                            rows += PdfRow(right = "Current Balance: Rs ${formatMoney(balance)}", bold = true, fontSize = 16f)

                            rows += PdfRow(kind = RowKind.SPACE)
                            rows += PdfRow(left = "Software by ${Branding.COMPANY}  |  ${Branding.PHONE}", fontSize = 9f)
                            val safeName = vendor?.name?.filter { it.isLetterOrDigit() } ?: "Vendor"
                            printer.print("Vendor_Statement_$safeName", rows)
                        }
                    }) { Icon(Icons.Default.Print, "Print", tint = Color.DarkGray) }
                    IconButton(onClick = { showEdit = true }) { Icon(Icons.Default.Edit, "Edit", tint = Color.DarkGray) }
                    IconButton(onClick = {
                        scope.launch {
                            val deleted = repository.deleteVendor(vendorId)
                            if (deleted) onBack() else snackbar.showSnackbar("❌ Cannot delete! This vendor has purchases or payments.")
                        }
                    }) { Icon(Icons.Default.Delete, "Delete", tint = Color.Red) }
                    Spacer(Modifier.width(16.dp))
                    Button(
                        onClick = { editingPayment = null; showPayment = true },
                        shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = AppGreen), modifier = Modifier.height(40.dp)
                    ) {
                        Icon(Icons.Default.Payments, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Payment", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    Modifier.widthIn(max = 1000.dp).fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFFE0E0E0)), modifier = Modifier.fillMaxWidth()
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
                                    when {
                                        balance > 0.005 -> "Payable: Rs ${formatMoney(balance)}"
                                        balance < -0.005 -> "Advance: Rs ${formatMoney(-balance)}"
                                        else -> "Settled"
                                    },
                                    color = balColor, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                                )
                            }
                        }
                    }
                    item { SectionTitle("Payments (${payments.size})") }
                    if (payments.isEmpty()) item { Text("No payments.", color = Color.Gray, fontSize = 14.sp) }
                    items(payments) { p ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, Color(0xFFE0E0E0)), modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Rs ${formatMoney(p.amount + p.discount)}", fontWeight = FontWeight.Bold, color = AppGreen, fontSize = 16.sp)
                                    if (p.discount > 0) Text("(Paid ${formatMoney(p.amount)} + Disc ${formatMoney(p.discount)})", fontSize = 11.sp, color = Color(0xFF1565C0))
                                    Text(displayDate(p.date), fontSize = 12.sp, color = Color.Gray)
                                }
                                if (p.note.isNotBlank()) Text(p.note, fontSize = 13.sp, color = Color.DarkGray, modifier = Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                                IconButton(onClick = { editingPayment = p; showPayment = true }) { Icon(Icons.Default.Edit, null, tint = Color(0xFF1565C0), modifier = Modifier.size(20.dp)) }
                                IconButton(onClick = { paymentToDelete = p }) { Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(20.dp)) }
                            }
                        }
                    }
                    item { SectionTitle("Purchases (${purchases.size})") }
                    if (purchases.isEmpty()) item { Text("No purchases.", color = Color.Gray, fontSize = 14.sp) }
                    items(purchases) { pu ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, Color(0xFFE0E0E0)), modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Pur #${pu.id} • ${displayDate(pu.date)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                                    Text("Total: Rs ${formatMoney(pu.total)}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    pu.items.take(3).forEach { it ->
                                        Text("• ${it.name} — ${it.qty} × ${formatMoney(it.priceValue)}", fontSize = 12.sp, color = Color.DarkGray)
                                    }
                                    if (pu.items.size > 3) Text("+ ${pu.items.size - 3} more...", fontSize = 11.sp, color = Color.Gray)
                                }
                                HorizontalDivider(color = Color(0xFFF1F5F9))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                    Text("Paid: Rs ${formatMoney(pu.paid)}", color = PaidColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    if (pu.discount > 0) Text("Disc: Rs ${formatMoney(pu.discount)}", color = Color(0xFF1565C0), fontSize = 13.sp)
                                    if (pu.balance > 0.005) Text("Bal: Rs ${formatMoney(pu.balance)}", color = DueColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
        VendorEditDialog(initial = vendor, onSave = { repository.updateVendor(it); reload() }, onDismiss = { showEdit = false })
    }
    if (showPayment) {
        PaymentDialog(
            initial = editingPayment,
            onSave = { amount, discount, date, note ->
                if (editingPayment != null) repository.updatePayment(editingPayment!!.copy(amount = amount, discount = discount, date = date, note = note))
                else repository.addPayment(vendorId, amount, discount, date, note)
                reload()
            },
            onDismiss = { showPayment = false; editingPayment = null }
        )
    }
    paymentToDelete?.let { p ->
        ConfirmDialog(
            "Delete Payment", "Delete this payment of Rs ${formatMoney(p.amount)}?", "Delete",
            { scope.launch { repository.deletePayment(p.id); paymentToDelete = null; reload() } },
            { paymentToDelete = null }
        )
    }
}

// ================================================================
// CUSTOMER DETAIL
// ================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    repository: VendorRepository,
    customerId: Long,
    onBack: () -> Unit,
    printer: PdfPrinter
) {
    PlatformBackHandler { onBack() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var customer by remember { mutableStateOf<CustomerAccount?>(null) }
    var balance by remember { mutableStateOf(0.0) }
    var payments by remember { mutableStateOf<List<VendorRepository.CustomerPayment>>(emptyList()) }
    var purchases by remember { mutableStateOf<List<Bill>>(emptyList()) }
    var showEdit by remember { mutableStateOf(false) }
    var showBillAllocation by remember { mutableStateOf(false) }
    var billToPay by remember { mutableStateOf<Bill?>(null) }
    var editingPayment by remember { mutableStateOf<VendorRepository.CustomerPayment?>(null) }
    var paymentToDelete by remember { mutableStateOf<VendorRepository.CustomerPayment?>(null) }

    suspend fun reload() {
        customer = repository.listCustomerAccounts().firstOrNull { it.id == customerId }
        balance = repository.getCustomerBalance(customerId)
        payments = repository.getCustomerPayments(customerId)
        purchases = repository.getCustomerPurchases(customerId)
    }
    LaunchedEffect(customerId) { reload() }

    val canPay = purchases.isNotEmpty()

    Scaffold(containerColor = Color(0xFFF4F5F7), snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF2C3E50)) }
                    Spacer(Modifier.width(12.dp))
                    Text(customer?.name ?: "Customer Details", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        scope.launch {
                            val rows = mutableListOf<PdfRow>()
                            if (Branding.licensedTo.isNotBlank()) rows += PdfRow(left = Branding.licensedTo, bold = true, fontSize = 20f)
                            rows += PdfRow(left = "CUSTOMER STATEMENT", bold = true, fontSize = 18f)
                            rows += PdfRow(left = "Date: ${displayDate(nowIso())}", fontSize = 12f)
                            rows += PdfRow(kind = RowKind.LINE)
                            rows += PdfRow(left = "Name: ${customer?.name}", bold = true, fontSize = 14f)
                            rows += PdfRow(left = "Phone: ${customer?.phone1}", fontSize = 12f)
                            if (customer?.address?.isNotBlank() == true) rows += PdfRow(left = "Address: ${customer?.address}", fontSize = 12f)
                            rows += PdfRow(kind = RowKind.SPACE)

                            rows += PdfRow(left = "--- PURCHASES (BILLS) ---", bold = true, fontSize = 14f)
                            var totalSales = 0.0
                            var totalReceived = 0.0
                            if (purchases.isEmpty()) rows += PdfRow(left = "No purchases.", fontSize = 12f)
                            else purchases.forEach { b ->
                                rows += PdfRow(left = "Date: ${displayDate(b.date)} | Inv: ${b.invoiceText}", right = "Rs ${formatMoney(b.grandTotal)}", fontSize = 12f)
                                totalSales += b.grandTotal
                                totalReceived += b.received
                            }

                            rows += PdfRow(kind = RowKind.SPACE)
                            rows += PdfRow(left = "--- PAYMENTS ---", bold = true, fontSize = 14f)
                            if (payments.isEmpty()) rows += PdfRow(left = "No separate payments.", fontSize = 12f)
                            else payments.forEach { p ->
                                rows += PdfRow(left = "Date: ${displayDate(p.date)} | ${p.note.ifBlank { "-" }}", right = "Rs ${formatMoney(p.amount)}", fontSize = 12f)
                                totalReceived += p.amount
                            }
                            rows += PdfRow(kind = RowKind.LINE)
                            rows += PdfRow(right = "Total Sales: Rs ${formatMoney(totalSales)}", bold = true)
                            rows += PdfRow(right = "Total Received: Rs ${formatMoney(totalReceived)}", bold = true)
                            rows += PdfRow(right = "Current Due: Rs ${formatMoney(balance)}", bold = true, fontSize = 16f)

                            rows += PdfRow(kind = RowKind.SPACE)
                            rows += PdfRow(left = "Software by ${Branding.COMPANY}  |  ${Branding.PHONE}", fontSize = 9f)
                            val safe = customer?.name?.filter { it.isLetterOrDigit() } ?: "Customer"
                            printer.print("Customer_Statement_$safe", rows)
                        }
                    }) { Icon(Icons.Default.Print, "Print", tint = Color.DarkGray) }
                    IconButton(onClick = { showEdit = true }) { Icon(Icons.Default.Edit, "Edit", tint = Color.DarkGray) }
                    IconButton(onClick = {
                        scope.launch {
                            val deleted = repository.deleteCustomerAccount(customerId)
                            if (deleted) onBack() else snackbar.showSnackbar("❌ Cannot delete! This customer has bills or payments.")
                        }
                    }) { Icon(Icons.Default.Delete, "Delete", tint = Color.Red) }
                    Spacer(Modifier.width(16.dp))
                    Button(
                        onClick = { editingPayment = null; showBillAllocation = true },
                        enabled = canPay, shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = AppGreen), modifier = Modifier.height(40.dp)
                    ) {
                        Icon(Icons.Default.Payments, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Payment", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (!canPay) {
                    Text(
                        "Payment only allowed after first purchase.",
                        fontSize = 11.sp, color = Color(0xFFE65100),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                    )
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    Modifier.widthIn(max = 1000.dp).fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFFE0E0E0)), modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        InfoLine("Phone 1", customer?.phone1.orEmpty())
                                        InfoLine("Phone 2", customer?.phone2.orEmpty())
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        InfoLine("Address", customer?.address.orEmpty())
                                        InfoLine("Account No", customer?.accountNo.orEmpty())
                                    }
                                }
                                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0xFFE0E0E0))
                                val balColor = if (balance > 0.005) DueColor else if (balance < -0.005) PaidColor else Color.Gray
                                Text(
                                    when {
                                        balance > 0.005 -> "Due: Rs ${formatMoney(balance)}"
                                        balance < -0.005 -> "Advance: Rs ${formatMoney(-balance)}"
                                        else -> "Settled"
                                    },
                                    color = balColor, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                                )
                            }
                        }
                    }
                    item { SectionTitle("Payments (${payments.size})") }
                    if (payments.isEmpty()) item { Text("No separate payments.", color = Color.Gray, fontSize = 14.sp) }
                    items(payments) { p ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, Color(0xFFE0E0E0)), modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Rs ${formatMoney(p.amount + p.discount)}", fontWeight = FontWeight.Bold, color = AppGreen, fontSize = 16.sp)
                                    if (p.discount > 0) Text("(Paid ${formatMoney(p.amount)} + Disc ${formatMoney(p.discount)})", fontSize = 11.sp, color = Color(0xFF1565C0))
                                    Text(displayDate(p.date), fontSize = 12.sp, color = Color.Gray)
                                }
                                if (p.note.isNotBlank()) Text(p.note, fontSize = 13.sp, color = Color.DarkGray, modifier = Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                                IconButton(onClick = { editingPayment = p; showBillAllocation = true }) { Icon(Icons.Default.Edit, null, tint = Color(0xFF1565C0), modifier = Modifier.size(20.dp)) }
                                IconButton(onClick = { paymentToDelete = p }) { Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(20.dp)) }
                            }
                        }
                    }
                    item { SectionTitle("Purchases / Bills (${purchases.size})") }
                    if (purchases.isEmpty()) item { Text("No purchases.", color = Color.Gray, fontSize = 14.sp) }
                    items(purchases) { b ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, Color(0xFFE0E0E0)), modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${b.invoiceText} • ${displayDate(b.date)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                                    Text("Rs ${formatMoney(b.grandTotal)}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                b.rooms.forEach { room ->
                                    room.items.take(5).forEach { it ->
                                        Text("• ${it.name} — ${it.qty} × ${formatMoney(it.rateValue)}", fontSize = 12.sp, color = Color.DarkGray)
                                    }
                                }
                                HorizontalDivider(color = Color(0xFFF1F5F9))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                    Text("Received: Rs ${formatMoney(b.paidAmount ?: 0.0)}", color = PaidColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    if (b.balance > 0.005) Text("Balance: Rs ${formatMoney(b.balance)}", color = DueColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }

    if (showEdit && customer != null) {
        CustomerAccountDialog(
            initial = customer,
            onSave = { acc ->
                scope.launch {
                    repository.updateCustomerAccount(acc)
                    reload()
                    showEdit = false
                }
            },
            onDismiss = { showEdit = false }
        )
    }
    if (showBillAllocation) {
        AllocatePaymentDialog(
            bills = purchases.filter { it.balance > 0.005 },
            onSave = { selectedBill, amount, discount ->
                scope.launch {
                    repository.payBill(selectedBill.id, amount, discount)
                    reload()
                    showBillAllocation = false
                }
            },
            onDismiss = { showBillAllocation = false }
        )
    }
    paymentToDelete?.let { p ->
        ConfirmDialog(
            "Delete Payment", "Delete this payment of Rs ${formatMoney(p.amount)}?", "Delete",
            { scope.launch { repository.deleteCustomerPayment(p.id); paymentToDelete = null; reload() } },
            { paymentToDelete = null }
        )
    }
}

@Composable
private fun AllocatePaymentDialog(
    bills: List<Bill>,
    onSave: (Bill, Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedBill by remember { mutableStateOf<Bill?>(bills.firstOrNull()) }
    var amount by remember { mutableStateOf("") }
    var discount by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Payment to Bill", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (bills.isEmpty()) {
                    Text("No pending bills found.", color = Color.Red)
                } else {
                    Text("Select Bill:", fontSize = 12.sp, color = Color.Gray)
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(selectedBill?.let { "${it.invoiceText} (Pending: Rs ${formatMoney(it.balance)})" } ?: "Select Bill", maxLines = 1)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            bills.forEach { b ->
                                DropdownMenuItem(
                                    text = { Text("${b.invoiceText} (Pending: Rs ${formatMoney(b.balance)})") },
                                    onClick = { selectedBill = b; expanded = false }
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = Color(0xFFE0E0E0))
                    OutlinedTextField(
                        value = amount, onValueChange = { amount = sanitizeDecimal(it) },
                        label = { Text("Amount Received (Rs)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = discount, onValueChange = { discount = sanitizeDecimal(it) },
                        label = { Text("Discount on this Bill (Rs)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedBill != null && (amount.toDoubleOrNull() ?: 0.0) + (discount.toDoubleOrNull() ?: 0.0) > 0.0,
                onClick = { onSave(selectedBill!!, amount.toDoubleOrNull() ?: 0.0, discount.toDoubleOrNull() ?: 0.0) },
                shape = RoundedCornerShape(6.dp)
            ) { Text("Save Payment", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(8.dp), containerColor = Color.White
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    if (value.isBlank()) return
    Row {
        Text("$label: ", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp))
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
    val totalCovered = (amount.toDoubleOrNull() ?: 0.0) + (discount.toDoubleOrNull() ?: 0.0)

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (initial == null) "Add Vendor Payment" else "Edit Payment", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(amount, { amount = sanitizeDecimal(it) }, label = { Text("Amount Paid (Rs)") }, singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(discount, { discount = sanitizeDecimal(it) }, label = { Text("Discount (Rs) — optional") }, singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                if (totalCovered > 0) Text("Total: Rs ${formatMoney(totalCovered)}", fontSize = 13.sp, color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                    Icon(Icons.Default.DateRange, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                    Text("Date: ${isoDayToDisplay(dateIso.take(10))}")
                }
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && totalCovered > 0.0,
                onClick = { scope.launch { busy = true; onSave(amount.toDoubleOrNull() ?: 0.0, discount.toDoubleOrNull() ?: 0.0, dateIso, note); busy = false; onDismiss() } },
                shape = RoundedCornerShape(6.dp)
            ) { if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White) else Text("Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(8.dp), containerColor = Color.White
    )
    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = isoDayToEpochMillis(dateIso.take(10)))
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { dateIso = epochMillisToIsoDay(it) }; showDate = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}