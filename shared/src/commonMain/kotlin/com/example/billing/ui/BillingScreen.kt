package com.example.billing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.billing.AppServices
import com.example.billing.model.*
import com.example.billing.pdf.PdfPrinter
import com.example.billing.pdf.buildInvoiceRows
import com.example.billing.presentation.BillingViewModel
import com.example.billing.utils.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.system.exitProcess

@Composable
fun BillingScreen(
    printer: PdfPrinter, onOpenHistory: () -> Unit, onLogout: () -> Unit, onOpenManageItems: () -> Unit,
    onOpenItemReport: () -> Unit, userName: String, onOpenVendors: () -> Unit, onOpenPurchases: () -> Unit,
    isDeveloper: Boolean, exporter: FileExporter?, importer: FileImporter?
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var message by remember { mutableStateOf<Pair<String, String>?>(null) }
    var roomToDelete by remember { mutableStateOf<RoomState?>(null) }
    var addItemForRoom by remember { mutableStateOf<RoomState?>(null) }
    var isPrinting by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportText by remember { mutableStateOf<String?>(null) }

    var accounts by remember { mutableStateOf<List<CustomerAccount>>(emptyList()) }
    var editingAccount by remember { mutableStateOf<CustomerAccount?>(null) }
    var deleteAccount by remember { mutableStateOf<CustomerAccount?>(null) }

    val viewModel: BillingViewModel = koinViewModel()
    val services = koinInject<AppServices>()
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }

    var showDeleteCodeDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var generatedCode by remember { mutableStateOf("") }

    fun initiateDelete() {
        generatedCode = (100000..999999).random().toString()
        showDeleteCodeDialog = true
    }

    LaunchedEffect(Unit) {
        products = services.vendors.listProductsWithStock()
        accounts = services.vendors.listCustomerAccounts()
        viewModel.form.shouldPrint = services.settings.get("should_print")?.toBoolean() ?: true
    }

    val form = viewModel.form
    val previousBalance by viewModel.previousBalance.collectAsState()
    LaunchedEffect(form.phone) {
        if (form.phone.isNotBlank()) {
            val prevBill = services.bills.getPreviousBill(form.phone)
            form.loadPreviousBill(prevBill)
        } else {
            form.previousBill = null
            form.previousBalance = 0.0
        }
    }

    PlatformBackHandler { showExitConfirm = true }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Exit App?", fontWeight = FontWeight.Bold) },
            text = { Text("Do you really want to exit the billing system?") },
            confirmButton = {
                TextButton(onClick = { showExitConfirm = false; exitProcess(0) }) {
                    Text(
                        "Exit",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = { TextButton(onClick = { showExitConfirm = false }) { Text("Cancel") } }
        )
    }

    val filledItems by remember { derivedStateOf { form.rooms.sumOf { r -> r.items.count { it.total > 0 } } } }
    val summary = "${form.rooms.size} Section(s) | $filledItems Item(s)"

    fun printAndSave() {
        if (isPrinting) return
        val problem = validateForm(form)
        if (problem != null) {
            message = problem; return
        }
        val savedShowProduct = form.showProduct;
        val savedShowCategory = form.showCategory;
        val savedShowSubcategory = form.showSubcategory
        val savedShowGroup = form.showGroup
        val savedShowSubGroup = form.showSubgroup
        isPrinting = true
        viewModel.saveBill(
            userName = userName,
            showProduct = savedShowProduct,
            showCategory = savedShowCategory,
            showSubcategory = savedShowSubcategory,
            showGroup = savedShowGroup,
            showSubGroup = savedShowSubGroup,
            onSuccess = { invoiceNo, customer, phone, iso, rooms, discVal, extraVal, paid ->
                val prevBill = form.previousBill
                val prevBalance = form.previousBalance
                form.reset()
                scope.launch {
                    if (form.shouldPrint) {
                        var snackText: String? = null
                        try {
                            val printed = printer.print(
                                title = "Invoice_${customer.filter { it.isLetterOrDigit() || it == ' ' }}",
                                rows = buildInvoiceRows(
                                    customer,
                                    phone,
                                    displayDate(iso),
                                    rooms,
                                    discVal,
                                    extraVal,
                                    invoiceNo = invoiceNo,
                                    paidAmount = paid,
                                    showProduct = savedShowProduct,
                                    showCategory = savedShowCategory,
                                    showSubcategory = savedShowSubcategory,
                                    showGroup = savedShowGroup,
                                    showSubgroup =  savedShowSubGroup,
                                    previousBill = prevBill, previousBalance = prevBalance
                                )
                            )
                            snackText = if (printed) "Bill printed & saved!" else "Bill saved. Print cancelled."
                        } catch (e: Throwable) {
                            snackText = "Bill saved, but print failed."
                        }
                        products = services.vendors.listProductsWithStock(); isPrinting =
                            false; snackText?.let { snackbar.showSnackbar(it) }
                    } else {
                        products = services.vendors.listProductsWithStock(); isPrinting =
                            false; snackbar.showSnackbar("Bill saved successfully (No Print).")
                    }
                }
            },
            onError = { error -> isPrinting = false; message = "Save Error" to error }
        )
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = Color(0xFFF4F5F7)) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val wide = maxWidth >= 950.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    DesktopSidebar(
                        onOpenHistory,
                        onOpenItemReport,
                        onOpenManageItems,
                        onOpenVendors,
                        onOpenPurchases,
                        { showBackupDialog = true },
                        onLogout,
                        userName,
                        isDeveloper,
                        !isPrinting
                    )
                    VerticalDivider(color = Color(0xFFE0E0E0))
                    Column(Modifier.weight(1f).fillMaxHeight().background(Color.White)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "New Invoice",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2C3E50)
                            )
                            Spacer(Modifier.weight(1f))
                            Text(summary, fontSize = 13.sp, color = Color.Gray); Spacer(Modifier.width(16.dp))
                            Button(
                                onClick = { form.addRoom() },
                                enabled = !isPrinting,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(
                                "Add Section",
                                fontSize = 13.sp
                            )
                            }
                        }
                        HorizontalDivider(color = Color(0xFFEEEEEE))
                        LazyColumn(
                            Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            roomItems(form, onAddItem = { addItemForRoom = it }, onDelete = { roomToDelete = it })
                        }
                    }
                    VerticalDivider(color = Color(0xFFE0E0E0))
                    RightCheckoutPanel(
                        form = form, viewModel = viewModel, previousBalance = previousBalance,
                        onManageCustomers = { null },
                        accounts = accounts,
                        onEditAccount = { editingAccount = it },
                        onDeleteAccount = { deleteAccount = it },
                        isPrinting = isPrinting, onPrint = { printAndSave() }, onReset = { confirmReset = true }
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            MobileHeaderBlock(
                                form = form,
                                onOpenHistory = onOpenHistory,
                                onOpenItemReport = onOpenItemReport,
                                onOpenManageItems = onOpenManageItems,
                                onOpenVendors = onOpenVendors,
                                onOpenPurchases = onOpenPurchases,
                                onOpenBackupRestore = { showBackupDialog = true },
                                onLogout = onLogout,
                                userName = userName,
                                isDeveloper = isDeveloper,
                                enabled = !isPrinting,
                                onManageCustomers = { null },
                                previousBalance = previousBalance,
                                accounts = accounts,
                                onEditAccount = { editingAccount = it },
                                onDeleteAccount = { deleteAccount = it }
                            )
                        }
                        item { FinancialAdjustmentsBlock(form, enabled = !isPrinting) }
                        item { DisplayOptionsBlock(form, onOptionChange = { viewModel.setDisplayOption(it) }) }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Button(
                                    onClick = { form.addRoom() },
                                    enabled = !isPrinting,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        null,
                                        Modifier.size(16.dp)
                                    ); Spacer(Modifier.width(6.dp)); Text("Add Section", fontSize = 13.sp)
                                }
                            }
                        }
                        roomItems(form, onAddItem = { addItemForRoom = it }, onDelete = { roomToDelete = it })
                    }
                    Surface(shadowElevation = 8.dp, color = Color.White) {
                        Column(Modifier.padding(16.dp)) {
                            AmountBreakdown(form.subtotal, form.discountValue, form.extraValue)
                            if (form.balance > 0.005) BalanceBanner(form.receivedValue, form.balance)
                            Spacer(Modifier.height(12.dp))
                            MobileActionBlock(
                                form.grandTotal,
                                summary,
                                !isPrinting,
                                { printAndSave() },
                                { confirmReset = true },
                                form.shouldPrint
                            )
                        }
                    }
                }
            }
            if (isPrinting) {
                Dialog(
                    onDismissRequest = {},
                    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = AppGreen); Spacer(Modifier.height(16.dp)); Text(
                            "Printing & Saving Bill...",
                            fontWeight = FontWeight.Bold
                        )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(addItemForRoom) { if (addItemForRoom != null) products = services.vendors.listProductsWithStock() }
    addItemForRoom?.let { room ->
        AddItemDialog(
            products,
            { p -> room.addProduct(p, p.liveStock ?: p.openingStock); addItemForRoom = null },
            { addItemForRoom = null; onOpenManageItems() },
            { addItemForRoom = null })
    }
    message?.let { (title, text) -> MessageDialog(title, text) { message = null } }
    roomToDelete?.let { room ->
        ConfirmDialog(
            "Delete Bill",
            "Are you sure you want to delete this bill?",
            "Confirm",
            { form.removeRoom(room); roomToDelete = null },
            { roomToDelete = null })
    }
    if (confirmReset) ConfirmDialog(
        "Reset",
        "Do you want to clear all the fields?",
        "Reset",
        { form.reset(); confirmReset = false },
        { confirmReset = false })

    if (editingAccount != null) {
        CustomerAccountDialog(
            initial = editingAccount,
            onSave = { acc ->
                scope.launch {
                    if (acc.id == 0L) services.vendors.addCustomerAccount(
                        acc.name,
                        acc.phone1,
                        acc.phone2,
                        acc.address,
                        acc.accountNo
                    )
                    else services.vendors.updateCustomerAccount(acc)
                    accounts = services.vendors.listCustomerAccounts()
                    editingAccount = null
                }
            },
            onDismiss = { editingAccount = null }
        )
    }

    deleteAccount?.let { acc ->
        AlertDialog(
            onDismissRequest = { deleteAccount = null },
            title = { Text("Delete Customer ID?") },
            text = { Text("Are you sure you want to delete '${acc.name}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (services.vendors.isCustomerAccountUsedInBills(acc.id)) {
                            snackbar.showSnackbar("Cannot delete! This ID is used in bills.")
                        } else {
                            services.vendors.deleteCustomerAccount(acc.id)
                            accounts = services.vendors.listCustomerAccounts()
                            snackbar.showSnackbar("Deleted!")
                        }
                    }
                    deleteAccount = null
                }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { deleteAccount = null }) { Text("Cancel") } }
        )
    }

    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text("Data Management") },
            text = {
                Column {
                    Spacer(Modifier.height(8.dp))
                    // DELETE ALL DATA: Sabke liye visible hai
                    OutlinedButton(
                        onClick = { showBackupDialog = false; initiateDelete() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteForever, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete All Data (Permanent)", fontWeight = FontWeight.Bold)
                    }

                    // EXPORT / IMPORT INFO: Sirf Developer ko dikhega
                    if (isDeveloper) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Export creates a JSON file with ALL data.\n\nImport will REPLACE all current data.",
                            color = Color.Red,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                // EXPORT BUTTON: Sirf Developer ko dikhega
                if (isDeveloper) {
                    TextButton(onClick = {
                        showBackupDialog = false; scope.launch {
                        try {
                            val content = services.backup.exportAll();
                            val where = exporter?.saveText("full_backup_${nowIso().take(10)}.json", content);
                            snackbar.showSnackbar(if (where != null) "Exported to $where" else "Export cancelled.")
                        } catch (e: Exception) {
                            snackbar.showSnackbar("Export failed: ${e.message}")
                        }
                    }
                    }) { Text("Export All") }
                } else {
                    // Staff ke liye bas 'Close' ka button hoga
                    TextButton(onClick = { showBackupDialog = false }) { Text("Close") }
                }
            },
            dismissButton = {
                // IMPORT BUTTON: Sirf Developer ko dikhega
                if (isDeveloper) {
                    TextButton(onClick = {
                        showBackupDialog = false; scope.launch {
                        try {
                            val text = importer?.pickJsonFile(); if (!text.isNullOrBlank()) {
                                pendingImportText = text; showImportConfirm = true
                            }
                        } catch (e: Exception) {
                            snackbar.showSnackbar("Import failed: ${e.message}")
                        }
                    }
                    }) { Text("Import / Restore", color = Color.Red) }
                }
            }
        )
    }
    if (showImportConfirm && pendingImportText != null) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false; pendingImportText = null },
            title = { Text("⚠️ Warning! Data Replace", color = Color.Red, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will DELETE all current data and replace it with the backup. Are you sure?",
                    color = Color.DarkGray
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false;
                    val text = pendingImportText; pendingImportText = null; if (text != null) {
                    scope.launch {
                        try {
                            val err = services.backup.importAll(text); if (err == null) {
                                snackbar.showSnackbar("Import successful! All data restored."); products =
                                    services.vendors.listProductsWithStock(); accounts =
                                    services.vendors.listCustomerAccounts()
                            } else snackbar.showSnackbar("Import failed: $err")
                        } catch (e: Exception) {
                            snackbar.showSnackbar("Import failed: ${e.message}")
                        }
                    }
                }
                }) { Text("Yes, Replace All", color = Color.Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportConfirm = false; pendingImportText = null
                }) { Text("Cancel") }
            }
        )
    }


    if (showDeleteCodeDialog) {
        var inputCode by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDeleteCodeDialog = false },
            title = { Text("Security Check", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Type the code below to confirm you want to DELETE ALL DATA:", fontWeight = FontWeight.Bold)
                    Text(
                        generatedCode,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Red,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = { inputCode = it },
                        label = { Text("Enter Code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = inputCode == generatedCode,
                    onClick = { showDeleteCodeDialog = false; showDeleteConfirmDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Next", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDeleteCodeDialog = false }) { Text("Cancel") } }
        )
    }
    if (showDeleteConfirmDialog) {
        val isOnline = services.sync.status.collectAsState().value != com.example.billing.sync.SyncStatus.Disabled
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("⚠️ Final Warning", color = Color.Red, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (isOnline)
                            "You are ONLINE. This will delete ALL data from this laptop AND from Firebase.\n\nIf Firebase delete fails, LOCAL data will still be deleted."
                        else
                            "You are OFFLINE. This will delete ALL DATA from THIS LAPTOP ONLY.",
                        fontWeight = FontWeight.Bold, color = Color.DarkGray
                    )
                    Text("Are you absolutely sure? This cannot be undone.", color = Color.Red)

                    // Backup option (both online & offline)
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val backup = services.backup.exportAll()
                                    val where = exporter?.saveText("backup_before_delete.json", backup)
                                    snackbar.showSnackbar(
                                        if (where != null) "✅ Backup saved to $where" else "Backup cancelled."
                                    )
                                } catch (e: Exception) {
                                    snackbar.showSnackbar("Backup failed: ${e.message}")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Download Backup First (Recommended)") }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        scope.launch {
                            var cloudErr: String? = null

                            // Step 1: Try cloud delete (do NOT block local on failure)
                            if (isOnline) {
                                try {
                                    cloudErr = services.sync.clearCloudData()
                                } catch (e: Exception) {
                                    cloudErr = e.message ?: "unknown error"
                                }
                            }

                            // Step 2: ALWAYS delete local
                            try {
                                services.backup.clearLocalData()
                                products = services.vendors.listProductsWithStock()
                                accounts = services.vendors.listCustomerAccounts()
                                viewModel.form.reset()

                                val msg = when {
                                    isOnline && cloudErr == null ->
                                        "✅ ALL data deleted (Local + Cloud)"
                                    isOnline && cloudErr != null ->
                                        "✅ Local data deleted.\n⚠️ Cloud delete failed: $cloudErr"
                                    else ->
                                        "✅ All local data deleted (Offline)"
                                }
                                snackbar.showSnackbar(msg)
                            } catch (e: Exception) {
                                snackbar.showSnackbar("❌ Local delete failed: ${e.message}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("DELETE PERMANENTLY", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancel") } }
        )
    }


}

// ================= CUSTOMER ACCOUNT DIALOGS =================

@Composable
fun CustomerManageDialog(services: AppServices, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var accounts by remember { mutableStateOf<List<CustomerAccount>>(emptyList()) }
    var showAddEdit by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<CustomerAccount?>(null) }
    var deleteAccount by remember { mutableStateOf<CustomerAccount?>(null) }

    suspend fun reload() {
        accounts = services.vendors.listCustomerAccounts()
    }
    LaunchedEffect(Unit) { reload() }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Manage Customer IDs", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 400.dp)) {
                Button(
                    onClick = { editingAccount = null; showAddEdit = true },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) { Text("+ Add New Customer ID") }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(accounts) { acc ->
                        Card(border = BorderStroke(1.dp, Color.LightGray)) {
                            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(acc.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                IconButton(
                                    onClick = { editingAccount = acc; showAddEdit = true },
                                    modifier = Modifier.size(32.dp)
                                ) { Icon(Icons.Default.Edit, "Edit", tint = Color.Blue) }
                                IconButton(onClick = { deleteAccount = acc }, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        Icons.Default.Delete,
                                        "Delete",
                                        tint = Color.Red
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
    )

    if (showAddEdit) {
        CustomerAccountDialog(
            initial = editingAccount,
            onSave = { acc ->
                scope.launch {
                    if (acc.id == 0L) services.vendors.addCustomerAccount(
                        acc.name,
                        acc.phone1,
                        acc.phone2,
                        acc.address,
                        acc.accountNo
                    )
                    else services.vendors.updateCustomerAccount(acc)
                    reload()
                    showAddEdit = false
                }
            },
            onDismiss = { showAddEdit = false }
        )
    }

    deleteAccount?.let { acc ->
        AlertDialog(
            onDismissRequest = { deleteAccount = null },
            title = { Text("Delete Customer ID?") },
            text = { Text("Are you sure you want to delete '${acc.name}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (services.vendors.isCustomerAccountUsedInBills(acc.id)) {
                            snackbar.showSnackbar("Cannot delete! This ID is used in bills.")
                        } else {
                            services.vendors.deleteCustomerAccount(acc.id)
                            reload()
                            snackbar.showSnackbar("Deleted!")
                        }
                    }
                    deleteAccount = null
                }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { deleteAccount = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun CustomerAccountDialog(initial: CustomerAccount?, onSave: (CustomerAccount) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone1 by remember { mutableStateOf(initial?.phone1 ?: "") }
    var phone2 by remember { mutableStateOf(initial?.phone2 ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var accountNo by remember { mutableStateOf(initial?.accountNo ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Customer ID" else "Edit Customer ID", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name *") },
                    singleLine = true,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone1,
                    onValueChange = { phone1 = sanitizePhone(it) },
                    label = { Text("Phone 1") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone2,
                    onValueChange = { phone2 = sanitizePhone(it) },
                    label = { Text("Phone 2") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    singleLine = true,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = accountNo,
                    onValueChange = { accountNo = it },
                    label = { Text("Account No") },
                    singleLine = true,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) onSave(
                    CustomerAccount(
                        initial?.id ?: 0L,
                        name.trim(),
                        phone1.trim(),
                        phone2.trim(),
                        address.trim(),
                        accountNo.trim()
                    )
                )
            }, enabled = name.isNotBlank(), shape = RoundedCornerShape(6.dp)) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(8.dp), containerColor = Color.White
    )
}

@Composable
fun CustomerSelector(
    accounts: List<CustomerAccount>,
    selected: CustomerAccount?,
    onSelect: (CustomerAccount?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(selected?.name ?: "Counter Sale (Manual)", maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Counter Sale (Manual)", fontWeight = FontWeight.Bold, color = AppGreen) },
                onClick = { onSelect(null); expanded = false }
            )
            HorizontalDivider()
            accounts.forEach { acc ->
                DropdownMenuItem(
                    text = { Text(acc.name, Modifier.width(220.dp), maxLines = 1) },
                    onClick = { onSelect(acc); expanded = false }
                )
            }
        }
    }
}
// ===============================================================

@Composable
private fun DesktopSidebar(
    onOpenHistory: () -> Unit,
    onOpenItemReport: () -> Unit,
    onOpenManageItems: () -> Unit,
    onOpenVendors: () -> Unit,
    onOpenPurchases: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    onLogout: () -> Unit,
    userName: String,
    isDeveloper: Boolean,
    enabled: Boolean
) {
    Column(Modifier.width(240.dp).fillMaxHeight().background(Color(0xFFF8F9FA)).padding(16.dp)) {
        Text(
            "Billing System",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = AppGreen
        ); Text("by ${Branding.COMPANY}", fontSize = 12.sp, color = Color.Gray); Spacer(Modifier.height(24.dp))
        SidebarButton("Billing", Icons.Default.ShoppingCart, selected = true) { }
        SidebarButton("History", Icons.Outlined.History, enabled = enabled) { onOpenHistory() }
        SidebarButton("Item Report", Icons.Outlined.BarChart, enabled = enabled) { onOpenItemReport() }
        SidebarButton("Products", Icons.Outlined.Category, enabled = enabled) { onOpenManageItems() }
        SidebarButton("Vendors/Customers", Icons.Outlined.People, enabled = enabled) { onOpenVendors() }
        SidebarButton("Purchases", Icons.Outlined.LocalShipping, enabled = enabled) { onOpenPurchases() }
        Spacer(Modifier.height(8.dp)); HorizontalDivider(color = Color.LightGray); Spacer(Modifier.height(8.dp)); SidebarButton(
        "Data Management",
        Icons.Outlined.SettingsBackupRestore,
        enabled = enabled
    ) { onOpenBackupRestore() }
        Spacer(Modifier.weight(1f)); HorizontalDivider(color = Color.LightGray); Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.AccountCircle,
                null,
                tint = Color.Gray,
                modifier = Modifier.size(32.dp)
            ); Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    userName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ); Text(if (isDeveloper) "Developer" else "Staff", fontSize = 11.sp, color = Color.Gray)
            }
            IconButton(onClick = onLogout, enabled = enabled) { Icon(Icons.Default.Logout, "Logout", tint = Color.Red) }
        }
    }
}

@Composable
private fun SidebarButton(
    text: String,
    icon: ImageVector,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val bg = if (selected) AppGreen.copy(alpha = 0.1f) else Color.Transparent;
    val contentColor = if (selected) AppGreen else Color.DarkGray
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).background(bg, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            null,
            tint = if (enabled) contentColor else Color.LightGray,
            modifier = Modifier.size(20.dp)
        ); Spacer(Modifier.width(12.dp)); Text(
        text,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = if (enabled) contentColor else Color.LightGray
    )
    }
}

