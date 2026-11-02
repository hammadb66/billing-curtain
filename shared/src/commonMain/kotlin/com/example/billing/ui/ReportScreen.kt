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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

private enum class ReportTab { SALE, STOCK, PURCHASE, VENDOR, CUSTOMER, ITEM_LEDGER }

// ============ THEME COLORS ============
private val BgColor = Color(0xFFF8FAFC)
private val CardBg = Color.White
private val TextPrimary = Color(0xFF1E293B)
private val TextSecondary = Color(0xFF64748B)
private val SaleColor = Color(0xFF10B981) // Emerald Green
private val PurchaseColor = Color(0xFF3B82F6) // Blue
private val DuesColor = Color(0xFFEF4444) // Red
private val WarningColor = Color(0xFFF59E0B) // Amber

// ============ 5-LEVEL FILTER HELPER ============
private fun hasMatchingItems(
    productIds: List<Long?>, allProducts: List<Product>,
    selGroups: List<Group>, selSubgroups: List<Subgroup>,
    selCategories: List<Category>, selSubcategories: List<Subcategory>,
    selProducts: List<Product>
): Boolean {
    if (selGroups.isEmpty() && selSubgroups.isEmpty() && selCategories.isEmpty() &&
        selSubcategories.isEmpty() && selProducts.isEmpty()
    ) return true
    return productIds.any { pid ->
        val p = allProducts.firstOrNull { it.id == pid } ?: return@any false
        val gOk = selGroups.isEmpty() || selGroups.any { it.id == p.groupId }
        val sgOk = selSubgroups.isEmpty() || selSubgroups.any { it.id == p.subgroupId }
        val cOk = selCategories.isEmpty() || selCategories.any { it.id == p.categoryId }
        val sOk = selSubcategories.isEmpty() || selSubcategories.any { it.id == p.subcategoryId }
        val pOk = selProducts.isEmpty() || selProducts.any { it.id == p.id }
        gOk && sgOk && cOk && sOk && pOk
    }
}

private fun Bill.matchesItemFilter(
    allProducts: List<Product>, g: List<Group>, sg: List<Subgroup>, c: List<Category>, s: List<Subcategory>, p: List<Product>
): Boolean = hasMatchingItems(rooms.flatMap { room -> room.items.map { it.productId } }, allProducts, g, sg, c, s, p)

private fun Purchase.matchesItemFilter(
    allProducts: List<Product>, g: List<Group>, sg: List<Subgroup>, c: List<Category>, s: List<Subcategory>, p: List<Product>
): Boolean = hasMatchingItems(items.map { it.productId }, allProducts, g, sg, c, s, p)

// ============ FILTER GROUP ============
interface BaseFilterGroup {
    val label: String
    val itemStrings: List<String>
    val selectedIndices: Set<Int>
    fun onApply(indices: Set<Int>)
}

class FilterGroup<T>(
    override val label: String, val items: List<T>, val selected: List<T>,
    val onSelect: (List<T>) -> Unit, val nameOf: (T) -> String
) : BaseFilterGroup {
    override val itemStrings: List<String> = items.map { nameOf(it) }
    override val selectedIndices: Set<Int> = items.mapIndexedNotNull { index, item ->
        if (selected.contains(item)) index else null
    }.toSet()
    override fun onApply(indices: Set<Int>) {
        onSelect(indices.mapNotNull { items.getOrNull(it) })
    }
}

