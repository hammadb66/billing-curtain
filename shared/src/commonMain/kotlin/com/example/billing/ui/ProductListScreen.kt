package com.example.billing.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billing.AppServices
import com.example.billing.data.VendorRepository
import com.example.billing.model.*
import com.example.billing.utils.PlatformBackHandler
import com.example.billing.utils.sanitizeDecimal
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private enum class InventoryTab { PRODUCTS, GROUPS, SUBGROUPS, CATEGORIES, SUBCATEGORIES }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProductListScreen(repository: VendorRepository, isViewerMode: Boolean = false, onBack: () -> Unit) {
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
        groups = repository.listGroups(); subgroups = repository.listSubgroups(); categories =
            repository.listCategories(); subcategories = repository.listSubcategories(); products =
            repository.listProductsWithStock()
    }

    val dataVersion = koinInject<AppServices>().sync.dataVersion.collectAsState().value
    LaunchedEffect(dataVersion) { reload() }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = AppTheme.Bg,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Surface(color = AppTheme.Surface, shadowElevation = 2.dp) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppTheme.TextPrimary
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Inventory Management",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = AppTheme.TextPrimary
                        )
                    }
                    ScrollableTabRow(
                        selectedTabIndex = tab.ordinal,
                        containerColor = AppTheme.Surface,
                        edgePadding = 16.dp,
                        contentColor = AppGreen
                    ) {
                        Tab(
                            selected = tab == InventoryTab.PRODUCTS,
                            onClick = { tab = InventoryTab.PRODUCTS },
                            text = { Text("Products (${products.size})", fontWeight = FontWeight.Bold) })
                        Tab(
                            selected = tab == InventoryTab.GROUPS,
                            onClick = { tab = InventoryTab.GROUPS },
                            text = { Text("Groups (${groups.size})", fontWeight = FontWeight.Bold) })
                        Tab(
                            selected = tab == InventoryTab.SUBGROUPS,
                            onClick = { tab = InventoryTab.SUBGROUPS },
                            text = { Text("Subgroups (${subgroups.size})", fontWeight = FontWeight.Bold) })
                        Tab(
                            selected = tab == InventoryTab.CATEGORIES,
                            onClick = { tab = InventoryTab.CATEGORIES },
                            text = { Text("Categories (${categories.size})", fontWeight = FontWeight.Bold) })
                        Tab(
                            selected = tab == InventoryTab.SUBCATEGORIES,
                            onClick = { tab = InventoryTab.SUBCATEGORIES },
                            text = { Text("Subcategories (${subcategories.size})", fontWeight = FontWeight.Bold) })
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    InventoryTab.PRODUCTS -> ProductsTabSplit(
                        repository,
                        products,
                        groups,
                        subgroups,
                        categories,
                        subcategories,
                        { scope.launch { reload() } },
                        snackbar,
                        isViewerMode = isViewerMode
                    )

                    InventoryTab.GROUPS -> GroupsTabSplit(
                        repository, groups, subgroups, products, { scope.launch { reload() } }, snackbar,isViewerMode = isViewerMode
                    )

                    InventoryTab.SUBGROUPS -> SubgroupsTabSplit(
                        repository, groups, subgroups, categories, products, { scope.launch { reload() } }, snackbar,isViewerMode = isViewerMode
                    )

                    InventoryTab.CATEGORIES -> CategoriesTabSplit(
                        repository,
                        subgroups,
                        categories,
                        subcategories,
                        products,
                        { scope.launch { reload() } },
                        snackbar,isViewerMode = isViewerMode
                    )

                    InventoryTab.SUBCATEGORIES -> SubcategoriesTabSplit(
                        repository, categories, subcategories, products, { scope.launch { reload() } }, snackbar,isViewerMode = isViewerMode
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductsTabSplit(
    repository: VendorRepository,
    products: List<Product>,
    groups: List<Group>,
    subgroups: List<Subgroup>,
    categories: List<Category>,
    subcategories: List<Subcategory>,
    onReload: () -> Unit,
    snackbar: SnackbarHostState,
    isViewerMode: Boolean
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    var formName by remember { mutableStateOf("") };
    var formGroupId by remember { mutableStateOf<Long?>(null) };
    var formSubgroupId by remember { mutableStateOf<Long?>(null) };
    var formCatId by remember { mutableStateOf<Long?>(null) };
    var formSubId by remember { mutableStateOf<Long?>(null) }
    var formPrice by remember { mutableStateOf("") };
    var formSalePrice by remember { mutableStateOf("") };
    var formDiscount by remember { mutableStateOf("0") };
    var formStock by remember { mutableStateOf("") };
    var formUnit by remember { mutableStateOf("pcs") }

    fun showSnack(msg: String) {
        scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) }
    }

    fun clearForm() {
        formName = ""; formGroupId = null; formSubgroupId = null; formCatId = null; formSubId = null; formPrice =
            ""; formSalePrice = ""; formDiscount = "0"; formStock = ""; formUnit = "pcs"
    }

    LaunchedEffect(selectedProduct) {
        if (selectedProduct != null) {
            val p = selectedProduct!!
            formName = p.name; formGroupId = p.groupId; formSubgroupId = p.subgroupId; formCatId =
                p.categoryId; formSubId = p.subcategoryId
            formPrice = p.price.toString(); formSalePrice = p.salePrice.toString(); formDiscount =
                p.discountPct.toString(); formStock = p.openingStock.toString(); formUnit = p.unit
        }
    }

    val filtered = products.filter { query.isBlank() || it.name.contains(query, true) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 800.dp
        if (isWide) {
            Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(
                    Modifier.weight(1f).fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.Surface),
                    shape = RoundedCornerShape(AppTheme.RadiusLg),
                    border = BorderStroke(1.dp, AppTheme.Border)
                ) {
                    ProductListContent(isViewerMode = isViewerMode,
                        query,
                        { query = it },
                        selectedProduct,
                        isNew,
                        filtered,
                        { selectedProduct = it; isNew = false },
                        { selectedProduct = null; isNew = true; clearForm() })
                }
                Card(
                    Modifier.weight(1.5f).fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.Surface),
                    shape = RoundedCornerShape(AppTheme.RadiusLg),
                    border = BorderStroke(1.dp, AppTheme.Border)
                ) {
                    if (selectedProduct == null && !isNew) Box(
                        Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Select a product from the list or add a new one.",
                            color = AppTheme.TextMuted,
                            fontSize = 15.sp
                        )
                    }
                    else {
                        ProductForm(
                            initial = selectedProduct,
                            groups = groups,
                            subgroups = subgroups,
                            categories = categories,
                            subcategories = subcategories,
                            isSaving = isSaving,
                            name = formName,
                            onNameChange = { formName = it },
                            groupId = formGroupId,
                            onGroupChange = { formGroupId = it },
                            subgroupId = formSubgroupId,
                            onSubgroupChange = { formSubgroupId = it },
                            categoryId = formCatId,
                            onCategoryChange = { formCatId = it },
                            subcategoryId = formSubId,
                            onSubcategoryChange = { formSubId = it },
                            price = formPrice,
                            onPriceChange = { formPrice = it },
                            salePrice = formSalePrice,
                            onSalePriceChange = { formSalePrice = it },
                            discount = formDiscount,
                            onDiscountChange = { formDiscount = it },
                            openingStock = formStock,
                            onStockChange = { formStock = it },
                            unit = formUnit,
                            onUnitChange = { formUnit = it },
                            onSave = { p ->
                                scope.launch {
                                    isSaving = true;
                                    val err =
                                        if (p.id == 0L) repository.addProduct(p) else repository.updateProduct(p); isSaving =
                                    false; if (err != null) showSnack(err) else {
                                    selectedProduct = null; isNew =
                                        false; clearForm(); onReload(); showSnack("Product saved successfully!")
                                }
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    if (selectedProduct != null) {
                                        isSaving = true; if (repository.isProductUsedInAnyBill(selectedProduct!!.id)) {
                                            isSaving = false; showSnack("Cannot delete! Used in bills.")
                                        } else {
                                            repository.deleteProduct(selectedProduct!!.id); isSaving = false; showSnack(
                                                "Deleted!"
                                            ); selectedProduct = null; isNew = false; clearForm(); onReload()
                                        }
                                    }
                                }
                            },
                            isViewerMode = isViewerMode
                        )
                    }
                }
            }
        } else {
            if (selectedProduct == null && !isNew) ProductListContent(isViewerMode = isViewerMode,
                query,
                { query = it },
                selectedProduct,
                isNew,
                filtered,
                { selectedProduct = it; isNew = false },
                { selectedProduct = null; isNew = true; clearForm() },
                Modifier.fillMaxSize()
            )
            else {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            selectedProduct = null; isNew = false; clearForm()
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppTheme.TextPrimary) }
                        Text("Back to List", fontWeight = FontWeight.Bold, color = AppTheme.TextPrimary)
                    }
                    ProductForm(
                        initial = selectedProduct,
                        groups = groups,
                        subgroups = subgroups,
                        categories = categories,
                        subcategories = subcategories,
                        isSaving = isSaving,
                        name = formName,
                        onNameChange = { formName = it },
                        groupId = formGroupId,
                        onGroupChange = { formGroupId = it },
                        subgroupId = formSubgroupId,
                        onSubgroupChange = { formSubgroupId = it },
                        categoryId = formCatId,
                        onCategoryChange = { formCatId = it },
                        subcategoryId = formSubId,
                        onSubcategoryChange = { formSubId = it },
                        price = formPrice,
                        onPriceChange = { formPrice = it },
                        salePrice = formSalePrice,
                        onSalePriceChange = { formSalePrice = it },
                        discount = formDiscount,
                        onDiscountChange = { formDiscount = it },
                        openingStock = formStock,
                        onStockChange = { formStock = it },
                        unit = formUnit,
                        onUnitChange = { formUnit = it },
                        onSave = { p ->
                            scope.launch {
                                isSaving = true;
                                val err =
                                    if (p.id == 0L) repository.addProduct(p) else repository.updateProduct(p); isSaving =
                                false; if (err != null) showSnack(err) else {
                                selectedProduct = null; isNew = false; clearForm(); onReload(); showSnack("Saved!")
                            }
                            }
                        },
                        onDelete = {
                            scope.launch {
                                if (selectedProduct != null) {
                                    isSaving = true; if (repository.isProductUsedInAnyBill(selectedProduct!!.id)) {
                                        isSaving = false; showSnack("Cannot delete!")
                                    } else {
                                        repository.deleteProduct(selectedProduct!!.id); isSaving =
                                            false; showSnack("Deleted!"); selectedProduct = null; isNew =
                                            false; clearForm(); onReload()
                                    }
                                }
                            }
                        },
                        isViewerMode = isViewerMode
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductListContent(
    isViewerMode: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedProduct: Product?,
    isNew: Boolean,
    filtered: List<Product>,
    onProductSelect: (Product) -> Unit,
    onAddNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        CompactSearchField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "Search product...", modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onAddNew,
            enabled = !isViewerMode,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(AppTheme.RadiusSm),
            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.TextPrimary)
        ) {
            Icon(
                Icons.Default.Add, null, modifier = Modifier.size(16.dp)
            ); Spacer(Modifier.width(6.dp)); Text("Add New Product", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = AppTheme.Border)
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered, key = { it.id }) { p ->
                val isSelected = selectedProduct?.id == p.id && !isNew
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (isSelected) AppGreen.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable { onProductSelect(p) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            p.name,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp,
                            color = if (isSelected) AppGreen else AppTheme.TextPrimary
                        )
                        val sub = listOfNotNull(
                            p.groupName.takeIf { it.isNotBlank() },
                            p.subgroupName.takeIf { it.isNotBlank() },
                            p.categoryName.takeIf { it.isNotBlank() },
                            p.subcategoryName.takeIf { it.isNotBlank() }).joinToString(" › ")
                        if (sub.isNotBlank()) Text(sub, fontSize = 10.sp, color = AppTheme.TextSecondary)
                    }
                }
                HorizontalDivider(color = AppTheme.Border)
            }
        }
    }
}

