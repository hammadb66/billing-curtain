package com.example.billing

import com.example.billing.auth.AppSettings
import com.example.billing.auth.AuthRepository
import com.example.billing.auth.DeviceIdentity
import com.example.billing.auth.FirebaseApi
import com.example.billing.auth.LicenseManager
import com.example.billing.data.BillRepository
import com.example.billing.data.DriverFactory
import com.example.billing.data.VendorRepository
import com.example.billing.sync.BillCloudApi
import com.example.billing.sync.CloudAuth
import com.example.billing.sync.SyncManager

class AppServices(
    val bills: BillRepository,
    val auth: AuthRepository,
    val settings: AppSettings,
    val license: LicenseManager,
    val firebase: FirebaseApi,
    val sync: SyncManager,
    val vendors: VendorRepository,   // NEW

)

fun createAppServices(driverFactory: DriverFactory, device: DeviceIdentity): AppServices {
    val database = BillingDatabase(driverFactory.createDriver())
    val settings = AppSettings(database)
    val firebase = FirebaseApi()
    val bills = BillRepository(database)
    val license = LicenseManager(settings, firebase, device)
    val vendors = VendorRepository(database, bills) // <--- YAHAN BILLS PASS KAREIN
    return AppServices(
        bills = bills,
        auth = AuthRepository(database, settings),
        settings = settings,
        license = license,
        firebase = firebase,
        sync = SyncManager(settings, bills, BillCloudApi(CloudAuth(license.deviceId), license.deviceId), license.deviceId),
        vendors = vendors,
    )
}
