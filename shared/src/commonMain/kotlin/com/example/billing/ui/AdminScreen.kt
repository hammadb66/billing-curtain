package com.example.billing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    services: AppServices,
    session: DevSession,
    onOpenBilling: () -> Unit,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var users by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var shops by remember { mutableStateOf<Map<String, LicenseInfo>>(emptyMap()) }
    var allDevices by remember { mutableStateOf<Map<String, Map<String, DeviceRecord>>>(emptyMap()) }
    var shopsError by remember { mutableStateOf<String?>(null) }
    var thisShop by remember { mutableStateOf<String?>(null) }

    var showAddUser by remember { mutableStateOf(false) }
    var showAddShop by remember { mutableStateOf(false) }
    var showChangeShop by remember { mutableStateOf(false) }
    var passwordFor by remember { mutableStateOf<AppUser?>(null) }
    var deleteFor by remember { mutableStateOf<AppUser?>(null) }
    var devicesFor by remember { mutableStateOf<String?>(null) }
    var editFor by remember { mutableStateOf<String?>(null) }
    var deleteShopFor by remember { mutableStateOf<String?>(null) }
    var deleteShopBills by remember { mutableStateOf(false) }

    suspend fun loadUsers() { users = services.auth.listUsers() }
    suspend fun loadThisShop() { thisShop = services.settings.get(SettingKeys.SHOP_ID) }
    suspend fun loadShops() {
        when (val r = services.firebase.listLicenses(session.idToken)) {
            is ShopsResult.Ok -> { shops = r.shops; shopsError = null }
            is ShopsResult.Failed -> shopsError = r.message
        }
        services.firebase.listAllDevices(session.idToken)?.let { allDevices = it }
    }

    LaunchedEffect(Unit) {
        loadUsers()
        loadThisShop()
        loadShops()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color(0xFFF4F5F7),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // ================== HEADER ==================
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Admin Panel", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
                        Text(session.email, fontSize = 12.sp, color = Color.Gray)
                    }
                    Button(
                        onClick = onOpenBilling,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)),
                        modifier = Modifier.height(40.dp)
                    ) { Text("Open Billing", fontSize = 13.sp) }

                    Spacer(Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = onLogout,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color.Red),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                        modifier = Modifier.height(40.dp)
                    ) { Text("Logout", fontSize = 13.sp) }
                }
            }

            // ================== CONTENT ==================
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 900.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Panel("This laptop's shop code") {
                        Text(thisShop ?: "Not set yet.", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
                        Text("The same code needs to be applied to all the laptops in a shop.", fontSize = 12.sp, color = Color.Gray)
                        OutlinedButton(
                            onClick = { showChangeShop = true },
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                            modifier = Modifier.height(40.dp)
                        ) { Text("Change Shop Code", color = Color(0xFF2C3E50)) }
                    }

                    SyncPanel(services)

                    Panel("Shops (Firebase license ON / OFF)") {
                        shopsError?.let { Text(it, color = Color.Red, fontSize = 13.sp) }
                        if (shops.isEmpty() && shopsError == null) Text("No shop yet. Please add a shop below.", color = Color.Gray)
                        shops.entries.sortedBy { it.key }.forEach { (id, info) ->
                            val used = allDevices[id]?.values?.count { !it.blocked } ?: 0
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(id, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E293B))
                                    val label = info.shopName.ifBlank { "-" } + if (id == thisShop) "   (or laptop)" else ""
                                    Text(label, fontSize = 12.sp, color = Color.Gray)
                                    Text("Laptops: $used / ${info.maxDevices}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (used > info.maxDevices) Color.Red else AppGreen)
                                }
                                Text(if (info.active) "ON" else "OFF", color = if (info.active) AppGreen else Color.Red, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
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
                            }
                            Row {
                                TextButton(onClick = { devicesFor = id }) { Text("Laptops", fontSize = 12.sp) }
                                TextButton(onClick = { editFor = id }) { Text("Name / Message", fontSize = 12.sp) }
                                TextButton(onClick = { deleteShopFor = id }) { Text("Delete", color = Color.Red, fontSize = 12.sp) }
                            }
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { showAddShop = true },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppGreen),
                                modifier = Modifier.height(40.dp)
                            ) { Text("+ New Shop", fontSize = 13.sp) }

                            OutlinedButton(
                                onClick = { scope.launch { loadShops() } },
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                                modifier = Modifier.height(40.dp)
                            ) { Text("Refresh", color = Color(0xFF2C3E50), fontSize = 13.sp) }
                        }
                    }

                    Panel("Users (of this laptop only)") {
                        if (users.isEmpty()) Text("No user. Add one below.", color = Color.Gray)
                        users.forEach { u ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(u.username, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(if (u.isActive) "Active" else "Closed", fontSize = 12.sp, color = if (u.isActive) AppGreen else Color.Red)
                                }
                                Switch(
                                    checked = u.isActive,
                                    onCheckedChange = { on ->
                                        scope.launch { services.auth.setActive(u.id, on); loadUsers() }
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppGreen)
                                )
                                TextButton(onClick = { passwordFor = u }) { Text("Password", fontSize = 12.sp) }
                                TextButton(onClick = { deleteFor = u }) { Text("Delete", color = Color.Red, fontSize = 12.sp) }
                            }
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                        }
                        Button(
                            onClick = { showAddUser = true },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppGreen),
                            modifier = Modifier.height(40.dp)
                        ) { Text("+ Add User", fontSize = 13.sp) }
                    }
                }
            }
        }
    }

    // ================= Dialogs =================
    if (showAddUser) {
        FormDialog(
            title = "New User",
            labels = listOf("Username", "Password"),
            secret = listOf(false, true),
            confirmText = "Add",
            onSubmit = { v ->
                val problem = services.auth.addUser(v[0], v[1])
                if (problem == null) loadUsers()
                problem
            },
            onDismiss = { showAddUser = false },
        )
    }

    passwordFor?.let { u ->
        FormDialog(
            title = "${u.username} new password",
            labels = listOf("New password"),
            secret = listOf(true),
            confirmText = "Change",
            onSubmit = { v -> services.auth.changePassword(u.id, v[0]) },
            onDismiss = { passwordFor = null },
        )
    }

    deleteFor?.let { u ->
        ConfirmDialog(
            title = "Delete User",
            message = "'${u.username}' delete permanently?",
            confirmText = "Delete",
            onConfirm = {
                scope.launch { services.auth.deleteUser(u.id); loadUsers() }
                deleteFor = null
            },
            onDismiss = { deleteFor = null },
        )
    }

    if (showAddShop) {
        FormDialog(
            title = "New Shop",
            labels = listOf(
                "Shop Code (leave blank = random)",
                "Shop Name",
                "Max laptops (Blank = ${AppConfig.DEFAULT_MAX_DEVICES})",
            ),
            secret = listOf(false, false, false),
            confirmText = "Create",
            onSubmit = { v ->
                val id = v[0].trim().ifBlank { "shop_" + randomSaltHex().take(20) }
                val max = v[2].trim().ifBlank { AppConfig.DEFAULT_MAX_DEVICES.toString() }.toIntOrNull()
                if (!isValidShopId(id)) {
                    "Shop code must be 3 to 40 characters long: only a-z, A-Z, 0-9, _, or -"
                } else if (max == null || max < 1) {
                    "Max laptops should be at least 1."
                } else {
                    val problem = services.firebase.putLicense(session.idToken, id, LicenseInfo(true, v[1].trim(), "", max))
                    if (problem == null) loadShops()
                    problem
                }
            },
            onDismiss = { showAddShop = false },
        )
    }

    if (showChangeShop) {
        FormDialog(
            title = "This laptop's shop code",
            labels = listOf("New Shop Code"),
            secret = listOf(false),
            confirmText = "Save",
            onSubmit = { v ->
                val problem = services.license.activate(v[0])
                if (problem == null) {
                    loadThisShop()
                    if (services.sync.handleShopChange()) {
                        scope.launch { snackbar.showSnackbar("Shop code changed, online syncing disabled.") }
                    }
                }
                problem
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
                secret = listOf(false, false),
                initial = listOf(info.shopName, info.message),
                confirmText = "Save",
                onSubmit = { v ->
                    val problem = services.firebase.putLicense(session.idToken, id, info.copy(shopName = v[0].trim(), message = v[1].trim()))
                    if (problem == null) loadShops()
                    problem
                },
                onDismiss = { editFor = null },
            )
        }
    }

    deleteShopFor?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteShopFor = null; deleteShopBills = false },
            title = { Text("Shop delete", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("'$id' and the records of all its laptops will be deleted. All laptops will be closed. Sure?")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = deleteShopBills, onCheckedChange = { deleteShopBills = it })
                        Text("Also delete this shop's online bills forever", color = if (deleteShopBills) Color.Red else Color.DarkGray, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val withBills = deleteShopBills
                        scope.launch {
                            val problem = services.firebase.deleteShop(session.idToken, id, deleteBills = withBills)
                            if (problem == null) loadShops() else snackbar.showSnackbar(problem)
                        }
                        deleteShopFor = null; deleteShopBills = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(6.dp)
                ) { Text("Delete", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deleteShopFor = null; deleteShopBills = false }) { Text("Cancel") } },
            shape = RoundedCornerShape(8.dp), containerColor = Color.White
        )
    }

    devicesFor?.let { id ->
        val info = shops[id]
        if (info != null) {
            DevicesDialog(
                shopId = id, info = info, devices = allDevices[id]?.values?.toList().orEmpty(),
                services = services, session = session, onChanged = { loadShops() }, onClose = { devicesFor = null },
            )
        }
    }
}