@Composable
private fun GroupsTabSplit(
    repository: VendorRepository,
    groups: List<Group>,
    subgroups: List<Subgroup>,
    products: List<Product>,
    onReload: () -> Unit,
    snackbar: SnackbarHostState,isViewerMode: Boolean
) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Group?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    fun showSnack(msg: String) {
        scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 800.dp
        val listContent = @Composable {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                GenericListContent(
                    groups,
                    selected,
                    { it.name },
                    { selected = it; text = it.name; isAddingNew = false },
                    { selected = null; text = ""; isAddingNew = true },
                    "Add New Group"
                )
            }
        }
        val formContent = @Composable {
            GenericFormContent(
                "Group", selected, text, { text = it }, isSaving, onSave = {
                    scope.launch {
                        isSaving = true; try {
                        if (selected == null) repository.addGroup(text) else repository.updateGroup(
                            selected!!.id, text
                        ); text = ""; selected = null; isAddingNew = false; onReload(); showSnack("Saved!")
                    } catch (e: Exception) {
                        showSnack(e.message ?: "Error")
                    }; isSaving = false
                    }
                }, onDelete = {
                    scope.launch {
                        isSaving = true;
                        val g =
                            selected!!; if (subgroups.any { it.groupId == g.id } || products.any { it.groupId == g.id }) {
                        isSaving = false; showSnack("Cannot delete! Used in subgroups/products.")
                    } else {
                        repository.deleteGroup(g.id); isSaving = false; text = ""; selected = null; isAddingNew =
                            false; onReload(); showSnack("Deleted")
                    }
                    }
                },
                isViewerMode = isViewerMode)
        }
        if (isWide) {
            Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(
                    Modifier.weight(1f).fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.Surface),
                    shape = RoundedCornerShape(AppTheme.RadiusLg),
                    border = BorderStroke(1.dp, AppTheme.Border)
                ) { listContent() }
                Card(
                    Modifier.weight(1.5f).fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.Surface),
                    shape = RoundedCornerShape(AppTheme.RadiusLg),
                    border = BorderStroke(1.dp, AppTheme.Border)
                ) { if (selected == null && !isAddingNew) EmptyState("Select a group.") else formContent() }
            }
        } else {
            if (selected == null && !isAddingNew) listContent() else Column {
                IconButton(onClick = {
                    selected = null; isAddingNew = false; text = ""
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }; formContent()
            }
        }
    }
}

