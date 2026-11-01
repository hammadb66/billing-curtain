package com.example.billing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.example.billing.utils.PlatformBackHandler
import com.example.billing.utils.formatMoney
import com.example.billing.utils.sanitizeDecimal
import kotlinx.coroutines.launch

private enum class InventoryTab { PRODUCTS, GROUPS, SUBGROUPS, CATEGORIES, SUBCATEGORIES }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProductListScreen(repository: VendorRepository, onBack: () -> Unit) {
    PlatformBackHandler { onBack() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableStateOf(InventoryTab.PRODUCTS) }

    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var subgroups by remember { mutableStateOf<List<Subgroup>>(emptyList()) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var subcategories by remember { mutableStateOf<List<Subcategory>>(emptyList()) }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }

    suspend fun reload() {
        groups = repository.listGroups()
        subgroups = repository.listSubgroups()
        categories = repository.listCategories()
        subcategories = repository.listSubcategories()
        products = repository.listProductsWithStock()
    }
    LaunchedEffect(Unit) { reload() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color(0xFFF4F5F7),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF2C3E50))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("Inventory Management", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
                    }

                    ScrollableTabRow(selectedTabIndex = tab.ordinal, containerColor = Color.White, edgePadding = 16.dp) {
                        Tab(selected = tab == InventoryTab.PRODUCTS, onClick = { tab = InventoryTab.PRODUCTS }, text = { Text("Products (${products.size})", fontWeight = FontWeight.SemiBold) })
                        Tab(selected = tab == InventoryTab.GROUPS, onClick = { tab = InventoryTab.GROUPS }, text = { Text("Groups (${groups.size})", fontWeight = FontWeight.SemiBold) })
                        Tab(selected = tab == InventoryTab.SUBGROUPS, onClick = { tab = InventoryTab.SUBGROUPS }, text = { Text("Subgroups (${subgroups.size})", fontWeight = FontWeight.SemiBold) })
                        Tab(selected = tab == InventoryTab.CATEGORIES, onClick = { tab = InventoryTab.CATEGORIES }, text = { Text("Categories (${categories.size})", fontWeight = FontWeight.SemiBold) })
                        Tab(selected = tab == InventoryTab.SUBCATEGORIES, onClick = { tab = InventoryTab.SUBCATEGORIES }, text = { Text("Subcategories (${subcategories.size})", fontWeight = FontWeight.SemiBold) })
                    }
                }
            }

            Box(Modifier.weight(1f)) {
                when (tab) {
                    InventoryTab.PRODUCTS -> ProductsTabSplit(repository, products, groups, subgroups, categories, subcategories, { scope.launch { reload() } }, snackbar)
                    InventoryTab.GROUPS -> GroupsTabSplit(repository, groups, subgroups, products, { scope.launch { reload() } }, snackbar)
                    InventoryTab.SUBGROUPS -> SubgroupsTabSplit(repository, groups, subgroups, categories, products, { scope.launch { reload() } }, snackbar)
                    InventoryTab.CATEGORIES -> CategoriesTabSplit(repository, subgroups, categories, subcategories, products, { scope.launch { reload() } }, snackbar)
                    InventoryTab.SUBCATEGORIES -> SubcategoriesTabSplit(repository, categories, subcategories, products, { scope.launch { reload() } }, snackbar)
                }
            }
        }
    }
}

