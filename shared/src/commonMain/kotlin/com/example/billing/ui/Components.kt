package com.example.billing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billing.utils.formatMoney
import com.example.billing.utils.isValidDecimal

val AppGreen = Color(0xFF2E7D32)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = AppGreen,
            onPrimary = Color.White,
            background = Color(0xFFF4F5F7), // Desktop background
            surface = Color.White,
        ),
        content = content,
    )
}

@Composable
fun CompactNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = { if (isValidDecimal(it)) onValueChange(it) },
        singleLine = true,
        textStyle = TextStyle(fontSize = 14.sp, color = Color(0xFF1E293B)),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        cursorBrush = SolidColor(AppGreen),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                Modifier
                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(6.dp))
                    .background(Color.White, RoundedCornerShape(6.dp))
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
            .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("GRAND TOTAL", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Text("Rs ${formatMoney(total)}", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F172A))
    }
}

@Composable
fun MessageDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(message, color = Color.DarkGray) } },
        confirmButton = { Button(onClick = onDismiss, shape = RoundedCornerShape(6.dp)) { Text("OK", fontWeight = FontWeight.Bold) } },
        shape = RoundedCornerShape(8.dp),
        containerColor = Color.White
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
        title = { Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50)) },
        text = { Text(message, color = Color.DarkGray) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(6.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) { Text(confirmText, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) } },
        shape = RoundedCornerShape(8.dp),
        containerColor = Color.White
    )
}