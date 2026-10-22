package com.example.billing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billing.AppServices
import com.example.billing.data.BillRepository
import com.example.billing.model.*
import com.example.billing.utils.*
import org.koin.compose.koinInject
import java.util.Collections.emptyList

private enum class ReportTab { SALE, STOCK, PURCHASE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(billRepo: BillRepository, onBack: () -> Unit) {
    PlatformBackHandler { onBack() }
    val services = koinInject<AppServices>()
    var tab by remember { mutableStateOf(ReportTab.SALE) }

    val today = nowIso().take(10)
    var fromDate by remember { mutableStateOf(today) }
    var toDate by remember { mutableStateOf(today) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    var bills by remember { mutableStateOf<List<Bill>>(emptyList()) }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var purchases by remember { mutableStateOf<List<Purchase>>(emptyList()) }
    var salesmen by remember { mutableStateOf<List<Salesman>>(emptyList()) }
    var vendors by remember { mutableStateOf<List<VendorWithBalance>>(emptyList()) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var subcategories by remember { mutableStateOf<List<Subcategory>>(emptyList()) }

    // MULTI-SELECT STATES (Lists)
    var selectedSalesmen by remember { mutableStateOf<List<Salesman>>(emptyList()) }
    var selectedVendors by remember { mutableStateOf<List<Vendor>>(emptyList()) }
    var selectedCategories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var selectedSubcategories by remember { mutableStateOf<List<Subcategory>>(emptyList()) }
    var selectedProducts by remember { mutableStateOf<List<Product>>(emptyList()) }

    LaunchedEffect(Unit) {
        bills = billRepo.getAllBills()
        products = services.vendors.listProductsWithStock()
        purchases = services.vendors.listPurchases()
        salesmen = services.vendors.listSalesmen()
        categories = services.vendors.listCategories()
        subcategories = services.vendors.listSubcategories()
        vendors = services.vendors.listVendors()
    }

    if (showFromPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = isoDayToEpochMillis(fromDate))
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { fromDate = epochMillisToIsoDay(it) }; showFromPicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
    if (showToPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = isoDayToEpochMillis(toDate))
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { toDate = epochMillisToIsoDay(it) }; showToPicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    Scaffold(containerColor = Color(0xFFF4F5F7)) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF2C3E50)) }
                        Spacer(Modifier.width(12.dp))
                        Text("Reports", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
                    }
                    TabRow(selectedTabIndex = tab.ordinal, containerColor = Color.White, contentColor = AppGreen) {
                        Tab(selected = tab == ReportTab.SALE, onClick = { tab = ReportTab.SALE }, text = { Text("Sale Report") })
                        Tab(selected = tab == ReportTab.STOCK, onClick = { tab = ReportTab.STOCK }, text = { Text("Stock Report") })
                        Tab(selected = tab == ReportTab.PURCHASE, onClick = { tab = ReportTab.PURCHASE }, text = { Text("Purchase Report") })
                    }
                }
            }
            Box(Modifier.weight(1f)) {
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
                }
            }
        }
    }
}