// ======================= PRODUCTS TAB =======================
// ======================= PRODUCTS TAB =======================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductsTabSplit(
    repository: VendorRepository, products: List<Product>,
    groups: List<Group>, subgroups: List<Subgroup>, categories: List<Category>, subcategories: List<Subcategory>,
    onReload: () -> Unit, snackbar: SnackbarHostState
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    var formName by remember { mutableStateOf("") }
    var formGroupId by remember { mutableStateOf<Long?>(null) }
    var formSubgroupId by remember { mutableStateOf<Long?>(null) }
    var formCatId by remember { mutableStateOf<Long?>(null) }
    var formSubId by remember { mutableStateOf<Long?>(null) }
    var formPrice by remember { mutableStateOf("") }
    var formSalePrice by remember { mutableStateOf("") }
    var formDiscount by remember { mutableStateOf("0") }
    var formStock by remember { mutableStateOf("") }
    var formUnit by remember { mutableStateOf("pcs") }

    fun showSnack(msg: String) { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } }
    fun clearForm() { formName = ""; formGroupId = null; formSubgroupId = null; formCatId = null; formSubId = null; formPrice = ""; formSalePrice = ""; formDiscount = "0"; formStock = ""; formUnit = "pcs" }

    LaunchedEffect(selectedProduct) {
        if (selectedProduct != null) {
            val p = selectedProduct!!
            formName = p.name; formGroupId = p.groupId; formSubgroupId = p.subgroupId; formCatId = p.categoryId; formSubId = p.subcategoryId
            formPrice = p.price.toString(); formSalePrice = p.salePrice.toString()
            formDiscount = p.discountPct.toString(); formStock = p.openingStock.toString(); formUnit = p.unit
        }
    }

    val filtered = products.filter { query.isBlank() || it.name.contains(query, true) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 800.dp
        if (isWide) {
            Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(Modifier.weight(1f).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFFE0E0E0))) {
                    ProductListContent(query, { query = it }, selectedProduct, isNew, filtered, { selectedProduct = it; isNew = false }, { selectedProduct = null; isNew = true; clearForm() })
                }
                Card(Modifier.weight(1.5f).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFFE0E0E0))) {
                    if (selectedProduct == null && !isNew) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Select a product from the list or add a new one.", color = Color.Gray, fontSize = 15.sp) }
                    } else {
                        ProductForm(
                            initial = selectedProduct, groups = groups, subgroups = subgroups, categories = categories, subcategories = subcategories, isSaving = isSaving,
                            name = formName, onNameChange = { formName = it },
                            // ✅ CHANGE: ab parent change pe children clear NAHI hote
                            groupId = formGroupId, onGroupChange = { formGroupId = it },
                            subgroupId = formSubgroupId, onSubgroupChange = { formSubgroupId = it },
                            categoryId = formCatId, onCategoryChange = { formCatId = it },
                            subcategoryId = formSubId, onSubcategoryChange = { formSubId = it },
                            price = formPrice, onPriceChange = { formPrice = it }, salePrice = formSalePrice, onSalePriceChange = { formSalePrice = it },
                            discount = formDiscount, onDiscountChange = { formDiscount = it }, openingStock = formStock, onStockChange = { formStock = it },
                            unit = formUnit, onUnitChange = { formUnit = it },
                            onSave = { p -> scope.launch {
                                isSaving = true; val err = if (p.id == 0L) repository.addProduct(p) else repository.updateProduct(p); isSaving = false
                                if (err != null) showSnack(err) else { selectedProduct = null; isNew = false; clearForm(); onReload(); showSnack("Product saved successfully!") }
                            } },
                            onDelete = { scope.launch {
                                if (selectedProduct != null) {
                                    isSaving = true
                                    if (repository.isProductUsedInAnyBill(selectedProduct!!.id)) { isSaving = false; showSnack("Cannot delete! Used in bills.") }
                                    else { repository.deleteProduct(selectedProduct!!.id); isSaving = false; showSnack("Deleted!"); selectedProduct = null; isNew = false; clearForm(); onReload() }
                                }
                            } }
                        )
                    }
                }
            }
        } else {
            if (selectedProduct == null && !isNew) {
                ProductListContent(query, { query = it }, selectedProduct, isNew, filtered, { selectedProduct = it; isNew = false }, { selectedProduct = null; isNew = true; clearForm() }, Modifier.fillMaxSize())
            } else {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedProduct = null; isNew = false; clearForm() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF2C3E50)) }
                        Text("Back to List", fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
                    }
                    ProductForm(
                        initial = selectedProduct, groups = groups, subgroups = subgroups, categories = categories, subcategories = subcategories, isSaving = isSaving,
                        name = formName, onNameChange = { formName = it },
                        // ✅ CHANGE: yeh bhi same
                        groupId = formGroupId, onGroupChange = { formGroupId = it },
                        subgroupId = formSubgroupId, onSubgroupChange = { formSubgroupId = it },
                        categoryId = formCatId, onCategoryChange = { formCatId = it },
                        subcategoryId = formSubId, onSubcategoryChange = { formSubId = it },
                        price = formPrice, onPriceChange = { formPrice = it }, salePrice = formSalePrice, onSalePriceChange = { formSalePrice = it },
                        discount = formDiscount, onDiscountChange = { formDiscount = it }, openingStock = formStock, onStockChange = { formStock = it }, unit = formUnit, onUnitChange = { formUnit = it },
                        onSave = { p -> scope.launch {
                            isSaving = true; val err = if (p.id == 0L) repository.addProduct(p) else repository.updateProduct(p); isSaving = false
                            if (err != null) showSnack(err) else { selectedProduct = null; isNew = false; clearForm(); onReload(); showSnack("Saved!") }
                        } },
                        onDelete = { scope.launch {
                            if (selectedProduct != null) {
                                isSaving = true
                                if (repository.isProductUsedInAnyBill(selectedProduct!!.id)) { isSaving = false; showSnack("Cannot delete!") }
                                else { repository.deleteProduct(selectedProduct!!.id); isSaving = false; showSnack("Deleted!"); selectedProduct = null; isNew = false; clearForm(); onReload() }
                            }
                        } }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductListContent(query: String, onQueryChange: (String) -> Unit, selectedProduct: Product?, isNew: Boolean, filtered: List<Product>, onProductSelect: (Product) -> Unit, onAddNew: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(value = query, onValueChange = onQueryChange, placeholder = { Text("Search product...", fontSize = 13.sp) }, leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp), tint = Color.Gray) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(6.dp))
        Spacer(Modifier.height(12.dp))
        Button(onClick = onAddNew, modifier = Modifier.fillMaxWidth().height(40.dp), shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50))) { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Add New Product", fontSize = 13.sp) }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = Color(0xFFE0E0E0))
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered, key = { it.id }) { p ->
                val isSelected = selectedProduct?.id == p.id && !isNew
                Row(Modifier.fillMaxWidth().background(if (isSelected) AppGreen.copy(alpha = 0.08f) else Color.Transparent).clickable { onProductSelect(p) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp, color = if (isSelected) AppGreen else Color(0xFF1E293B))
                        val sub = listOfNotNull(p.groupName.takeIf { it.isNotBlank() }, p.subgroupName.takeIf { it.isNotBlank() }, p.categoryName.takeIf { it.isNotBlank() }, p.subcategoryName.takeIf { it.isNotBlank() }).joinToString(" › ")
                        if (sub.isNotBlank()) Text(sub, fontSize = 10.sp, color = Color.Gray)
                    }
                }
                HorizontalDivider(color = Color(0xFFF1F5F9))
            }
        }
    }
}

