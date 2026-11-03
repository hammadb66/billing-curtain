package com.example.billing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.billing.utils.formatQty
import com.example.billing.utils.sanitizeDecimal

internal val DiscountColor = AppTheme.Danger
internal val ExtraColor = AppTheme.Info
internal val PaidColor = AppTheme.Success
internal val DueColor = AppTheme.Warning

@Composable
fun AmountBreakdown(
    subtotal: Double, discount: Double, extra: Double,
    modifier: Modifier = Modifier, grandTotal: Double? = null, received: Double? = null,
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
            if (hasAdjustments) HorizontalDivider(color = AppTheme.Border, modifier = Modifier.padding(vertical = 4.dp))
            AmountLine("GRAND TOTAL", "Rs ${formatMoney(grandTotal)}", AppTheme.TextPrimary, big = true)
            if (hasBalance && received != null) {
                AmountLine("Received", "Rs ${formatMoney(received)}", PaidColor)
                AmountLine("PENDING", "Rs ${formatMoney(balance)}", DueColor, big = true)
            }
        }
    }
}

@Composable
private fun AmountLine(label: String, value: String, color: Color = AppTheme.TextSecondary, big: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = if (big) FontWeight.Bold else FontWeight.Medium,
            color = if (big) AppTheme.TextPrimary else color)
        Text(value, fontSize = if (big) 18.sp else 14.sp, fontWeight = if (big) FontWeight.Black else FontWeight.SemiBold, color = color)
    }
}

