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
import com.example.billing.utils.buildNumberFromCountry
import com.example.billing.utils.buildNumberFromFull
import com.example.billing.utils.isValidWhatsAppNumber
import com.example.billing.utils.normalizeWhatsAppNumber
import com.example.billing.utils.sanitizePhone
import com.example.billing.utils.waCountries

/**
 * "Kis number par bhejna hai?" ka dialog.
 *  1) Bill wala number (agar sahi lagta ho)
 *  2) Dusra number: mulk chunein (PK / UAE / UK / US / ...) + number likhein
 *
 * onSend(e164, saveOnBill): e164 = country code samet number bina '+' (jaise 971501234567)
 */
@Composable
fun WhatsAppSendDialog(
    customerName: String,
    savedPhone: String,
    onDismiss: () -> Unit,
    onSend: (e164: String, saveOnBill: Boolean) -> Unit,
) {
    val savedNumber = remember(savedPhone) { normalizeWhatsAppNumber(savedPhone) }
    val savedValid = isValidWhatsAppNumber(savedNumber)

    var useOther by remember { mutableStateOf(!savedValid) }
    var countryIndex by remember { mutableStateOf(0) }          // waCountries.size = "Other"
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
        title = { Text("Send bill on WhatsApp") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Customer: $customerName", fontSize = 13.sp, color = Color.Gray)

                NumberChoiceCard(
                    selected = !useOther,
                    enabled = savedValid,
                    title = "Number on this bill",
                    subtitle = when {
                        savedValid -> "+$savedNumber"
                        savedPhone.isBlank() -> "No number saved on this bill"
                        else -> "\"$savedPhone\" is not a valid WhatsApp number"
                    },
                    subtitleColor = if (savedValid || savedPhone.isBlank()) Color.Gray else Color.Red,
                    onClick = { useOther = false },
                )
                NumberChoiceCard(
                    selected = useOther,
                    enabled = true,
                    title = "Different number",
                    subtitle = "Second number, or a number from another country",
                    subtitleColor = Color.Gray,
                    onClick = { useOther = true },
                )

                if (useOther) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        waCountries.forEachIndexed { i, c ->
                            FilterChip(
                                selected = countryIndex == i,
                                onClick = { countryIndex = i },
                                label = { Text("${c.label} +${c.code}") },
                            )
                        }
                        FilterChip(
                            selected = isOther,
                            onClick = { countryIndex = waCountries.size },
                            label = { Text("Other") },
                        )
                    }
                    OutlinedTextField(
                        value = local,
                        onValueChange = { local = sanitizePhone(it) },
                        label = { Text(if (isOther) "Number with country code" else "Phone number") },
                        prefix = { Text(if (isOther) "+" else "+$code ") },
                        singleLine = true,
                        isError = local.isNotBlank() && !otherValid,
                        supportingText = {
                            Text(
                                if (isOther) "Example: 447911123456 (UK), 971501234567 (UAE)"
                                else "Without country code, e.g. 0501234567 or 501234567"
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Text(
                    if (targetValid) "Chat will open with: +$target" else "Enter a valid number to continue",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (targetValid) PaidColor else Color.Red,
                )

                if (useOther && otherValid) {
                    Row(
                        Modifier.fillMaxWidth().clickable { saveOnBill = !saveOnBill },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = saveOnBill, onCheckedChange = { saveOnBill = it })
                        Text("Also save this number on the bill", fontSize = 13.sp)
                    }
                }

                Text(
                    "WhatsApp cannot tell us in advance whether a number has WhatsApp. " +
                            "If it says the number is not on WhatsApp, come back and choose \"Different number\".",
                    fontSize = 11.sp,
                    color = Color.Gray,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSend(target, useOther && saveOnBill) },
                enabled = targetValid,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
            ) { Text("Send", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NumberChoiceCard(
    selected: Boolean,
    enabled: Boolean,
    title: String,
    subtitle: String,
    subtitleColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) Color.White else Color(0xFFF5F5F5),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) AppGreen else Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (enabled) Color(0xFF0F172A) else Color.Gray)
                Text(subtitle, fontSize = 12.sp, color = subtitleColor)
            }
        }
    }
}