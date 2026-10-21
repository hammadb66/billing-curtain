package com.example.billing.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.example.billing.utils.PlatformBackHandler
import com.example.billing.utils.formatMoney
import com.example.billing.utils.sanitizeDecimal
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(repository: VendorRepository, onBack: () -> Unit) {
    PlatformBackHandler { onBack() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }

    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var subcategories by remember { mutableStateOf<List<Subcategory>>(emptyList()) }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }

    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var showProductDialog by remember { mutableStateOf(false) }
    var productToDelete by remember { mutableStateOf<Product?>(null) }

    suspend fun reload() {
        categories = repository.listCategories()
        subcategories = repository.listSubcategories()
        products = repository.listProductsWithStock()
    }
    LaunchedEffect(Unit) { reload() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Inventory", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
        floatingActionButton = {
            if (tab == 0) FloatingActionButton(
                onClick = { editingProduct = null; showProductDialog = true },
                containerColor = AppGreen,
            ) { Icon(Icons.Default.Add, null, tint = Color.White) }
        },
        containerColor = Color(0xFFF4F6F8),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab, containerColor = Color.White) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Products (${products.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Categories (${categories.size})") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Subcategories") })
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                when (tab) {
                    0 -> ProductsTab(products, onEdit = { editingProduct = it; showProductDialog = true },
                        onDelete = { productToDelete = it })
                    1 -> CategoriesTab(categories,
                        onAdd = { name -> val err = repository.addCategory(name); if (err != null) snackbar.showSnackbar(err); reload() },
                        onUpdate = { id, name -> val err = repository.updateCategory(id, name); if (err != null) snackbar.showSnackbar(err); reload() },
                        onDelete = { id ->
                            scope.launch {
                                val productsUsingCat = products.any { it.categoryId == id }
                                if (productsUsingCat) {
                                    snackbar.showSnackbar("Cannot delete! Products exist in this category.")
                                } else {
                                    repository.deleteCategory(id)
                                    reload()
                                }
                            }
                        })
                    2 -> SubcategoriesTab(categories, subcategories,
                        onAdd = { cid, name -> val err = repository.addSubcategory(cid, name); if (err != null) snackbar.showSnackbar(err); reload() },
                        onUpdate = { id, name -> val err = repository.updateSubcategory(id, name); if (err != null) snackbar.showSnackbar(err); reload() },
                        onDelete = { id ->
                            scope.launch {
                                val productsUsingSub = products.any { it.subcategoryId == id }
                                if (productsUsingSub) {
                                    snackbar.showSnackbar("Cannot delete! Products exist in this subcategory.")
                                } else {
                                    repository.deleteSubcategory(id)
                                    reload()
                                }
                            }
                        })
                }
            }
        }
    }

    if (showProductDialog) {
        ProductEditDialog(
            initial = editingProduct,
            categories = categories,
            subcategories = subcategories,
            onSave = { p ->
                if (p.id == 0L) repository.addProduct(p) else repository.updateProduct(p)
                reload()
            },
            onDismiss = { showProductDialog = false; editingProduct = null },
        )
    }

    productToDelete?.let { p ->
        ConfirmDialog("Delete Product", "Delete \"${p.name}\"?", "Delete", {
            scope.launch {
                if (repository.isProductUsedInAnyBill(p.id)) {
                    snackbar.showSnackbar("Cannot delete! This product is used in bills.")
                } else {
                    repository.deleteProduct(p.id)
                    reload()
                }
                productToDelete = null
            }
        }, { productToDelete = null })
    }
}

@Composable
private fun ProductsTab(products: List<Product>, onEdit: (Product) -> Unit, onDelete: (Product) -> Unit) {
    if (products.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No product yet.", color = Color.Gray) }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(products, key = { it.id }) { p ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        val sub = listOfNotNull(p.categoryName.takeIf { it.isNotBlank() }, p.subcategoryName.takeIf { it.isNotBlank() }).joinToString(" › ")
                        if (sub.isNotBlank()) Text(sub, fontSize = 11.sp, color = Color.Gray)
                        Text("Sale: Rs ${formatMoney(p.salePrice)} • Cost: Rs ${formatMoney(p.price)} • Stock: ${formatMoney(p.liveStock ?: p.openingStock)} ${p.unit}",
                            fontSize = 12.sp, color = Color.DarkGray)
                    }
                    IconButton(onClick = { onEdit(p) }) { Icon(Icons.Default.Edit, "Edit", tint = Color(0xFF1565C0)) }
                    IconButton(onClick = { onDelete(p) }) { Icon(Icons.Default.Delete, "Delete", tint = Color.Red) }
                }
            }
        }
    }
}

