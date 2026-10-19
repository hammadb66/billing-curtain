package com.example.billing

import android.app.Application
import android.os.Build
import android.provider.Settings
import com.example.billing.auth.DeviceIdentity
import com.example.billing.data.DriverFactory
import com.example.billing.di.initKoin

class BillingApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val label = "${Build.MANUFACTURER} ${Build.MODEL} (Android)"
        val device = DeviceIdentity(androidId, label)
        val driverFactory = DriverFactory(this) // Android me Context chahiye

        // Koin ko yahan start karein
        initKoin(driverFactory, device)
    }
}