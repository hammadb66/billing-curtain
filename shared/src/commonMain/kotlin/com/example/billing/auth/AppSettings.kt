package com.example.billing.auth

import com.example.billing.BillingDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Chhoti chhoti cheezen yaad rakhne ke liye (shop code, license ka aakhri status...). */
class AppSettings(database: BillingDatabase) {
    private val q = database.authQueries

    suspend fun get(key: String): String? = withContext(Dispatchers.Default) {
        q.getSetting(key).executeAsOneOrNull()
    }

    suspend fun put(key: String, value: String) {
        withContext(Dispatchers.Default) { q.putSetting(key, value) }
    }
}

object SettingKeys {
    const val SHOP_ID = "shopId"
    const val LIC_OK = "licenseOk"
    const val LIC_CHECKED_AT = "licenseCheckedAt"
    const val LIC_MESSAGE = "licenseMessage"
    const val LIC_SIG = "licenseSig"
}
