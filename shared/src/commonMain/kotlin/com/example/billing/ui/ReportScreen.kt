package com.example.billing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billing.AppServices
import com.example.billing.data.BillRepository
import com.example.billing.data.VendorRepository
import com.example.billing.model.*
import com.example.billing.pdf.PdfPrinter
import com.example.billing.pdf.PdfRow
import com.example.billing.pdf.RowKind
import com.example.billing.utils.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private enum class ReportTab { SALE, STOCK, PURCHASE, VENDOR, CUSTOMER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(billRepo: BillRepository, printer: PdfPrinter, onBack: () -> Unit) {
    PlatformBackHandler { onBack() }
    val services = koinInject<AppServices>()
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(ReportTab.SALE) }
    // ReportScreen.kt (Top level, Scaffold se pehle)
    var selectedVendor by remember { mutableStateOf<Vendor?>(null) }
    var vPayments by remember { mutableStateOf<List<VendorPayment>>(emptyList()) }
    var currentBalance by remember { mutableStateOf(0.0) }

    var selectedCustomer by remember { mutableStateOf<CustomerAccount?>(null) }
    var cPayments by remember { mutableStateOf<List<VendorRepository.CustomerPayment>>(emptyList()) }
    var customerCurrentBalance by remember { mutableStateOf(0.0) }



    val today = nowIso().take(10)
    var fromDate by remember { mutableStateOf(today) }
    var toDate by remember { mutableStateOf(today) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    var bills by remember { mutableStateOf<List<Bill>>(emptyList()) }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var purchases by remember { mutableStateOf<List<Purchase>>(emptyList()) }
    var salesmen by remember { mutableStateOf<List<CustomerAccount>>(emptyList()) }
    var vendors by remember { mutableStateOf<List<VendorWithBalance>>(emptyList()) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var subcategories by remember { mutableStateOf<List<Subcategory>>(emptyList()) }

    var selectedSalesmen by remember { mutableStateOf<List<CustomerAccount>>(emptyList()) }
    var selectedVendors by remember { mutableStateOf<List<Vendor>>(emptyList()) }
    var selectedCategories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var selectedSubcategories by remember { mutableStateOf<List<Subcategory>>(emptyList()) }
    var selectedProducts by remember { mutableStateOf<List<Product>>(emptyList()) }

    LaunchedEffect(Unit) {
        bills = billRepo.getAllBills()
        products = services.vendors.listProductsWithStock()
        purchases = services.vendors.listPurchases()
        val counterSale = CustomerAccount(-1L, "Counter Sale", "", "", "", "")
        salesmen = listOf(counterSale) + services.vendors.listCustomerAccounts()
        categories = services.vendors.listCategories()
        subcategories = services.vendors.listSubcategories()
        vendors = services.vendors.listVendors()
    }
    // Ye LaunchedEffect top par rakhein taake Print button ko data mil sake
    LaunchedEffect(selectedVendor, fromDate, toDate) {
        if (selectedVendor != null) {
            vPayments = services.vendors.paymentsFor(selectedVendor!!.id)
                .filter { it.date.take(10) in fromDate..toDate }
            currentBalance = services.vendors.getVendorBalance(selectedVendor!!.id)
        } else {
            vPayments = emptyList()
            currentBalance = 0.0
        }
    }

    LaunchedEffect(selectedCustomer, fromDate, toDate) {
        if (selectedCustomer != null) {
            cPayments = services.vendors.getCustomerPayments(selectedCustomer!!.id).filter { it.date.take(10) in fromDate..toDate }
            customerCurrentBalance = services.vendors.getCustomerBalance(selectedCustomer!!.id)
        } else {
            cPayments = emptyList()
            customerCurrentBalance = 0.0
        }
    }
    if (showFromPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = isoDayToEpochMillis(fromDate))
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        fromDate = epochMillisToIsoDay(it)
                    }; showFromPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
    if (showToPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = isoDayToEpochMillis(toDate))
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        toDate = epochMillisToIsoDay(it)
                    }; showToPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    Scaffold(
        containerColor = Color(0xFFF4F6F8),
        topBar = {
            Surface(color = Color.White, shadowElevation = 2.dp) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                "Back",
                                tint = Color(0xFF2C3E50)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Reports & Analytics",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2C3E50)
                        )

                        Spacer(Modifier.weight(1f))


                        // NAYA: Refresh button
                        IconButton(onClick = {
                            scope.launch {
                                bills = billRepo.getAllBills()
                                products = services.vendors.listProductsWithStock()
                                purchases = services.vendors.listPurchases()
                                salesmen = listOf(CustomerAccount(-1L, "Counter Sale", "", "", "", "")) + services.vendors.listCustomerAccounts()
                                categories = services.vendors.listCategories()
                                subcategories = services.vendors.listSubcategories()
                                vendors = services.vendors.listVendors()
                            }
                        }) { Icon(Icons.Default.Refresh, "Refresh", tint = Color.DarkGray) }

                        // Print Button
                        Button(
                            onClick = {
                                scope.launch {
                                    val rows = mutableListOf<PdfRow>()
                                    rows += PdfRow(left = "REPORT", bold = true, fontSize = 18f)
                                    rows += PdfRow(
                                        left = "From: ${isoDayToDisplay(fromDate)} To: ${
                                            isoDayToDisplay(
                                                toDate
                                            )
                                        }"
                                    )
                                    rows += PdfRow(kind = RowKind.LINE)

                                    when (tab) {
                                        ReportTab.SALE -> {
                                            val filteredBills = bills.filter { b ->
                                                val dateOk = b.date.take(10) in fromDate..toDate
                                                val salesOk = if (selectedSalesmen.isEmpty()) true
                                                else if (b.salesmanId == null) selectedSalesmen.any { it.id == -1L }
                                                else selectedSalesmen.any { it.id == b.salesmanId }
                                                val itemsOk = if (selectedCategories.isEmpty() && selectedSubcategories.isEmpty() && selectedProducts.isEmpty()) true
                                                else b.rooms.any { room -> room.items.any { item ->
                                                    val p = products.firstOrNull { it.id == item.productId }
                                                    val cOk = selectedCategories.isEmpty() || selectedCategories.any { it.id == p?.categoryId }
                                                    val sOk = selectedSubcategories.isEmpty() || selectedSubcategories.any { it.id == p?.subcategoryId }
                                                    val pOk = selectedProducts.isEmpty() || selectedProducts.any { it.id == p?.id }
                                                    cOk && sOk && pOk
                                                } }
                                                dateOk && salesOk && itemsOk
                                            }
                                            rows += PdfRow(left = "SALE REPORT", bold = true)
                                            var total = 0.0
                                            filteredBills.forEach { b ->
                                                rows += PdfRow(left = "${b.invoiceText} | ${b.customer}", right = "Rs ${formatMoney(b.grandTotal)}")
                                                total += b.grandTotal
                                            }
                                            rows += PdfRow(right = "Total Sales: Rs ${formatMoney(total)}", bold = true)
                                        }

                                        ReportTab.STOCK -> {
                                            val filteredProducts = products.filter { p ->
                                                (selectedCategories.isEmpty() || selectedCategories.any { it.id == p.categoryId }) &&
                                                        (selectedSubcategories.isEmpty() || selectedSubcategories.any { it.id == p.subcategoryId }) &&
                                                        (selectedProducts.isEmpty() || selectedProducts.any { it.id == p.id })
                                            }
                                            rows += PdfRow(left = "STOCK REPORT", bold = true)
                                            filteredProducts.forEach { p ->
                                                rows += PdfRow(
                                                    left = p.name,
                                                    qty = "Qty: ${formatQty(p.liveStock ?: p.openingStock)} ${p.unit}",
                                                    right = "Rate: Rs ${formatMoney(p.salePrice)}"
                                                )
                                            }
                                        }

                                        ReportTab.PURCHASE -> {
                                            val filteredPurchases = purchases.filter { it.date.take(10) in fromDate..toDate }
                                            rows += PdfRow(left = "PURCHASE REPORT", bold = true)
                                            var total = 0.0
                                            filteredPurchases.forEach { p ->
                                                rows += PdfRow(left = "${p.vendorName} | ${p.items.size} items", right = "Rs ${formatMoney(p.payable)}")   // ✅
                                                total += p.payable                                                                                            // ✅
                                            }
                                            rows += PdfRow(right = "Total Purchases: Rs ${formatMoney(total)}", bold = true)
                                        }
                                        // NAYA: VENDOR TAB PRINT
                                        // ReportScreen.kt (Print Button onClick ke andar)
                                        ReportTab.VENDOR -> {
                                            if (selectedVendor != null) {
                                                val vPurchases = purchases.filter { it.vendorId == selectedVendor!!.id && it.date.take(10) in fromDate..toDate }
                                                val vPayments = services.vendors.paymentsFor(selectedVendor!!.id).filter { it.date.take(10) in fromDate..toDate }
                                                rows += PdfRow(left = "VENDOR STATEMENT: ${selectedVendor!!.name}", bold = true, fontSize = 16f)
                                                rows += PdfRow(left = "From: ${isoDayToDisplay(fromDate)} To: ${isoDayToDisplay(toDate)}", fontSize = 12f)
                                                rows += PdfRow(kind = RowKind.LINE)

                                                rows += PdfRow(left = "--- PURCHASES ---", bold = true)
                                                vPurchases.forEach { p ->
                                                    rows += PdfRow(left = "Date: ${displayDate(p.date)} | Items: ${p.items.size}", right = "Rs ${formatMoney(p.payable)}")
                                                }

                                                rows += PdfRow(kind = RowKind.SPACE)
                                                rows += PdfRow(left = "--- PAYMENTS ---", bold = true)
                                                vPayments.forEach { p ->
                                                    rows += PdfRow(left = "Date: ${displayDate(p.date)}", right = "Rs ${formatMoney(p.amount + p.discount)}")
                                                }
                                                rows += PdfRow(kind = RowKind.LINE)
                                                rows += PdfRow(right = "Current Balance: Rs ${formatMoney(currentBalance)}", bold = true, fontSize = 14f)
                                            } else {
                                                rows += PdfRow(left = "No vendor selected.")
                                            }
                                        }

                                        // NAYA: CUSTOMER TAB PRINT
                                        ReportTab.CUSTOMER -> {
                                            if (selectedCustomer != null) {
                                                val cBills = bills.filter { it.salesmanId == selectedCustomer!!.id && it.date.take(10) in fromDate..toDate }
                                                val cPayments = services.vendors.getCustomerPayments(selectedCustomer!!.id).filter { it.date.take(10) in fromDate..toDate }
                                                rows += PdfRow(left = "Customer: ${selectedCustomer?.name}", bold = true, fontSize = 16f)
                                                rows += PdfRow(left = "From: ${isoDayToDisplay(fromDate)} To: ${isoDayToDisplay(toDate)}", fontSize = 12f)
                                                rows += PdfRow(kind = RowKind.LINE)

                                                rows += PdfRow(left = "--- BILLS ---", bold = true)
                                                cBills.forEach { b ->
                                                    rows += PdfRow(left = "Bill ${b.invoiceText} | ${displayDate(b.date)}", right = "Rs ${formatMoney(b.grandTotal)}")
                                                }

                                                rows += PdfRow(kind = RowKind.SPACE)
                                                rows += PdfRow(left = "--- PAYMENTS ---", bold = true)
                                                cPayments.forEach { p ->
                                                    rows += PdfRow(left = "Date: ${displayDate(p.date)}", right = "Rs ${formatMoney(p.amount + p.discount)}")
                                                }
                                                rows += PdfRow(kind = RowKind.LINE)
                                                rows += PdfRow(right = "Current Due: Rs ${formatMoney(customerCurrentBalance)}", bold = true, fontSize = 14f)
                                            } else {
                                                rows += PdfRow(left = "No customer selected.")
                                            }
                                        }
                                    }
                                    printer.print("Report_${tab.name}", rows)
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = "Print", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Print", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    TabRow(selectedTabIndex = tab.ordinal, containerColor = Color.White) {
                        Tab(
                            selected = tab == ReportTab.SALE,
                            onClick = { tab = ReportTab.SALE },
                            text = { Text("Sale") })
                        Tab(
                            selected = tab == ReportTab.STOCK,
                            onClick = { tab = ReportTab.STOCK },
                            text = { Text("Stock") })
                        Tab(
                            selected = tab == ReportTab.PURCHASE,
                            onClick = { tab = ReportTab.PURCHASE },
                            text = { Text("Purchase") })
                        Tab(
                            selected = tab == ReportTab.VENDOR,
                            onClick = { tab = ReportTab.VENDOR },
                            text = { Text("Vendor") })
                        Tab(
                            selected = tab == ReportTab.CUSTOMER,
                            onClick = { tab = ReportTab.CUSTOMER },
                            text = { Text("Customer") })
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                ReportTab.SALE -> SaleReportTab(
                    bills, salesmen, categories, subcategories, products, fromDate, toDate,
                    { showFromPicker = true }, { showToPicker = true },
                    selectedSalesmen, { selectedSalesmen = it },
                    selectedCategories, { selectedCategories = it },
                    selectedSubcategories, { selectedSubcategories = it },
                    selectedProducts, { selectedProducts = it }
                )

                ReportTab.STOCK -> StockReportTab(
                    products, categories, subcategories,
                    selectedCategories, { selectedCategories = it },
                    selectedSubcategories, { selectedSubcategories = it },
                    selectedProducts, { selectedProducts = it }
                )

                ReportTab.PURCHASE -> PurchaseReportTab(
                    purchases, vendors, categories, subcategories, products, fromDate, toDate,
                    { showFromPicker = true }, { showToPicker = true },
                    selectedVendors, { selectedVendors = it },
                    selectedCategories, { selectedCategories = it },
                    selectedSubcategories, { selectedSubcategories = it },
                    selectedProducts, { selectedProducts = it }
                )

                ReportTab.VENDOR -> {
                                  Column(Modifier.fillMaxSize()) {
                        FilterSection<Vendor, Unit, Unit, Unit>(
                            fromDate,
                            toDate,
                            { showFromPicker = true },
                            { showToPicker = true },
                            "Vendors",
                            vendors.map { it.vendor },
                            if (selectedVendor != null) listOf(selectedVendor!!) else emptyList(),
                            { list -> selectedVendor = list.firstOrNull() },
                            { it.name },
                            label2 = null,
                            label3 = null,
                            label4 = null
                        )
                        if (selectedVendor != null) {
                            val vPurchases =
                                purchases.filter { it.vendorId == selectedVendor!!.id && it.date.take(10) in fromDate..toDate }
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    val totalPayable = vPurchases.sumOf { it.payable }
                                    val paidAtPurchase = vPurchases.sumOf { it.paid }
                                    val paidSeparate = vPayments.sumOf { it.amount + it.discount }
                                    val totalPaid = paidAtPurchase + paidSeparate

                                    Card(Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(selectedVendor!!.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)

                                            Text("Period Purchases: Rs ${formatMoney(totalPayable)}")
                                            Text("Period Paid: Rs ${formatMoney(totalPaid)}")
                                            Text(
                                                "(with purchase: Rs ${formatMoney(paidAtPurchase)}, separately: Rs ${formatMoney(paidSeparate)})",
                                                fontSize = 11.sp, color = Color.Gray
                                            )

                                            HorizontalDivider(Modifier.padding(vertical = 4.dp))

                                            if (currentBalance > 0.005) {
                                                Text("Current Balance: Rs ${formatMoney(currentBalance)}", color = DueColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            } else if (currentBalance < -0.005) {
                                                Text("Advance: Rs ${formatMoney(-currentBalance)}", color = PaidColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            } else {
                                                Text("Settled", color = PaidColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            }
                                        }
                                    }
                                }
                                items(vPurchases) { p ->
                                    Card(Modifier.fillMaxWidth()) {
                                        Row(
                                            Modifier.padding(16.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Purchase #${p.id} (${displayDate(p.date)})"); Text(
                                            "Rs ${formatMoney(p.total)}",
                                            fontWeight = FontWeight.Bold
                                        )
                                        }
                                    }
                                }
                                item { Text("Payments", fontWeight = FontWeight.Bold) }
                                items(vPayments) { p ->
                                    Card(Modifier.fillMaxWidth()) {
                                        Row(
                                            Modifier.padding(16.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Paid on ${displayDate(p.date)}"); Text(
                                            "Rs ${formatMoney(p.amount + p.discount)}",
                                            color = AppGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                        }
                                    }
                                }
                            }
                        } else {
                            EmptyState("Select a vendor to view report.")
                        }
                    }
                }

                ReportTab.CUSTOMER -> {
                    Column(Modifier.fillMaxSize()) {
                        FilterSection<CustomerAccount, Unit, Unit, Unit>(
                            fromDate,
                            toDate,
                            { showFromPicker = true },
                            { showToPicker = true },
                            "Customers",
                            salesmen,
                            if (selectedCustomer != null) listOf(selectedCustomer!!) else emptyList(),
                            { list -> selectedCustomer = list.firstOrNull() },
                            { it.name },
                            label2 = null,
                            label3 = null,
                            label4 = null
                        )
                        if (selectedCustomer != null) {
                            val cBills =
                                bills.filter { it.salesmanId == selectedCustomer!!.id && it.date.take(10) in fromDate..toDate }
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    val periodSales = cBills.sumOf { it.grandTotal }
                                    val periodReceived = cBills.sumOf { it.received } + cPayments.sumOf { it.amount + it.discount }

                                    Card(Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(selectedCustomer!!.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                            Text("Period Sales: Rs ${formatMoney(periodSales)}")
                                            Text("Period Received: Rs ${formatMoney(periodReceived)}")

                                            HorizontalDivider(Modifier.padding(vertical = 4.dp))

                                            if (customerCurrentBalance > 0.005) {
                                                Text("Current Due: Rs ${formatMoney(customerCurrentBalance)}", color = DueColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            } else if (customerCurrentBalance < -0.005) {
                                                Text("Advance: Rs ${formatMoney(-customerCurrentBalance)}", color = PaidColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            } else {
                                                Text("Settled", color = PaidColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            }
                                        }
                                    }
                                }
                                items(cBills) { b ->
                                    Card(Modifier.fillMaxWidth()) {
                                        Row(
                                            Modifier.padding(16.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Bill ${b.invoiceText} (${displayDate(b.date)})"); Text(
                                            "Rs ${formatMoney(b.grandTotal)}",
                                            fontWeight = FontWeight.Bold
                                        )
                                        }
                                    }
                                }
                                item { Text("Separate Payments", fontWeight = FontWeight.Bold) }
                                items(cPayments) { p ->
                                    Card(Modifier.fillMaxWidth()) {
                                        Row(
                                            Modifier.padding(16.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Paid on ${displayDate(p.date)}"); Text(
                                            "Rs ${formatMoney(p.amount + p.discount)}",
                                            color = AppGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                        }
                                    }
                                }
                            }
                        } else {
                            EmptyState("Select a customer to view report.")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SaleReportTab(
    bills: List<Bill>,
    salesmen: List<CustomerAccount>,
    categories: List<Category>,
    subcategories: List<Subcategory>,
    products: List<Product>,
    fromDate: String,
    toDate: String,
    onFromClick: () -> Unit,
    onToClick: () -> Unit,
    selectedSalesmen: List<CustomerAccount>,
    onSalesmenChange: (List<CustomerAccount>) -> Unit,
    selectedCategories: List<Category>,
    onCategoriesChange: (List<Category>) -> Unit,
    selectedSubcategories: List<Subcategory>,
    onSubcategoriesChange: (List<Subcategory>) -> Unit,
    selectedProducts: List<Product>,
    onProductsChange: (List<Product>) -> Unit
) {
    val filtered = bills.filter { bill ->
        val dateOk = bill.date.take(10) in fromDate..toDate
        val salesOk = if (selectedSalesmen.isEmpty()) true else {
            if (bill.salesmanId == null) selectedSalesmen.any { it.id == -1L }
            else selectedSalesmen.any { it.id == bill.salesmanId }
        }

        val itemsOk =
            if (selectedCategories.isEmpty() && selectedSubcategories.isEmpty() && selectedProducts.isEmpty()) true else {
                bill.rooms.any { room ->
                    room.items.any { item ->
                        val p = products.firstOrNull { it.id == item.productId }
                        val cOk = selectedCategories.isEmpty() || selectedCategories.any { it.id == p?.categoryId }
                        val sOk =
                            selectedSubcategories.isEmpty() || selectedSubcategories.any { it.id == p?.subcategoryId }
                        val pOk = selectedProducts.isEmpty() || selectedProducts.any { it.id == p?.id }
                        cOk && sOk && pOk
                    }
                }
            }
        dateOk && salesOk && itemsOk
    }

    Column(Modifier.fillMaxSize()) {
        FilterSection(
            fromDate, toDate, onFromClick, onToClick,
            "Customer IDs", salesmen, selectedSalesmen, onSalesmenChange, { it.name },
            "Categories", categories, selectedCategories, onCategoriesChange, { it.name },
            "Subcategories", subcategories, selectedSubcategories, onSubcategoriesChange, { it.name },
            "Products", products, selectedProducts, onProductsChange, { it.name }
        )

        Surface(
            color = AppGreen.copy(alpha = 0.1f),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Total Bills: ${filtered.size}",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                    fontSize = 14.sp
                )
                Text(
                    "Amount: Rs ${formatMoney(filtered.sumOf { it.grandTotal })}",
                    fontWeight = FontWeight.Black,
                    color = AppGreen,
                    fontSize = 16.sp
                )
            }
        }

        if (filtered.isEmpty()) {
            EmptyState("No Sale records found for the selected filters.")
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filtered, key = { it.id }) { bill ->
                    val salesmanName =
                        if (bill.salesmanId == null) "Counter Sale" else salesmen.firstOrNull { it.id == bill.salesmanId }?.name
                            ?: "Unknown"
                    val initial = bill.customer.trim().take(1).uppercase().ifBlank { "?" }
                    val isPending = bill.balance > 0.005

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(44.dp).background(
                                    if (isPending) DueColor.copy(alpha = 0.1f) else AppGreen.copy(alpha = 0.1f),
                                    CircleShape
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    initial,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = if (isPending) DueColor else AppGreen
                                )
                            }
                            Spacer(Modifier.width(16.dp))

                            Column(Modifier.weight(1f)) {
                                Text(
                                    bill.customer,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFF1E293B)
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        bill.invoiceText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.DarkGray
                                    )
                                    Text(" • ", fontSize = 12.sp, color = Color.Gray)
                                    Text(displayDate(bill.date), fontSize = 12.sp, color = Color.Gray)
                                }
                                Text(
                                    "ID: $salesmanName",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "Rs ${formatMoney(bill.grandTotal)}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(Modifier.height(4.dp))
                                Surface(
                                    color = if (isPending) DueColor.copy(alpha = 0.1f) else PaidColor.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        if (isPending) "Due: Rs ${formatMoney(bill.balance)}" else "PAID",
                                        color = if (isPending) DueColor else PaidColor,
                                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StockReportTab(
    products: List<Product>, categories: List<Category>, subcategories: List<Subcategory>,
    selectedCategories: List<Category>, onCategoriesChange: (List<Category>) -> Unit,
    selectedSubcategories: List<Subcategory>, onSubcategoriesChange: (List<Subcategory>) -> Unit,
    selectedProducts: List<Product>, onProductsChange: (List<Product>) -> Unit
) {
    val filtered = products.filter { p ->
        val cOk = selectedCategories.isEmpty() || selectedCategories.any { it.id == p.categoryId }
        val sOk = selectedSubcategories.isEmpty() || selectedSubcategories.any { it.id == p.subcategoryId }
        val pOk = selectedProducts.isEmpty() || selectedProducts.any { it.id == p.id }
        cOk && sOk && pOk
    }

    Column(Modifier.fillMaxSize()) {
        FilterSection<Any, Category, Subcategory, Product>(
            "", "", {}, {},
            null, emptyList(), emptyList(), {}, { "" },
            "Categories", categories, selectedCategories, onCategoriesChange, { it.name },
            "Subcategories", subcategories, selectedSubcategories, onSubcategoriesChange, { it.name },
            "Products", products, selectedProducts, onProductsChange, { it.name },
            hideDate = true
        )

        if (filtered.isEmpty()) {
            EmptyState("No products found matching your filter.")
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.id }) { p ->
                    val liveStock = p.liveStock ?: p.openingStock
                    val lowStockThreshold = 5.0   // ← yahan 5 ki jagah koi bhi value
                    val isLowStock = liveStock <= lowStockThreshold

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).background(Color(0xFFF1F5F9), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Inventory, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(16.dp))

                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E293B))
                                val catText = listOfNotNull(
                                    p.categoryName.takeIf { it.isNotBlank() },
                                    p.subcategoryName.takeIf { it.isNotBlank() }
                                ).joinToString(" › ")
                                if (catText.isNotBlank()) {
                                    Text(catText, fontSize = 11.sp, color = Color.Gray)
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "Rs ${formatMoney(p.price)}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                                Spacer(Modifier.height(4.dp))
                                Surface(
                                    color = if (isLowStock) Color.Red.copy(alpha = 0.1f) else AppGreen.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "${formatQty(liveStock)} ${p.unit}",
                                        color = if (isLowStock) Color.Red else AppGreen,
                                        fontSize = 13.sp, fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PurchaseReportTab(
    purchases: List<Purchase>,
    vendors: List<VendorWithBalance>,
    categories: List<Category>,
    subcategories: List<Subcategory>,
    products: List<Product>,
    fromDate: String,
    toDate: String,
    onFromClick: () -> Unit,
    onToClick: () -> Unit,
    selectedVendors: List<Vendor>,
    onVendorsChange: (List<Vendor>) -> Unit,
    selectedCategories: List<Category>,
    onCategoriesChange: (List<Category>) -> Unit,
    selectedSubcategories: List<Subcategory>,
    onSubcategoriesChange: (List<Subcategory>) -> Unit,
    selectedProducts: List<Product>,
    onProductsChange: (List<Product>) -> Unit
) {
    val filtered = purchases.filter { p ->
        val dateOk = p.date.take(10) in fromDate..toDate
        val vendOk = selectedVendors.isEmpty() || selectedVendors.any { it.id == p.vendorId }

        val itemsOk =
            if (selectedCategories.isEmpty() && selectedSubcategories.isEmpty() && selectedProducts.isEmpty()) true else {
                p.items.any { item ->
                    val prod = products.firstOrNull { it.id == item.productId }
                    val cOk = selectedCategories.isEmpty() || selectedCategories.any { it.id == prod?.categoryId }
                    val sOk =
                        selectedSubcategories.isEmpty() || selectedSubcategories.any { it.id == prod?.subcategoryId }
                    val pOk = selectedProducts.isEmpty() || selectedProducts.any { it.id == prod?.id }
                    cOk && sOk && pOk
                }
            }
        dateOk && vendOk && itemsOk
    }

    Column(Modifier.fillMaxSize()) {
        FilterSection(
            fromDate, toDate, onFromClick, onToClick,
            "Vendors", vendors.map { it.vendor }, selectedVendors, onVendorsChange, { it.name },
            "Categories", categories, selectedCategories, onCategoriesChange, { it.name },
            "Subcategories", subcategories, selectedSubcategories, onSubcategoriesChange, { it.name },
            "Products", products, selectedProducts, onProductsChange, { it.name }
        )

        if (filtered.isEmpty()) {
            EmptyState("No Purchase records found.")
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filtered, key = { it.id }) { pu ->
                    val isPending = pu.balance > 0.005

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(32.dp).background(Color(0xFFE0F2FE), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.LocalShipping,
                                            null,
                                            tint = Color(0xFF0284C7),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        pu.vendorName.ifBlank { "Unknown Vendor" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color(0xFF1E293B)
                                    )
                                }
                                Text("Rs ${formatMoney(pu.payable)}", color = Color(0xFF1565C0), fontWeight = FontWeight.Black, fontSize = 16.sp)
                            }
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            Spacer(Modifier.height(12.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Date: ${displayDate(pu.date)} • ${pu.items.size} Items",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                if (isPending) {
                                    Text(
                                        "Due: Rs ${formatMoney(pu.balance)}",
                                        color = DueColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Text("Settled", color = PaidColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.SearchOff, null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text(message, color = Color.Gray, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ✅ NAYA FIXED FilterSection (FilterChip ke saath)
@Composable
fun <T1, T2, T3, T4> FilterSection(
    fromDate: String,
    toDate: String,
    onFromClick: () -> Unit,
    onToClick: () -> Unit,
    label1: String?,
    items1: List<T1>,
    selected1: List<T1>,
    onSelect1: (List<T1>) -> Unit,
    name1: (T1) -> String,
    label2: String? = null,
    items2: List<T2> = emptyList(),
    selected2: List<T2> = emptyList(),
    onSelect2: (List<T2>) -> Unit = {},
    name2: (T2) -> String = { "" },
    label3: String? = null,
    items3: List<T3> = emptyList(),
    selected3: List<T3> = emptyList(),
    onSelect3: (List<T3>) -> Unit = {},
    name3: (T3) -> String = { "" },
    label4: String? = null,
    items4: List<T4> = emptyList(),
    selected4: List<T4> = emptyList(),
    onSelect4: (List<T4>) -> Unit = {},
    name4: (T4) -> String = { "" },
    hideDate: Boolean = false
) {
    var showDialog1 by remember { mutableStateOf(false) }
    var showDialog2 by remember { mutableStateOf(false) }
    var showDialog3 by remember { mutableStateOf(false) }
    var showDialog4 by remember { mutableStateOf(false) }

    Surface(color = Color.White, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!hideDate) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onFromClick,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFFE0E0E0))
                    ) {
                        Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                        Spacer(Modifier.width(6.dp))
                        Text("From: $fromDate", fontSize = 12.sp, color = Color.DarkGray)
                    }
                    OutlinedButton(
                        onClick = onToClick,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFFE0E0E0))
                    ) {
                        Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                        Spacer(Modifier.width(6.dp))
                        Text("To: $toDate", fontSize = 12.sp, color = Color.DarkGray)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (label1 != null) {
                    FilterChip(
                        selected = selected1.isNotEmpty(),
                        onClick = { showDialog1 = true },
                        label = {
                            Text(
                                if (selected1.isEmpty()) "All $label1" else "${selected1.size} $label1",
                                fontSize = 11.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppGreen.copy(alpha = 0.1f),
                            selectedLabelColor = AppGreen
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = Color(0xFFE0E0E0),
                            selectedBorderColor = AppGreen,
                            enabled = true,
                            selected = selected1.isNotEmpty()
                        )
                    )
                }
                if (label2 != null) {
                    FilterChip(
                        selected = selected2.isNotEmpty(),
                        onClick = { showDialog2 = true },
                        label = {
                            Text(
                                if (selected2.isEmpty()) "All $label2" else "${selected2.size} $label2",
                                fontSize = 11.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppGreen.copy(alpha = 0.1f),
                            selectedLabelColor = AppGreen
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = Color(0xFFE0E0E0),
                            selectedBorderColor = AppGreen,
                            enabled = true,
                            selected = selected2.isNotEmpty()
                        )
                    )
                }
                if (label3 != null) {
                    FilterChip(
                        selected = selected3.isNotEmpty(),
                        onClick = { showDialog3 = true },
                        label = {
                            Text(
                                if (selected3.isEmpty()) "All $label3" else "${selected3.size} $label3",
                                fontSize = 11.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppGreen.copy(alpha = 0.1f),
                            selectedLabelColor = AppGreen
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = Color(0xFFE0E0E0),
                            selectedBorderColor = AppGreen,
                            enabled = true,
                            selected = selected3.isNotEmpty()
                        )
                    )
                }
                if (label4 != null) {
                    FilterChip(
                        selected = selected4.isNotEmpty(),
                        onClick = { showDialog4 = true },
                        label = {
                            Text(
                                if (selected4.isEmpty()) "All $label4" else "${selected4.size} $label4",
                                fontSize = 11.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppGreen.copy(alpha = 0.1f),
                            selectedLabelColor = AppGreen
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = Color(0xFFE0E0E0),
                            selectedBorderColor = AppGreen,
                            enabled = true,
                            selected = selected4.isNotEmpty()
                        )
                    )
                }
            }
        }
    }

    if (showDialog1 && label1 != null) MultiSelectDialog(
        "Select $label1",
        items1,
        selected1,
        name1,
        onSelect1
    ) { showDialog1 = false }
    if (showDialog2 && label2 != null) MultiSelectDialog(
        "Select $label2",
        items2,
        selected2,
        name2,
        onSelect2
    ) { showDialog2 = false }
    if (showDialog3 && label3 != null) MultiSelectDialog(
        "Select $label3",
        items3,
        selected3,
        name3,
        onSelect3
    ) { showDialog3 = false }
    if (showDialog4 && label4 != null) MultiSelectDialog(
        "Select $label4",
        items4,
        selected4,
        name4,
        onSelect4
    ) { showDialog4 = false }
}

@Composable
fun <T> MultiSelectDialog(
    title: String,
    allItems: List<T>,
    selectedItems: List<T>,
    itemName: (T) -> String,
    onApply: (List<T>) -> Unit,
    onDismiss: () -> Unit
) {
    var tempSelected by remember { mutableStateOf(selectedItems) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(allItems) { item ->
                    val isSelected = tempSelected.contains(item)
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            tempSelected = if (isSelected) tempSelected - item else tempSelected + item
                        }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = AppGreen)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(itemName(item), fontSize = 14.sp, color = Color(0xFF1E293B))
                    }
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(tempSelected); onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = AppGreen),
                shape = RoundedCornerShape(6.dp)
            ) { Text("Apply", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = { tempSelected = emptyList(); onApply(emptyList()); onDismiss() }) {
                Text("Clear All", color = Color.Red, fontWeight = FontWeight.Medium)
            }
        },
        shape = RoundedCornerShape(8.dp),
        containerColor = Color.White
    )
}