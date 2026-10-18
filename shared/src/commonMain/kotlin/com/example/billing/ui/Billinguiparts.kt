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
import com.example.billing.model.MasterItem
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

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (hasAdjustments) AmountLine("Subtotal", "Rs ${formatMoney(subtotal)}")
        if (discount > 0.0) AmountLine("Discount", "- Rs ${formatMoney(discount)}", DiscountColor)
        if (extra > 0.0) AmountLine("Extra / Misc", "+ Rs ${formatMoney(extra)}", ExtraColor)
        if (grandTotal != null) {
            if (hasAdjustments) HorizontalDivider(color = Color(0xFFE2E8F0))
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
        Text(label, fontSize = 13.sp, fontWeight = if (big) FontWeight.Bold else FontWeight.Medium, color = if (big) Color.Gray else color)
        Text(value, fontSize = if (big) 19.sp else 14.sp, fontWeight = if (big) FontWeight.Black else FontWeight.SemiBold, color = color)
    }
}

@Composable
fun BalanceBanner(received: Double, balance: Double, modifier: Modifier = Modifier) {
    Surface(color = DueColor.copy(alpha = 0.10f), shape = RoundedCornerShape(10.dp), modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Received: Rs ${formatMoney(received)}", fontSize = 12.sp, color = Color(0xFF475569))
            Text("Pending: Rs ${formatMoney(balance)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DueColor)
        }
    }
}

@Composable
fun AmountTag(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
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
        label = { Text(if (isPercent) "Discount (%)" else "Discount (Rs)") },
        trailingIcon = {
            TextButton(onClick = { onModeChange(!isPercent) }, enabled = enabled, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(if (isPercent) "%" else "Rs", fontWeight = FontWeight.Bold)
            }
        },
        supportingText = if (isPercent) ({ Text("= Rs ${formatMoney(computedAmount)}") }) else null,
        singleLine = true,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

// YAHAN UNIT ADD KIYA HAI
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
    unit: String = "", // <--- YEH NAYA HAI
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isIncomplete) Color(0xFFFFF3F3) else Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, if (isIncomplete) Color(0xFFFFCDD2) else Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (occurrence > 1) "$name ($occurrence)" else name, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                TextButton(onClick = onDuplicate, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(34.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = AppGreen, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Same item", fontSize = 11.sp, color = AppGreen, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove item", tint = Color.Red, modifier = Modifier.size(16.dp))
                }
            }
            Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    // Yahan Label badla hai
                    Text(if (unit.isNotBlank()) "Qty ($unit)" else "Qty", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    CompactNumberField(qty, onQtyChange, Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1f)) {
                    Text("Rate", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    CompactNumberField(rate, onRateChange, Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Total", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(formatMoney(total), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppGreen, textAlign = TextAlign.End, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
    }
}


@Composable
fun AddItemDialog(
    masterItems: List<MasterItem>,
    onPick: (MasterItem) -> Unit,
    onManageItems: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val list = remember(query, masterItems) {
        masterItems.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Item", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search item...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen)
                )
                Spacer(Modifier.height(12.dp))

                Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                    if (list.isEmpty()) {
                        Text("No item found", color = Color.Gray, modifier = Modifier.padding(vertical = 12.dp))
                    }
                    list.forEach { item ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onPick(item) },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8FAFC)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                Surface(
                                    color = AppGreen.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        item.unit,
                                        fontSize = 11.sp,
                                        color = AppGreen,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onManageItems) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Manage Items", color = AppGreen, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}