// ======================= GROUPS TAB =======================
@Composable
private fun GroupsTabSplit(
    repository: VendorRepository, groups: List<Group>, subgroups: List<Subgroup>, products: List<Product>,
    onReload: () -> Unit, snackbar: SnackbarHostState
) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Group?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    fun showSnack(msg: String) { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 800.dp

        val listContent = @Composable {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                GenericListContent(
                    items = groups, selected = selected, nameOf = { it.name },
                    onSelect = { selected = it; text = it.name; isAddingNew = false },
                    onAddNew = { selected = null; text = ""; isAddingNew = true },
                    title = "Add New Group"
                )
            }
        }

        val formContent = @Composable {
            GenericFormContent(
                title = "Group", selected = selected, text = text, onTextChange = { text = it }, isSaving = isSaving,
                onSave = {
                    scope.launch {
                        isSaving = true
                        try {
                            if (selected == null) repository.addGroup(text)
                            else repository.updateGroup(selected!!.id, text)
                            text = ""; selected = null; isAddingNew = false; onReload(); showSnack("Saved!")
                        } catch (e: Exception) { showSnack(e.message ?: "Error") }
                        isSaving = false
                    }
                },
                onDelete = {
                    scope.launch {
                        isSaving = true
                        val g = selected!!
                        if (subgroups.any { it.groupId == g.id } || products.any { it.groupId == g.id }) {
                            isSaving = false; showSnack("Cannot delete! Used in subgroups/products.")
                        } else {
                            repository.deleteGroup(g.id)
                            isSaving = false; text = ""; selected = null; isAddingNew = false; onReload(); showSnack("Deleted")
                        }
                    }
                }
            )
        }

        if (isWide) {
            Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(Modifier.weight(1f).fillMaxHeight(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFFE0E0E0))) { listContent() }
                Card(Modifier.weight(1.5f).fillMaxHeight(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFFE0E0E0))) {
                    if (selected == null && !isAddingNew) EmptyState("Select a group.")
                    else formContent()
                }
            }
        } else {
            if (selected == null && !isAddingNew) listContent() else Column { IconButton(onClick = { selected = null; isAddingNew = false; text = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }; formContent() }
        }
    }
}