@Composable
fun BalanceBanner(received: Double, balance: Double, modifier: Modifier = Modifier) {
    Surface(
        color = DueColor.copy(alpha = 0.08f),
        shape = RoundedCornerShape(AppTheme.RadiusSm),
        border = BorderStroke(1.dp, DueColor.copy(alpha = 0.25f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Received: Rs ${formatMoney(received)}", fontSize = 12.sp, color = AppTheme.TextSecondary)
            Text("Pending: Rs ${formatMoney(balance)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DueColor)
        }
    }
}

@Composable
fun AmountTag(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
fun DiscountInput(
    value: String, onValueChange: (String) -> Unit,
    isPercent: Boolean, onModeChange: (Boolean) -> Unit,
    computedAmount: Double, modifier: Modifier = Modifier, enabled: Boolean = true,
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
        singleLine = true, enabled = enabled,
        shape = RoundedCornerShape(AppTheme.RadiusSm),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppGreen,
            unfocusedBorderColor = AppTheme.Border
        )
    )
}

@Composable
fun ItemEntryCard(
    name: String, occurrence: Int, qty: String, discount: String, discountIsPercent: Boolean,
    onDiscountChange: (String) -> Unit, onQtyChange: (String) -> Unit,
    rate: String, onRateChange: (String) -> Unit, total: Double, isIncomplete: Boolean,
    unit: String = "", displayType: String = "default", onDisplayTypeChange: (String) -> Unit = {},
    productName: String = "", categoryName: String = "", subcategoryName: String = "",
    groupName: String = "", subgroupName: String = "", stock: Double = 0.0,
    productDiscountPct: Double = 0.0, onDuplicate: () -> Unit, onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(AppTheme.RadiusSm),
        color = AppTheme.Surface,
        border = BorderStroke(1.dp, if (isIncomplete) AppTheme.Danger.copy(alpha = 0.4f) else AppTheme.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (occurrence > 1) "$name ($occurrence)" else name,
                    modifier = Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = AppTheme.TextPrimary, maxLines = 2,
                )
                TextButton(onClick = onDuplicate, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(30.dp)) {
                    Icon(Icons.Default.Add, null, tint = AppGreen, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Same", fontSize = 11.sp, color = AppGreen, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, null, tint = AppTheme.Danger, modifier = Modifier.size(16.dp))
                }
            }

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
                            "product" -> "Product"; "category" -> "Category"; "subcategory" -> "Subcategory"
                            "group" -> "Group"; "subgroup" -> "Subgroup"; else -> "Default"
                        }
                        Surface(color = AppTheme.InfoLight, shape = RoundedCornerShape(4.dp)) {
                            Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(displayLabel, fontSize = 10.sp, color = AppTheme.Info, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(12.dp), tint = AppTheme.Info)
                            }
                        }
                    }
                    DropdownMenu(expanded, { expanded = false }) {
                        listOf(
                            "default" to "Default",
                            "product" to "Product: ${productName.ifBlank { "—" }}",
                            "category" to "Category: ${categoryName.ifBlank { "—" }}",
                            "subcategory" to "Subcategory: ${subcategoryName.ifBlank { "—" }}",
                            "group" to "Group: ${groupName.ifBlank { "—" }}",
                            "subgroup" to "Subgroup: ${subgroupName.ifBlank { "—" }}"
                        ).forEach { (key, label) ->
                            DropdownMenuItem(text = { Text(label, fontSize = 13.sp) }, onClick = { onDisplayTypeChange(key); expanded = false })
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (productDiscountPct > 0.0) {
                        Surface(color = AppTheme.WarningLight, shape = RoundedCornerShape(4.dp)) {
                            Text("${formatQty(productDiscountPct)}% off", fontSize = 10.sp, color = AppTheme.Warning,
                                fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Surface(
                        color = if (stock > 0) AppTheme.SuccessLight else AppTheme.DangerLight,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("Stock: ${formatMoney(stock)} $unit", fontSize = 10.sp,
                            color = if (stock > 0) AppTheme.Success else AppTheme.Danger,
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(if (unit.isNotBlank()) "Qty ($unit)" else "Qty", fontSize = 10.sp, color = AppTheme.TextSecondary, fontWeight = FontWeight.Bold)
                    CompactNumberField(qty, onQtyChange, Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1f)) {
                    Text("Rate", fontSize = 10.sp, color = AppTheme.TextSecondary, fontWeight = FontWeight.Bold)
                    CompactNumberField(rate, onRateChange, Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(0.9f)) {
                    Text("Disc", fontSize = 10.sp, color = AppTheme.TextSecondary, fontWeight = FontWeight.Bold)
                    CompactNumberField(discount, onDiscountChange, Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1.2f), horizontalAlignment = Alignment.End) {
                    Text("Total", fontSize = 10.sp, color = AppTheme.TextSecondary, fontWeight = FontWeight.Bold)
                    val totalValue = (qty.toDoubleOrNull() ?: 0.0) * (rate.toDoubleOrNull() ?: 0.0)
                    val prodDisc = totalValue * (productDiscountPct / 100.0)
                    val netValue = totalValue - prodDisc
                    val discValue = discount.toDoubleOrNull() ?: 0.0
                    val finalTotal = if (discountIsPercent) (netValue * (1.0 - discValue / 100.0)) else (netValue - discValue)
                    Text(
                        "Rs ${formatMoney(finalTotal.coerceAtLeast(0.0))}",
                        fontSize = 15.sp, fontWeight = FontWeight.Black,
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
    products: List<Product>, onPick: (Product) -> Unit,
    onManageProducts: () -> Unit, onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val list = remember(query, products) {
        products.filter { query.isBlank() || it.name.contains(query, true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Product", fontWeight = FontWeight.Bold, color = AppTheme.TextPrimary, fontSize = 18.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Search product...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = AppTheme.TextMuted, modifier = Modifier.size(18.dp)) },
                    singleLine = true, shape = RoundedCornerShape(AppTheme.RadiusSm),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen, unfocusedBorderColor = AppTheme.Border)
                )
                Spacer(Modifier.height(12.dp))
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    if (list.isEmpty()) Text("No product found", color = AppTheme.TextMuted, modifier = Modifier.padding(12.dp))
                    list.forEach { p ->
                        Card(
                            onClick = { onPick(p) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            shape = RoundedCornerShape(AppTheme.RadiusSm),
                            border = BorderStroke(1.dp, AppTheme.Border),
                            colors = CardDefaults.cardColors(containerColor = AppTheme.Surface),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconCircle(Icons.Default.Inventory2, AppGreen, size = 36, iconSize = 18)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppTheme.TextPrimary)
                                    val subtitle = listOfNotNull(
                                        p.categoryName.takeIf { it.isNotBlank() },
                                        p.subcategoryName.takeIf { it.isNotBlank() }
                                    ).joinToString(" › ")
                                    if (subtitle.isNotBlank()) Text(subtitle, fontSize = 11.sp, color = AppTheme.TextSecondary)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Rs ${formatMoney(p.salePrice)}", fontSize = 13.sp, color = AppGreen, fontWeight = FontWeight.Bold)
                                    if (p.discountPct > 0.0) Text("${formatQty(p.discountPct)}% off", fontSize = 10.sp, color = AppTheme.Warning, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Stock: ${formatMoney(p.liveStock ?: p.openingStock)} ${p.unit}",
                                        fontSize = 10.sp,
                                        color = if ((p.liveStock ?: p.openingStock) > 0) AppTheme.Success else AppTheme.Danger,
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AppTheme.TextSecondary) } },
        shape = RoundedCornerShape(AppTheme.RadiusLg),
        containerColor = AppTheme.Surface
    )
}