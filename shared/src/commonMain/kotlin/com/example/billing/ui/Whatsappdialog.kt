package com.example.billing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billing.utils.*
import com.example.billing.utils.sanitizePhone

@Composable
fun WhatsAppSendDialog(customerName: String, savedPhone: String, onDismiss: () -> Unit, onSend: (e164: String, saveOnBill: Boolean) -> Unit) {
    val savedNumber = remember(savedPhone) { normalizeWhatsAppNumber(savedPhone) }
    val savedValid = isValidWhatsAppNumber(savedNumber)

    var useOther by remember { mutableStateOf(!savedValid) }
    var countryIndex by remember { mutableStateOf(0) }
    var local by remember { mutableStateOf("") }
    var saveOnBill by remember { mutableStateOf(false) }

    val isOther = countryIndex >= waCountries.size
    val code = if (isOther) "" else waCountries[countryIndex].code
    val otherNumber = if (isOther) buildNumberFromFull(local) else buildNumberFromCountry(code, local)
    val otherValid = isValidWhatsAppNumber(otherNumber)

    val target = if (useOther) otherNumber else savedNumber
    val targetValid = if (useOther) otherValid else savedValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send bill on WhatsApp", fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Customer: $customerName", fontSize = 13.sp, color = AppTheme.TextSecondary, fontWeight = FontWeight.Bold)
                NumberChoiceCard(selected = !useOther, enabled = savedValid, title = "Number on this bill", subtitle = when { savedValid -> "+$savedNumber"; savedPhone.isBlank() -> "No number saved on this bill"; else -> "\"$savedPhone\" is not a valid WhatsApp number" }, subtitleColor = if (savedValid || savedPhone.isBlank()) AppTheme.TextSecondary else AppTheme.Danger, onClick = { useOther = false })
                NumberChoiceCard(selected = useOther, enabled = true, title = "Different number", subtitle = "Second number, or a number from another country", subtitleColor = AppTheme.TextSecondary, onClick = { useOther = true })

                if (useOther) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        waCountries.forEachIndexed { i, c -> FilterChip(selected = countryIndex == i, onClick = { countryIndex = i }, label = { Text("${c.label} +${c.code}", fontSize = 12.sp) }, shape = RoundedCornerShape(AppTheme.RadiusSm)) }
                        FilterChip(selected = isOther, onClick = { countryIndex = waCountries.size }, label = { Text("Other", fontSize = 12.sp) }, shape = RoundedCornerShape(AppTheme.RadiusSm))
                    }
                    OutlinedTextField(value = local, onValueChange = { local = sanitizePhone(it) }, label = { Text(if (isOther) "Number with country code" else "Phone number") }, prefix = { Text(if (isOther) "+" else "+$code ") }, singleLine = true, shape = RoundedCornerShape(AppTheme.RadiusSm), isError = local.isNotBlank() && !otherValid, supportingText = { Text(if (isOther) "Example: 447911123456 (UK), 971501234567 (UAE)" else "Without country code, e.g. 0501234567") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen, unfocusedBorderColor = AppTheme.Border))
                }

                Text(if (targetValid) "Chat will open with: +$target" else "Enter a valid number to continue", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (targetValid) AppTheme.Success else AppTheme.Danger)

                if (useOther && otherValid) {
                    Row(Modifier.fillMaxWidth().clickable { saveOnBill = !saveOnBill }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = saveOnBill, onCheckedChange = { saveOnBill = it }, colors = CheckboxDefaults.colors(checkedColor = AppGreen))
                        Text("Also save this number on the bill", fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSend(target, useOther && saveOnBill) }, enabled = targetValid, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)), shape = RoundedCornerShape(AppTheme.RadiusSm)) { Text("Send Message", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(AppTheme.RadiusLg), containerColor = AppTheme.Surface
    )
}

@Composable
private fun NumberChoiceCard(selected: Boolean, enabled: Boolean, title: String, subtitle: String, subtitleColor: Color, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(AppTheme.RadiusSm), color = if (enabled) AppTheme.Surface else AppTheme.SurfaceAlt, border = BorderStroke(1.dp, if (selected) AppGreen else AppTheme.Border), modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null, enabled = enabled, colors = RadioButtonDefaults.colors(selectedColor = AppGreen))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (enabled) AppTheme.TextPrimary else AppTheme.TextMuted)
                Text(subtitle, fontSize = 12.sp, color = subtitleColor)
            }
        }
    }
}