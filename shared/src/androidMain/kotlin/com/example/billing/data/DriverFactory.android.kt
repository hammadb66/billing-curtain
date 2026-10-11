package com.example.billing.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.example.billing.BillingDatabase

actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        val driver = AndroidSqliteDriver(BillingDatabase.Schema, context, "billing.db")
        BillingDatabase.Schema.create(driver)
        return driver
    }
}