// ======================= SUBGROUPS TAB =======================
@Composable
private fun SubgroupsTabSplit(
    repository: VendorRepository, groups: List<Group>, subgroups: List<Subgroup>,
    categories: List<Category>, products: List<Product>,
    onReload: () -> Unit, snackbar: SnackbarHostState
) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Subgroup?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    fun showSnack(msg: String) { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 800.dp
        val listContent = @Composable {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                // ❌ DropdownSelector<Group> HATA DIYA — parent zaroori nahi
                GenericListContent(
                    items = subgroups, selected = selected, nameOf = { it.name },
                    onSelect = { selected = it; text = it.name; isAddingNew = false },
                    onAddNew = { selected = null; text = ""; isAddingNew = true },
                    title = "Add New Subgroup"
                )
            }
        }
        val formContent = @Composable {
            GenericFormContent(
                title = "Subgroup", selected = selected, text = text, onTextChange = { text = it },
                isSaving = isSaving,
                onSave = {
                    scope.launch {
                        isSaving = true
                        try {
                            if (selected == null) {
                                // Duplicate check before insert
                                if (subgroups.any { it.groupId == null && it.name.equals(text.trim(), true) }) {
                                    showSnack("Subgroup '${text.trim()}' already exists.")
                                } else {
                                    repository.addSubgroup(null, text)   // null = no parent
                                    text = ""; selected = null; isAddingNew = false; onReload(); showSnack("Saved!")
                                }
                            } else {
                                repository.updateSubgroup(selected!!.id, text)
                                text = ""; selected = null; isAddingNew = false; onReload(); showSnack("Saved!")
                            }
                        } catch (e: Exception) { showSnack(e.message ?: "Error") }
                        isSaving = false
                    }
                },
                onDelete = {
                    scope.launch {
                        isSaving = true
                        val s = selected!!
                        if (categories.any { it.subgroupId == s.id } || products.any { it.subgroupId == s.id }) {
                            isSaving = false; showSnack("Cannot delete! Used by category/product.")
                        } else {
                            repository.deleteSubgroup(s.id)
                            isSaving = false; text = ""; selected = null; isAddingNew = false; onReload(); showSnack("Deleted")
                        }
                    }
                }
            )
        }

        if (isWide) {
            Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(Modifier.weight(1f).fillMaxHeight(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFFE0E0E0))) { listContent() }
                Card(Modifier.weight(1.5f).fillMaxHeight(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFFE0E0E0))) {
                    if (selected == null && !isAddingNew) EmptyState("Select a subgroup.") else formContent()
                }
            }
        } else {
            if (selected == null && !isAddingNew) listContent()
            else Column { IconButton(onClick = { selected = null; isAddingNew = false; text = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }; formContent() }
        }
    }
}

