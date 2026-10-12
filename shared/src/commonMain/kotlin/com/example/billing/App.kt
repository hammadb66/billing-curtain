package com.example.billing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.billing.auth.AppConfig
import com.example.billing.auth.AppUser
import com.example.billing.auth.DevSession
import com.example.billing.auth.LicenseState
import com.example.billing.model.Bill
import com.example.billing.model.BillingFormState
import com.example.billing.pdf.PdfPrinter
import com.example.billing.ui.ActivationScreen
import com.example.billing.ui.AdminScreen
import com.example.billing.ui.AppTheme
import com.example.billing.ui.BillingScreen
import com.example.billing.ui.BlockedScreen
import com.example.billing.ui.CheckingScreen
import com.example.billing.ui.EditBillScreen
import com.example.billing.ui.HistoryScreen
import com.example.billing.ui.LoginScreen
import com.example.billing.utils.Branding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Flutter ka Navigator.push ka sada version: "abhi kaun si screen dikhani hai?" */
sealed interface Screen {
    data object Billing : Screen
    data object History : Screen
    data class Edit(val bill: Bill) : Screen
}

/** Software ke "darwaze": pehle license, phir login, phir asli app. */
private sealed interface Gate {
    data object Checking : Gate
    data object NeedActivation : Gate
    data class Blocked(val message: String) : Gate
    data object Login : Gate

    /** dev != null matlab developer ne Admin Panel se billing kholi hai */
    data class LoggedIn(val user: AppUser, val dev: DevSession? = null) : Gate
    data class Admin(val session: DevSession) : Gate
}

/**
 * presetShopCode: agar laptop par "shop.code" file rakhi ho to uska code (customer ko type nahi karna padta).
 */
@Composable
fun App(services: AppServices, printer: PdfPrinter, presetShopCode: String? = null) {
    AppTheme {
        val scope = rememberCoroutineScope()
        val form = remember { BillingFormState() }
        var screen by remember { mutableStateOf<Screen>(Screen.Billing) }
        var gate by remember { mutableStateOf<Gate>(Gate.Checking) }
        var offline by remember { mutableStateOf(false) }

        suspend fun runLicenseCheck() {
            gate = Gate.Checking
            // Pehli dafa aur shop.code file maujood ho to chup chap activate (error aaye to Activation screen)
            if (presetShopCode != null && services.license.currentShopId() == null) {
                services.license.activate(presetShopCode)
            }
            when (val s = services.license.evaluate()) {
                LicenseState.NeedActivation -> gate = Gate.NeedActivation
                is LicenseState.Blocked -> gate = Gate.Blocked(s.message)
                is LicenseState.Allowed -> {
                    offline = s.offline
                    gate = Gate.Login
                }
            }
            // "Licensed to: ..." ke liye dukaan ka naam
            Branding.licensedTo = services.license.currentShopName()
        }

        // 1) Software khulte hi license check
        LaunchedEffect(Unit) { runLicenseCheck() }

        // 2) Software chalte hue bhi har kuch minute baad check (Firebase se OFF kiya to band ho jaye)
        if (gate is Gate.LoggedIn) {
            LaunchedEffect(Unit) {
                while (true) {
                    val blocked = services.license.recheck()
                    if (blocked != null) {
                        gate = Gate.Blocked(blocked.message)
                        break
                    }
                    delay(AppConfig.RECHECK_MINUTES * 60_000L)
                }
            }
        }

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                when (val g = gate) {
                    Gate.Checking -> CheckingScreen()

                    Gate.NeedActivation -> ActivationScreen(
                        license = services.license,
                        firebase = services.firebase,
                        onActivated = { scope.launch { runLicenseCheck() } },
                        onDeveloperLoggedIn = { session -> gate = Gate.Admin(session) },
                    )

                    is Gate.Blocked -> BlockedScreen(
                        message = g.message,
                        firebase = services.firebase,
                        onRetry = { scope.launch { runLicenseCheck() } },
                        onDeveloperLoggedIn = { session -> gate = Gate.Admin(session) },
                    )

                    Gate.Login -> LoginScreen(
                        services = services,
                        offline = offline,
                        onUserLoggedIn = { user ->
                            screen = Screen.Billing
                            gate = Gate.LoggedIn(user)
                        },
                        onDeveloperLoggedIn = { session -> gate = Gate.Admin(session) },
                    )

                    is Gate.Admin -> AdminScreen(
                        services = services,
                        session = g.session,
                        onOpenBilling = {
                            screen = Screen.Billing
                            gate = Gate.LoggedIn(AppUser(0L, "developer", true), dev = g.session)
                        },
                        // Admin se nikalte hi license dobara check hota hai (band license par login nahi khulta)
                        onLogout = { scope.launch { runLicenseCheck() } },
                    )

                    is Gate.LoggedIn -> when (val current = screen) {
                        Screen.Billing -> BillingScreen(
                            form = form,
                            repository = services.bills,
                            printer = printer,
                            onOpenHistory = { screen = Screen.History },
                            onLogout = {
                                val dev = g.dev
                                form.reset()
                                screen = Screen.Billing
                                // Developer ho to Admin Panel par wapas, warna login par
                                gate = if (dev != null) Gate.Admin(dev) else Gate.Login
                            },
                            userName = g.user.username,
                            isDeveloper = g.dev != null,
                        )
                        Screen.History -> HistoryScreen(
                            repository = services.bills,
                            printer = printer,
                            onBack = { screen = Screen.Billing },
                            onEdit = { screen = Screen.Edit(it) },
                        )
                        is Screen.Edit -> EditBillScreen(
                            bill = current.bill,
                            repository = services.bills,
                            onClose = { screen = Screen.History },
                        )
                    }
                }
            }
        }
    }
}
