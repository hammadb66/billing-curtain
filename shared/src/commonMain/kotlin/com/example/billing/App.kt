package com.example.billing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.billing.auth.AppConfig
import com.example.billing.auth.AppUser
import com.example.billing.auth.DevSession
import com.example.billing.auth.LicenseState
import com.example.billing.model.Bill
import com.example.billing.pdf.PdfPrinter
import com.example.billing.presentation.BillingRoute
import com.example.billing.sync.SyncStatus
import com.example.billing.ui.ActivationScreen
import com.example.billing.ui.AdminScreen
import com.example.billing.ui.AppTheme
import com.example.billing.ui.BillingScreen
import com.example.billing.ui.BlockedScreen
import com.example.billing.ui.CheckingScreen
import com.example.billing.ui.EditBillScreen
import com.example.billing.ui.FileImporter
import com.example.billing.ui.HistoryScreen
import com.example.billing.ui.LoginScreen
import com.example.billing.ui.ManageItemsScreen
import com.example.billing.ui.ProductListScreen
import com.example.billing.ui.PurchaseEditScreen
import com.example.billing.ui.PurchaseListScreen
import com.example.billing.ui.ReportScreen
import com.example.billing.ui.VendorDetailScreen
import com.example.billing.ui.VendorListScreen
import com.example.billing.utils.Branding
import com.example.billing.utils.FileExporter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlinx.coroutines.runBlocking

private sealed interface Gate {
    data object Checking : Gate
    data object NeedActivation : Gate
    data class Blocked(val message: String) : Gate
    data object Login : Gate
    data class LoggedIn(val user: AppUser, val dev: DevSession? = null) : Gate
    data class Admin(val session: DevSession) : Gate
}

