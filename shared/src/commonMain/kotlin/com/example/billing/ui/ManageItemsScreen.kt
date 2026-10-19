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
                snackbar.showSnackbar("Item '${newItem.name}' already exists!")
                isSaving = false
                return@launch
            }

            val newList = if (oldItem != null) {
                items.map { if (it == oldItem) newItem else it }
            } else {
                items + newItem
            }

            saveItems(newList)

            if (oldItem != null && (oldItem.name != newItem.name || oldItem.unit != newItem.unit)) {
                repository.updateItemInBills(oldItem.name, newItem.name, newItem.unit)
            }

            items = newList
            itemName = ""
            itemUnit = ""
            editingItem = null
            isSaving = false
            snackbar.showSnackbar(if (oldItem != null) "Item updated in bills!" else "Item added!")
        }
    }

    fun deleteItem(item: MasterItem) {
        scope.launch {
            val isUsed = repository.isItemUsedInAnyBill(item.name)
            if (isUsed) {
                snackbar.showSnackbar("❌ '${item.name}' cannot be deleted! It is used in bills.")
            } else {
                val newList = items - item
                items = newList
                saveItems(newList)
                snackbar.showSnackbar("✅ '${item.name}' deleted.")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Manage Inventory", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF1E293B))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF4F6F8)
    ) { padding ->
        // Box lagaya hai taake content center mein rahe aur screen par phailay nahi
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 850.dp) // Professional software jesi width
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // --- Add/Edit Form Card ---
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Inventory, contentDescription = null, tint = AppGreen)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (editingItem != null) "Edit Item Details" else "Add New Item",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color(0xFF1E293B)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = itemName,
                                onValueChange = { itemName = it },
                                label = { Text("Item Name") },
                                placeholder = { Text("e.g. Fabric, Carpet") },
                                leadingIcon = { Icon(Icons.Default.Inventory, contentDescription = null, tint = Color.Gray) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppGreen,
                                    unfocusedBorderColor = Color(0xFFE2E8F0)
                                )
                            )
                            OutlinedTextField(
                                value = itemUnit,
                                onValueChange = { itemUnit = it },
                                label = { Text("Unit / Measurement") },
                                placeholder = { Text("e.g. Meter, Kg, Pcs") },
                                leadingIcon = { Icon(Icons.Default.Straighten, contentDescription = null, tint = Color.Gray) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppGreen,
                                    unfocusedBorderColor = Color(0xFFE2E8F0)
                                )
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (editingItem != null) {
                                OutlinedButton(
                                    onClick = { editingItem = null; itemName = ""; itemUnit = "" },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                                ) { Text("Cancel", color = Color.DarkGray) }
                            }
                            Button(
                                onClick = { saveItem() },
                                modifier = Modifier.weight(1f).height(48.dp),
                                enabled = itemName.isNotBlank() && itemUnit.isNotBlank() && !isSaving,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (editingItem != null) "Update Item" else "Save Item", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Inventory List", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1E293B))
                    Surface(color = AppGreen.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)) {
                        Text("${items.size} Items", color = AppGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))

                // --- Items List ---
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Inventory, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No items added yet.", color = Color.Gray, fontSize = 16.sp)
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 32.dp) ) {
                        items(items) { item ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Avatar
                                    Box(
                                        Modifier.size(44.dp).background(AppGreen.copy(alpha = 0.1f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(item.name.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AppGreen)
                                    }
                                    Spacer(Modifier.width(16.dp))

                                    // Text
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
                                        Spacer(Modifier.height(4.dp))
                                        Surface(color = Color(0xFFF8FAFC), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, Color(0xFFE2E8F0))) {
                                            Text("Unit: ${item.unit}", color = Color.DarkGray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }

                                    // Actions
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        IconButton(
                                            onClick = {
                                                editingItem = item
                                                itemName = item.name
                                                itemUnit = item.unit
                                            },
                                            modifier = Modifier.background(Color(0xFFE3F2FD), shape = CircleShape).size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF1565C0), modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(
                                            onClick = { itemToDelete = item },
                                            modifier = Modifier.background(Color(0xFFFFEBEE), shape = CircleShape).size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    Spacer(Modifier.height(30.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
            title = { Text("Delete Item?", fontWeight = FontWeight.Bold) },
            text = { Text("Do you really want to delete \"${item.name}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = item
                    itemToDelete = null
                    deleteItem(toDelete)
                }) {
                    Text("Delete", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }
}