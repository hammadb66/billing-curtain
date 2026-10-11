package com.example.billing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.billing.data.DriverFactory
import com.example.billing.pdf.AndroidPdfPrinter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val services = (application as BillingApplication).services

        val printer = AndroidPdfPrinter(this)

        setContent {
            App( services,  printer)
        }
    }
}