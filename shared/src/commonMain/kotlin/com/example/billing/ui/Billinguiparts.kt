package com.example.billing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billing.model.Product
import com.example.billing.utils.formatMoney
import com.example.billing.utils.sanitizeDecimal

internal val DiscountColor = Color(0xFFD32F2F)
internal val ExtraColor = Color(0xFF1565C0)
internal val PaidColor = Color(0xFF2E7D32)
internal val DueColor = Color(0xFFE65100)

@Composable
fun AmountBreakdown(
    subtotal: Double,
    discount: Double,
    extra: Double,
    modifier: Modifier = Modifier,
    grandTotal: Double? = null,
    received: Double? = null,
) {
    val hasAdjustments = discount > 0.0 || extra > 0.0
    val balance = if (grandTotal != null && received != null) (grandTotal - received).coerceAtLeast(0.0) else 0.0
    val hasBalance = balance > 0.005
    if (!hasAdjustments && grandTotal == null) return

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (hasAdjustments) AmountLine("Subtotal", "Rs ${formatMoney(subtotal)}")
        if (discount > 0.0) AmountLine("Discount", "- Rs ${formatMoney(discount)}", DiscountColor)
        if (extra > 0.0) AmountLine("Extra / Misc", "+ Rs ${formatMoney(extra)}", ExtraColor)
        if (grandTotal != null) {
            if (hasAdjustments) HorizontalDivider(color = Color(0xFFE0E0E0), modifier = Modifier.padding(vertical = 4.dp))
            AmountLine("GRAND TOTAL", "Rs ${formatMoney(grandTotal)}", Color(0xFF0F172A), big = true)
            if (hasBalance && received != null) {
                AmountLine("Received", "Rs ${formatMoney(received)}", PaidColor)
                AmountLine("PENDING", "Rs ${formatMoney(balance)}", DueColor, big = true)
            }
        }
    }
}

@Composable
private fun AmountLine(label: String, value: String, color: Color = Color(0xFF475569), big: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = if (big) FontWeight.Bold else FontWeight.Medium, color = if (big) Color(0xFF2C3E50) else color)
        Text(value, fontSize = if (big) 18.sp else 14.sp, fontWeight = if (big) FontWeight.Black else FontWeight.SemiBold, color = color)
    }
}