@Composable
private fun SubgroupsTabSplit(
    repository: VendorRepository, groups: List<Group>, subgroups: List<Subgroup>, categories: List<Category>,
    products: List<Product>, onReload: () -> Unit, snackbar: SnackbarHostState,isViewerMode: Boolean
) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Subgroup?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    fun showSnack(msg: String) {
        scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 800.dp
        val listContent = @Composable {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                GenericListContent(
                    subgroups,
                    selected,
                    { it.name },
                    { selected = it; text = it.name; isAddingNew = false },
                    { selected = null; text = ""; isAddingNew = true },
                    "Add New Subgroup"
                )
            }
        }
        val formContent = @Composable {
            GenericFormContent(
                "Subgroup", selected, text, { text = it }, isSaving,
                onSave = {
                    scope.launch {
                        isSaving = true; try {
                        if (selected == null) {
                            if (subgroups.any {
                                    it.groupId == null && it.name.equals(
                                        text.trim(), true
                                    )
                                }) showSnack("Subgroup '${text.trim()}' already exists.") else {
                                repository.addSubgroup(null, text); text = ""; selected = null; isAddingNew =
                                    false; onReload(); showSnack("Saved!")
                            }
                        } else {
                            repository.updateSubgroup(selected!!.id, text); text = ""; selected = null; isAddingNew =
                                false; onReload(); showSnack("Saved!")
                        }
                    } catch (e: Exception) {
                        showSnack(e.message ?: "Error")
                    }; isSaving = false
                    }
                }, onDelete = {
                    scope.launch {
                        isSaving = true;
                        val s =
                            selected!!; if (categories.any { it.subgroupId == s.id } || products.any { it.subgroupId == s.id }) {
                        isSaving = false; showSnack("Cannot delete! Used by category/product.")
                    } else {
                        repository.deleteSubgroup(s.id); isSaving = false; text = ""; selected = null; isAddingNew =
                            false; onReload(); showSnack("Deleted")
                    }
                    }
                },
                isViewerMode = isViewerMode)
        }
        if (isWide) {
            Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(
                    Modifier.weight(1f).fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.Surface),
                    shape = RoundedCornerShape(AppTheme.RadiusLg),
                    border = BorderStroke(1.dp, AppTheme.Border)
                ) { listContent() }
                Card(
                    Modifier.weight(1.5f).fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.Surface),
                    shape = RoundedCornerShape(AppTheme.RadiusLg), border = BorderStroke(1.dp, AppTheme.Border)
                ) { if (selected == null && !isAddingNew) EmptyState("Select a subgroup.") else formContent() }
            }
        } else {
            if (selected == null && !isAddingNew) listContent() else Column {
                IconButton(onClick = {
                    selected = null; isAddingNew = false; text = ""
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }; formContent()
            }
        }
    }
}

