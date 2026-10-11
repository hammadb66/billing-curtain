package com.example.billing.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billing.AppServices
import com.example.billing.auth.AppConfig
import com.example.billing.auth.AppUser
import com.example.billing.auth.DevSession
import com.example.billing.auth.DeviceRecord
import com.example.billing.auth.LicenseInfo
import com.example.billing.auth.SettingKeys
import com.example.billing.auth.ShopsResult
import com.example.billing.auth.isValidShopId
import com.example.billing.auth.randomSaltHex
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Sirf developer ke login par khulne wali screen. */
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
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 860.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ---- Upar wali patti ----
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Admin Panel", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AppGreen)
                        Text(session.email, fontSize = 12.sp, color = Color.Gray)
                    }
                    Button(onClick = onOpenBilling) { Text("Billing kholein") }
                    OutlinedButton(onClick = onLogout) { Text("Logout") }
                }

                // ---- Is laptop ka shop code ----
                Panel("Is laptop ka Shop Code") {
                    Text(thisShop ?: "Abhi set nahi hai", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Ek dukaan ke saare laptops par yehi ek code lagana hai. Har dukaan ke laptops ki had (Max laptops) neeche set hoti hai.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                    )
                    OutlinedButton(onClick = { showChangeShop = true }) { Text("Shop Code badlein") }
                }

                // ---- Shops (Firebase) ----
                Panel("Shops (Firebase par license ON / OFF)") {
                    shopsError?.let { Text(it, color = Color.Red, fontSize = 13.sp) }
                    if (shops.isEmpty() && shopsError == null) {
                        Text("Abhi koi shop nahi. Neeche se banayein.", color = Color.Gray)
                    }
                    shops.entries.sortedBy { it.key }.forEach { (id, info) ->
                        val used = allDevices[id]?.values?.count { !it.blocked } ?: 0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(id, fontWeight = FontWeight.SemiBold)
                                val label = info.shopName.ifBlank { "-" } + if (id == thisShop) "   (ye laptop)" else ""
                                Text(label, fontSize = 12.sp, color = Color.Gray)
                                Text(
                                    "Laptops: $used / ${info.maxDevices}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (used > info.maxDevices) Color.Red else AppGreen,
                                )
                            }
                            Text(
                                if (info.active) "ON" else "OFF",
                                color = if (info.active) AppGreen else Color.Red,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(8.dp))
                            Switch(
                                checked = info.active,
                                onCheckedChange = { on ->
                                    scope.launch {
                                        val problem = services.firebase.putLicense(
                                            session.idToken, id, info.copy(active = on),
                                        )
                                        if (problem == null) loadShops() else snackbar.showSnackbar(problem)
                                    }
                                },
                            )
                        }
                        Row {
                            TextButton(onClick = { devicesFor = id }) { Text("Laptops") }
                            TextButton(onClick = { editFor = id }) { Text("Naam / Message") }
                            TextButton(onClick = { deleteShopFor = id }) { Text("Delete", color = Color.Red) }
                        }
                        HorizontalDivider()
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showAddShop = true }) { Text("+ New Shop") }
                        OutlinedButton(onClick = { scope.launch { loadShops() } }) { Text("Refresh") }
                    }
                }

                // ---- Users (is laptop ke) ----
                Panel("Users (sirf is laptop ke)") {
                    if (users.isEmpty()) Text("Koi user nahi. Neeche se banayein.", color = Color.Gray)
                    users.forEach { u ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(u.username, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (u.isActive) "Active" else "Band",
                                    fontSize = 12.sp,
                                    color = if (u.isActive) AppGreen else Color.Red,
                                )
                            }
                            Switch(
                                checked = u.isActive,
                                onCheckedChange = { on ->
                                    scope.launch {
                                        services.auth.setActive(u.id, on)
                                        loadUsers()
                                    }
                                },
                            )
                            TextButton(onClick = { passwordFor = u }) { Text("Password") }
                            TextButton(onClick = { deleteFor = u }) { Text("Delete", color = Color.Red) }
                        }
                        HorizontalDivider()
                    }
                    Button(onClick = { showAddUser = true }) { Text("+ Add User") }
                }
            }
        }
    }

    // ================= Dialogs =================

    if (showAddUser) {
        FormDialog(
            title = "Naya User",
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
            title = "${u.username} ka naya password",
            labels = listOf("Naya password"),
            secret = listOf(true),
            confirmText = "Change",
            onSubmit = { v -> services.auth.changePassword(u.id, v[0]) },
            onDismiss = { passwordFor = null },
        )
    }

    deleteFor?.let { u ->
        ConfirmDialog(
            title = "Delete User",
            message = "'${u.username}' ko hamesha ke liye delete karna hai?",
            confirmText = "Delete",
            onConfirm = {
                scope.launch {
                    services.auth.deleteUser(u.id)
                    loadUsers()
                }
                deleteFor = null
            },
            onDismiss = { deleteFor = null },
        )
    }

    if (showAddShop) {
        FormDialog(
            title = "Naya Shop",
            labels = listOf(
                "Shop code (khali chhorein to khud ban jayega)",
                "Shop ka naam",
                "Max laptops (khali = ${AppConfig.DEFAULT_MAX_DEVICES})",
            ),
            secret = listOf(false, false, false),
            confirmText = "Create",
            onSubmit = { v ->
                val id = v[0].trim().ifBlank { "shop_" + randomSaltHex().take(10) }
                val max = v[2].trim().ifBlank { AppConfig.DEFAULT_MAX_DEVICES.toString() }.toIntOrNull()
                if (!isValidShopId(id)) {
                    "Shop code 3 se 40 harf ka ho: sirf a-z, A-Z, 0-9, _ ya -"
                } else if (max == null || max < 1) {
                    "Max laptops kam az kam 1 ho"
                } else {
                    val problem = services.firebase.putLicense(
                        session.idToken, id, LicenseInfo(true, v[1].trim(), "", max),
                    )
                    if (problem == null) loadShops()
                    problem
                }
            },
            onDismiss = { showAddShop = false },
        )
    }

    if (showChangeShop) {
        FormDialog(
            title = "Is laptop ka Shop Code",
            labels = listOf("Naya Shop Code"),
            secret = listOf(false),
            confirmText = "Save",
            onSubmit = { v ->
                val problem = services.license.activate(v[0])
                if (problem == null) loadThisShop()
                problem
            },
            onDismiss = { showChangeShop = false },
        )
    }

    editFor?.let { id ->
        val info = shops[id]
        if (info != null) {
            FormDialog(
                title = "Shop ki tafseel: $id",
                labels = listOf("Shop ka naam", "Band hone par dikhne wala message (khali = default)"),
                secret = listOf(false, false),
                initial = listOf(info.shopName, info.message),
                confirmText = "Save",
                onSubmit = { v ->
                    val problem = services.firebase.putLicense(
                        session.idToken, id, info.copy(shopName = v[0].trim(), message = v[1].trim()),
                    )
                    if (problem == null) loadShops()
                    problem
                },
                onDismiss = { editFor = null },
            )
        }
    }

    deleteShopFor?.let { id ->
        ConfirmDialog(
            title = "Shop delete",
            message = "'$id' aur uske saare laptops ka record hamesha ke liye delete hoga. " +
                "Is shop ke saare laptops band ho jayenge. Pakka?",
            confirmText = "Delete",
            onConfirm = {
                scope.launch {
                    val problem = services.firebase.deleteShop(session.idToken, id)
                    if (problem == null) loadShops() else snackbar.showSnackbar(problem)
                }
                deleteShopFor = null
            },
            onDismiss = { deleteShopFor = null },
        )
    }

    devicesFor?.let { id ->
        val info = shops[id]
        if (info != null) {
            DevicesDialog(
                shopId = id,
                info = info,
                devices = allDevices[id]?.values?.toList().orEmpty(),
                services = services,
                session = session,
                onChanged = { loadShops() },
                onClose = { devicesFor = null },
            )
        }
    }
}

