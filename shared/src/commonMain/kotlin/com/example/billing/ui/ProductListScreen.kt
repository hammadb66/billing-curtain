package com.example.billing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billing.data.VendorRepository
import com.example.billing.model.*
import com.example.billing.utils.PlatformBackHandler
import com.example.billing.utils.sanitizeDecimal
import kotlinx.coroutines.launch

private enum class InventoryTab { PRODUCTS, CATEGORIES, SUBCATEGORIES }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProductListScreen(repository: VendorRepository, onBack: () -> Unit) {
    PlatformBackHandler { onBack() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableStateOf(InventoryTab.PRODUCTS) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var subcategories by remember { mutableStateOf<List<Subcategory>>(emptyList()) }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }

    suspend fun reload() {
        categories = repository.listCategories()
        subcategories = repository.listSubcategories()
        products = repository.listProductsWithStock()
    }
    LaunchedEffect(Unit) { reload() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color(0xFFF4F5F7), // Desktop background
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            // ================== CUSTOM DESKTOP HEADER ==================
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

                    TabRow(
                        selectedTabIndex = tab.ordinal,
                        containerColor = Color.White,
                        contentColor = AppGreen,
                        divider = { HorizontalDivider(color = Color(0xFFE0E0E0)) }
                    ) {
                        Tab(
                            selected = tab == InventoryTab.PRODUCTS,
                            onClick = { tab = InventoryTab.PRODUCTS },
                            text = { Text("Products (${products.size})", fontWeight = FontWeight.SemiBold) }
                        )
                        Tab(
                            selected = tab == InventoryTab.CATEGORIES,
                            onClick = { tab = InventoryTab.CATEGORIES },
                            text = { Text("Categories (${categories.size})", fontWeight = FontWeight.SemiBold) }
                        )
                        Tab(
                            selected = tab == InventoryTab.SUBCATEGORIES,
                            onClick = { tab = InventoryTab.SUBCATEGORIES },
                            text = { Text("Subcategories", fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
            }

            // ================== MAIN CONTENT ==================
            Box(Modifier.weight(1f)) {
                when (tab) {
                    InventoryTab.PRODUCTS -> ProductsTabSplit(
                        repository = repository,
                        products = products,
                        categories = categories,
                        subcategories = subcategories,
                        onReload = { scope.launch { reload() } },
                        snackbar = snackbar
                    )
                    InventoryTab.CATEGORIES -> CategoriesTabSplit(
                        repository = repository,
                        categories = categories,
                        products = products,
                        onReload = { scope.launch { reload() } },
                        snackbar = snackbar
                    )
                    InventoryTab.SUBCATEGORIES -> SubcategoriesTabSplit(
                        repository = repository,
                        categories = categories,
                        subcategories = subcategories,
                        products = products,
                        onReload = { scope.launch { reload() } },
                        snackbar = snackbar
                    )
                }
            }
        }
    }
}

// ======================= PRODUCTS TAB =======================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductsTabSplit(
    repository: VendorRepository,
    products: List<Product>,
    categories: List<Category>,
    subcategories: List<Subcategory>,
    onReload: () -> Unit,
    snackbar: SnackbarHostState
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // --- FORM STATES ---
    var formName by remember { mutableStateOf("") }
    var formCatId by remember { mutableStateOf<Long?>(null) }
    var formSubId by remember { mutableStateOf<Long?>(null) }
    var formPrice by remember { mutableStateOf("") }
    var formSalePrice by remember { mutableStateOf("") }
    var formStock by remember { mutableStateOf("") }
    var formUnit by remember { mutableStateOf("pcs") }

    fun showSnack(msg: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(msg)
        }
    }

    fun clearForm() {
        formName = ""
        formCatId = null
        formSubId = null
        formPrice = ""
        formSalePrice = ""
        formStock = ""
        formUnit = "pcs"
    }