@Composable
private fun CategoriesTabSplit(
    repository: VendorRepository,
    subgroups: List<Subgroup>,
    categories: List<Category>,
    subcategories: List<Subcategory>,
    products: List<Product>,
    onReload: () -> Unit,
    snackbar: SnackbarHostState,isViewerMode: Boolean
) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Category?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    fun showSnack(msg: String) {
        scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 800.dp
        val listContent = @Composable {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                GenericListContent(
                    categories,
                    selected,
                    { it.name },
                    { selected = it; text = it.name; isAddingNew = false },
                    { selected = null; text = ""; isAddingNew = true },
                    "Add New Category"
                )
            }
        }
        val formContent = @Composable {
            GenericFormContent(
                "Category", selected, text, { text = it }, isSaving,
                onSave = {
                    scope.launch {
                        isSaving = true; try {
                        if (selected == null) {
                            if (categories.any {
                                    it.subgroupId == null && it.name.equals(
                                        text.trim(), true
                                    )
                                }) showSnack("Category '${text.trim()}' already exists.") else {
                                repository.addCategory(null, text); text = ""; selected = null; isAddingNew =
                                    false; onReload(); showSnack("Saved!")
                            }
                        } else {
                            repository.updateCategory(selected!!.id, text); text = ""; selected = null; isAddingNew =
                                false; onReload(); showSnack("Saved!")
                        }
                    } catch (e: Exception) {
                        showSnack(e.message ?: "Error")
                    }; isSaving = false
                    }
                }, onDelete = {
                    scope.launch {
                        isSaving = true;
                        val c =
                            selected!!; if (subcategories.any { it.categoryId == c.id } || products.any { it.categoryId == c.id }) {
                        isSaving = false; showSnack("Cannot delete! Used by subcategory/product.")
                    } else {
                        repository.deleteCategory(c.id); isSaving = false; text = ""; selected = null; isAddingNew =
                            false; onReload(); showSnack("Deleted")
                    }
                    }
                },
                isViewerMode = isViewerMode)
        }
        if (isWide) {
            Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(
                    Modifier.weight(1f).fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.Surface),
                    shape = RoundedCornerShape(AppTheme.RadiusLg),
                    border = BorderStroke(1.dp, AppTheme.Border)
                ) { listContent() }
                Card(
                    Modifier.weight(1.5f).fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.Surface),
                    shape = RoundedCornerShape(AppTheme.RadiusLg), border = BorderStroke(1.dp, AppTheme.Border)
                ) { if (selected == null && !isAddingNew) EmptyState("Select a category.") else formContent() }
            }
        } else {
            if (selected == null && !isAddingNew) listContent() else Column {
                IconButton(onClick = {
                    selected = null; isAddingNew = false; text = ""
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }; formContent()
            }
        }
    }
}

