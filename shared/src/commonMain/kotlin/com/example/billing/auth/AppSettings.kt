package com.example.billing.auth

import com.example.billing.BillingDatabase
import com.example.billing.model.MasterItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class AppSettings(database: BillingDatabase) {
    private val q = database.authQueries
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun get(key: String): String? = withContext(Dispatchers.Default) {
        q.getSetting(key).executeAsOneOrNull()
    }

    suspend fun put(key: String, value: String) {
        withContext(Dispatchers.Default) { q.putSetting(key, value) }
    }

    // ============== MASTER ITEMS FUNCTIONS ==============
    suspend fun getMasterItems(): List<MasterItem> {
        val str = get("master_items_json")
        if (str.isNullOrBlank()) {
            return listOf(
                MasterItem("Fabric curtain", "Meter"),
                MasterItem("Lining", "Meter"),
                MasterItem("Fitting", "pcs")
            )
        }
        return runCatching {
            json.decodeFromString(ListSerializer(MasterItem.serializer()), str)
        }.getOrDefault(emptyList())
    }

    suspend fun saveMasterItems(items: List<MasterItem>) {
        put("master_items_json", json.encodeToString(ListSerializer(MasterItem.serializer()), items))
    }
}

object SettingKeys {
    const val SHOP_ID = "shopId"
    const val SHOP_NAME = "shopName"
    const val LIC_OK = "licenseOk"
    const val LIC_CHECKED_AT = "licenseCheckedAt"
    const val LIC_MESSAGE = "licenseMessage"
    const val LIC_SIG = "licenseSig"
    const val SYNC_MODE = "syncMode"
    const val SYNC_SHOP = "syncShop"
    const val SYNC_SKEW = "syncSkew"
    fun syncCursor(shopId: String) = "syncCursor_$shopId"
}