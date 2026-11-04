package com.example.billing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billing.AppServices
import com.example.billing.auth.*
import com.example.billing.sync.BillCloudApi
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    services: AppServices, session: DevSession,
    onOpenBilling: () -> Unit, onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var users by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var shops by remember { mutableStateOf<Map<String, LicenseInfo>>(emptyMap()) }
    var allDevices by remember { mutableStateOf<Map<String, Map<String, DeviceRecord>>>(emptyMap()) }
    var shopsError by remember { mutableStateOf<String?>(null) }
    var thisShop by remember { mutableStateOf<String?>(null) }
    var shopMetas by remember { mutableStateOf<Map<String, BillCloudApi.ShopMeta>>(emptyMap()) }

    // Loading States
    var syncingShopId by remember { mutableStateOf<String?>(null) }
    var updatingRoleDeviceId by remember { mutableStateOf<String?>(null) }

    var showAddUser by remember { mutableStateOf(false) }
    var showAddShop by remember { mutableStateOf(false) }
    var showChangeShop by remember { mutableStateOf(false) }
    var passwordFor by remember { mutableStateOf<AppUser?>(null) }
    var deleteFor by remember { mutableStateOf<AppUser?>(null) }
    var editFor by remember { mutableStateOf<String?>(null) }
    var deleteShopFor by remember { mutableStateOf<String?>(null) }
    var deleteShopBills by remember { mutableStateOf(false) }

    // Naye Variables: Alerts ke liye
    var deviceToMakeMaster by remember { mutableStateOf<Pair<String, DeviceRecord>?>(null) }
    var deviceToDelete by remember { mutableStateOf<Pair<String, DeviceRecord>?>(null) }

    suspend fun loadUsers() { users = services.auth.listUsers() }
    suspend fun loadThisShop() { thisShop = services.settings.get(SettingKeys.SHOP_ID) }
    suspend fun loadShops() {
        when (val r = services.firebase.listLicenses(session.idToken)) {
            is ShopsResult.Ok -> { shops = r.shops; shopsError = null }
            is ShopsResult.Failed -> shopsError = r.message
        }
        services.firebase.listAllDevices(session.idToken)?.let { allDevices = it }
        val metas = mutableMapOf<String, BillCloudApi.ShopMeta>()
        for (shopId in shops.keys) {
            metas[shopId] = services.sync.getShopMeta(shopId)
        }
        shopMetas = metas
    }

    LaunchedEffect(Unit) { loadUsers(); loadThisShop(); loadShops() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = AppTheme.Bg,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // ===== HEADER =====
            Surface(color = AppTheme.Surface, shadowElevation = 2.dp) {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    val isCompact = maxWidth < 600.dp

                    if (isCompact) {
                        // MOBILE VIEW (Stack vertically)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconCircle(Icons.Default.AdminPanelSettings, AppGreen, size = 36, iconSize = 18)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Admin Panel", fontSize = 18.sp, fontWeight = FontWeight.Black, color = AppTheme.TextPrimary)
                                    Text(session.email, fontSize = 11.sp, color = AppTheme.TextSecondary)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = onOpenBilling,
                                    shape = RoundedCornerShape(AppTheme.RadiusSm),
                                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.TextPrimary),
                                    modifier = Modifier.weight(1f).height(38.dp)
                                ) { Text("Billing", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }

                                OutlinedButton(
                                    onClick = onLogout,
                                    shape = RoundedCornerShape(AppTheme.RadiusSm),
                                    border = BorderStroke(1.dp, AppTheme.Danger),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTheme.Danger),
                                    modifier = Modifier.weight(1f).height(38.dp)
                                ) { Text("Logout", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                            }
                        }
                    } else {
                        // DESKTOP VIEW (Horizontal Row)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconCircle(Icons.Default.AdminPanelSettings, AppGreen, size = 44, iconSize = 22)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Admin Panel", fontSize = 20.sp, fontWeight = FontWeight.Black, color = AppTheme.TextPrimary)
                                Text(session.email, fontSize = 12.sp, color = AppTheme.TextSecondary)
                            }
                            Button(
                                onClick = onOpenBilling,
                                shape = RoundedCornerShape(AppTheme.RadiusSm),
                                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.TextPrimary),
                                modifier = Modifier.height(42.dp)
                            ) { Text("Open Billing", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                            Spacer(Modifier.width(10.dp))
                            OutlinedButton(
                                onClick = onLogout,
                                shape = RoundedCornerShape(AppTheme.RadiusSm),
                                border = BorderStroke(1.dp, AppTheme.Danger),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTheme.Danger),
                                modifier = Modifier.height(42.dp)
                            ) { Text("Logout", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
            // ===== CONTENT =====
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 1000.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    PremiumPanel("This laptop's shop code", Icons.Default.Computer, AppTheme.Info) {
                        Surface(color = AppTheme.InfoLight, shape = RoundedCornerShape(AppTheme.RadiusSm)) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VpnKey, null, tint = AppTheme.Info, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(thisShop ?: "Not set yet.", fontWeight = FontWeight.Black, fontSize = 16.sp, color = AppTheme.TextPrimary)
                                    Text("The same code needs to be applied to all the laptops in a shop.", fontSize = 12.sp, color = AppTheme.TextSecondary)
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = { showChangeShop = true },
                            shape = RoundedCornerShape(AppTheme.RadiusSm),
                            border = BorderStroke(1.dp, AppTheme.Border),
                            modifier = Modifier.height(42.dp)
                        ) { Icon(Icons.Default.Edit, null, tint = AppTheme.TextSecondary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Change Shop Code", color = AppTheme.TextPrimary, fontWeight = FontWeight.SemiBold) }
                    }

                    // ===== SHOPS & DEVICES PANEL =====
                    PremiumPanel("Shops & Devices (License & Sync)", Icons.Default.Storefront, AppGreen) {
                        shopsError?.let {
                            Surface(color = AppTheme.DangerLight, shape = RoundedCornerShape(AppTheme.RadiusSm)) {
                                Text(it, color = AppTheme.Danger, fontSize = 13.sp, modifier = Modifier.padding(10.dp))
                            }
                        }
                        if (shops.isEmpty() && shopsError == null) {
                            Text("No shop yet. Please add a shop below.", color = AppTheme.TextMuted, fontSize = 13.sp)
                        }

                        shops.entries.sortedBy { it.key }.forEach { (id, info) ->
                            val used = allDevices[id]?.values?.count { !it.blocked } ?: 0
                            val meta = shopMetas[id] ?: BillCloudApi.ShopMeta()
                            val devices = allDevices[id]?.values?.toList() ?: emptyList()

                            Card(
                                shape = RoundedCornerShape(AppTheme.RadiusMd),
                                border = BorderStroke(1.dp, AppTheme.Border),
                                colors = CardDefaults.cardColors(containerColor = AppTheme.SurfaceAlt),
                                elevation = CardDefaults.cardElevation(0.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                                    // ---- Row 1: Shop Identity + Switches ----
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconCircle(
                                            Icons.Default.Storefront,
                                            if (info.active) AppGreen else AppTheme.TextMuted,
                                            size = 36, iconSize = 18
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(id, fontWeight = FontWeight.Black, fontSize = 15.sp, color = AppTheme.TextPrimary)
                                            val label = info.shopName.ifBlank { "-" } + if (id == thisShop) "   (this laptop)" else ""
                                            Text(label, fontSize = 12.sp, color = AppTheme.TextSecondary)
                                            Text(
                                                "Laptops: $used / ${info.maxDevices}",
                                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                                color = if (used > info.maxDevices) AppTheme.Danger else AppGreen
                                            )
                                        }

                                        // License Switch
                                        Surface(color = if (info.active) AppTheme.SuccessLight else AppTheme.DangerLight, shape = RoundedCornerShape(6.dp)) {
                                            Text("LIC", color = if (info.active) AppTheme.Success else AppTheme.Danger, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                        Spacer(Modifier.width(4.dp))
                                        Switch(
                                            checked = info.active,
                                            onCheckedChange = { on ->
                                                scope.launch {
                                                    val problem = services.firebase.putLicense(session.idToken, id, info.copy(active = on))
                                                    if (problem == null) loadShops() else snackbar.showSnackbar(problem)
                                                }
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppGreen)
                                        )

                                        // Sync Switch — only when license is ON
                                        if (info.active) {
                                            Spacer(Modifier.width(12.dp))
                                            Surface(color = if (meta.syncEnabled) AppTheme.InfoLight else AppTheme.SurfaceAlt, shape = RoundedCornerShape(6.dp)) {
                                                Text("SYNC", color = if (meta.syncEnabled) AppTheme.Info else AppTheme.TextMuted, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                            }
                                            Spacer(Modifier.width(4.dp))

                                            if (syncingShopId == id) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp).padding(4.dp),
                                                    strokeWidth = 2.dp,
                                                    color = AppTheme.Info
                                                )
                                            } else {
                                                Switch(
                                                    checked = meta.syncEnabled,
                                                    onCheckedChange = { on ->
                                                        scope.launch {
                                                            syncingShopId = id
                                                            try {
                                                                val url = "${AppConfig.DB_URL}/shops/$id/meta/syncEnabled.json?auth=${session.idToken}"
                                                                val res = httpCall("PUT", url, on.toString(), 15_000)
                                                                if (res.code == 200) {
                                                                    snackbar.showSnackbar("Sync ${if (on) "enabled" else "disabled"} successfully.")
                                                                    loadShops()
                                                                } else {
                                                                    snackbar.showSnackbar("Sync toggle failed: HTTP ${res.code}")
                                                                }
                                                            } catch (e: Exception) {
                                                                snackbar.showSnackbar("Error: ${e.message}")
                                                            } finally {
                                                                syncingShopId = null
                                                            }
                                                        }
                                                    },
                                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppTheme.Info)
                                                )
                                            }
                                        }
                                    }

                                    // ---- Row 2: Devices & Roles (Only if Sync is ON) ----
                                    if (info.active) {
                                        HorizontalDivider(color = AppTheme.Border)
                                        Text("Devices & Roles", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppTheme.TextSecondary)

                                        if (devices.isEmpty()) {
                                            Text("No laptops registered yet.", color = AppTheme.TextMuted, fontSize = 12.sp)
                                        } else {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                devices.forEach { d ->
                                                    val isMaster = d.id == meta.masterDeviceId
                                                    val isUpdatingRole = updatingRoleDeviceId == d.id

                                                    Surface(
                                                        color = if (isMaster) AppGreen.copy(alpha = 0.08f) else AppTheme.Surface,
                                                        shape = RoundedCornerShape(AppTheme.RadiusSm),
                                                        border = BorderStroke(1.dp, if (isMaster) AppGreen else AppTheme.Border),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        // Responsive UI Fix using BoxWithConstraints
                                                        BoxWithConstraints {
                                                            val isSmallScreen = maxWidth < 450.dp

                                                            if (isSmallScreen) {
                                                                // MOBILE VIEW (Stack vertically)
                                                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                                        Icon(Icons.Default.Computer, null, tint = if (d.blocked) AppTheme.Danger else if (isMaster) AppGreen else AppTheme.TextSecondary, modifier = Modifier.size(18.dp))
                                                                        Spacer(Modifier.width(8.dp))
                                                                        Column(Modifier.weight(1f)) {
                                                                            Text(d.label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AppTheme.TextPrimary)
                                                                            Text("ID: ${d.id.take(8)}", fontSize = 10.sp, color = AppTheme.TextMuted)
                                                                        }
                                                                        Surface(color = if (isMaster) AppGreen else AppTheme.InfoLight, shape = RoundedCornerShape(4.dp)) {
                                                                            Text(
                                                                                if (isMaster) "MASTER" else "VIEWER",
                                                                                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                                                                color = if (isMaster) Color.White else AppTheme.Info,
                                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                            )
                                                                        }
                                                                    }

                                                                    // Mobile: Action buttons below
                                                                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                                        if (!isMaster && !d.blocked) {
                                                                            if (isUpdatingRole) {
                                                                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AppTheme.Info)
                                                                            } else {
                                                                                TextButton(
                                                                                    onClick = { deviceToMakeMaster = id to d },
                                                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                                                    modifier = Modifier.height(30.dp)
                                                                                ) {
                                                                                    Text("Make Master", fontSize = 11.sp, color = AppTheme.Info, fontWeight = FontWeight.Bold)
                                                                                }
                                                                            }
                                                                            Spacer(Modifier.width(8.dp))
                                                                        }
                                                                        IconButton(
                                                                            onClick = {
                                                                                scope.launch {
                                                                                    updatingRoleDeviceId = d.id
                                                                                    val err = services.firebase.setDeviceBlocked(session.idToken, id, d.id, !d.blocked)
                                                                                    if (err == null) {
                                                                                        snackbar.showSnackbar(if (d.blocked) "Device unblocked." else "Device blocked.")
                                                                                        loadShops()
                                                                                    } else snackbar.showSnackbar(err)
                                                                                    updatingRoleDeviceId = null
                                                                                }
                                                                            },
                                                                            modifier = Modifier.size(30.dp)
                                                                        ) {
                                                                            Icon(if (d.blocked) Icons.Default.LockOpen else Icons.Default.Lock, "Block/Unblock", tint = if (d.blocked) AppGreen else AppTheme.Danger, modifier = Modifier.size(16.dp))
                                                                        }
                                                                        IconButton(
                                                                            onClick = { deviceToDelete = id to d },
                                                                            modifier = Modifier.size(30.dp)
                                                                        ) {
                                                                            Icon(Icons.Default.Delete, "Delete", tint = AppTheme.Danger, modifier = Modifier.size(16.dp))
                                                                        }
                                                                    }
                                                                }
                                                            } else {
                                                                // DESKTOP VIEW (Single row)
                                                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                    Icon(Icons.Default.Computer, null, tint = if (d.blocked) AppTheme.Danger else if (isMaster) AppGreen else AppTheme.TextSecondary, modifier = Modifier.size(18.dp))
                                                                    Spacer(Modifier.width(8.dp))
                                                                    Column(Modifier.weight(1f)) {
                                                                        Text(d.label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AppTheme.TextPrimary)
                                                                        Text("ID: ${d.id.take(8)}", fontSize = 10.sp, color = AppTheme.TextMuted)
                                                                    }

                                                                    // Role Badge
                                                                    Surface(color = if (isMaster) AppGreen else AppTheme.InfoLight, shape = RoundedCornerShape(4.dp)) {
                                                                        Text(
                                                                            if (isMaster) "MASTER" else "VIEWER",
                                                                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                                                            color = if (isMaster) Color.White else AppTheme.Info,
                                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                        )
                                                                    }

                                                                    if (!isMaster && !d.blocked) {
                                                                        Spacer(Modifier.width(8.dp))
                                                                        if (isUpdatingRole) {
                                                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AppTheme.Info)
                                                                        } else {
                                                                            TextButton(
                                                                                onClick = { deviceToMakeMaster = id to d },
                                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                                                modifier = Modifier.height(30.dp)
                                                                            ) {
                                                                                Text("Make Master", fontSize = 11.sp, color = AppTheme.Info, fontWeight = FontWeight.Bold)
                                                                            }
                                                                        }
                                                                    }

                                                                    Spacer(Modifier.width(4.dp))
                                                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                        IconButton(
                                                                            onClick = {
                                                                                scope.launch {
                                                                                    updatingRoleDeviceId = d.id
                                                                                    val err = services.firebase.setDeviceBlocked(session.idToken, id, d.id, !d.blocked)
                                                                                    if (err == null) {
                                                                                        snackbar.showSnackbar(if (d.blocked) "Device unblocked." else "Device blocked.")
                                                                                        loadShops()
                                                                                    } else snackbar.showSnackbar(err)
                                                                                    updatingRoleDeviceId = null
                                                                                }
                                                                            },
                                                                            modifier = Modifier.size(30.dp)
                                                                        ) {
                                                                            Icon(if (d.blocked) Icons.Default.LockOpen else Icons.Default.Lock, "Block/Unblock", tint = if (d.blocked) AppGreen else AppTheme.Danger, modifier = Modifier.size(16.dp))
                                                                        }
                                                                        IconButton(
                                                                            onClick = { deviceToDelete = id to d },
                                                                            modifier = Modifier.size(30.dp)
                                                                        ) {
                                                                            Icon(Icons.Default.Delete, "Delete", tint = AppTheme.Danger, modifier = Modifier.size(16.dp))
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // ---- Row 3: Actions ----
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        TextButton(onClick = { editFor = id }, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp)); Text("Name / Message", fontSize = 11.sp)
                                        }
                                        TextButton(onClick = { deleteShopFor = id }, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Default.Delete, null, tint = AppTheme.Danger, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp)); Text("Delete", color = AppTheme.Danger, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { showAddShop = true },
                                shape = RoundedCornerShape(AppTheme.RadiusSm),
                                colors = ButtonDefaults.buttonColors(containerColor = AppGreen),
                                modifier = Modifier.height(42.dp)
                            ) { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("New Shop", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                            OutlinedButton(
                                onClick = { scope.launch { loadShops() } },
                                shape = RoundedCornerShape(AppTheme.RadiusSm),
                                border = BorderStroke(1.dp, AppTheme.Border),
                                modifier = Modifier.height(42.dp)
                            ) { Icon(Icons.Default.Refresh, null, tint = AppTheme.TextSecondary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Refresh", color = AppTheme.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }

                    PremiumPanel("Users (of this laptop only)", Icons.Default.People, AppTheme.Info) {
                        if (users.isEmpty()) Text("No user. Add one below.", color = AppTheme.TextMuted, fontSize = 13.sp)
                        users.forEach { u ->
                            Card(
                                shape = RoundedCornerShape(AppTheme.RadiusSm),
                                border = BorderStroke(1.dp, AppTheme.Border),
                                colors = CardDefaults.cardColors(containerColor = AppTheme.SurfaceAlt),
                                elevation = CardDefaults.cardElevation(0.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    IconCircle(Icons.Default.Person, if (u.isActive) AppGreen else AppTheme.TextMuted, size = 36, iconSize = 18)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(u.username, fontWeight = FontWeight.Black, fontSize = 15.sp, color = AppTheme.TextPrimary)
                                        Text(if (u.isActive) "Active" else "Closed", fontSize = 12.sp, color = if (u.isActive) AppTheme.Success else AppTheme.Danger, fontWeight = FontWeight.SemiBold)
                                    }
                                    Switch(
                                        checked = u.isActive,
                                        onCheckedChange = { on -> scope.launch { services.auth.setActive(u.id, on); loadUsers() } },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppGreen)
                                    )
                                    TextButton(onClick = { passwordFor = u }) { Text("Password", fontSize = 12.sp) }
                                    TextButton(onClick = { deleteFor = u }) { Text("Delete", color = AppTheme.Danger, fontSize = 12.sp) }
                                }
                            }
                        }
                        Button(
                            onClick = { showAddUser = true },
                            shape = RoundedCornerShape(AppTheme.RadiusSm),
                            colors = ButtonDefaults.buttonColors(containerColor = AppGreen),
                            modifier = Modifier.height(42.dp)
                        ) { Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Add User", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }

    // ===== DIALOGS =====
    if (showAddUser) {
        FormDialog(
            title = "New User", labels = listOf("Username", "Password"), secret = listOf(false, true), confirmText = "Add",
            onSubmit = { v -> val p = services.auth.addUser(v[0], v[1]); if (p == null) loadUsers(); p },
            onDismiss = { showAddUser = false },
        )
    }
    passwordFor?.let { u ->
        FormDialog(
            title = "${u.username} new password", labels = listOf("New password"), secret = listOf(true), confirmText = "Change",
            onSubmit = { v -> services.auth.changePassword(u.id, v[0]) },
            onDismiss = { passwordFor = null },
        )
    }
    deleteFor?.let { u ->
        ConfirmDialog(
            "Delete User", "'${u.username}' delete permanently?", "Delete",
            { scope.launch { services.auth.deleteUser(u.id); loadUsers() }; deleteFor = null },
            { deleteFor = null }
        )
    }
    if (showAddShop) {
        FormDialog(
            title = "New Shop",
            labels = listOf("Shop Code (leave blank = random)", "Shop Name", "Max laptops (Blank = ${AppConfig.DEFAULT_MAX_DEVICES})"),
            secret = listOf(false, false, false), confirmText = "Create",
            onSubmit = { v ->
                val id = v[0].trim().ifBlank { "shop_" + randomSaltHex().take(20) }
                val max = v[2].trim().ifBlank { AppConfig.DEFAULT_MAX_DEVICES.toString() }.toIntOrNull()
                when {
                    !isValidShopId(id) -> "Shop code must be 3 to 40 characters long: only a-z, A-Z, 0-9, _, or -"
                    max == null || max < 1 -> "Max laptops should be at least 1."
                    else -> { val p = services.firebase.putLicense(session.idToken, id, LicenseInfo(true, v[1].trim(), "", max)); if (p == null) loadShops(); p }
                }
            },
            onDismiss = { showAddShop = false },
        )
    }
    if (showChangeShop) {
        FormDialog(
            title = "This laptop's shop code", labels = listOf("New Shop Code"), secret = listOf(false), confirmText = "Save",
            onSubmit = { v ->
                val p = services.license.activate(v[0])
                if (p == null) {
                    services.sync.onShopChanged()
                    loadThisShop()
                    scope.launch { snackbar.showSnackbar("Shop code changed. Sync is now OFFLINE.") }
                }
                p
            },
            onDismiss = { showChangeShop = false },
        )
    }
    editFor?.let { id ->
        val info = shops[id]
        if (info != null) {
            FormDialog(
                title = "Shop Details: $id",
                labels = listOf("Shop Name", "Message displayed upon banning (blank = default)"),
                secret = listOf(false, false), initial = listOf(info.shopName, info.message), confirmText = "Save",
                onSubmit = { v -> val p = services.firebase.putLicense(session.idToken, id, info.copy(shopName = v[0].trim(), message = v[1].trim())); if (p == null) loadShops(); p },
                onDismiss = { editFor = null },
            )
        }
    }
    deleteShopFor?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteShopFor = null; deleteShopBills = false },
            icon = { IconCircle(Icons.Default.DeleteForever, AppTheme.Danger, size = 48, iconSize = 24) },
            title = { Text("Shop Delete", fontWeight = FontWeight.Black, color = AppTheme.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("'$id' and the records of all its laptops will be deleted. All laptops will be closed. Sure?", color = AppTheme.TextSecondary, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = deleteShopBills, onCheckedChange = { deleteShopBills = it })
                        Text("Also delete this shop's online bills forever", color = if (deleteShopBills) AppTheme.Danger else AppTheme.TextPrimary, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val withBills = deleteShopBills
                        scope.launch {
                            val p = services.firebase.deleteShop(session.idToken, id, deleteBills = withBills)
                            if (p == null) loadShops() else snackbar.showSnackbar(p)
                        }
                        deleteShopFor = null; deleteShopBills = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Danger),
                    shape = RoundedCornerShape(AppTheme.RadiusSm)
                ) { Text("Delete", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deleteShopFor = null; deleteShopBills = false }) { Text("Cancel", color = AppTheme.TextSecondary) } },
            shape = RoundedCornerShape(AppTheme.RadiusLg), containerColor = AppTheme.Surface
        )
    }

    // ALERT 1: Make Master Confirmation
    deviceToMakeMaster?.let { (shopId, device) ->
        AlertDialog(
            onDismissRequest = { deviceToMakeMaster = null },
            icon = { IconCircle(Icons.Default.Warning, AppTheme.Warning, 48, 24) },
            title = { Text("Change Master Device?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to make '${device.label}' the Master?\n\nNote: The current Master will become a Viewer and download data from this device.", color = AppTheme.TextSecondary)
            },
            confirmButton = {
                Button(
                    onClick = {
                        deviceToMakeMaster = null
                        scope.launch {
                            updatingRoleDeviceId = device.id
                            try {
                                val url = "${AppConfig.DB_URL}/shops/$shopId/meta/masterDeviceId.json?auth=${session.idToken}"
                                val res = httpCall("PUT", url, "\"${device.id}\"", 15_000)
                                if (res.code == 200) {
                                    snackbar.showSnackbar("${device.label} is now the Master.")
                                    loadShops()
                                } else {
                                    snackbar.showSnackbar("Failed: HTTP ${res.code}")
                                }
                            } catch (e: Exception) {
                                snackbar.showSnackbar("Error: ${e.message}")
                            } finally {
                                updatingRoleDeviceId = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Warning)
                ) { Text("Yes, Make Master", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deviceToMakeMaster = null }) { Text("Cancel") } },
            shape = RoundedCornerShape(AppTheme.RadiusLg), containerColor = AppTheme.Surface
        )
    }

    // ALERT 2: Delete Device Confirmation
    deviceToDelete?.let { (shopId, device) ->
        AlertDialog(
            onDismissRequest = { deviceToDelete = null },
            icon = { IconCircle(Icons.Default.Delete, AppTheme.Danger, 48, 24) },
            title = { Text("Remove Device?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently remove '${device.label}' from this shop?", color = AppTheme.TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        deviceToDelete = null
                        scope.launch {
                            updatingRoleDeviceId = device.id
                            val err = services.firebase.removeDevice(session.idToken, shopId, device.id)
                            if (err == null) {
                                snackbar.showSnackbar("Device removed.")
                                loadShops()
                            } else snackbar.showSnackbar(err)
                            updatingRoleDeviceId = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Danger)
                ) { Text("Delete", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deviceToDelete = null }) { Text("Cancel") } },
            shape = RoundedCornerShape(AppTheme.RadiusLg), containerColor = AppTheme.Surface
        )
    }
}

@Composable
private fun PremiumPanel(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(AppTheme.RadiusLg),
        colors = CardDefaults.cardColors(containerColor = AppTheme.Surface),
        border = BorderStroke(1.dp, AppTheme.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(icon, accent, size = 36, iconSize = 18)
                Spacer(Modifier.width(12.dp))
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Black, color = AppTheme.TextPrimary)
            }
            HorizontalDivider(color = AppTheme.Border)
            content()
        }
    }
}

@Composable
private fun FormDialog(
    title: String, labels: List<String>, secret: List<Boolean>, confirmText: String,
    onSubmit: suspend (List<String>) -> String?, onDismiss: () -> Unit, initial: List<String> = emptyList(),
) {
    val values = remember { mutableStateListOf<String>().apply { repeat(labels.size) { add(initial.getOrElse(it) { "" }) } } }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title, fontWeight = FontWeight.Black, color = AppTheme.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                labels.forEachIndexed { i, label ->
                    OutlinedTextField(
                        value = values[i], onValueChange = { values[i] = it },
                        label = { Text(label, fontSize = 12.sp) }, singleLine = true, enabled = !busy,
                        shape = RoundedCornerShape(AppTheme.RadiusSm),
                        visualTransformation = if (secret[i]) PasswordVisualTransformation() else VisualTransformation.None,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen, unfocusedBorderColor = AppTheme.Border)
                    )
                }
                error?.let {
                    Surface(color = AppTheme.DangerLight, shape = RoundedCornerShape(AppTheme.RadiusSm)) {
                        Text(it, color = AppTheme.Danger, fontSize = 12.sp, modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = { scope.launch {
                    busy = true; error = null
                    val problem = onSubmit(values.toList())
                    busy = false
                    if (problem == null) onDismiss() else error = problem
                } },
                shape = RoundedCornerShape(AppTheme.RadiusSm),
                colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
            ) { if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White) else Text(confirmText, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel", color = AppTheme.TextSecondary) } },
        shape = RoundedCornerShape(AppTheme.RadiusLg), containerColor = AppTheme.Surface
    )
}