@Composable
private fun SubcategoriesTabSplit(
    repository: VendorRepository,
    categories: List<Category>,
    subcategories: List<Subcategory>,
    products: List<Product>,
    onReload: () -> Unit,
    snackbar: SnackbarHostState,isViewerMode: Boolean
) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Subcategory?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    fun showSnack(msg: String) {
        scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 800.dp
        val listContent = @Composable {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                GenericListContent(
                    subcategories,
                    selected,
                    { it.name },
                    { selected = it; text = it.name; isAddingNew = false },
                    { selected = null; text = ""; isAddingNew = true }, "Add New Subcategory"
                )
            }
        }
        val formContent = @Composable {
            GenericFormContent(
                "Subcategory", selected, text, { text = it }, isSaving,
                onSave = {
                    scope.launch {
                        isSaving = true; try {
                        if (selected == null) {
                            if (subcategories.any {
                                    it.categoryId == null && it.name.equals(
                                        text.trim(), true
                                    )
                                }) showSnack(
                                "Subcategory '${text.trim()}' already exists."
                            ) else {
                                val err = repository.addSubcategory(null, text); if (err != null) showSnack(err) else {
                                    text = ""; selected = null; isAddingNew = false; onReload(); showSnack("Saved!")
                                }
                            }
                        } else {
                            repository.updateSubcategory(selected!!.id, text); text = ""; selected = null; isAddingNew =
                                false; onReload(); showSnack("Saved!")
                        }
                    } catch (e: Exception) {
                        showSnack(e.message ?: "Error")
                    }; isSaving = false
                    }
                }, onDelete = {
                    scope.launch {
                        isSaving = true;
                        val s = selected!!; if (products.any { it.subcategoryId == s.id }) {
                        isSaving = false; showSnack("Cannot delete! Used by product.")
                    } else {
                        repository.deleteSubcategory(s.id); isSaving = false; text = ""; selected = null; isAddingNew =
                            false; onReload(); showSnack("Deleted")
                    }
                    }
                },
                isViewerMode = isViewerMode)
        }
        if (isWide) {
            Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(
                    Modifier.weight(1f).fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.Surface),
                    shape = RoundedCornerShape(AppTheme.RadiusLg),
                    border = BorderStroke(1.dp, AppTheme.Border)
                ) { listContent() }
                Card(
                    Modifier.weight(1.5f).fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.Surface),
                    shape = RoundedCornerShape(AppTheme.RadiusLg),
                    border = BorderStroke(1.dp, AppTheme.Border)
                ) { if (selected == null && !isAddingNew) EmptyState("Select a subcategory.") else formContent() }
            }
        } else {
            if (selected == null && !isAddingNew) listContent() else Column {
                IconButton(onClick = {
                    selected = null; isAddingNew = false; text = ""
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }; formContent()
            }
        }
    }
}