@Composable
private fun CategoriesTab(
    categories: List<Category>,
    onAdd: suspend (String) -> Unit,
    onUpdate: suspend (Long, String) -> Unit,
    onDelete: (Long) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var editingCat by remember { mutableStateOf<Category?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                text, { text = it },
                label = { Text(if (editingCat == null) "New Category" else "Edit Category") },
                singleLine = true, modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                if (text.isNotBlank()) {
                    scope.launch {
                        if (editingCat == null) onAdd(text) else onUpdate(editingCat!!.id, text)
                        text = ""; editingCat = null
                    }
                }
            }, modifier = Modifier.height(56.dp)) {
                Text(if (editingCat == null) "Add" else "Update")
            }
            if (editingCat != null) {
                TextButton(onClick = { editingCat = null; text = "" }, modifier = Modifier.height(56.dp)) {
                    Text("Cancel", color = Color.Red)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(categories, key = { it.id }) { c ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { editingCat = c; text = c.name }) {
                            Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(18.dp), tint = Color(0xFF1565C0))
                        }
                        IconButton(onClick = { onDelete(c.id) }) {
                            Icon(Icons.Default.Delete, "Delete", modifier = Modifier.size(18.dp), tint = Color.Red)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubcategoriesTab(
    categories: List<Category>,
    subcategories: List<Subcategory>,
    onAdd: suspend (Long, String) -> Unit,
    onUpdate: suspend (Long, String) -> Unit,
    onDelete: (Long) -> Unit
) {
    var selectedCatId by remember { mutableStateOf<Long?>(categories.firstOrNull()?.id) }
    var text by remember { mutableStateOf("") }
    var editingSub by remember { mutableStateOf<Subcategory?>(null) }
    val scope = rememberCoroutineScope()
    val filtered = subcategories.filter { it.categoryId == selectedCatId }

    LaunchedEffect(categories) {
        if (selectedCatId == null || categories.none { it.id == selectedCatId }) {
            selectedCatId = categories.firstOrNull()?.id
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = categories.firstOrNull { it.id == selectedCatId }?.name ?: "",
            onValueChange = {},
            readOnly = true, label = { Text("Category") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.forEach { c ->
                FilterChip(selected = c.id == selectedCatId, onClick = { selectedCatId = c.id; editingSub = null; text = "" }, label = { Text(c.name) })
            }
        }
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                text, { text = it },
                label = { Text(if (editingSub == null) "New Subcategory" else "Edit Subcategory") },
                singleLine = true, modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                if (text.isNotBlank() && selectedCatId != null) {
                    scope.launch {
                        if (editingSub == null) onAdd(selectedCatId!!, text) else onUpdate(editingSub!!.id, text)
                        text = ""; editingSub = null
                    }
                }
            }, enabled = selectedCatId != null, modifier = Modifier.height(56.dp)) {
                Text(if (editingSub == null) "Add" else "Update")
            }
            if (editingSub != null) {
                TextButton(onClick = { editingSub = null; text = "" }, modifier = Modifier.height(56.dp)) {
                    Text("Cancel", color = Color.Red)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.id }) { s ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name, Modifier.weight(1f))
                        IconButton(onClick = { editingSub = s; text = s.name }) {
                            Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(18.dp), tint = Color(0xFF1565C0))
                        }
                        IconButton(onClick = { onDelete(s.id) }) {
                            Icon(Icons.Default.Delete, "Delete", modifier = Modifier.size(18.dp), tint = Color.Red)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProductEditDialog(
    initial: Product?,
    categories: List<Category>,
    subcategories: List<Subcategory>,
    onSave: suspend (Product) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var categoryId by remember { mutableStateOf(initial?.categoryId) }
    var subcategoryId by remember { mutableStateOf(initial?.subcategoryId) }
    var price by remember { mutableStateOf(initial?.price?.toString() ?: "") }
    var salePrice by remember { mutableStateOf(initial?.salePrice?.toString() ?: "") }
    var openingStock by remember { mutableStateOf(initial?.openingStock?.toString() ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: "pcs") }
    var busy by remember { mutableStateOf(false) }
    var showCatMenu by remember { mutableStateOf(false) }
    var showSubMenu by remember { mutableStateOf(false) }

    val filteredSubs = subcategories.filter { it.categoryId == categoryId }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (initial == null) "New Product" else "Edit Product") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 500.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedButton(onClick = { showCatMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Category: ${categories.firstOrNull { it.id == categoryId }?.name ?: "None"}")
                    }
                    DropdownMenu(showCatMenu, { showCatMenu = false }) {
                        DropdownMenuItem(text = { Text("None") }, onClick = { categoryId = null; subcategoryId = null; showCatMenu = false })
                        categories.forEach { c ->
                            DropdownMenuItem(text = { Text(c.name) }, onClick = { categoryId = c.id; subcategoryId = null; showCatMenu = false })
                        }
                    }
                }
                Box {
                    OutlinedButton(onClick = { showSubMenu = true }, enabled = categoryId != null, modifier = Modifier.fillMaxWidth()) {
                        Text("Subcategory: ${filteredSubs.firstOrNull { it.id == subcategoryId }?.name ?: "None"}")
                    }
                    DropdownMenu(showSubMenu, { showSubMenu = false }) {
                        DropdownMenuItem(text = { Text("None") }, onClick = { subcategoryId = null; showSubMenu = false })
                        filteredSubs.forEach { s ->
                            DropdownMenuItem(text = { Text(s.name) }, onClick = { subcategoryId = s.id; showSubMenu = false })
                        }
                    }
                }
                OutlinedTextField(price, { price = sanitizeDecimal(it) }, label = { Text("Purchase Price") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(salePrice, { salePrice = sanitizeDecimal(it) }, label = { Text("Sale Price") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(openingStock, { openingStock = sanitizeDecimal(it) }, label = { Text("Opening Stock") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(unit, { unit = it }, label = { Text("Unit") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && name.isNotBlank(), onClick = {
                scope.launch {
                    busy = true
                    onSave(Product(
                        id = initial?.id ?: 0L, name = name.trim(),
                        categoryId = categoryId, subcategoryId = subcategoryId,
                        price = price.toDoubleOrNull() ?: 0.0,
                        salePrice = salePrice.toDoubleOrNull() ?: 0.0,
                        openingStock = openingStock.toDoubleOrNull() ?: 0.0,
                        unit = unit.trim().ifBlank { "pcs" },
                    ))
                    busy = false; onDismiss()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}