@Composable
fun SaleReportTab(
    bills: List<Bill>, salesmen: List<Salesman>, categories: List<Category>, subcategories: List<Subcategory>, products: List<Product>,
    fromDate: String, toDate: String, onFromClick: ()->Unit, onToClick: ()->Unit,
    selectedSalesmen: List<Salesman>, onSalesmenChange: (List<Salesman>)->Unit,
    selectedCategories: List<Category>, onCategoriesChange: (List<Category>)->Unit,
    selectedSubcategories: List<Subcategory>, onSubcategoriesChange: (List<Subcategory>)->Unit,
    selectedProducts: List<Product>, onProductsChange: (List<Product>)->Unit
) {
    val filtered = bills.filter { bill ->
        val dateOk = bill.date.take(10) in fromDate..toDate
        val salesOk = selectedSalesmen.isEmpty() || selectedSalesmen.any { it.id == bill.salesmanId }

        val itemsOk = if (selectedCategories.isEmpty() && selectedSubcategories.isEmpty() && selectedProducts.isEmpty()) true else {
            bill.rooms.any { room -> room.items.any { item ->
                val p = products.firstOrNull { it.id == item.productId }
                val cOk = selectedCategories.isEmpty() || selectedCategories.any { it.id == p?.categoryId }
                val sOk = selectedSubcategories.isEmpty() || selectedSubcategories.any { it.id == p?.subcategoryId }
                val pOk = selectedProducts.isEmpty() || selectedProducts.any { it.id == p?.id }
                cOk && sOk && pOk
            } }
        }
        dateOk && salesOk && itemsOk
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MultiSelectFilterRow(
            fromDate, toDate, onFromClick, onToClick,
            "Salesmen", salesmen, selectedSalesmen, onSalesmenChange, { it.name },
            "Categories", categories, selectedCategories, onCategoriesChange, { it.name },
            "Subcategories", subcategories, selectedSubcategories, onSubcategoriesChange, { it.name },
            "Products", products, selectedProducts, onProductsChange, { it.name }
        )
        Text("Total Bills: ${filtered.size} | Amount: Rs ${formatMoney(filtered.sumOf { it.grandTotal })}", fontWeight = FontWeight.Bold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered) { bill ->
                val salesmanName = salesmen.firstOrNull { it.id == bill.salesmanId }?.name ?: "N/A"
                Card(border = BorderStroke(1.dp, Color(0xFFE0E0E0))) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(bill.customer, fontWeight = FontWeight.Bold)
                            Text("Rs ${formatMoney(bill.grandTotal)}", color = AppGreen, fontWeight = FontWeight.Bold)
                        }
                        Text("${bill.invoiceText} | ${displayDate(bill.date)} | Salesman: $salesmanName", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun StockReportTab(
    products: List<Product>, categories: List<Category>, subcategories: List<Subcategory>,
    selectedCategories: List<Category>, onCategoriesChange: (List<Category>)->Unit,
    selectedSubcategories: List<Subcategory>, onSubcategoriesChange: (List<Subcategory>)->Unit,
    selectedProducts: List<Product>, onProductsChange: (List<Product>)->Unit
) {
    val filtered = products.filter { p ->
        val cOk = selectedCategories.isEmpty() || selectedCategories.any { it.id == p.categoryId }
        val sOk = selectedSubcategories.isEmpty() || selectedSubcategories.any { it.id == p.subcategoryId }
        val pOk = selectedProducts.isEmpty() || selectedProducts.any { it.id == p.id }
        cOk && sOk && pOk
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MultiSelectFilterRow<Any, Category, Subcategory, Product>(
            "", "", {},{},
            null, emptyList(), emptyList(), {}, { "" },
            "Categories", categories, selectedCategories, onCategoriesChange, { it.name },
            "Subcategories", subcategories, selectedSubcategories, onSubcategoriesChange, { it.name },
            "Products", products, selectedProducts, onProductsChange, { it.name },
            hideDate = true
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered) { p ->
                Card(border = BorderStroke(1.dp, Color(0xFFE0E0E0))) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(p.name, fontWeight = FontWeight.Bold)
                            Text("${p.categoryName} > ${p.subcategoryName}", fontSize = 12.sp, color = Color.Gray)
                        }
                        Text("Stock: ${formatQty(p.liveStock ?: 0.0)} ${p.unit}", color = if ((p.liveStock ?: 0.0) > 0) AppGreen else Color.Red, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PurchaseReportTab(
    purchases: List<Purchase>, vendors: List<VendorWithBalance>, categories: List<Category>, subcategories: List<Subcategory>, products: List<Product>,
    fromDate: String, toDate: String, onFromClick: ()->Unit, onToClick: ()->Unit,
    selectedVendors: List<Vendor>, onVendorsChange: (List<Vendor>)->Unit,
    selectedCategories: List<Category>, onCategoriesChange: (List<Category>)->Unit,
    selectedSubcategories: List<Subcategory>, onSubcategoriesChange: (List<Subcategory>)->Unit,
    selectedProducts: List<Product>, onProductsChange: (List<Product>)->Unit
) {
    val filtered = purchases.filter { p ->
        val dateOk = p.date.take(10) in fromDate..toDate
        val vendOk = selectedVendors.isEmpty() || selectedVendors.any { it.id == p.vendorId }

        val itemsOk = if (selectedCategories.isEmpty() && selectedSubcategories.isEmpty() && selectedProducts.isEmpty()) true else {
            p.items.any { item ->
                val prod = products.firstOrNull { it.id == item.productId }
                val cOk = selectedCategories.isEmpty() || selectedCategories.any { it.id == prod?.categoryId }
                val sOk = selectedSubcategories.isEmpty() || selectedSubcategories.any { it.id == prod?.subcategoryId }
                val pOk = selectedProducts.isEmpty() || selectedProducts.any { it.id == prod?.id }
                cOk && sOk && pOk
            }
        }
        dateOk && vendOk && itemsOk
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MultiSelectFilterRow(
            fromDate, toDate, onFromClick, onToClick,
            "Vendors", vendors.map { it.vendor }, selectedVendors, onVendorsChange, { it.name },
            "Categories", categories, selectedCategories, onCategoriesChange, { it.name },
            "Subcategories", subcategories, selectedSubcategories, onSubcategoriesChange, { it.name },
            "Products", products, selectedProducts, onProductsChange, { it.name }
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered) { pu ->
                Card(border = BorderStroke(1.dp, Color(0xFFE0E0E0))) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(pu.vendorName, fontWeight = FontWeight.Bold)
                            Text("Rs ${formatMoney(pu.total)}", color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)
                        }
                        Text("${displayDate(pu.date)} | Items: ${pu.items.size}", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

// ================== REUSABLE MULTI-SELECT COMPONENTS ==================

@Composable
fun <T1, T2, T3, T4> MultiSelectFilterRow(
    fromDate: String, toDate: String, onFromClick: ()->Unit, onToClick: ()->Unit,
    label1: String?, items1: List<T1>, selected1: List<T1>, onSelect1: (List<T1>)->Unit, name1: (T1)->String,
    label2: String, items2: List<T2>, selected2: List<T2>, onSelect2: (List<T2>)->Unit, name2: (T2)->String,
    label3: String, items3: List<T3>, selected3: List<T3>, onSelect3: (List<T3>)->Unit, name3: (T3)->String,
    label4: String, items4: List<T4>, selected4: List<T4>, onSelect4: (List<T4>)->Unit, name4: (T4)->String,
    hideDate: Boolean = false
) {
    var showDialog1 by remember { mutableStateOf(false) }
    var showDialog2 by remember { mutableStateOf(false) }
    var showDialog3 by remember { mutableStateOf(false) }
    var showDialog4 by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!hideDate) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onFromClick, modifier = Modifier.weight(1f), shape = RoundedCornerShape(6.dp)) { Text("From: $fromDate", fontSize = 12.sp) }
                OutlinedButton(onClick = onToClick, modifier = Modifier.weight(1f), shape = RoundedCornerShape(6.dp)) { Text("To: $toDate", fontSize = 12.sp) }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (label1 != null) {
                FilterChip(selected = selected1.isNotEmpty(), onClick = { showDialog1 = true }, label = { Text(if (selected1.isEmpty()) "All $label1" else "${selected1.size} $label1", fontSize = 11.sp) })
            }
            FilterChip(selected = selected2.isNotEmpty(), onClick = { showDialog2 = true }, label = { Text(if (selected2.isEmpty()) "All $label2" else "${selected2.size} $label2", fontSize = 11.sp) })
            FilterChip(selected = selected3.isNotEmpty(), onClick = { showDialog3 = true }, label = { Text(if (selected3.isEmpty()) "All $label3" else "${selected3.size} $label3", fontSize = 11.sp) })
            FilterChip(selected = selected4.isNotEmpty(), onClick = { showDialog4 = true }, label = { Text(if (selected4.isEmpty()) "All $label4" else "${selected4.size} $label4", fontSize = 11.sp) })
        }
    }

    if (showDialog1 && label1 != null) MultiSelectDialog("Select $label1", items1, selected1, name1, onSelect1) { showDialog1 = false }
    if (showDialog2) MultiSelectDialog("Select $label2", items2, selected2, name2, onSelect2) { showDialog2 = false }
    if (showDialog3) MultiSelectDialog("Select $label3", items3, selected3, name3, onSelect3) { showDialog3 = false }
    if (showDialog4) MultiSelectDialog("Select $label4", items4, selected4, name4, onSelect4) { showDialog4 = false }
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
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(allItems) { item ->
                    val isSelected = tempSelected.contains(item)
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            tempSelected = if (isSelected) tempSelected - item else tempSelected + item
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isSelected, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(itemName(item))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(tempSelected); onDismiss() }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = { tempSelected = emptyList(); onApply(emptyList()); onDismiss() }) { Text("Clear All") }
        }
    )
}