// ======================= CATEGORIES TAB =======================
@Composable
private fun CategoriesTabSplit(
    repository: VendorRepository, subgroups: List<Subgroup>, categories: List<Category>,
    subcategories: List<Subcategory>, products: List<Product>,
    onReload: () -> Unit, snackbar: SnackbarHostState
) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Category?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    fun showSnack(msg: String) { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 800.dp
        val listContent = @Composable {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                // ❌ DropdownSelector<Subgroup> HATA DIYA
                GenericListContent(
                    items = categories, selected = selected, nameOf = { it.name },
                    onSelect = { selected = it; text = it.name; isAddingNew = false },
                    onAddNew = { selected = null; text = ""; isAddingNew = true },
                    title = "Add New Category"
                )
            }
        }
        val formContent = @Composable {
            GenericFormContent(
                title = "Category", selected = selected, text = text, onTextChange = { text = it }, isSaving = isSaving,
                onSave = {
                    scope.launch {
                        isSaving = true
                        try {
                            if (selected == null) {
                                if (categories.any { it.subgroupId == null && it.name.equals(text.trim(), true) }) {
                                    showSnack("Category '${text.trim()}' already exists.")
                                } else {
                                    repository.addCategory(null, text)   // null parent
                                    text = ""; selected = null; isAddingNew = false; onReload(); showSnack("Saved!")
                                }
                            } else {
                                repository.updateCategory(selected!!.id, text)
                                text = ""; selected = null; isAddingNew = false; onReload(); showSnack("Saved!")
                            }
                        } catch (e: Exception) { showSnack(e.message ?: "Error") }
                        isSaving = false
                    }
                },
                onDelete = {
                    scope.launch {
                        isSaving = true
                        val c = selected!!
                        if (subcategories.any { it.categoryId == c.id } || products.any { it.categoryId == c.id }) {
                            isSaving = false; showSnack("Cannot delete! Used by subcategory/product.")
                        } else {
                            repository.deleteCategory(c.id)
                            isSaving = false; text = ""; selected = null; isAddingNew = false; onReload(); showSnack("Deleted")
                        }
                    }
                }
            )
        }

        if (isWide) {
            Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(Modifier.weight(1f).fillMaxHeight(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFFE0E0E0))) { listContent() }
                Card(Modifier.weight(1.5f).fillMaxHeight(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFFE0E0E0))) {
                    if (selected == null && !isAddingNew) EmptyState("Select a category.") else formContent()
                }
            }
        } else {
            if (selected == null && !isAddingNew) listContent()
            else Column { IconButton(onClick = { selected = null; isAddingNew = false; text = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }; formContent() }
        }
    }
}
// ======================= SUBCATEGORIES TAB =======================
@Composable
private fun SubcategoriesTabSplit(
    repository: VendorRepository, categories: List<Category>, subcategories: List<Subcategory>,
    products: List<Product>,
    onReload: () -> Unit, snackbar: SnackbarHostState
) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Subcategory?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    fun showSnack(msg: String) { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 800.dp
        val listContent = @Composable {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                // ❌ DropdownSelector<Category> HATA DIYA
                GenericListContent(
                    items = subcategories, selected = selected, nameOf = { it.name },
                    onSelect = { selected = it; text = it.name; isAddingNew = false },
                    onAddNew = { selected = null; text = ""; isAddingNew = true },
                    title = "Add New Subcategory"
                )
            }
        }
        val formContent = @Composable {
            GenericFormContent(
                title = "Subcategory", selected = selected, text = text, onTextChange = { text = it }, isSaving = isSaving,
                onSave = {
                    scope.launch {
                        isSaving = true
                        try {
                            if (selected == null) {
                                if (subcategories.any { it.categoryId == null && it.name.equals(text.trim(), true) }) {
                                    showSnack("Subcategory '${text.trim()}' already exists.")
                                } else {
                                    val err = repository.addSubcategory(null, text)
                                    if (err != null) showSnack(err)
                                    else { text = ""; selected = null; isAddingNew = false; onReload(); showSnack("Saved!") }
                                }
                            } else {
                                repository.updateSubcategory(selected!!.id, text)
                                text = ""; selected = null; isAddingNew = false; onReload(); showSnack("Saved!")
                            }
                        } catch (e: Exception) { showSnack(e.message ?: "Error") }
                        isSaving = false
                    }
                },
                onDelete = {
                    scope.launch {
                        isSaving = true
                        val s = selected!!
                        if (products.any { it.subcategoryId == s.id }) {
                            isSaving = false; showSnack("Cannot delete! Used by product.")
                        } else {
                            repository.deleteSubcategory(s.id)
                            isSaving = false; text = ""; selected = null; isAddingNew = false; onReload(); showSnack("Deleted")
                        }
                    }
                }
            )
        }

        if (isWide) {
            Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(Modifier.weight(1f).fillMaxHeight(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFFE0E0E0))) { listContent() }
                Card(Modifier.weight(1.5f).fillMaxHeight(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFFE0E0E0))) {
                    if (selected == null && !isAddingNew) EmptyState("Select a subcategory.") else formContent()
                }
            }
        } else {
            if (selected == null && !isAddingNew) listContent()
            else Column { IconButton(onClick = { selected = null; isAddingNew = false; text = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }; formContent() }
        }
    }
}