@Composable
fun App(
    printer: PdfPrinter,
    presetShopCode: String? = null,
    exporter: FileExporter? = null,
    importer: FileImporter? = null
) {
    val services = koinInject<AppServices>()
    val navController = rememberNavController()

    AppTheme {
        val scope = rememberCoroutineScope()
        var gate by remember { mutableStateOf<Gate>(Gate.Checking) }
        var offline by remember { mutableStateOf(false) }
        var licensed by remember { mutableStateOf(false) }

        suspend fun runLicenseCheck() {
            gate = Gate.Checking
            if (presetShopCode != null && services.license.currentShopId() == null) {
                services.license.activate(presetShopCode)
            }
            when (val s = services.license.evaluate()) {
                LicenseState.NeedActivation -> {
                    licensed = false; gate = Gate.NeedActivation
                }

                is LicenseState.Blocked -> {
                    licensed = false; gate = Gate.Blocked(s.message)
                }

                is LicenseState.Allowed -> {
                    offline = s.offline
                    licensed = true
                    gate = Gate.Login
                }
            }
            Branding.licensedTo = services.license.currentShopName()
        }

        LaunchedEffect(Unit) { runLicenseCheck() }

        if (licensed) {
            LaunchedEffect(Unit) { services.sync.runLoop() }
        }

        if (gate is Gate.LoggedIn) {
            LaunchedEffect(Unit) {
                while (true) {
                    val blocked = services.license.recheck()
                    if (blocked != null) {
                        licensed = false
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
                            // screen = ... HATA DIYA
                            gate = Gate.LoggedIn(user)
                        },
                        onDeveloperLoggedIn = { session -> gate = Gate.Admin(session) },
                    )

                    is Gate.Admin -> AdminScreen(
                        services = services,
                        session = g.session,
                        onOpenBilling = {
                            // screen = ... HATA DIYA
                            gate = Gate.LoggedIn(AppUser(0L, "developer", true), dev = g.session)
                        },
                        onLogout = { scope.launch { runLicenseCheck() } },
                    )

                    is Gate.LoggedIn -> {
                        NavHost(navController = navController, startDestination = BillingRoute.Billing) {
                            composable<BillingRoute.Vendors> {
                                VendorListScreen(
                                    repository = services.vendors,
                                    onBack = { navController.popBackStack() },
                                    onOpenVendor = { id -> navController.navigate(BillingRoute.VendorDetail(id)) },
                                    onAddVendor = { /* handled in screen via dialog */ },
                                )
                            }
                            composable<BillingRoute.VendorDetail> { entry ->
                                val route = entry.toRoute<BillingRoute.VendorDetail>()
                                VendorDetailScreen(
                                    repository = services.vendors,
                                    vendorId = route.vendorId,
                                    onBack = { navController.popBackStack() },
                                    printer = printer
                                )
                            }
                            composable<BillingRoute.Products> {
                                ProductListScreen(
                                    repository = services.vendors,
                                    onBack = { navController.popBackStack() },
                                )
                            }
                            composable<BillingRoute.Purchases> {
                                PurchaseListScreen(
                                    repository = services.vendors,
                                    onBack = { navController.popBackStack() },
                                    onOpen = { id -> navController.navigate(BillingRoute.PurchaseEdit(id)) },
                                )
                            }
                            composable<BillingRoute.PurchaseEdit> { entry ->
                                val route = entry.toRoute<BillingRoute.PurchaseEdit>()
                                PurchaseEditScreen(
                                    repository = services.vendors,
                                    purchaseId = route.purchaseId,
                                    onClose = { navController.popBackStack() },
                                    printer = printer,
                                )
                            }
                            composable<BillingRoute.Billing> {
                                BillingScreen(
                                    printer = printer,
                                    onOpenHistory = { navController.navigate(BillingRoute.History) },
                                    onLogout = {
                                        val dev = g.dev
                                        gate = if (dev != null) Gate.Admin(dev) else Gate.Login
                                    },
                                    onOpenManageItems = { navController.navigate(BillingRoute.Products) },   // ← Ab Products khulega
                                    onOpenItemReport = { navController.navigate(BillingRoute.ItemReport) },
                                    onOpenVendors = { navController.navigate(BillingRoute.Vendors) },       // ← NAYA
                                    onOpenPurchases = { navController.navigate(BillingRoute.Purchases) },   // ← NAYA                                    onOpenItemReport = { navController.navigate(BillingRoute.ItemReport) },
                                    userName = g.user.username,
                                    isDeveloper = g.dev != null,
                                    exporter = exporter,   // <-- NAYA
                                    importer = importer    // <-- NAYA
                                )
                            }

                            composable<BillingRoute.History> {
                                HistoryScreen(
                                    repository = services.bills,
                                    printer = printer,
                                    onBack = { navController.popBackStack() },
                                    onEdit = { bill -> navController.navigate(BillingRoute.EditBill(bill.id)) },
                                    exporter = exporter,
                                    importer = importer,
                                    isDeveloper = g.dev != null,
                                    syncVersion = services.sync.dataVersion.collectAsState().value,
                                    cloudExport = { if (services.sync.isOnlineMode()) services.sync.exportCloudJson() else null },
                                    onlineMode = services.sync.status.collectAsState().value != SyncStatus.Disabled,
                                    onRefresh = { if (services.sync.isOnlineMode()) services.sync.syncNow() else null }
                                )
                            }

                            composable<BillingRoute.ManageItems> {
                                ManageItemsScreen(
                                    repository = services.bills,
                                    getItems = { services.settings.getMasterItems() },
                                    saveItems = { services.settings.saveMasterItems(it) },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<BillingRoute.ItemReport> {
                                ReportScreen(
                                    billRepo = services.bills,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<BillingRoute.EditBill> { backStackEntry ->
                                val route = backStackEntry.toRoute<BillingRoute.EditBill>()
                                var bill by remember { mutableStateOf<Bill?>(null) }
                                var isLoading by remember { mutableStateOf(true) }

                                LaunchedEffect(route.billId) {
                                    bill = services.bills.getBill(route.billId)
                                    isLoading = false
                                }

                                if (isLoading) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                } else bill?.let {
                                    EditBillScreen(
                                        bill = it,
                                        repository = services.bills,
                                        onClose = { navController.popBackStack() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}