@Composable
private fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
            content()
        }
    }
}

private fun fmtTime(ms: Long): String = try {
    Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault()).toString().take(16).replace('T', ' ')
} catch (e: Throwable) { "-" }

@Composable
private fun DevicesDialog(
    shopId: String,
    info: LicenseInfo,
    devices: List<DeviceRecord>,
    services: AppServices,
    session: DevSession,
    onChanged: suspend () -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var maxText by remember { mutableStateOf(info.maxDevices.toString()) }
    var message by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Laptops: ${info.shopName.ifBlank { shopId }}", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.widthIn(min = 360.dp).heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = maxText, onValueChange = { maxText = it.filter { c -> c.isDigit() } },
                        label = { Text("Max laptops") }, singleLine = true, shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.width(140.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                val n = maxText.toIntOrNull()
                                if (n == null || n < 1) { message = "Write at least 1." }
                                else { message = services.firebase.putLicense(session.idToken, shopId, info.copy(maxDevices = n)) ?: "Saved"; onChanged() }
                            }
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) { Text("Save") }
                }
                message?.let { Text(it, fontSize = 12.sp, color = Color.Gray) }
                HorizontalDivider(color = Color(0xFFE0E0E0))

                if (devices.isEmpty()) Text("No laptop registered yet.", color = Color.Gray)
                devices.sortedBy { it.firstSeen }.forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("ID ${d.id.take(8)}   first: ${fmtTime(d.firstSeen)}   last: ${fmtTime(d.lastSeen)}", fontSize = 11.sp, color = Color.Gray)
                            if (d.blocked) Text("BLOCKED", fontSize = 11.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { scope.launch { message = services.firebase.setDeviceBlocked(session.idToken, shopId, d.id, !d.blocked); onChanged() } }) { Text(if (d.blocked) "Unblock" else "Block", fontSize = 12.sp) }
                        TextButton(onClick = { scope.launch { message = services.firebase.removeDevice(session.idToken, shopId, d.id); onChanged() } }) { Text("Remove", color = Color.Red, fontSize = 12.sp) }
                    }
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close", fontWeight = FontWeight.Bold) } },
        shape = RoundedCornerShape(8.dp), containerColor = Color.White
    )
}

@Composable
private fun FormDialog(
    title: String,
    labels: List<String>,
    secret: List<Boolean>,
    confirmText: String,
    onSubmit: suspend (List<String>) -> String?,
    onDismiss: () -> Unit,
    initial: List<String> = emptyList(),
) {
    val values = remember { mutableStateListOf<String>().apply { repeat(labels.size) { add(initial.getOrElse(it) { "" }) } } }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                labels.forEachIndexed { i, label ->
                    OutlinedTextField(
                        value = values[i], onValueChange = { values[i] = it },
                        label = { Text(label, fontSize = 12.sp) }, singleLine = true, enabled = !busy,
                        shape = RoundedCornerShape(6.dp),
                        visualTransformation = if (secret[i]) PasswordVisualTransformation() else VisualTransformation.None,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                error?.let { Text(it, color = Color.Red, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true; error = null
                        val problem = onSubmit(values.toList())
                        busy = false
                        if (problem == null) onDismiss() else error = problem
                    }
                },
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
            ) { if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White) else Text(confirmText, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(8.dp), containerColor = Color.White
    )
}