@Composable
private fun RightCheckoutPanel(
    form: BillingFormState, viewModel: BillingViewModel, previousBalance: Double,
    onManageCustomers: () -> Unit,
    accounts: List<CustomerAccount>,
    onEditAccount: (CustomerAccount) -> Unit,
    onDeleteAccount: (CustomerAccount) -> Unit,
    isPrinting: Boolean, onPrint: () -> Unit, onReset: () -> Unit
) {
    val scope = rememberCoroutineScope()
    Column(Modifier.width(360.dp).fillMaxHeight().background(Color(0xFFFAFAFA)).verticalScroll(rememberScrollState())) {
        // Customer Details section ke andar
        if (form.previousBill != null) {
            Surface(
                color = Color(0xFFFFF3E0),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("PREVIOUS BILL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                    Text(
                        "${form.previousBill!!.invoiceText} • Rs ${formatMoney(form.previousBill!!.balance)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (form.previousBalance > 0) {
                        Text(
                            "Pending: Rs ${formatMoney(form.previousBalance)}",
                            fontSize = 12.sp,
                            color = Color(0xFFE65100)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.padding(24.dp).fillMaxWidth()) {
            Text("Customer Details", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50)); Spacer(
            Modifier.height(12.dp)
        )

            CustomerSelector(
                accounts = accounts,
                selected = form.selectedCustomerAccount,
                onSelect = {
                    form.selectedCustomerAccount = it
                    if (it != null) {
                        form.customer = it.name
                        form.phone = it.phone1
                        form.phone2 = it.phone2
                        form.address = it.address
                        form.accountNo = it.accountNo
                    } else {
                        // Counter Sale select karne par sab clear karo
                        form.customer = ""
                        form.phone = ""
                        form.phone2 = ""
                        form.address = ""
                        form.accountNo = ""
                        form.previousBill = null
                        form.previousBalance = 0.0
                    }
                },

                )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = form.customer,
                onValueChange = { form.customer = it },
                label = { Text("Name", fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(6.dp),
                enabled = !isPrinting,
                modifier = Modifier.fillMaxWidth()
            ); Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = form.phone,
                onValueChange = { form.phone = sanitizePhone(it) },
                label = { Text("Phone Number", fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(6.dp),
                enabled = !isPrinting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            ); Spacer(Modifier.height(12.dp))

            if (previousBalance > 0.0) {
                if (form.previousBalance > 0) {
                    TextButton(
                        onClick = {
                            // Sirf CURRENT bill ka grandTotal set karo. Purana balance alag hai.
                            form.received = form.grandTotal.toInputString()
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("Pay Full Bill", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Old Pending: Rs ${formatMoney(previousBalance)}",
                    color = Color(0xFFE65100),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    "Old pending ko History screen se alag 'Receive Payment' se clear karein.",
                    fontSize = 10.sp, color = Color.Gray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
        HorizontalDivider(color = Color(0xFFE0E0E0))
        Column(Modifier.padding(24.dp).fillMaxWidth()) {
            Text("Payment Details", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50)); Spacer(
            Modifier.height(12.dp)
        )
            FinancialAdjustmentsBlock(form, enabled = !isPrinting); Spacer(Modifier.height(16.dp))
            DisplayOptionsBlock(form, onOptionChange = { viewModel.setDisplayOption(it) })
        }
        HorizontalDivider(color = Color(0xFFE0E0E0))
        Column(Modifier.padding(24.dp).fillMaxWidth()) {
            AmountBreakdown(form.subtotal, form.discountValue, form.extraValue)
            if (form.balance > 0.005) {
                Spacer(Modifier.height(8.dp)); BalanceBanner(form.receivedValue, form.balance)
            }

            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    form.shouldPrint = !form.shouldPrint; scope.launch { viewModel.saveShouldPrint(form.shouldPrint) }
                }) {
                Checkbox(
                    checked = form.shouldPrint,
                    onCheckedChange = { form.shouldPrint = it; scope.launch { viewModel.saveShouldPrint(it) } },
                    colors = CheckboxDefaults.colors(checkedColor = AppGreen)
                )
                Text("Print Bill", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onPrint,
                enabled = !isPrinting,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
            ) {
                Icon(
                    Icons.Default.Print,
                    null
                ); Spacer(Modifier.width(8.dp)); Text(
                if (form.shouldPrint) "PRINT & SAVE BILL" else "SAVE BILL (NO PRINT)",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            }
            Spacer(Modifier.height(8.dp)); TextButton(
            onClick = onReset,
            enabled = !isPrinting,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Reset All Fields", color = Color.Red, fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
private fun MobileHeaderBlock(
    form: BillingFormState,
    onOpenHistory: () -> Unit,
    onOpenItemReport: () -> Unit,
    onOpenManageItems: () -> Unit,
    onOpenVendors: () -> Unit,
    onOpenPurchases: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    onLogout: () -> Unit,
    userName: String,
    isDeveloper: Boolean,
    enabled: Boolean = true,
    onManageCustomers: () -> Unit,
    previousBalance: Double = 0.0,
    accounts: List<CustomerAccount>,
    onEditAccount: (CustomerAccount) -> Unit,
    onDeleteAccount: (CustomerAccount) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Billing System",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = AppGreen
        ); Spacer(Modifier.height(8.dp))

        CustomerSelector(
            accounts = accounts,
            selected = form.selectedCustomerAccount,
            onSelect = {
                form.selectedCustomerAccount = it
                if (it != null) {
                    form.customer = it.name
                    form.phone = it.phone1
                    form.phone2 = it.phone2
                    form.address = it.address
                    form.accountNo = it.accountNo
                } else {
                    form.customer = ""
                    form.phone = ""
                    form.phone2 = ""
                    form.address = ""
                    form.accountNo = ""
                    form.previousBill = null
                    form.previousBalance = 0.0
                }
            },
        )

        OutlinedTextField(
            value = form.customer,
            onValueChange = { form.customer = it },
            label = { Text("Customer Name") },
            shape = RoundedCornerShape(10.dp),
            leadingIcon = { Icon(Icons.Default.Person, null) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = form.phone,
            onValueChange = { form.phone = sanitizePhone(it) },
            label = { Text("Customer Number 1") },
            shape = RoundedCornerShape(10.dp),
            leadingIcon = { Icon(Icons.Default.Phone, null) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )

        if (previousBalance > 0.0) {
            Text(
                "Old Pending: Rs ${formatMoney(previousBalance)}",
                color = Color(0xFFE65100),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onOpenHistory, enabled = enabled) { Text("History", fontSize = 12.sp) }
            OutlinedButton(onClick = onOpenVendors, enabled = enabled) { Text("Vendors", fontSize = 12.sp) }
            OutlinedButton(onClick = onOpenPurchases, enabled = enabled) { Text("Purchases", fontSize = 12.sp) }
            OutlinedButton(onClick = onOpenManageItems, enabled = enabled) { Text("Products", fontSize = 12.sp) }
            OutlinedButton(onClick = onOpenItemReport, enabled = enabled) { Text("Reports", fontSize = 12.sp) }
            OutlinedButton(onClick = onOpenBackupRestore, enabled = enabled) {
                Text("Data", fontSize = 12.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Person, null, tint = AppGreen); Spacer(Modifier.width(6.dp)); Text(
            userName,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        ); Spacer(Modifier.weight(1f)); TextButton(onClick = onLogout, enabled = enabled) {
            Text(
                "Logout",
                color = Color.Red
            )
        }
        }
    }
}

@Composable
private fun MobileActionBlock(
    total: Double,
    summary: String,
    enabled: Boolean,
    onPrint: () -> Unit,
    onReset: () -> Unit,
    shouldPrint: Boolean
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "GRAND TOTAL",
                fontSize = 10.sp,
                color = AppGreen,
                fontWeight = FontWeight.Bold
            ); Text("Rs ${formatMoney(total)}", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(
            summary,
            fontSize = 9.sp,
            color = Color.Gray
        )
        }
        TextButton(onClick = onReset, enabled = enabled) { Text("Reset", color = Color.Red) }
        Button(
            onClick = onPrint,
            enabled = enabled,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF212121))
        ) { Text(if (shouldPrint) "PRINT" else "SAVE", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun FinancialAdjustmentsBlock(form: BillingFormState, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Discount, Extra & Payment", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DiscountInput(
                value = form.discount,
                onValueChange = { form.discount = it },
                isPercent = form.discountIsPercent,
                onModeChange = { form.discountIsPercent = it; form.discount = "" },
                computedAmount = form.discountValue,
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = form.extraCharges,
                onValueChange = { form.extraCharges = sanitizeDecimal(it) },
                label = { Text("Extra/Misc (Rs)", fontSize = 12.sp) },
                singleLine = true,
                enabled = enabled,
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = form.received,
            onValueChange = { form.received = sanitizeDecimal(it) },
            label = { Text("Received Amount (Rs)") },
            supportingText = { Text("Blank = nothing received. Tap Full if fully paid.") },
            trailingIcon = {
                TextButton(
                    onClick = { form.received = form.grandTotal.toInputString() },
                    enabled = enabled && form.grandTotal > 0.0,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("Full", fontWeight = FontWeight.Bold) }
            },
            singleLine = true,
            enabled = enabled,
            shape = RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DisplayOptionsBlock(form: BillingFormState, onOptionChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Invoice Display (Select One)",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.DarkGray
        ); Text("Applies to all new invoices until you change it.", fontSize = 11.sp, color = Color.Gray)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = form.showProduct,
                    onClick = { onOptionChange("product") }); Text("Show Product Name", fontSize = 13.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = form.showCategory,
                    onClick = { onOptionChange("category") }); Text("Show Category", fontSize = 13.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = form.showSubcategory,
                    onClick = { onOptionChange("subcategory") }); Text("Show Subcategory", fontSize = 13.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = form.showGroup, onClick = { onOptionChange("group") })
                Text("Show Group", fontSize = 13.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = form.showSubgroup, onClick = { onOptionChange("subgroup") })
                Text("Show Subgroup", fontSize = 13.sp)
            }
        }
    }
}

private fun LazyListScope.roomItems(
    form: BillingFormState,
    onAddItem: (RoomState) -> Unit,
    onDelete: (RoomState) -> Unit
) {
    itemsIndexed(items = form.rooms, key = { _, room -> room.id }) { index, room ->
        RoomCard(
            index,
            room,
            onAddItem = { onAddItem(room) },
            onDelete = onDelete
        )
    }
}

@Composable
private fun RoomCard(index: Int, room: RoomState, onAddItem: () -> Unit, onDelete: (RoomState) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (index == 0) {
                    Box(
                        Modifier.size(36.dp).background(AppGreen, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Receipt, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                } else {
                    IconButton(onClick = { onDelete(room) }) {
                        Icon(
                            Icons.Default.Delete,
                            "Delete bill",
                            tint = Color.Red,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = room.name,
                    onValueChange = { room.name = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (room.name.isEmpty()) Text(
                                "All Items",
                                fontSize = 16.sp,
                                color = Color.Gray
                            ); inner()
                        }
                    })
                Text("Rs ${formatMoney(room.total)}", color = AppGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                IconButton(onClick = {
                    room.expanded = !room.expanded
                }) {
                    Icon(
                        if (room.expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        "Expand"
                    )
                }
            }
            if (room.expanded) {
                HorizontalDivider(color = Color(0xFFEEEEEE))
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (room.items.isEmpty()) Text(
                        "No items added yet",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            room.items.forEachIndexed { index, item ->
                                key(item) {
                                    val occurrence = room.items.take(index + 1).count { it.name == item.name }
                                    ItemEntryCard(
                                        name = item.name,
                                        occurrence = occurrence,
                                        qty = item.qty,
                                        discount = item.discount,
                                        discountIsPercent = item.discountIsPercent,
                                        onDiscountChange = { item.discount = it },
                                        onQtyChange = { item.qty = it },
                                        rate = item.rate,
                                        onRateChange = { item.rate = it },
                                        total = item.total,
                                        isIncomplete = item.isIncomplete,
                                        unit = item.unit,
                                        displayType = item.displayType,
                                        onDisplayTypeChange = { item.displayType = it },
                                        productName = item.productName,
                                        categoryName = item.categoryName,
                                        subcategoryName = item.subcategoryName,
                                        groupName = item.groupName,
                                        subgroupName = item.subgroupName,
                                        stock = item.stock,
                                        productDiscountPct = item.productDiscountPct,
                                        onDuplicate = { room.duplicateItem(item) },
                                        onDelete = { room.removeItem(item) })
                                }
                            }
                        }
                    }
                    TextButton(onClick = onAddItem) {
                        Icon(
                            Icons.Default.Add,
                            null,
                            tint = AppGreen,
                            modifier = Modifier.size(18.dp)
                        ); Spacer(Modifier.width(6.dp)); Text(
                        "Add item",
                        color = AppGreen,
                        fontWeight = FontWeight.Bold
                    )
                    }
                }
            }
        }
    }
}

private fun validateForm(form: BillingFormState): Pair<String, String>? {
    if (form.customer.isBlank()) return "Missing Data" to "Customer name is required"
    if (form.phone.isBlank()) return "Missing Data" to "Customer Number is required"
    if (!form.hasAnyItem) return "Missing Data" to "Enter the quantity and rate for at least one item."
    for (room in form.rooms) {
        for (item in room.items) {
            if (item.isIncomplete) {
                val sectionName =
                    room.name.ifBlank { "All Items" }; return "Incomplete Item" to "\"$sectionName\" -> \"${item.name}\" qty or rate is blank."
            }
        }
    }
    if (form.discountValue > form.subtotal) return "Discount Error" to "Discount is greater then subtotal"
    if (form.receivedValue > form.grandTotal + 0.005) return "Received Error" to "Received amount is greater than total"
    return null
}