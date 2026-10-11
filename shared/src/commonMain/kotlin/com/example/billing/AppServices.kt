package com.example.billing

import com.example.billing.auth.AppSettings
import com.example.billing.auth.AuthRepository
import com.example.billing.auth.DeviceIdentity
import com.example.billing.auth.FirebaseApi
import com.example.billing.auth.LicenseManager
import com.example.billing.data.BillRepository
import com.example.billing.data.DriverFactory

/** App ke saare "kaam karne wale" objects ek jagah (ek hi database sab share karte hain). */
class AppServices(
    val bills: BillRepository,
    val auth: AuthRepository,
    val settings: AppSettings,
    val license: LicenseManager,
    val firebase: FirebaseApi,
)

fun createAppServices(driverFactory: DriverFactory, device: DeviceIdentity): AppServices {
    val database = BillingDatabase(driverFactory.createDriver())
    val settings = AppSettings(database)
    val firebase = FirebaseApi()
    return AppServices(
        bills = BillRepository(database),
        auth = AuthRepository(database, settings),
        settings = settings,
        license = LicenseManager(settings, firebase, device),
        firebase = firebase,
    )
}
