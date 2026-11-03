package com.example.billing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billing.data.BillRepository
import com.example.billing.model.MasterItem
import com.example.billing.utils.PlatformBackHandler
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageItemsScreen(
    repository: BillRepository,
    getItems: suspend () -> List<MasterItem>,
    saveItems: suspend (List<MasterItem>) -> Unit,
    onBack: () -> Unit
) {
    PlatformBackHandler { onBack() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var items by remember { mutableStateOf<List<MasterItem>>(emptyList()) }
    var itemName by remember { mutableStateOf("") }
    var itemUnit by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<MasterItem?>(null) }
    var itemToDelete by remember { mutableStateOf<MasterItem?>(null) }

    LaunchedEffect(Unit) { items = getItems() }

    fun saveItem() {
        if (itemName.isBlank() || itemUnit.isBlank()) return
        scope.launch {
            isSaving = true
            val oldItem = editingItem
            val newItem = MasterItem(itemName.trim(), itemUnit.trim())
            if (items.any { it.name.equals(newItem.name, ignoreCase = true) && it != oldItem }) {
                snackbar.showSnackbar("Item '${newItem.name}' already exists!"); isSaving = false; return@launch
            }
            val newList = if (oldItem != null) items.map { if (it == oldItem) newItem else it } else items + newItem
            saveItems(newList)
            if (oldItem != null && (oldItem.name != newItem.name || oldItem.unit != newItem.unit)) repository.updateItemInBills(oldItem.name, newItem.name, newItem.unit)
            items = newList; itemName = ""; itemUnit = ""; editingItem = null; isSaving = false
            snackbar.showSnackbar(if (oldItem != null) "Item updated in bills!" else "Item added!")
        }
    }

    fun deleteItem(item: MasterItem) {
        scope.launch {
            if (repository.isItemUsedInAnyBill(item.name)) snackbar.showSnackbar("❌ '${item.name}' cannot be deleted! It is used in bills.")
            else { val newList = items - item; items = newList; saveItems(newList); snackbar.showSnackbar("✅ '${item.name}' deleted.") }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Surface(color = AppTheme.Surface, shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppTheme.TextPrimary) }
                    Spacer(Modifier.width(12.dp))
                    Text("Manage Inventory", fontSize = 20.sp, fontWeight = FontWeight.Black, color = AppTheme.TextPrimary)
                }
            }
        },
        containerColor = AppTheme.Bg
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 850.dp).fillMaxSize().padding(16.dp)) {
                Card(shape = RoundedCornerShape(AppTheme.RadiusLg), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = AppTheme.Surface), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        PremiumSectionHeader(if (editingItem != null) "Edit Item Details" else "Add New Item", Icons.Default.Inventory, AppGreen)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(value = itemName, onValueChange = { itemName = it }, label = { Text("Item Name") }, placeholder = { Text("e.g. Fabric, Carpet") }, leadingIcon = { Icon(Icons.Default.Inventory, null, tint = AppTheme.TextMuted) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(AppTheme.RadiusMd), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen, unfocusedBorderColor = AppTheme.Border))
                            OutlinedTextField(value = itemUnit, onValueChange = { itemUnit = it }, label = { Text("Unit / Measurement") }, placeholder = { Text("e.g. Meter, Kg") }, leadingIcon = { Icon(Icons.Default.Straighten, null, tint = AppTheme.TextMuted) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(AppTheme.RadiusMd), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen, unfocusedBorderColor = AppTheme.Border))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (editingItem != null) OutlinedButton(onClick = { editingItem = null; itemName = ""; itemUnit = "" }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(AppTheme.RadiusMd), border = BorderStroke(1.dp, AppTheme.Border)) { Text("Cancel", color = AppTheme.TextSecondary) }
                            Button(onClick = { saveItem() }, modifier = Modifier.weight(1f).height(48.dp), enabled = itemName.isNotBlank() && itemUnit.isNotBlank() && !isSaving, shape = RoundedCornerShape(AppTheme.RadiusMd), colors = ButtonDefaults.buttonColors(containerColor = AppGreen)) {
                                if (isSaving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                else { Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (editingItem != null) "Update Item" else "Save Item", fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Inventory List", fontWeight = FontWeight.Black, fontSize = 18.sp, color = AppTheme.TextPrimary)
                    Surface(color = AppGreen.copy(alpha = 0.1f), shape = RoundedCornerShape(AppTheme.RadiusSm)) { Text("${items.size} Items", color = AppGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) }
                }
                Spacer(Modifier.height(12.dp))
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { IconCircle(Icons.Default.Inventory, AppTheme.TextMuted, size = 64, iconSize = 32); Spacer(Modifier.height(12.dp)); Text("No items added yet.", color = AppTheme.TextSecondary, fontSize = 16.sp) }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                        items(items) { item ->
                            Card(shape = RoundedCornerShape(AppTheme.RadiusMd), colors = CardDefaults.cardColors(containerColor = AppTheme.Surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), border = BorderStroke(1.dp, AppTheme.Border)) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(44.dp).background(AppGreen.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) { Text(item.name.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AppGreen) }
                                    Spacer(Modifier.width(16.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppTheme.TextPrimary)
                                        Spacer(Modifier.height(4.dp))
                                        Surface(color = AppTheme.SurfaceAlt, shape = RoundedCornerShape(AppTheme.RadiusSm), border = BorderStroke(1.dp, AppTheme.Border)) { Text("Unit: ${item.unit}", color = AppTheme.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        IconButton(onClick = { editingItem = item; itemName = item.name; itemUnit = item.unit }, modifier = Modifier.background(AppTheme.InfoLight, shape = CircleShape).size(36.dp)) { Icon(Icons.Default.Edit, "Edit", tint = AppTheme.Info, modifier = Modifier.size(18.dp)) }
                                        IconButton(onClick = { itemToDelete = item }, modifier = Modifier.background(AppTheme.DangerLight, shape = CircleShape).size(36.dp)) { Icon(Icons.Default.Delete, "Delete", tint = AppTheme.Danger, modifier = Modifier.size(18.dp)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            icon = { IconCircle(Icons.Default.Delete, AppTheme.Danger, 48, 24) },
            title = { Text("Delete Item?", fontWeight = FontWeight.Bold) },
            text = { Text("Do you really want to delete \"${item.name}\"? This action cannot be undone.", color = AppTheme.TextSecondary) },
            confirmButton = { TextButton(onClick = { val toDelete = item; itemToDelete = null; deleteItem(toDelete) }) { Text("Delete", color = AppTheme.Danger, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { itemToDelete = null }) { Text("Cancel", color = AppTheme.TextSecondary) } },
            shape = RoundedCornerShape(AppTheme.RadiusLg), containerColor = AppTheme.Surface
        )
    }
}