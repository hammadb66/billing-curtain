package com.example.billing.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

@Composable
fun BrandFooter(modifier: Modifier = Modifier, centered: Boolean = false) {
    val align = if (centered) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (centered) TextAlign.Center else TextAlign.Start

    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = align,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        HorizontalDivider(color = Color(0xFFE0E0E0))
        Spacer(Modifier.height(4.dp))
        if (Branding.licensedTo.isNotBlank()) {
            Text(
                "Licensed to: ${Branding.licensedTo}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
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
