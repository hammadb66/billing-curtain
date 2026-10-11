package com.example.billing

import android.app.Application
import android.os.Build
import android.provider.Settings
import com.example.billing.auth.DeviceIdentity
import com.example.billing.data.DriverFactory

class BillingApplication : Application() {

    val services: AppServices by lazy {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val label = "${Build.MANUFACTURER} ${Build.MODEL} (Android)"
        createAppServices(DriverFactory(this), DeviceIdentity(androidId, label))
    }
}