@Composable
fun BalanceBanner(received: Double, balance: Double, modifier: Modifier = Modifier) {
    Surface(
        color = DueColor.copy(alpha = 0.08f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, DueColor.copy(alpha = 0.3f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Received: Rs ${formatMoney(received)}", fontSize = 13.sp, color = Color(0xFF475569))
            Text("Pending: Rs ${formatMoney(balance)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DueColor)
        }
    }
}

@Composable
fun AmountTag(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
fun DiscountInput(
    value: String,
    onValueChange: (String) -> Unit,
    isPercent: Boolean,
    onModeChange: (Boolean) -> Unit,
    computedAmount: Double,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = {
            val clean = sanitizeDecimal(it)
            onValueChange(if (isPercent && (clean.toDoubleOrNull() ?: 0.0) > 100.0) "100" else clean)
        },
        label = { Text(if (isPercent) "Discount (%)" else "Discount (Rs)", fontSize = 12.sp) },
        trailingIcon = {
            TextButton(onClick = { onModeChange(!isPercent) }, enabled = enabled, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(if (isPercent) "%" else "Rs", fontWeight = FontWeight.Bold, color = AppGreen)
            }
        },
        supportingText = if (isPercent) ({ Text("= Rs ${formatMoney(computedAmount)}", fontSize = 10.sp) }) else null,
        singleLine = true,
        enabled = enabled,
        shape = RoundedCornerShape(6.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen)
    )
}

@Composable
fun ItemEntryCard(
    name: String,
    occurrence: Int,
    qty: String,
    onQtyChange: (String) -> Unit,
    rate: String,
    onRateChange: (String) -> Unit,
    total: Double,
    isIncomplete: Boolean,
    unit: String = "",
    displayType: String = "default",
    onDisplayTypeChange: (String) -> Unit = {},
    productName: String = "",
    categoryName: String = "",
    subcategoryName: String = "",
    stock: Double = 0.0,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.White,
        border = BorderStroke(1.dp, if (isIncomplete) Color(0xFFFFCDD2) else Color(0xFFE0E0E0)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
            // ========== TOP ROW: Naam + Same Item + Delete ==========
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (occurrence > 1) "$name ($occurrence)" else name,
                    modifier = Modifier.weight(1f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                    maxLines = 2,
                )

                TextButton(
                    onClick = onDuplicate,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Icon(Icons.Default.Add, null, tint = AppGreen, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Same", fontSize = 11.sp, color = AppGreen, fontWeight = FontWeight.Bold)
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                }
            }

            // ========== MIDDLE ROW: Dropdown aur Stock ==========
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { expanded = true }.padding(vertical = 2.dp)
                    ) {
                        val displayLabel = when(displayType) {
                            "product" -> "Show: Product"
                            "category" -> "Show: Category"
                            "subcategory" -> "Show: Subcategory"
                            else -> "Show: Default"
                        }
                        Text(displayLabel, fontSize = 11.sp, color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(14.dp), tint = Color(0xFF1565C0))
                    }
                    DropdownMenu(expanded, { expanded = false }) {
                        listOf(
                            "default" to "Default",
                            "product" to "Product: ${productName.ifBlank { "—" }}",
                            "category" to "Category: ${categoryName.ifBlank { "—" }}",
                            "subcategory" to "Subcategory: ${subcategoryName.ifBlank { "—" }}",
                        ).forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label, fontSize = 13.sp) },
                                onClick = { onDisplayTypeChange(key); expanded = false }
                            )
                        }
                    }
                }

                Text(
                    "Stock: ${formatMoney(stock)} $unit",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (stock > 0) AppGreen else Color.Red
                )
            }

            Spacer(Modifier.height(6.dp))

            // ========== BOTTOM ROW: Qty | Rate | Total ==========
            Row(
                Modifier.padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(if (unit.isNotBlank()) "Qty ($unit)" else "Qty", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    CompactNumberField(qty, onQtyChange, Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1f)) {
                    Text("Rate", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    CompactNumberField(rate, onRateChange, Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Total", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(
                        "Rs ${formatMoney(total)}",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = AppGreen, textAlign = TextAlign.End,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun AddItemDialog(
    products: List<Product>,
    onPick: (Product) -> Unit,
    onManageProducts: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val list = remember(query, products) {
        products.filter { query.isBlank() || it.name.contains(query, true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Product", fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Search product...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                    singleLine = true, shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen)
                )
                Spacer(Modifier.height(12.dp))
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    if (list.isEmpty()) Text("No product found", color = Color.Gray, modifier = Modifier.padding(12.dp))
                    list.forEach { p ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onPick(p) },
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                            color = Color.White
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                    val subtitle = listOfNotNull(
                                        p.categoryName.takeIf { it.isNotBlank() },
                                        p.subcategoryName.takeIf { it.isNotBlank() }
                                    ).joinToString(" › ")
                                    if (subtitle.isNotBlank()) Text(subtitle, fontSize = 11.sp, color = Color.Gray)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Rs ${formatMoney(p.salePrice)}", fontSize = 13.sp, color = AppGreen, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Stock: ${formatMoney(p.liveStock ?: p.openingStock)} ${p.unit}",
                                        fontSize = 10.sp,
                                        color = if ((p.liveStock ?: p.openingStock) > 0) AppGreen else Color.Red,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onManageProducts) {
                Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Manage Products", color = AppGreen, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) } },
        shape = RoundedCornerShape(8.dp),
        containerColor = Color.White
    )
}