// ============ MAIN SCREEN ============
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(billRepo: BillRepository, printer: PdfPrinter, onBack: () -> Unit) {
    PlatformBackHandler { onBack() }
    val services = koinInject<AppServices>()
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(ReportTab.SALE) }

    var selectedVendor by remember { mutableStateOf<Vendor?>(null) }
    var vPayments by remember { mutableStateOf<List<VendorPayment>>(emptyList()) }
    var currentBalance by remember { mutableStateOf(0.0) }

    var selectedCustomer by remember { mutableStateOf<CustomerAccount?>(null) }
    var cPayments by remember { mutableStateOf<List<VendorRepository.CustomerPayment>>(emptyList()) }
    var customerCurrentBalance by remember { mutableStateOf(0.0) }

    var selectedLedgerProduct by remember { mutableStateOf<Product?>(null) }

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

    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var subgroups by remember { mutableStateOf<List<Subgroup>>(emptyList()) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var subcategories by remember { mutableStateOf<List<Subcategory>>(emptyList()) }

    var selectedSalesmen by remember { mutableStateOf<List<CustomerAccount>>(emptyList()) }
    var selectedVendors by remember { mutableStateOf<List<Vendor>>(emptyList()) }
    var selectedGroups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var selectedSubgroups by remember { mutableStateOf<List<Subgroup>>(emptyList()) }
    var selectedCategories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var selectedSubcategories by remember { mutableStateOf<List<Subcategory>>(emptyList()) }
    var selectedProducts by remember { mutableStateOf<List<Product>>(emptyList()) }

    suspend fun reloadAll() {
        try {
            bills = billRepo.getAllBills()
            products = services.vendors.listProductsWithStock()
            purchases = services.vendors.listPurchases()
            salesmen = listOf(CustomerAccount(-1L, "Counter Sale", "", "", "", "")) + services.vendors.listCustomerAccounts()
            vendors = services.vendors.listVendors()
            groups = services.vendors.listGroups()
            subgroups = services.vendors.listSubgroups()
            categories = services.vendors.listCategories()
            subcategories = services.vendors.listSubcategories()
        } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) { reloadAll() }

    LaunchedEffect(selectedVendor, fromDate, toDate) {
        val v = selectedVendor
        if (v != null) {
            vPayments = services.vendors.paymentsFor(v.id).filter { it.date.take(10) in fromDate..toDate }
            currentBalance = services.vendors.getVendorBalance(v.id)
        } else { vPayments = emptyList(); currentBalance = 0.0 }
    }

    LaunchedEffect(selectedCustomer, fromDate, toDate) {
        val c = selectedCustomer
        if (c != null) {
            cPayments = services.vendors.getCustomerPayments(c.id).filter { it.date.take(10) in fromDate..toDate }
            customerCurrentBalance = services.vendors.getCustomerBalance(c.id)
        } else { cPayments = emptyList(); customerCurrentBalance = 0.0 }
    }

    if (showFromPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = isoDayToEpochMillis(fromDate))
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = { state.selectedDateMillis?.let { fromDate = epochMillisToIsoDay(it) }; showFromPicker = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
    if (showToPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = isoDayToEpochMillis(toDate))
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = { state.selectedDateMillis?.let { toDate = epochMillisToIsoDay(it) }; showToPicker = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    Scaffold(
        containerColor = BgColor,
        topBar = {
            Surface(color = CardBg, shadowElevation = 2.dp) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.background(Color(0xFFF1F5F9), CircleShape).size(40.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Text("Reports & Analytics", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                        Spacer(Modifier.weight(1f))

                        IconButton(onClick = { scope.launch { reloadAll() } }) {
                            Icon(Icons.Default.Refresh, "Refresh", tint = TextSecondary)
                        }

                        Spacer(Modifier.width(8.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    val rows = buildPrintRows(
                                        tab, bills, products, purchases, selectedVendor, selectedCustomer, selectedLedgerProduct,
                                        fromDate, toDate, selectedSalesmen, selectedVendors,
                                        selectedGroups, selectedSubgroups, selectedCategories,
                                        selectedSubcategories, selectedProducts, currentBalance, customerCurrentBalance
                                    )
                                    printer.print("Report_${tab.name}", rows)
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppGreen),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = "Print", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Print Report", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    PrimaryScrollableTabRow(
                        selectedTabIndex = tab.ordinal,
                        containerColor = CardBg,
                        edgePadding = 24.dp,
                        divider = { HorizontalDivider(color = Color(0xFFF1F5F9)) }
                    ) {
                        ReportTab.entries.forEach { t ->
                            Tab(
                                selected = tab == t,
                                onClick = { tab = t },
                                text = {
                                    Text(
                                        t.name.replace("_", " "),
                                        fontWeight = if (tab == t) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                ReportTab.SALE -> SaleReportTab(
                    bills, salesmen, groups, subgroups, categories, subcategories, products,
                    fromDate, toDate, { showFromPicker = true }, { showToPicker = true },
                    selectedSalesmen, { selectedSalesmen = it },
                    selectedGroups, { selectedGroups = it }, selectedSubgroups, { selectedSubgroups = it },
                    selectedCategories, { selectedCategories = it }, selectedSubcategories, { selectedSubcategories = it },
                    selectedProducts, { selectedProducts = it }
                )
                ReportTab.STOCK -> StockReportTab(
                    products, groups, subgroups, categories, subcategories,
                    selectedGroups, { selectedGroups = it }, selectedSubgroups, { selectedSubgroups = it },
                    selectedCategories, { selectedCategories = it }, selectedSubcategories, { selectedSubcategories = it },
                    selectedProducts, { selectedProducts = it }
                )
                ReportTab.PURCHASE -> PurchaseReportTab(
                    purchases, vendors, groups, subgroups, categories, subcategories, products,
                    fromDate, toDate, { showFromPicker = true }, { showToPicker = true },
                    selectedVendors, { selectedVendors = it },
                    selectedGroups, { selectedGroups = it }, selectedSubgroups, { selectedSubgroups = it },
                    selectedCategories, { selectedCategories = it }, selectedSubcategories, { selectedSubcategories = it },
                    selectedProducts, { selectedProducts = it }
                )
                ReportTab.VENDOR -> {
                    Column(Modifier.fillMaxSize()) {
                        val vFilter = FilterGroup("Vendors", vendors.map { it.vendor }, selectedVendor?.let { listOf(it) } ?: emptyList(), { selectedVendor = it.firstOrNull() }, { it.name })
                        FilterSection(fromDate, toDate, { showFromPicker = true }, { showToPicker = true }, vFilter)
                        if (selectedVendor != null) {
                            val vPurchases = purchases.filter { pu ->
                                pu.vendorId == selectedVendor?.id && pu.date.take(10) in fromDate..toDate &&
                                        pu.matchesItemFilter(products, selectedGroups, selectedSubgroups, selectedCategories, selectedSubcategories, selectedProducts)
                            }
                            LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                item {
                                    val totalPayable = vPurchases.sumOf { it.payable }
                                    val paidSeparate = vPayments.sumOf { it.amount + it.discount }
                                    SummaryCard(
                                        title = selectedVendor!!.name,
                                        val1 = "Purchases: Rs ${formatMoney(totalPayable)}",
                                        val2 = "Paid: Rs ${formatMoney(vPurchases.sumOf { it.paid } + paidSeparate)}",
                                        val3 = if (currentBalance > 0.005) "Due: Rs ${formatMoney(currentBalance)}" else "Settled",
                                        val3Color = if (currentBalance > 0.005) DuesColor else SaleColor
                                    )
                                }
                                items(vPurchases, key = { it.id }) { p ->
                                    DataRowCard(
                                        title = "Purchase #${p.id}",
                                        subtitle = displayDate(p.date),
                                        amount = "Rs ${formatMoney(p.total)}",
                                        icon = Icons.Default.LocalShipping,
                                        iconColor = PurchaseColor
                                    )
                                }
                            }
                        } else {
                            EmptyState("Select a vendor to view report.", Icons.Default.Person)
                        }
                    }
                }
                ReportTab.CUSTOMER -> {
                    Column(Modifier.fillMaxSize()) {
                        val cFilter = FilterGroup("Customers", salesmen, selectedCustomer?.let { listOf(it) } ?: emptyList(), { selectedCustomer = it.firstOrNull() }, { it.name })
                        FilterSection(fromDate, toDate, { showFromPicker = true }, { showToPicker = true }, cFilter)
                        if (selectedCustomer != null) {
                            val cBills = bills.filter { b ->
                                b.salesmanId == selectedCustomer?.id && b.date.take(10) in fromDate..toDate &&
                                        b.matchesItemFilter(products, selectedGroups, selectedSubgroups, selectedCategories, selectedSubcategories, selectedProducts)
                            }
                            LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                item {
                                    val periodSales = cBills.sumOf { it.grandTotal }
                                    val periodReceived = cBills.sumOf { it.received } + cPayments.sumOf { it.amount + it.discount }
                                    SummaryCard(
                                        title = selectedCustomer!!.name,
                                        val1 = "Sales: Rs ${formatMoney(periodSales)}",
                                        val2 = "Received: Rs ${formatMoney(periodReceived)}",
                                        val3 = if (customerCurrentBalance > 0.005) "Due: Rs ${formatMoney(customerCurrentBalance)}" else "Settled",
                                        val3Color = if (customerCurrentBalance > 0.005) DuesColor else SaleColor
                                    )
                                }
                                items(cBills, key = { it.id }) { b ->
                                    DataRowCard(
                                        title = "Invoice ${b.invoiceText}",
                                        subtitle = displayDate(b.date),
                                        amount = "Rs ${formatMoney(b.grandTotal)}",
                                        icon = Icons.Default.Receipt,
                                        iconColor = SaleColor
                                    )
                                }
                            }
                        } else {
                            EmptyState("Select a customer to view report.", Icons.Default.Person)
                        }
                    }
                }
                ReportTab.ITEM_LEDGER -> {
                    Column(Modifier.fillMaxSize()) {
                        val pFilter = FilterGroup("Products", products, selectedLedgerProduct?.let { listOf(it) } ?: emptyList(), { selectedLedgerProduct = it.firstOrNull() }, { it.name })
                        FilterSection(fromDate, toDate, { showFromPicker = true }, { showToPicker = true }, pFilter)

                        if (selectedLedgerProduct != null) {
                            val prod = selectedLedgerProduct!!
                            val pSales = bills.filter { b -> b.date.take(10) in fromDate..toDate && b.rooms.any { r -> r.items.any { it.productId == prod.id } } }
                            val pPurchases = purchases.filter { p -> p.date.take(10) in fromDate..toDate && p.items.any { it.productId == prod.id } }

                            val totalSold = pSales.sumOf { b -> b.rooms.flatMap { it.items }.filter { it.productId == prod.id }.sumOf { it.qtyValue } }
                            val totalPurchased = pPurchases.sumOf { p -> p.items.filter { it.productId == prod.id }.sumOf { it.qtyValue } }
                            val liveStock = prod.liveStock ?: 0.0

                            LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                // --- PRO SUMMARY CARD ---
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = CardBg),
                                        shape = RoundedCornerShape(16.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(Modifier.padding(24.dp)) {
                                            Text(prod.name, fontWeight = FontWeight.Black, fontSize = 22.sp, color = TextPrimary)
                                            Text("${prod.categoryName} › ${prod.subcategoryName}", fontSize = 13.sp, color = TextSecondary)
                                            Spacer(Modifier.height(24.dp))

                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                StatBox(
                                                    label = "Opening Stock",
                                                    value = "${formatQty(prod.openingStock)} ${prod.unit}",
                                                    color = TextSecondary,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                StatBox(
                                                    label = "(+) Purchased",
                                                    value = "${formatQty(totalPurchased)} ${prod.unit}",
                                                    color = PurchaseColor,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                StatBox(
                                                    label = "(-) Sold",
                                                    value = "${formatQty(totalSold)} ${prod.unit}",
                                                    color = SaleColor,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                StatBox(
                                                    label = "(=) Live Stock",
                                                    value = "${formatQty(liveStock)} ${prod.unit}",
                                                    color = WarningColor,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }

                                // --- SALES HISTORY ---
                                item {
                                    SectionHeader(
                                        title = "Sales History",
                                        icon = Icons.Default.TrendingDown,
                                        color = SaleColor,
                                        count = pSales.size
                                    )
                                }
                                if (pSales.isEmpty()) {
                                    item { Text("No sales in this period.", color = TextSecondary, fontSize = 13.sp) }
                                } else {
                                    items(pSales, key = { "sale_${it.id}" }) { b ->
                                        val items = b.rooms.flatMap { it.items }.filter { it.productId == prod.id }
                                        val qty = items.sumOf { it.qtyValue }
                                        val amt = items.sumOf { it.total }
                                        DataRowCard(
                                            title = b.invoiceText,
                                            subtitle = "${b.customer} • ${displayDate(b.date)}",
                                            amount = "Rs ${formatMoney(amt)}",
                                            extraText = "Qty: ${formatQty(qty)}",
                                            icon = Icons.Default.ArrowDownward,
                                            iconColor = SaleColor
                                        )
                                    }
                                }

                                // --- PURCHASE HISTORY ---
                                item {
                                    SectionHeader(
                                        title = "Purchase History",
                                        icon = Icons.Default.TrendingUp,
                                        color = PurchaseColor,
                                        count = pPurchases.size
                                    )
                                }
                                if (pPurchases.isEmpty()) {
                                    item { Text("No purchases in this period.", color = TextSecondary, fontSize = 13.sp) }
                                } else {
                                    items(pPurchases, key = { "pur_${it.id}" }) { p ->
                                        val items = p.items.filter { it.productId == prod.id }
                                        val qty = items.sumOf { it.qtyValue }
                                        val amt = items.sumOf { it.total }
                                        DataRowCard(
                                            title = "Purchase #${p.id}",
                                            subtitle = "${p.vendorName} • ${displayDate(p.date)}",
                                            amount = "Rs ${formatMoney(amt)}",
                                            extraText = "Qty: ${formatQty(qty)}",
                                            icon = Icons.Default.ArrowUpward,
                                            iconColor = PurchaseColor
                                        )
                                    }
                                }
                            }
                        } else {
                            EmptyState("Select a product to view ledger.", Icons.Default.Inventory)
                        }
                    }
                }
            }
        }
    }
}

// ============ UI COMPONENTS ============

@Composable
fun SummaryCard(title: String, val1: String, val2: String, val3: String, val3Color: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(val1, fontSize = 14.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                Text(val2, fontSize = 14.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9))
            Spacer(Modifier.height(8.dp))
            Text(val3, fontSize = 18.sp, color = val3Color, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun StatBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, fontSize = 12.sp, color = color, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 16.sp, color = color, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector, color: Color, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
        Box(Modifier.background(color.copy(alpha = 0.1f), CircleShape).size(32.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.width(8.dp))
        Surface(color = color.copy(alpha = 0.1f), shape = CircleShape) {
            Text("$count", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        }
    }
}

@Composable
fun DataRowCard(title: String, subtitle: String, amount: String, extraText: String? = null, icon: ImageVector, iconColor: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.background(iconColor.copy(alpha = 0.1f), CircleShape).size(40.dp), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                Text(subtitle, fontSize = 12.sp, color = TextSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(amount, fontWeight = FontWeight.Black, fontSize = 16.sp, color = TextPrimary)
                if (extraText != null) {
                    Text(extraText, fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ============ SALE TAB ============
@Composable
fun SaleReportTab(
    bills: List<Bill>, salesmen: List<CustomerAccount>,
    groups: List<Group>, subgroups: List<Subgroup>, categories: List<Category>, subcategories: List<Subcategory>, products: List<Product>,
    fromDate: String, toDate: String, onFromClick: () -> Unit, onToClick: () -> Unit,
    selectedSalesmen: List<CustomerAccount>, onSalesmenChange: (List<CustomerAccount>) -> Unit,
    selectedGroups: List<Group>, onGroupsChange: (List<Group>) -> Unit, selectedSubgroups: List<Subgroup>, onSubgroupsChange: (List<Subgroup>) -> Unit,
    selectedCategories: List<Category>, onCategoriesChange: (List<Category>) -> Unit, selectedSubcategories: List<Subcategory>, onSubcategoriesChange: (List<Subcategory>) -> Unit,
    selectedProducts: List<Product>, onProductsChange: (List<Product>) -> Unit,
) {
    val filtered = bills.filter { bill ->
        val dateOk = bill.date.take(10) in fromDate..toDate
        val targetId = bill.salesmanId ?: -1L
        val salesOk = selectedSalesmen.isEmpty() || selectedSalesmen.any { it.id == targetId }
        val itemsOk = bill.matchesItemFilter(products, selectedGroups, selectedSubgroups, selectedCategories, selectedSubcategories, selectedProducts)
        dateOk && salesOk && itemsOk
    }

    Column(Modifier.fillMaxSize()) {
        FilterSection(
            fromDate, toDate, onFromClick, onToClick,
            FilterGroup("Customers", salesmen, selectedSalesmen, onSalesmenChange, { it.name }),
            FilterGroup("Groups", groups, selectedGroups, onGroupsChange, { it.name }),
            FilterGroup("Subgroups", subgroups, selectedSubgroups, onSubgroupsChange, { it.name }),
            FilterGroup("Categories", categories, selectedCategories, onCategoriesChange, { it.name }),
            FilterGroup("Subcategories", subcategories, selectedSubcategories, onSubcategoriesChange, { it.name }),
            FilterGroup("Products", products, selectedProducts, onProductsChange, { it.name })
        )

        Box(Modifier.padding(horizontal = 24.dp, vertical = 12.dp).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().background(SaleColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Total Bills: ${filtered.size}", fontWeight = FontWeight.Bold, color = SaleColor, fontSize = 14.sp)
                Text("Amount: Rs ${formatMoney(filtered.sumOf { it.grandTotal })}", fontWeight = FontWeight.Black, color = SaleColor, fontSize = 18.sp)
            }
        }

        if (filtered.isEmpty()) {
            EmptyState("No Sale records found.", Icons.Default.Receipt)
        } else {
            LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filtered, key = { it.id }) { bill ->
                    val salesmanName = if (bill.salesmanId == null) "Counter Sale" else salesmen.firstOrNull { it.id == bill.salesmanId }?.name ?: "Unknown"
                    val isPending = bill.balance > 0.005
                    DataRowCard(
                        title = bill.customer,
                        subtitle = "${bill.invoiceText} • ID: $salesmanName • ${displayDate(bill.date)}",
                        amount = "Rs ${formatMoney(bill.grandTotal)}",
                        extraText = if (isPending) "Due: Rs ${formatMoney(bill.balance)}" else "PAID",
                        icon = Icons.Default.ShoppingBag,
                        iconColor = if (isPending) DuesColor else SaleColor
                    )
                }
            }
        }
    }
}

// ============ STOCK TAB ============
@Composable
fun StockReportTab(
    products: List<Product>, groups: List<Group>, subgroups: List<Subgroup>, categories: List<Category>, subcategories: List<Subcategory>,
    selectedGroups: List<Group>, onGroupsChange: (List<Group>) -> Unit, selectedSubgroups: List<Subgroup>, onSubgroupsChange: (List<Subgroup>) -> Unit,
    selectedCategories: List<Category>, onCategoriesChange: (List<Category>) -> Unit, selectedSubcategories: List<Subcategory>, onSubcategoriesChange: (List<Subcategory>) -> Unit,
    selectedProducts: List<Product>, onProductsChange: (List<Product>) -> Unit,
) {
    val filtered = products.filter { p ->
        val gOk = selectedGroups.isEmpty() || selectedGroups.any { it.id == p.groupId }
        val sgOk = selectedSubgroups.isEmpty() || selectedSubgroups.any { it.id == p.subgroupId }
        val cOk = selectedCategories.isEmpty() || selectedCategories.any { it.id == p.categoryId }
        val sOk = selectedSubcategories.isEmpty() || selectedSubcategories.any { it.id == p.subcategoryId }
        val pOk = selectedProducts.isEmpty() || selectedProducts.any { it.id == p.id }
        gOk && sgOk && cOk && sOk && pOk
    }

    Column(Modifier.fillMaxSize()) {
        FilterSection(
            "", "", {}, {},
            FilterGroup("Groups", groups, selectedGroups, onGroupsChange, { it.name }),
            FilterGroup("Subgroups", subgroups, selectedSubgroups, onSubgroupsChange, { it.name }),
            FilterGroup("Categories", categories, selectedCategories, onCategoriesChange, { it.name }),
            FilterGroup("Subcategories", subcategories, selectedSubcategories, onSubcategoriesChange, { it.name }),
            FilterGroup("Products", products, selectedProducts, onProductsChange, { it.name }),
            hideDate = true
        )

        if (filtered.isEmpty()) {
            EmptyState("No products found.", Icons.Default.Inventory)
        } else {
            LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filtered, key = { it.id }) { p ->
                    val liveStock = p.liveStock ?: p.openingStock
                    val isLowStock = liveStock <= 5.0
                    DataRowCard(
                        title = p.name,
                        subtitle = "${p.groupName} › ${p.categoryName}",
                        amount = "${formatQty(liveStock)} ${p.unit}",
                        extraText = "Rs ${formatMoney(p.salePrice)}",
                        icon = Icons.Default.Inventory,
                        iconColor = if (isLowStock) DuesColor else WarningColor
                    )
                }
            }
        }
    }
}

// ============ PURCHASE TAB ============
@Composable
fun PurchaseReportTab(
    purchases: List<Purchase>, vendors: List<VendorWithBalance>,
    groups: List<Group>, subgroups: List<Subgroup>, categories: List<Category>, subcategories: List<Subcategory>, products: List<Product>,
    fromDate: String, toDate: String, onFromClick: () -> Unit, onToClick: () -> Unit,
    selectedVendors: List<Vendor>, onVendorsChange: (List<Vendor>) -> Unit,
    selectedGroups: List<Group>, onGroupsChange: (List<Group>) -> Unit, selectedSubgroups: List<Subgroup>, onSubgroupsChange: (List<Subgroup>) -> Unit,
    selectedCategories: List<Category>, onCategoriesChange: (List<Category>) -> Unit, selectedSubcategories: List<Subcategory>, onSubcategoriesChange: (List<Subcategory>) -> Unit,
    selectedProducts: List<Product>, onProductsChange: (List<Product>) -> Unit,
) {
    val filtered = purchases.filter { p ->
        val dateOk = p.date.take(10) in fromDate..toDate
        val vendOk = selectedVendors.isEmpty() || selectedVendors.any { it.id == p.vendorId }
        val itemsOk = p.matchesItemFilter(products, selectedGroups, selectedSubgroups, selectedCategories, selectedSubcategories, selectedProducts)
        dateOk && vendOk && itemsOk
    }

    Column(Modifier.fillMaxSize()) {
        FilterSection(
            fromDate, toDate, onFromClick, onToClick,
            FilterGroup("Vendors", vendors.map { it.vendor }, selectedVendors, onVendorsChange, { it.name }),
            FilterGroup("Groups", groups, selectedGroups, onGroupsChange, { it.name }),
            FilterGroup("Subgroups", subgroups, selectedSubgroups, onSubgroupsChange, { it.name }),
            FilterGroup("Categories", categories, selectedCategories, onCategoriesChange, { it.name }),
            FilterGroup("Subcategories", subcategories, selectedSubcategories, onSubcategoriesChange, { it.name }),
            FilterGroup("Products", products, selectedProducts, onProductsChange, { it.name })
        )

        if (filtered.isEmpty()) {
            EmptyState("No Purchase records found.", Icons.Default.LocalShipping)
        } else {
            LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filtered, key = { it.id }) { pu ->
                    val isPending = pu.balance > 0.005
                    DataRowCard(
                        title = pu.vendorName.ifBlank { "Unknown Vendor" },
                        subtitle = "Purchase #${pu.id} • ${displayDate(pu.date)}",
                        amount = "Rs ${formatMoney(pu.payable)}",
                        extraText = if (isPending) "Due: Rs ${formatMoney(pu.balance)}" else "Settled",
                        icon = Icons.Default.LocalShipping,
                        iconColor = if (isPending) DuesColor else PurchaseColor
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(message: String, icon: ImageVector) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.background(Color(0xFFF1F5F9), CircleShape).size(80.dp), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.LightGray, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(message, color = TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ============ FILTER SECTION ============
@Composable
fun FilterSection(
    fromDate: String, toDate: String, onFromClick: () -> Unit, onToClick: () -> Unit,
    filter1: BaseFilterGroup, filter2: BaseFilterGroup? = null, filter3: BaseFilterGroup? = null,
    filter4: BaseFilterGroup? = null, filter5: BaseFilterGroup? = null, filter6: BaseFilterGroup? = null,
    hideDate: Boolean = false,
) {
    val filters = listOfNotNull(filter1, filter2, filter3, filter4, filter5, filter6)
    var activeFilter by remember { mutableStateOf<BaseFilterGroup?>(null) }

    Surface(color = CardBg, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!hideDate) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateButton("From: $fromDate", onFromClick, Modifier.weight(1f))
                    DateButton("To: $toDate", onToClick, Modifier.weight(1f))
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEach { f ->
                    FilterChip(
                        selected = f.selectedIndices.isNotEmpty(),
                        onClick = { activeFilter = f },
                        label = { Text(if (f.selectedIndices.isEmpty()) "All ${f.label}" else "${f.selectedIndices.size} ${f.label}", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppGreen.copy(alpha = 0.1f),
                            selectedLabelColor = AppGreen,
                            containerColor = Color(0xFFF8FAFC),
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(borderColor = Color(0xFFE2E8F0), selectedBorderColor = AppGreen, enabled = true, selected = f.selectedIndices.isNotEmpty())
                    )
                }
            }
        }
    }

    activeFilter?.let { f ->
        MultiSelectDialog(filterGroup = f, onDismiss = { activeFilter = null })
    }
}

@Composable
fun DateButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = CardBg
        )
    ) {
        Icon(Icons.Default.DateRange, null, modifier = Modifier.size(18.dp), tint = TextSecondary)
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun MultiSelectDialog(
    filterGroup: BaseFilterGroup, onDismiss: () -> Unit
) {
    var tempSelectedIndices by remember { mutableStateOf(filterGroup.selectedIndices) }
    var searchQuery by remember { mutableStateOf("") }
    val filteredIndices = filterGroup.itemStrings.indices.filter { filterGroup.itemStrings[it].contains(searchQuery, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select ${filterGroup.label}", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    placeholder = { Text("Search...", fontSize = 14.sp, color = TextSecondary) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) },
                    singleLine = true, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen, unfocusedBorderColor = Color(0xFFE2E8F0))
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { tempSelectedIndices = filterGroup.itemStrings.indices.toSet() }) { Text("Select All", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    TextButton(onClick = { tempSelectedIndices = emptySet() }) { Text("Clear All", fontSize = 13.sp, color = DuesColor, fontWeight = FontWeight.Bold) }
                }
                LazyColumn(Modifier.heightIn(max = 350.dp)) {
                    items(filteredIndices) { index ->
                        val isSelected = tempSelectedIndices.contains(index)
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                tempSelectedIndices = if (isSelected) tempSelectedIndices - index else tempSelectedIndices + index
                            }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = isSelected, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = AppGreen))
                            Spacer(Modifier.width(12.dp))
                            Text(filterGroup.itemStrings[index], fontSize = 15.sp, color = TextPrimary)
                        }
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { filterGroup.onApply(tempSelectedIndices); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = AppGreen), shape = RoundedCornerShape(8.dp)) { Text("Apply", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
        shape = RoundedCornerShape(12.dp), containerColor = CardBg
    )
}

// ============ PRINT BUILDER ============
private fun buildPrintRows(
    tab: ReportTab, bills: List<Bill>, products: List<Product>, purchases: List<Purchase>,
    selectedVendor: Vendor?, selectedCustomer: CustomerAccount?, selectedProduct: Product?,
    fromDate: String, toDate: String, selectedSalesmen: List<CustomerAccount>, selectedVendors: List<Vendor>,
    selG: List<Group>, selSG: List<Subgroup>, selC: List<Category>, selSC: List<Subcategory>, selP: List<Product>,
    currentBalance: Double, customerCurrentBalance: Double
): List<PdfRow> {
    val rows = mutableListOf<PdfRow>()
    rows += PdfRow(left = "REPORT", bold = true, fontSize = 18f)
    rows += PdfRow(left = "Type: ${tab.name.replace("_", " ")}")
    rows += PdfRow(left = "From: ${isoDayToDisplay(fromDate)} To: ${isoDayToDisplay(toDate)}")
    rows += PdfRow(kind = RowKind.LINE)

    when (tab) {
        ReportTab.SALE -> {
            val f = bills.filter { b ->
                val dateOk = b.date.take(10) in fromDate..toDate
                val targetId = b.salesmanId ?: -1L
                val salesOk = selectedSalesmen.isEmpty() || selectedSalesmen.any { it.id == targetId }
                dateOk && salesOk && b.matchesItemFilter(products, selG, selSG, selC, selSC, selP)
            }
            rows += PdfRow(left = "SALE REPORT", bold = true)
            var total = 0.0; var due = 0.0
            f.forEach { b ->
                rows += PdfRow(left = "${b.invoiceText} | ${displayDate(b.date)} | ${b.customer}", right = "Rs ${formatMoney(b.grandTotal)}")
                total += b.grandTotal; due += b.balance
            }
            rows += PdfRow(kind = RowKind.LINE)
            rows += PdfRow(right = "Total Sales: Rs ${formatMoney(total)}", bold = true)
            rows += PdfRow(right = "Total Due: Rs ${formatMoney(due)}", bold = true)
        }
        ReportTab.STOCK -> {
            val f = products.filter { p ->
                (selG.isEmpty() || selG.any { it.id == p.groupId }) && (selSG.isEmpty() || selSG.any { it.id == p.subgroupId }) &&
                        (selC.isEmpty() || selC.any { it.id == p.categoryId }) && (selSC.isEmpty() || selSC.any { it.id == p.subcategoryId }) &&
                        (selP.isEmpty() || selP.any { it.id == p.id })
            }
            rows += PdfRow(left = "STOCK REPORT", bold = true)
            var totalValue = 0.0
            f.forEach { p ->
                val qty = p.liveStock ?: p.openingStock
                totalValue += (qty * p.salePrice)
                rows += PdfRow(left = p.name, qty = "${formatQty(qty)} ${p.unit}", right = "Rs ${formatMoney(p.salePrice)}")
            }
            rows += PdfRow(kind = RowKind.LINE)
            rows += PdfRow(right = "Total Stock Value: Rs ${formatMoney(totalValue)}", bold = true)
        }
        ReportTab.PURCHASE -> {
            val f = purchases.filter { p ->
                val dateOk = p.date.take(10) in fromDate..toDate
                val vendOk = selectedVendors.isEmpty() || selectedVendors.any { it.id == p.vendorId }
                dateOk && vendOk && p.matchesItemFilter(products, selG, selSG, selC, selSC, selP)
            }
            rows += PdfRow(left = "PURCHASE REPORT", bold = true)
            var total = 0.0; var paid = 0.0
            f.forEach { p ->
                rows += PdfRow(left = "${p.vendorName} | ${displayDate(p.date)}", right = "Rs ${formatMoney(p.payable)}")
                total += p.payable; paid += p.paid
            }
            rows += PdfRow(kind = RowKind.LINE)
            rows += PdfRow(right = "Total Purchase: Rs ${formatMoney(total)}", bold = true)
            rows += PdfRow(right = "Total Paid: Rs ${formatMoney(paid)}", bold = true)
        }
        ReportTab.VENDOR -> {
            if (selectedVendor != null) {
                val vPurchases = purchases.filter { pu ->
                    pu.vendorId == selectedVendor.id && pu.date.take(10) in fromDate..toDate &&
                            pu.matchesItemFilter(products, selG, selSG, selC, selSC, selP)
                }
                rows += PdfRow(left = "VENDOR DETAILED LEDGER: ${selectedVendor.name}", bold = true, fontSize = 16f)
                rows += PdfRow(left = "--- PURCHASES ---", bold = true)
                var totalPurchase = 0.0
                var totalPaid = 0.0
                vPurchases.forEach { p ->
                    rows += PdfRow(left = "Date: ${displayDate(p.date)} (Pur# ${p.id})", bold = true)
                    p.items.forEach { item ->
                        rows += PdfRow(left = "   ${item.name}", qty = "${item.qty} ${item.unit}", rate = formatMoney(item.priceValue), right = formatMoney(item.total), fontSize = 10f)
                    }
                    rows += PdfRow(right = "  Bill Total: Rs ${formatMoney(p.payable)}", fontSize = 11f)
                    if (p.discount > 0) rows += PdfRow(right = "  Discount: - Rs ${formatMoney(p.discount)}", fontSize = 10f)
                    rows += PdfRow(right = "  Paid: Rs ${formatMoney(p.paid)}", fontSize = 10f)
                    totalPurchase += p.payable
                    totalPaid += p.paid
                }
                rows += PdfRow(kind = RowKind.LINE)
                rows += PdfRow(right = "Period Total Purchases: Rs ${formatMoney(totalPurchase)}", bold = true)
                rows += PdfRow(right = "Period Total Paid: Rs ${formatMoney(totalPaid)}", bold = true)
                rows += PdfRow(right = "Current Balance: Rs ${formatMoney(currentBalance)}", bold = true, fontSize = 14f)
            }
        }
        ReportTab.CUSTOMER -> {
            if (selectedCustomer != null) {
                val cBills = bills.filter { b ->
                    b.salesmanId == selectedCustomer.id && b.date.take(10) in fromDate..toDate &&
                            b.matchesItemFilter(products, selG, selSG, selC, selSC, selP)
                }
                rows += PdfRow(left = "CUSTOMER DETAILED LEDGER: ${selectedCustomer.name}", bold = true, fontSize = 16f)
                rows += PdfRow(left = "--- BILLS ---", bold = true)
                var totalSales = 0.0
                var totalReceived = 0.0
                cBills.forEach { b ->
                    rows += PdfRow(left = "Date: ${displayDate(b.date)} (Inv: ${b.invoiceText})", bold = true)
                    b.rooms.forEach { room ->
                        room.items.forEach { item ->
                            rows += PdfRow(left = "   ${item.name}", qty = "${item.qty} ${item.unit}", rate = formatMoney(item.rateValue), right = formatMoney(item.total), fontSize = 10f)
                        }
                    }
                    rows += PdfRow(right = "  Bill Total: Rs ${formatMoney(b.grandTotal)}", fontSize = 11f)
                    if (b.discount > 0) rows += PdfRow(right = "  Discount: - Rs ${formatMoney(b.discount)}", fontSize = 10f)
                    rows += PdfRow(right = "  Received: Rs ${formatMoney(b.received)}", fontSize = 10f)
                    totalSales += b.grandTotal
                    totalReceived += b.received
                }
                rows += PdfRow(kind = RowKind.LINE)
                rows += PdfRow(right = "Period Total Sales: Rs ${formatMoney(totalSales)}", bold = true)
                rows += PdfRow(right = "Period Total Received: Rs ${formatMoney(totalReceived)}", bold = true)
                rows += PdfRow(right = "Current Due: Rs ${formatMoney(customerCurrentBalance)}", bold = true, fontSize = 14f)
            }
        }
        ReportTab.ITEM_LEDGER -> {
            if (selectedProduct != null) {
                val pSales = bills.filter { b -> b.date.take(10) in fromDate..toDate && b.rooms.any { r -> r.items.any { it.productId == selectedProduct.id } } }
                val pPurchases = purchases.filter { p -> p.date.take(10) in fromDate..toDate && p.items.any { it.productId == selectedProduct.id } }

                val totalSold = pSales.sumOf { b -> b.rooms.flatMap { it.items }.filter { it.productId == selectedProduct.id }.sumOf { it.qtyValue } }
                val totalPurchased = pPurchases.sumOf { p -> p.items.filter { it.productId == selectedProduct.id }.sumOf { it.qtyValue } }
                val currentStock = selectedProduct.liveStock ?: 0.0

                rows += PdfRow(left = "PRODUCT LEDGER: ${selectedProduct.name}", bold = true, fontSize = 16f)
                rows += PdfRow(kind = RowKind.LINE)

                rows += PdfRow(left = "Summary:", bold = true)
                rows += PdfRow(left = "  Opening Stock", right = "${formatQty(selectedProduct.openingStock)} ${selectedProduct.unit}", fontSize = 11f)
                rows += PdfRow(left = "  (+) Total Purchased", right = "${formatQty(totalPurchased)} ${selectedProduct.unit}", fontSize = 11f)
                rows += PdfRow(left = "  (-) Total Sold", right = "${formatQty(totalSold)} ${selectedProduct.unit}", fontSize = 11f)
                rows += PdfRow(left = "  (=) Current Live Stock", right = "${formatQty(currentStock)} ${selectedProduct.unit}", bold = true, fontSize = 12f)
                rows += PdfRow(kind = RowKind.LINE)

                rows += PdfRow(left = "--- SALES DETAIL ---", bold = true)
                if (pSales.isEmpty()) rows += PdfRow(left = "No sales in this period.", fontSize = 11f)
                pSales.forEach { b ->
                    val items = b.rooms.flatMap { it.items }.filter { it.productId == selectedProduct.id }
                    val qty = items.sumOf { it.qtyValue }
                    val amt = items.sumOf { it.total }
                    rows += PdfRow(left = "Date: ${displayDate(b.date)} | Inv: ${b.invoiceText}", qty = "Qty: ${formatQty(qty)}", right = "Rs ${formatMoney(amt)}", fontSize = 11f)
                }

                rows += PdfRow(kind = RowKind.SPACE)

                rows += PdfRow(left = "--- PURCHASES DETAIL ---", bold = true)
                if (pPurchases.isEmpty()) rows += PdfRow(left = "No purchases in this period.", fontSize = 11f)
                pPurchases.forEach { p ->
                    val items = p.items.filter { it.productId == selectedProduct.id }
                    val qty = items.sumOf { it.qtyValue }
                    val amt = items.sumOf { it.total }
                    rows += PdfRow(left = "Date: ${displayDate(p.date)} | Pur: #${p.id}", qty = "Qty: ${formatQty(qty)}", right = "Rs ${formatMoney(amt)}", fontSize = 11f)
                }
            }
        }
    }
    return rows
}