// ======================= HELPER COMPOSABLES =======================

@Composable
private fun <T> GenericListContent(items: List<T>, selected: T?, nameOf: (T) -> String, onSelect: (T) -> Unit, onAddNew: () -> Unit, title: String) {
    Button(onClick = onAddNew, modifier = Modifier.fillMaxWidth().height(40.dp), shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50))) {
        Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(title, fontSize = 13.sp)
    }
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = Color(0xFFE0E0E0))
    LazyColumn(Modifier.fillMaxSize()) {
        items(items.size) { index ->
            val item = items[index]
            val isSelected = selected == item
            Row(Modifier.fillMaxWidth().background(if (isSelected) AppGreen.copy(alpha = 0.08f) else Color.Transparent).clickable { onSelect(item) }.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(nameOf(item), fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) AppGreen else Color(0xFF1E293B))
            }
            HorizontalDivider(color = Color(0xFFF1F5F9))
        }
    }
}

@Composable
private fun <T> GenericFormContent(title: String, selected: T?, text: String, onTextChange: (String) -> Unit, isSaving: Boolean, onSave: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(if (selected == null) "New $title" else "Edit $title", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF2C3E50))
        OutlinedTextField(text, onTextChange, label = { Text("$title Name") }, modifier = Modifier.fillMaxWidth(), enabled = !isSaving, shape = RoundedCornerShape(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !isSaving && text.isNotBlank(), onClick = onSave, shape = RoundedCornerShape(6.dp), modifier = Modifier.height(44.dp)) {
                if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White) else Text("Save $title", fontWeight = FontWeight.Bold)
            }
            if (selected != null) OutlinedButton(enabled = !isSaving, onClick = onDelete, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red), border = BorderStroke(1.dp, Color.Red), shape = RoundedCornerShape(6.dp), modifier = Modifier.height(44.dp)) { Text("Delete", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun EmptyState(msg: String) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(msg, color = Color.Gray, fontSize = 15.sp) } }