@Composable
private fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AppGreen)
            content()
        }
    }
}

private fun fmtTime(ms: Long): String = try {
    Instant.fromEpochMilliseconds(ms)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .toString()
        .take(16)
        .replace('T', ' ')
} catch (e: Throwable) {
    "-"
}

/** Is shop ke saare laptops: kaun kab se, aakhri baar kab chala, block / remove, aur max limit. */
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
        title = { Text("Laptops: ${info.shopName.ifBlank { shopId }}") },
        text = {
            Column(
                Modifier.widthIn(min = 360.dp).heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = maxText,
                        onValueChange = { maxText = it.filter { c -> c.isDigit() } },
                        label = { Text("Max laptops") },
                        singleLine = true,
                        modifier = Modifier.width(140.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        scope.launch {
                            val n = maxText.toIntOrNull()
                            if (n == null || n < 1) {
                                message = "Kam az kam 1 likhein"
                            } else {
                                message = services.firebase.putLicense(
                                    session.idToken, shopId, info.copy(maxDevices = n),
                                ) ?: "Save ho gaya"
                                onChanged()
                            }
                        }
                    }) { Text("Save") }
                }
                message?.let { Text(it, fontSize = 12.sp, color = Color.Gray) }
                HorizontalDivider()

                if (devices.isEmpty()) {
                    Text("Abhi tak koi laptop register nahi hua.", color = Color.Gray)
                }
                devices.sortedBy { it.firstSeen }.forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                "ID ${d.id.take(8)}   Pehli: ${fmtTime(d.firstSeen)}   Aakhri: ${fmtTime(d.lastSeen)}",
                                fontSize = 11.sp,
                                color = Color.Gray,
                            )
                            if (d.blocked) {
                                Text("BLOCKED", fontSize = 11.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                            }
                        }
                        TextButton(onClick = {
                            scope.launch {
                                message = services.firebase.setDeviceBlocked(session.idToken, shopId, d.id, !d.blocked)
                                onChanged()
                            }
                        }) { Text(if (d.blocked) "Unblock" else "Block") }
                        TextButton(onClick = {
                            scope.launch {
                                message = services.firebase.removeDevice(session.idToken, shopId, d.id)
                                onChanged()
                            }
                        }) { Text("Remove", color = Color.Red) }
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Band karein") } },
    )
}

/**
 * Chhota sa form dialog. onSubmit null de to dialog band; text de to wo error dikhta hai.
 */
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
    val values = remember {
        mutableStateListOf<String>().apply { repeat(labels.size) { add(initial.getOrElse(it) { "" }) } }
    }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEachIndexed { i, label ->
                    OutlinedTextField(
                        value = values[i],
                        onValueChange = { values[i] = it },
                        label = { Text(label) },
                        singleLine = true,
                        enabled = !busy,
                        visualTransformation =
                            if (secret[i]) PasswordVisualTransformation() else VisualTransformation.None,
                    )
                }
                error?.let { Text(it, color = Color.Red, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        val problem = onSubmit(values.toList())
                        busy = false
                        if (problem == null) onDismiss() else error = problem
                    }
                },
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}
