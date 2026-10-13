package com.example.billing.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billing.utils.formatMoney
import com.example.billing.utils.sanitizeDecimal

internal val DiscountColor = Color(0xFFD32F2F)
internal val ExtraColor = Color(0xFF1565C0)
internal val PaidColor = Color(0xFF2E7D32)
internal val DueColor = Color(0xFFE65100)

/**
 * Subtotal / Discount / Extra / Grand Total (+ Received / Baqaya) ka hisaab.
 * Billing, Edit aur History teeno yehi use karti hain.
 *
 * grandTotal = null  -> Grand Total aur Received/Baqaya wali lines nahi dikhengi
 * received = null    -> "poora mila" (baqaya wali lines nahi dikhengi)
 */
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
                AmountLine("BAQAYA", "Rs ${formatMoney(balance)}", DueColor, big = true)
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
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (big) FontWeight.Bold else FontWeight.Medium,
            color = if (big) Color.Gray else color,
        )
        Text(
            value,
            fontSize = if (big) 19.sp else 14.sp,
            fontWeight = if (big) FontWeight.Black else FontWeight.SemiBold,
            color = color,
        )
    }
}

/** Billing screen ke liye: "Received Rs X  |  Baqaya Rs Y" ki narangi patti. */
@Composable
fun BalanceBanner(received: Double, balance: Double, modifier: Modifier = Modifier) {
    Surface(color = DueColor.copy(alpha = 0.10f), shape = RoundedCornerShape(10.dp), modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Received: Rs ${formatMoney(received)}", fontSize = 12.sp, color = Color(0xFF475569))
            Text("Baqaya: Rs ${formatMoney(balance)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DueColor)
        }
    }
}

/** Chhota rangeen label, jaise "Discount - Rs 500". */
@Composable
fun AmountTag(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
        Text(
            text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Discount ka text field jis me "Rs" / "%" ka button hai (tap karke badlein).
 * Billing aur Edit dono screens yehi use karti hain.
 */
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
            // % 100 se zyada nahi ho sakta
            onValueChange(if (isPercent && (clean.toDoubleOrNull() ?: 0.0) > 100.0) "100" else clean)
        },
        label = { Text(if (isPercent) "Discount (%)" else "Discount (Rs)") },
        trailingIcon = {
            TextButton(
                onClick = { onModeChange(!isPercent) },
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
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