@Composable
private fun <T> DropdownSelector(label: String, items: List<T>, selectedId: Long?, idOf: (T) -> Long, nameOf: (T) -> String, onSelect: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = items.firstOrNull { idOf(it) == selectedId }?.let { nameOf(it) } ?: ""
    Box {
        OutlinedTextField(value = selectedName, onValueChange = {}, readOnly = true, label = { Text(label) }, shape = RoundedCornerShape(6.dp), trailingIcon = { IconButton(onClick = { expanded = true }) { Icon(Icons.Default.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text("None") }, onClick = { onSelect(null); expanded = false })
            items.forEach { item -> DropdownMenuItem(text = { Text(nameOf(item)) }, onClick = { onSelect(idOf(item)); expanded = false }) }
        }
    }
}

// ======================= PRODUCT FORM =======================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductForm(
    initial: Product?, groups: List<Group>, subgroups: List<Subgroup>,
    categories: List<Category>, subcategories: List<Subcategory>, isSaving: Boolean,
    name: String, onNameChange: (String) -> Unit,
    groupId: Long?, onGroupChange: (Long?) -> Unit,
    subgroupId: Long?, onSubgroupChange: (Long?) -> Unit,
    categoryId: Long?, onCategoryChange: (Long?) -> Unit,
    subcategoryId: Long?, onSubcategoryChange: (Long?) -> Unit,
    price: String, onPriceChange: (String) -> Unit,
    salePrice: String, onSalePriceChange: (String) -> Unit,
    discount: String, onDiscountChange: (String) -> Unit,
    openingStock: String, onStockChange: (String) -> Unit,
    unit: String, onUnitChange: (String) -> Unit,
    onSave: (Product) -> Unit, onDelete: () -> Unit,
) {
    var showUnitMenu by remember { mutableStateOf(false) }
    // 🔥 FILTERING HATAO — ab har dropdown mein poori list
    // Purana:
    //   val filteredSubgroups = subgroups.filter { it.groupId == groupId }
    //   val filteredCats = categories.filter { it.subgroupId == subgroupId }
    //   val filteredSubCats = subcategories.filter { it.categoryId == categoryId }
    // Naya:
    //   (koi filter nahi — seedha original lists use karo)
    val units = listOf("pcs", "kg", "g", "m", "cm", "ft", "inch", "yd", "ltr")

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (initial == null) "New Product Details" else "Edit Product Details", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF2C3E50))
            OutlinedTextField(name, onNameChange, label = { Text("Product Name *") }, singleLine = true, enabled = !isSaving, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { DropdownSelector("Group", groups, groupId, { it.id }, { it.name }) { onGroupChange(it) } }
                // 🔥 subgroups — full list
                Box(Modifier.weight(1f)) { DropdownSelector("Subgroup", subgroups, subgroupId, { it.id }, { it.name }) { onSubgroupChange(it) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // 🔥 categories — full list
                Box(Modifier.weight(1f)) { DropdownSelector("Category", categories, categoryId, { it.id }, { it.name }) { onCategoryChange(it) } }
                // 🔥 subcategories — full list
                Box(Modifier.weight(1f)) { DropdownSelector("Subcategory", subcategories, subcategoryId, { it.id }, { it.name }) { onSubcategoryChange(it) } }
            }

            // ... baaki price, salePrice, discount, stock, unit fields jaisa hai waisa hi
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(price, { onPriceChange(sanitizeDecimal(it)) }, label = { Text("Pur. Price (Rs)") }, singleLine = true, enabled = !isSaving, shape = RoundedCornerShape(6.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(salePrice, { onSalePriceChange(sanitizeDecimal(it)) }, label = { Text("Sale Price (Rs)") }, singleLine = true, enabled = !isSaving, shape = RoundedCornerShape(6.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(discount, { onDiscountChange(sanitizeDecimal(it)) }, label = { Text("Discount % (Off)") }, singleLine = true, enabled = !isSaving, shape = RoundedCornerShape(6.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(openingStock, { onStockChange(sanitizeDecimal(it)) }, label = { Text("Opening Stock") }, singleLine = true, enabled = !isSaving, shape = RoundedCornerShape(6.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    OutlinedTextField(unit, onUnitChange, label = { Text("Unit") }, singleLine = true, enabled = !isSaving, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth(), trailingIcon = { IconButton(onClick = { showUnitMenu = true }, enabled = !isSaving) { Icon(Icons.Default.ArrowDropDown, null) } })
                    DropdownMenu(showUnitMenu, { showUnitMenu = false }) { units.forEach { u -> DropdownMenuItem(text = { Text(u) }, onClick = { onUnitChange(u); showUnitMenu = false }) } }
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 16.dp, bottom = 16.dp), color = Color(0xFFE0E0E0))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val p = Product(id = initial?.id ?: 0L, name = name.trim(), groupId = groupId, subgroupId = subgroupId, categoryId = categoryId, subcategoryId = subcategoryId, price = price.toDoubleOrNull() ?: 0.0, salePrice = salePrice.toDoubleOrNull() ?: 0.0, discountPct = discount.toDoubleOrNull() ?: 0.0, openingStock = openingStock.toDoubleOrNull() ?: 0.0, unit = unit.trim().ifBlank { "pcs" })
                        onSave(p)
                    }
                }, enabled = !isSaving && name.isNotBlank(), modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
            ) { if (isSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White) else Text("SAVE PRODUCT", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            if (initial != null) {
                OutlinedButton(onClick = onDelete, enabled = !isSaving, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red), border = BorderStroke(1.dp, Color.Red)) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("DELETE", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}