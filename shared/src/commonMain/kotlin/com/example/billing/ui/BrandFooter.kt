package com.example.billing.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billing.utils.Branding

/**
 * "Licensed to / Developed by / Contact" wali chhoti patti.
 * Contact number par mouse se select kar ke copy kiya ja sakta hai.
 */
@Composable
fun BrandFooter(modifier: Modifier = Modifier, centered: Boolean = false) {
    val align = if (centered) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (centered) TextAlign.Center else TextAlign.Start

    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = align,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        HorizontalDivider()
        if (Branding.licensedTo.isNotBlank()) {
            Text(
                "Licensed to: ${Branding.licensedTo}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppGreen,
                textAlign = textAlign,
            )
        }
        Text(
            "Developed by ${Branding.COMPANY}",
            fontSize = 11.sp,
            color = Color.Gray,
            textAlign = textAlign,
        )
        SelectionContainer {
            Text(
                "Contact: ${Branding.PHONE}",
                fontSize = 11.sp,
                color = Color.Gray,
                textAlign = textAlign,
            )
        }
    }
}
