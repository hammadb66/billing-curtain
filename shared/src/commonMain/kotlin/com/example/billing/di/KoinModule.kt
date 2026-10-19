package com.example.billing.di

import com.example.billing.AppServices
import com.example.billing.createAppServices
import com.example.billing.data.DriverFactory
import com.example.billing.presentation.BillingViewModel
import com.example.billing.auth.DeviceIdentity
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModel
val appModule = module {
    single { createAppServices(get(), get()) }
    single { get<AppServices>().bills }
    single { get<AppServices>().settings }
    viewModel  { BillingViewModel(get(), get()) }
}

// Yahan DriverFactory aur DeviceIdentity pass karwa rahe hain
fun initKoin(driverFactory: DriverFactory, device: DeviceIdentity) {
    org.koin.core.context.startKoin {
        modules(
            appModule,
            module {
                single { driverFactory }
                single { device }
            }
        )
    }
}