@Composable
private fun <T> GenericListContent(
    items: List<T>, selected: T?, nameOf: (T) -> String, onSelect: (T) -> Unit, onAddNew: () -> Unit, title: String
) {
    Button(
        onClick = onAddNew,
        modifier = Modifier.fillMaxWidth().height(44.dp),
        shape = RoundedCornerShape(AppTheme.RadiusSm),
        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.TextPrimary)
    ) {
        Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(
        title, fontSize = 13.sp, fontWeight = FontWeight.Bold
    )
    }
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = AppTheme.Border)
    LazyColumn(Modifier.fillMaxSize()) {
        items(items.size) { index ->
            val item = items[index]
            val isSelected = selected == item
            Row(
                Modifier.fillMaxWidth().background(if (isSelected) AppGreen.copy(alpha = 0.08f) else Color.Transparent)
                    .clickable { onSelect(item) }.padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    nameOf(item),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) AppGreen else AppTheme.TextPrimary
                )
            }
            HorizontalDivider(color = AppTheme.Border)
        }
    }
}

@Composable
private fun <T> GenericFormContent(title: String, selected: T?, text: String,
                                   onTextChange: (String) -> Unit,
                                   isSaving: Boolean,
                                   onSave: () -> Unit,
                                   onDelete: () -> Unit,isViewerMode: Boolean
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(
            16.dp
        )
    ) {
        Text(
            if (selected == null) "New $title" else "Edit $title",
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            color = AppTheme.TextPrimary
        )
        OutlinedTextField(
            text, onTextChange,
            label = { Text("$title Name") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving,
            shape = RoundedCornerShape(AppTheme.RadiusSm),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppGreen, unfocusedBorderColor = AppTheme.Border
            )
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !isSaving && text.isNotBlank() && !isViewerMode,
                onClick = onSave,
                shape = RoundedCornerShape(AppTheme.RadiusSm),
                modifier = Modifier.height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
            ) {
                if (isSaving) CircularProgressIndicator(
                    Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White
                ) else Text("Save $title", fontWeight = FontWeight.Bold)
            }
            if (selected != null) OutlinedButton(
                enabled = !isSaving && !isViewerMode,
                onClick = onDelete,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTheme.Danger),
                border = BorderStroke(1.dp, AppTheme.Danger),
                shape = RoundedCornerShape(AppTheme.RadiusSm),
                modifier = Modifier.height(44.dp)
            ) { Text("Delete", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun EmptyState(msg: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            msg, color = AppTheme.TextSecondary, fontSize = 15.sp
        )
    }
}