    LaunchedEffect(selectedProduct) {
        if (selectedProduct != null) {
            val p = selectedProduct!!
            formName = p.name
            formCatId = p.categoryId
            formSubId = p.subcategoryId
            formPrice = p.price.toString()
            formSalePrice = p.salePrice.toString()
            formStock = p.openingStock.toString()
            formUnit = p.unit
        }
    }

    val listFocus = remember { FocusRequester() }
    val nameFocus = remember { FocusRequester() }
    val filtered = products.filter { query.isBlank() || it.name.contains(query, true) }

    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.isCtrlPressed && event.key == Key.W && event.type == KeyEventType.KeyDown) {
                listFocus.requestFocus()
                true
            } else false
        }
    ) {
        Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {

            // LEFT: LIST PANEL
            Card(
                Modifier.weight(1f).fillMaxHeight().focusRequester(listFocus),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text("Search product...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp), tint = Color.Gray) },
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(6.dp)
                    )
                    Button(
                        onClick = {
                            selectedProduct = null
                            isNew = true
                            clearForm()
                            scope.launch {
                                kotlinx.coroutines.delay(100)
                                nameFocus.requestFocus()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(40.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50))
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add New Product", fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFFE0E0E0))

                    LazyColumn(Modifier.fillMaxSize()) {
                        items(filtered, key = { it.id }) { p ->
                            val isSelected = selectedProduct?.id == p.id && !isNew
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(if (isSelected) AppGreen.copy(alpha = 0.08f) else Color.Transparent)
                                    .clickable {
                                        selectedProduct = p
                                        isNew = false
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        p.name,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp,
                                        color = if (isSelected) AppGreen else Color(0xFF1E293B)
                                    )
                                    val sub = listOfNotNull(p.categoryName.takeIf { it.isNotBlank() }, p.subcategoryName.takeIf { it.isNotBlank() }).joinToString(" › ")
                                    if (sub.isNotBlank()) Text(sub, fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                        }
                    }
                }
            }

            // RIGHT: FORM PANEL
            Card(
                Modifier.weight(1.5f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                val showForm = selectedProduct != null || isNew
                if (!showForm) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Inventory, null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("Select a product from the list or add a new one.", color = Color.Gray, fontSize = 15.sp)
                        }
                    }
                } else {
                    ProductForm(
                        initial = selectedProduct,
                        categories = categories,
                        subcategories = subcategories,
                        isLocked = isLocked,
                        onLockChange = { isLocked = it },
                        isSaving = isSaving,
                        nameFocus = nameFocus,
                        name = formName, onNameChange = { formName = it },
                        categoryId = formCatId, onCategoryChange = { formCatId = it },
                        subcategoryId = formSubId, onSubcategoryChange = { formSubId = it },
                        price = formPrice, onPriceChange = { formPrice = it },
                        salePrice = formSalePrice, onSalePriceChange = { formSalePrice = it },
                        openingStock = formStock, onStockChange = { formStock = it },
                        unit = formUnit, onUnitChange = { formUnit = it },

                        onSave = { p, lock ->
                            if (isSaving) return@ProductForm
                            isSaving = true
                            scope.launch {
                                try {
                                    val err = if (p.id == 0L) repository.addProduct(p) else repository.updateProduct(p)
                                    if (err != null) {
                                        showSnack(err)
                                    } else {
                                        if (!lock) {
                                            selectedProduct = null
                                            isNew = false
                                            clearForm()
                                        } else {
                                            selectedProduct = null
                                            isNew = true
                                        }
                                        onReload()
                                        showSnack("Saved successfully!")
                                    }
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        onDelete = {
                            if (isSaving) return@ProductForm
                            isSaving = true
                            scope.launch {
                                try {
                                    val pid = selectedProduct!!.id
                                    if (repository.isProductUsedInAnyBill(pid)) {
                                        showSnack("Cannot delete! This product is used in bills.")
                                    } else {
                                        repository.deleteProduct(pid)
                                        selectedProduct = null
                                        isNew = false
                                        clearForm()
                                        onReload()
                                        showSnack("Deleted.")
                                    }
                                } finally {
                                    isSaving = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

// ======================= CATEGORIES TAB =======================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoriesTabSplit(
    repository: VendorRepository,
    categories: List<Category>,
    products: List<Product>,
    onReload: () -> Unit,
    snackbar: SnackbarHostState
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Category?>(null) }
    var text by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    fun showSnack(msg: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(msg)
        }
    }

    val listFocus = remember { FocusRequester() }
    val filtered = categories.filter { query.isBlank() || it.name.contains(query, true) }

    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.isCtrlPressed && event.key == Key.W && event.type == KeyEventType.KeyDown) {
                listFocus.requestFocus(); true
            } else false
        }
    ) {
        Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // LEFT PANEL
            Card(
                Modifier.weight(1f).fillMaxHeight().focusRequester(listFocus),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text("Search category...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp), tint = Color.Gray) },
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(6.dp)
                    )
                    Button(
                        onClick = { selected = null; text = "" },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(40.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50))
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add New Category", fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFFE0E0E0))

                    LazyColumn {
                        items(filtered, key = { it.id }) { c ->
                            val isSelected = selected?.id == c.id
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(if (isSelected) AppGreen.copy(alpha = 0.08f) else Color.Transparent)
                                    .clickable { selected = c; text = c.name }
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                Text(
                                    c.name,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) AppGreen else Color(0xFF1E293B)
                                )
                            }
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                        }
                    }
                }
            }

            // RIGHT PANEL
            Card(
                Modifier.weight(1.5f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(if (selected == null) "New Category Details" else "Edit Category Details", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF2C3E50))
                    OutlinedTextField(
                        text, { text = it },
                        label = { Text("Category Name") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving,
                        shape = RoundedCornerShape(6.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !isSaving && text.isNotBlank(),
                            onClick = {
                                if (isSaving) return@Button
                                isSaving = true
                                scope.launch {
                                    try {
                                        val err = if (selected == null) repository.addCategory(text) else repository.updateCategory(selected!!.id, text)
                                        if (err != null) {
                                            showSnack(err)
                                        } else {
                                            text = ""
                                            selected = null
                                            onReload()
                                            showSnack("Saved!")
                                        }
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                            else Text("Save Category", fontWeight = FontWeight.Bold)
                        }
                        if (selected != null) {
                            OutlinedButton(
                                enabled = !isSaving,
                                onClick = {
                                    if (isSaving) return@OutlinedButton
                                    isSaving = true
                                    scope.launch {
                                        try {
                                            val cat = selected!!
                                            val inUse = products.any { it.categoryId == cat.id }
                                            if (inUse) {
                                                showSnack("Cannot delete! Products exist in this category.")
                                            } else {
                                                repository.deleteCategory(cat.id)
                                                text = ""
                                                selected = null
                                                onReload()
                                                showSnack("Deleted")
                                            }
                                        } finally {
                                            isSaving = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                border = BorderStroke(1.dp, Color.Red),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(44.dp)
                            ) { Text("Delete Category", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

// ======================= SUBCATEGORIES TAB =======================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubcategoriesTabSplit(
    repository: VendorRepository,
    categories: List<Category>,
    subcategories: List<Subcategory>,
    products: List<Product>,
    onReload: () -> Unit,
    snackbar: SnackbarHostState
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var selectedCatId by remember { mutableStateOf<Long?>(categories.firstOrNull()?.id) }
    var selectedSub by remember { mutableStateOf<Subcategory?>(null) }
    var text by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    fun showSnack(msg: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(msg)
        }
    }

    val listFocus = remember { FocusRequester() }

    val filtered = subcategories.filter {
        it.categoryId == selectedCatId && (query.isBlank() || it.name.contains(query, true))
    }

    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.isCtrlPressed && event.key == Key.W && event.type == KeyEventType.KeyDown) {
                listFocus.requestFocus(); true
            } else false
        }
    ) {
        Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // LEFT PANEL
            Card(
                Modifier.weight(1f).fillMaxHeight().focusRequester(listFocus),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { c ->
                            FilterChip(
                                selected = c.id == selectedCatId,
                                onClick = { selectedCatId = c.id; selectedSub = null; text = "" },
                                label = { Text(c.name) },
                                shape = RoundedCornerShape(6.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text("Search subcategory...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp), tint = Color.Gray) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(6.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { selectedSub = null; text = "" },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(40.dp),
                        enabled = selectedCatId != null,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50))
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Subcategory", fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFFE0E0E0))

                    LazyColumn {
                        items(filtered, key = { it.id }) { s ->
                            val isSelected = selectedSub?.id == s.id
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(if (isSelected) AppGreen.copy(alpha = 0.08f) else Color.Transparent)
                                    .clickable { selectedSub = s; text = s.name }
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                Text(
                                    s.name,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) AppGreen else Color(0xFF1E293B)
                                )
                            }
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                        }
                    }
                }
            }

            // RIGHT PANEL
            Card(
                Modifier.weight(1.5f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(if (selectedSub == null) "New Subcategory Details" else "Edit Subcategory Details", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF2C3E50))
                    OutlinedTextField(
                        text, { text = it },
                        label = { Text("Subcategory Name") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedCatId != null && !isSaving,
                        shape = RoundedCornerShape(6.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !isSaving && text.isNotBlank() && selectedCatId != null,
                            onClick = {
                                if (isSaving) return@Button
                                isSaving = true
                                scope.launch {
                                    try {
                                        val err = if (selectedSub == null) repository.addSubcategory(selectedCatId!!, text) else repository.updateSubcategory(selectedSub!!.id, text)
                                        if (err != null) {
                                            showSnack(err)
                                        } else {
                                            text = ""
                                            selectedSub = null
                                            onReload()
                                            showSnack("Saved!")
                                        }
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                            else Text("Save Subcategory", fontWeight = FontWeight.Bold)
                        }
                        if (selectedSub != null) {
                            OutlinedButton(
                                enabled = !isSaving,
                                onClick = {
                                    if (isSaving) return@OutlinedButton
                                    isSaving = true
                                    scope.launch {
                                        try {
                                            val sub = selectedSub!!
                                            val inUse = products.any { it.subcategoryId == sub.id }
                                            if (inUse) {
                                                showSnack("Cannot delete! Products exist here.")
                                            } else {
                                                repository.deleteSubcategory(sub.id)
                                                text = ""
                                                selectedSub = null
                                                onReload()
                                                showSnack("Deleted")
                                            }
                                        } finally {
                                            isSaving = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                border = BorderStroke(1.dp, Color.Red),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(44.dp)
                            ) { Text("Delete", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

// ======================= PRODUCT FORM =======================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductForm(
    initial: Product?,
    categories: List<Category>,
    subcategories: List<Subcategory>,
    isLocked: Boolean,
    onLockChange: (Boolean) -> Unit,
    isSaving: Boolean,
    nameFocus: FocusRequester,
    name: String, onNameChange: (String) -> Unit,
    categoryId: Long?, onCategoryChange: (Long?) -> Unit,
    subcategoryId: Long?, onSubcategoryChange: (Long?) -> Unit,
    price: String, onPriceChange: (String) -> Unit,
    salePrice: String, onSalePriceChange: (String) -> Unit,
    openingStock: String, onStockChange: (String) -> Unit,
    unit: String, onUnitChange: (String) -> Unit,
    onSave: (Product, Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var showCatMenu by remember { mutableStateOf(false) }
    var showSubMenu by remember { mutableStateOf(false) }
    var showUnitMenu by remember { mutableStateOf(false) }

    val filteredSubs = subcategories.filter { it.categoryId == categoryId }
    val units = listOf("pcs", "kg", "g", "m", "cm", "ft", "inch", "yd", "ltr")

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        // ========== SCROLLABLE CONTENT ==========
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (initial == null) "New Product Details" else "Edit Product Details",
                fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF2C3E50)
            )

            OutlinedTextField(
                name, onNameChange,
                label = { Text("Product Name *") },
                singleLine = true,
                enabled = !isSaving,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth().focusRequester(nameFocus)
            )

            // Category & Subcategory Row
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = categories.firstOrNull { it.id == categoryId }?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        shape = RoundedCornerShape(6.dp),
                        trailingIcon = {
                            IconButton(onClick = { showCatMenu = true }, enabled = !isSaving) {
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(showCatMenu, { showCatMenu = false }) {
                        DropdownMenuItem(text = { Text("None") }, onClick = { onCategoryChange(null); onSubcategoryChange(null); showCatMenu = false })
                        categories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.name) },
                                onClick = { onCategoryChange(c.id); onSubcategoryChange(null); showCatMenu = false }
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = filteredSubs.firstOrNull { it.id == subcategoryId }?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Subcategory") },
                        shape = RoundedCornerShape(6.dp),
                        enabled = categoryId != null,
                        trailingIcon = {
                            IconButton(onClick = { showSubMenu = true }, enabled = categoryId != null && !isSaving) {
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(showSubMenu, { showSubMenu = false }) {
                        DropdownMenuItem(text = { Text("None") }, onClick = { onSubcategoryChange(null); showSubMenu = false })
                        filteredSubs.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.name) },
                                onClick = { onSubcategoryChange(s.id); showSubMenu = false }
                            )
                        }
                    }
                }
            }

            // Price Row
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    price, { onPriceChange(sanitizeDecimal(it)) },
                    label = { Text("Pur. Price (Rs)") },
                    singleLine = true,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(6.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    salePrice, { onSalePriceChange(sanitizeDecimal(it)) },
                    label = { Text("Sale Price (Rs)") },
                    singleLine = true,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(6.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            // Stock Row
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    openingStock, { onStockChange(sanitizeDecimal(it)) },
                    label = { Text("Opening Stock") },
                    singleLine = true,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(6.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                Box(Modifier.weight(1f)) {
                    OutlinedTextField(
                        unit, onUnitChange,
                        label = { Text("Unit") },
                        singleLine = true,
                        enabled = !isSaving,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { showUnitMenu = true }, enabled = !isSaving) {
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        }
                    )
                    DropdownMenu(showUnitMenu, { showUnitMenu = false }) {
                        units.forEach { u ->
                            DropdownMenuItem(
                                text = { Text(u) },
                                onClick = { onUnitChange(u); showUnitMenu = false }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0xFFE0E0E0))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(enabled = !isSaving) { onLockChange(!isLocked) }
            ) {
                Checkbox(
                    checked = isLocked,
                    onCheckedChange = { onLockChange(it) },
                    enabled = !isSaving,
                    colors = CheckboxDefaults.colors(checkedColor = AppGreen)
                )
                Spacer(Modifier.width(4.dp))
                Text("Data Not Clear (Lock form for next entry)", fontSize = 13.sp, color = Color.DarkGray)
            }
        }

        // ========== FIXED BOTTOM BUTTONS ==========
        HorizontalDivider(Modifier.padding(top = 16.dp, bottom = 16.dp), color = Color(0xFFE0E0E0))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val p = Product(
                            id = initial?.id ?: 0L,
                            name = name.trim(),
                            categoryId = categoryId,
                            subcategoryId = subcategoryId,
                            price = price.toDoubleOrNull() ?: 0.0,
                            salePrice = salePrice.toDoubleOrNull() ?: 0.0,
                            openingStock = openingStock.toDoubleOrNull() ?: 0.0,
                            unit = unit.trim().ifBlank { "pcs" }
                        )
                        onSave(p, isLocked)
                    }
                },
                enabled = !isSaving && name.isNotBlank(),
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("SAVE PRODUCT", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            if (initial != null) {
                OutlinedButton(
                    onClick = onDelete,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    border = BorderStroke(1.dp, Color.Red)
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("DELETE", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}