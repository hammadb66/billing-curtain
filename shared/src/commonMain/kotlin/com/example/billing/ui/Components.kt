package com.example.billing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billing.utils.formatMoney
import com.example.billing.utils.isValidDecimal

val AppGreen = Color(0xFF2E7D32)

// ============ PREMIUM DESIGN TOKENS ============
object AppTheme {
    val Bg = Color(0xFFF8FAFC)
    val Surface = Color.White
    val SurfaceAlt = Color(0xFFF1F5F9)
    val Border = Color(0xFFE2E8F0)
    val BorderStrong = Color(0xFFCBD5E1)
    val TextPrimary = Color(0xFF0F172A)
    val TextSecondary = Color(0xFF64748B)
    val TextMuted = Color(0xFF94A3B8)

    val Primary = AppGreen
    val PrimaryDark = Color(0xFF1B5E20)
    val PrimaryLight = Color(0xFFE8F5E9)

    val Danger = Color(0xFFDC2626)
    val DangerLight = Color(0xFFFEE2E2)
    val Warning = Color(0xFFF59E0B)
    val WarningLight = Color(0xFFFEF3C7)
    val Info = Color(0xFF3B82F6)
    val InfoLight = Color(0xFFDBEAFE)
    val Success = Color(0xFF10B981)
    val SuccessLight = Color(0xFFD1FAE5)
    val Slate = Color(0xFF475569)
    val SlateLight = Color(0xFFE2E8F0)

    val RadiusSm = 8.dp
    val RadiusMd = 12.dp
    val RadiusLg = 16.dp
    val RadiusXl = 20.dp
}

@Composable
fun AppThemeWrapper(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = AppGreen,
            onPrimary = Color.White,
            background = AppTheme.Bg,
            surface = Color.White,
            surfaceVariant = AppTheme.SurfaceAlt,
            onSurface = AppTheme.TextPrimary,
            outline = AppTheme.Border,
        ),
        content = content,
    )
}

// Legacy alias
@Composable
fun AppTheme(content: @Composable () -> Unit) = AppThemeWrapper(content)

// ============ REUSABLE PREMIUM COMPONENTS ============

/** Icon-in-circle badge used in headers */
@Composable
fun IconCircle(icon: ImageVector, color: Color, size: Int = 40, iconSize: Int = 20) {
    Box(
        Modifier
            .size(size.dp)
            .background(color.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(iconSize.dp))
    }
}

/** Section header with icon + title + optional count badge */
@Composable
fun PremiumSectionHeader(
    title: String,
    icon: ImageVector? = null,
    color: Color = AppTheme.Slate,
    count: Int? = null,
    modifier: Modifier = Modifier,
) {
    Row(modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            IconCircle(icon, color, size = 32, iconSize = 18)
            Spacer(Modifier.width(12.dp))
        }
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppTheme.TextPrimary)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Surface(color = color.copy(alpha = 0.1f), shape = CircleShape) {
                Text(
                    "$count", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun CompactNumberField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = { if (isValidDecimal(it)) onValueChange(it) },
        singleLine = true,
        textStyle = TextStyle(fontSize = 14.sp, color = AppTheme.TextPrimary, fontWeight = FontWeight.Medium),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        cursorBrush = SolidColor(AppGreen),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                Modifier
                    .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.RadiusSm))
                    .background(AppTheme.SurfaceAlt, RoundedCornerShape(AppTheme.RadiusSm))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) { inner() }
        },
    )
}

@Composable
fun TotalCard(total: Double, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.SurfaceAlt, RoundedCornerShape(AppTheme.RadiusMd))
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.RadiusMd))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("GRAND TOTAL", fontSize = 11.sp, color = AppTheme.TextSecondary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Text("Rs ${formatMoney(total)}", fontSize = 28.sp, fontWeight = FontWeight.Black, color = AppTheme.TextPrimary)
    }
}

@Composable
fun MessageDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, color = AppTheme.TextPrimary, fontSize = 18.sp) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(message, color = AppTheme.TextSecondary, fontSize = 14.sp) } },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(AppTheme.RadiusSm),
                colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
            ) { Text("OK", fontWeight = FontWeight.Bold) }
        },
        shape = RoundedCornerShape(AppTheme.RadiusLg),
        containerColor = AppTheme.Surface
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, color = AppTheme.TextPrimary, fontSize = 18.sp) },
        text = { Text(message, color = AppTheme.TextSecondary, fontSize = 14.sp) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(AppTheme.RadiusSm),
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Danger)
            ) { Text(confirmText, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AppTheme.TextSecondary) } },
        shape = RoundedCornerShape(AppTheme.RadiusLg),
        containerColor = AppTheme.Surface
    )
}

@Composable
fun CompactSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = 14.sp, color = AppTheme.TextPrimary),
        cursorBrush = SolidColor(AppGreen),
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                Modifier
                    .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.RadiusSm))
                    .background(AppTheme.SurfaceAlt, RoundedCornerShape(AppTheme.RadiusSm))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, null, tint = AppTheme.TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = AppTheme.TextMuted, fontSize = 14.sp)
                    inner()
                }
            }
        }
    )
}

/** Premium filter chip used across lists */
@Composable
fun PremiumFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    leadingIcon: ImageVector? = null,
) {
    Surface(
        shape = RoundedCornerShape(AppTheme.RadiusSm),
        color = if (selected) AppGreen.copy(alpha = 0.1f) else AppTheme.SurfaceAlt,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, if (selected) AppGreen else AppTheme.Border
        ),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, null, tint = if (selected) AppGreen else AppTheme.TextSecondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) AppGreen else AppTheme.TextSecondary
            )
        }
    }
}

/** Summary line: label on left, value on right */
@Composable
fun SummaryLine(label: String, value: String, valueColor: Color = AppTheme.TextPrimary) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = AppTheme.TextSecondary, fontWeight = FontWeight.Medium)
        Text(value, fontSize = 14.sp, color = valueColor, fontWeight = FontWeight.Bold)
    }
}