@Composable
private fun <T> DropdownSelector(
    label: String,
    items: List<T>,
    selectedId: Long?,
    idOf: (T) -> Long,
    nameOf: (T) -> String,
    onSelect: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = items.firstOrNull { idOf(it) == selectedId }?.let { nameOf(it) } ?: ""
    Box {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            shape = RoundedCornerShape(AppTheme.RadiusSm),
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        Icons.Default.ArrowDropDown, null, tint = AppTheme.TextSecondary
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppGreen, unfocusedBorderColor = AppTheme.Border
            )
        )
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text("None") }, onClick = { onSelect(null); expanded = false })
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(nameOf(item)) },
                    onClick = { onSelect(idOf(item)); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductForm(
    initial: Product?,
    groups: List<Group>,
    subgroups: List<Subgroup>,
    categories: List<Category>,
    subcategories: List<Subcategory>,
    isSaving: Boolean,
    name: String,
    onNameChange: (String) -> Unit,
    groupId: Long?,
    onGroupChange: (Long?) -> Unit,
    subgroupId: Long?,
    onSubgroupChange: (Long?) -> Unit,
    categoryId: Long?,
    onCategoryChange: (Long?) -> Unit,
    subcategoryId: Long?,
    onSubcategoryChange: (Long?) -> Unit,
    price: String,
    onPriceChange: (String) -> Unit,
    salePrice: String,
    onSalePriceChange: (String) -> Unit,
    discount: String,
    onDiscountChange: (String) -> Unit,
    openingStock: String,
    onStockChange: (String) -> Unit,
    unit: String,
    onUnitChange: (String) -> Unit,
    onSave: (Product) -> Unit,
    onDelete: () -> Unit,
    isViewerMode: Boolean
) {
    var showUnitMenu by remember { mutableStateOf(false) }
    val units = listOf("pcs", "kg", "g", "m", "cm", "ft", "inch", "yd", "ltr")

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (initial == null) "New Product Details" else "Edit Product Details",
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                color = AppTheme.TextPrimary
            )
            OutlinedTextField(
                name, onNameChange, label = { Text("Product Name *") }, singleLine = true, enabled = !isSaving,
                shape = RoundedCornerShape(AppTheme.RadiusSm),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppGreen, unfocusedBorderColor = AppTheme.Border
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    DropdownSelector(
                        "Group",
                        groups,
                        groupId,
                        { it.id },
                        { it.name }) { onGroupChange(it) }
                }
                Box(Modifier.weight(1f)) {
                    DropdownSelector(
                        "Subgroup",
                        subgroups,
                        subgroupId,
                        { it.id },
                        { it.name }) { onSubgroupChange(it) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    DropdownSelector(
                        "Category",
                        categories,
                        categoryId,
                        { it.id },
                        { it.name }) { onCategoryChange(it) }
                }
                Box(Modifier.weight(1f)) {
                    DropdownSelector(
                        "Subcategory",
                        subcategories,
                        subcategoryId,
                        { it.id },
                        { it.name }) { onSubcategoryChange(it) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    price,
                    { onPriceChange(sanitizeDecimal(it)) },
                    label = { Text("Pur. Price (Rs)") },
                    singleLine = true,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(AppTheme.RadiusSm),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppGreen, unfocusedBorderColor = AppTheme.Border
                    )
                )
                OutlinedTextField(
                    salePrice,
                    { onSalePriceChange(sanitizeDecimal(it)) },
                    label = { Text("Sale Price (Rs)") },
                    singleLine = true,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(AppTheme.RadiusSm),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppGreen, unfocusedBorderColor = AppTheme.Border
                    )
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    discount,
                    { onDiscountChange(sanitizeDecimal(it)) },
                    label = { Text("Discount % (Off)") },
                    singleLine = true,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(AppTheme.RadiusSm),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppGreen, unfocusedBorderColor = AppTheme.Border
                    )
                )
                OutlinedTextField(
                    openingStock,
                    { onStockChange(sanitizeDecimal(it)) },
                    label = { Text("Opening Stock") },
                    singleLine = true,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(AppTheme.RadiusSm),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppGreen, unfocusedBorderColor = AppTheme.Border
                    )
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    OutlinedTextField(
                        unit,
                        onUnitChange,
                        label = { Text("Unit") },
                        singleLine = true,
                        enabled = !isSaving,
                        shape = RoundedCornerShape(AppTheme.RadiusSm),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(
                                onClick = { showUnitMenu = true }, enabled = !isSaving
                            ) { Icon(Icons.Default.ArrowDropDown, null, tint = AppTheme.TextSecondary) }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppGreen, unfocusedBorderColor = AppTheme.Border
                        )
                    )
                    DropdownMenu(
                        showUnitMenu,
                        { showUnitMenu = false }) {
                        units.forEach { u ->
                            DropdownMenuItem(
                                text = { Text(u) },
                                onClick = { onUnitChange(u); showUnitMenu = false })
                        }
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 16.dp, bottom = 16.dp), color = AppTheme.Border)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val p = Product(
                            id = initial?.id ?: 0L,
                            name = name.trim(),
                            groupId = groupId,
                            subgroupId = subgroupId,
                            categoryId = categoryId,
                            subcategoryId = subcategoryId,
                            price = price.toDoubleOrNull() ?: 0.0,
                            salePrice = salePrice.toDoubleOrNull() ?: 0.0,
                            discountPct = discount.toDoubleOrNull() ?: 0.0,
                            openingStock = openingStock.toDoubleOrNull() ?: 0.0,
                            unit = unit.trim().ifBlank { "pcs" }); onSave(p)
                    }
                },
                enabled = !isSaving && name.isNotBlank() && !isViewerMode,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(AppTheme.RadiusSm),
                colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
            ) {
                if (isSaving) CircularProgressIndicator(
                    Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White
                ) else Text("SAVE PRODUCT", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            if (initial != null) {
                OutlinedButton(
                    onClick = onDelete,
                    enabled = !isSaving && !isViewerMode,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(AppTheme.RadiusSm),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTheme.Danger),
                    border = BorderStroke(1.dp, AppTheme.Danger)
                ) {
                    Icon(
                        Icons.Default.Delete, null, modifier = Modifier.size(18.dp)
                    ); Spacer(Modifier.width(6.dp)); Text("DELETE", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}