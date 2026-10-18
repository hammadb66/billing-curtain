package com.example.billing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Save
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

    // Edit mode state
    var editingItem by remember { mutableStateOf<MasterItem?>(null) }

    LaunchedEffect(Unit) { items = getItems() }

    fun saveItem() {
        if (itemName.isBlank() || itemUnit.isBlank()) return
        scope.launch {
            isSaving = true
            val oldItem = editingItem
            val newItem = MasterItem(itemName.trim(), itemUnit.trim())

            val newList = if (oldItem != null) {
                // Edit logic
                items.map { if (it == oldItem) newItem else it }
            } else {
                // Add logic
                items + newItem
            }

            saveItems(newList)

            // Agar edit hua hai, to bills mein bhi update karo
            if (oldItem != null && (oldItem.name != newItem.name || oldItem.unit != newItem.unit)) {
                repository.updateItemInBills(oldItem.name, newItem.name, newItem.unit)
            }

            items = newList
            itemName = ""
            itemUnit = ""
            editingItem = null
            isSaving = false
            snackbar.showSnackbar(if (oldItem != null) "Item updated in all bills!" else "Item added!")
        }
    }

    fun deleteItem(item: MasterItem) {
        scope.launch {
            val isUsed = repository.isItemUsedInAnyBill(item.name)
            if (isUsed) {
                snackbar.showSnackbar("Cannot delete! This item is used in bills.")
            } else {
                val newList = items - item
                items = newList
                saveItems(newList)
                snackbar.showSnackbar("Item deleted.")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Manage Inventory", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF4F6F8)
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {

            // --- Add/Edit Form Card ---
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Inventory, contentDescription = null, tint = AppGreen)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (editingItem != null) "Edit Item" else "Add New Item",
                            fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1E293B)
                        )
                    }

                    OutlinedTextField(
                        value = itemName, onValueChange = { itemName = it },
                        label = { Text("Item Name (e.g. Fabric, Carpet)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen)
                    )
                    OutlinedTextField(
                        value = itemUnit, onValueChange = { itemUnit = it },
                        label = { Text("Unit (e.g. Meter, Kg, Pcs)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (editingItem != null) {
                            OutlinedButton(
                                onClick = { editingItem = null; itemName = ""; itemUnit = "" },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("Cancel") }
                        }
                        Button(
                            onClick = { saveItem() },
                            modifier = Modifier.weight(1f),
                            enabled = itemName.isNotBlank() && itemUnit.isNotBlank() && !isSaving,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (editingItem != null) "Update" else "Add", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Your Items (${items.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))

            // --- Items List ---
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items) { item ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(40.dp).background(AppGreen.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(item.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = AppGreen)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
                                Text("Unit: ${item.unit}", color = Color.Gray, fontSize = 13.sp)
                            }
                            IconButton(
                                onClick = {
                                    editingItem = item
                                    itemName = item.name
                                    itemUnit = item.unit
                                }
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF1565C0))
                            }
                            IconButton(onClick = { deleteItem(item) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}