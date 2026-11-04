package com.example.billing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billing.AppServices
import com.example.billing.sync.EnableResult
import com.example.billing.sync.SyncManager
import com.example.billing.sync.SyncStatus
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
private fun SyncStatus.label(): String = when (this) {
    SyncStatus.Disabled -> "Offline: Data is only on this laptop."
    SyncStatus.Syncing -> "Syncing..."
    SyncStatus.Synced -> "Online: Everything is synced with Firebase."
    is SyncStatus.Waiting -> "Online: Waiting ($reason)."
}

private fun SyncStatus.color(): Color = when (this) {
    SyncStatus.Disabled -> AppTheme.TextSecondary
    SyncStatus.Syncing -> AppTheme.Info
    SyncStatus.Synced -> AppGreen
    is SyncStatus.Waiting -> AppTheme.Warning
}

@Composable
fun SyncStatusChip(services: AppServices, modifier: Modifier = Modifier) {
    val status by services.sync.status.collectAsState()
    val text = when (status) {
        SyncStatus.Disabled -> "OFFLINE"
        SyncStatus.Syncing -> "SYNCING"
        SyncStatus.Synced -> "ONLINE"
        is SyncStatus.Waiting -> "ONLINE • pending"
    }
    Text(text, color = status.color(), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = modifier)
}

@Composable
fun SyncPanel(services: AppServices) {
    val scope = rememberCoroutineScope()
    val status by services.sync.status.collectAsState()
    val role by services.sync.role.collectAsState()
    var online by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var forceOfflineReason by remember { mutableStateOf<String?>(null) }
    val notice by services.sync.notice.collectAsState()

    LaunchedEffect(status, role) { online = services.sync.isOnlineMode() }

    val isMaster = role == SyncManager.ROLE_MASTER

    fun enable() {
        scope.launch {
            busy = true; message = null
            when (val r = services.sync.enableOnline()) {
                is EnableResult.Success -> message = r.note ?: "Went online."
                is EnableResult.Error -> message = r.message
            }
            online = services.sync.isOnlineMode(); busy = false
        }
    }

    fun toggle(on: Boolean) {
        if (busy) return
        if (on) { enable(); return }
        scope.launch {
            busy = true; message = null
            val err = services.sync.disableOnline()
            if (err != null) forceOfflineReason = err else message = "Went offline."
            online = services.sync.isOnlineMode(); busy = false
        }
    }

    fun switchRole(master: Boolean) {
        if (busy || master == isMaster) return
        scope.launch {
            busy = true
            services.sync.setRole(master)
            message = if (master) "Now Master — changes push to cloud." else "Now Viewer — cloud pulls here."
            busy = false
        }
    }

    Card(
        shape = RoundedCornerShape(AppTheme.RadiusLg),
        colors = CardDefaults.cardColors(containerColor = AppTheme.Surface),
        border = BorderStroke(1.dp, AppTheme.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Default.Cloud, AppTheme.Info, size = 36, iconSize = 18)
                Spacer(Modifier.width(12.dp))
                Text("Online Sync (Firebase)", fontSize = 16.sp, fontWeight = FontWeight.Black, color = AppTheme.TextPrimary)
            }
            HorizontalDivider(color = AppTheme.Border)

            // ROLE SELECTOR
            Text("This device's role: ${if (isMaster) "MASTER" else "VIEWER"}",
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isMaster) AppGreen else AppTheme.Info)
            Text("(Role is assigned by Admin. You cannot change it here.)", fontSize = 11.sp, color = AppTheme.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // SyncPanel() function ke andar RoleChip me:
                RoleChip(
                    selected = isMaster,
                    title = "Master",
                    subtitle = "Desktop — data entry, auto push",
                    color = AppGreen,
                    onClick = { switchRole(true) },
                    enabled = online && !busy, // <-- YAHAN online && !busy lagayein
                    modifier = Modifier.weight(1f),
                )
                RoleChip(
                    selected = !isMaster,
                    title = "Viewer",
                    subtitle = "Mobile — download, view reports",
                    color = AppTheme.Info,
                    onClick = { switchRole(false) },
                    enabled = online && !busy, // <-- YAHAN bhi
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                if (isMaster) "Master mode: har bill/purchase/product change automatically cloud pe upload hoga."
                else "Viewer mode: har sync pe cloud se poora data download + replace hoga.",
                fontSize = 11.sp, color = AppTheme.TextSecondary, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )

            HorizontalDivider(color = AppTheme.Border)

            // ONLINE TOGGLE
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (online) "ONLINE" else "OFFLINE", fontWeight = FontWeight.Black, color = if (online) AppGreen else AppTheme.TextSecondary, fontSize = 15.sp)
                    Text(status.label(), fontSize = 12.sp, color = status.color(), fontWeight = FontWeight.Medium)
                }
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = AppGreen)
                Switch(checked = online, enabled = !busy, onCheckedChange = { toggle(it) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppGreen))
            }

            message?.let { Text(it, fontSize = 13.sp, color = AppTheme.TextSecondary) }

            notice?.let {
                Surface(color = AppTheme.WarningLight, shape = RoundedCornerShape(AppTheme.RadiusSm)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(it, fontSize = 13.sp, color = AppTheme.Warning, modifier = Modifier.weight(1f))
                        TextButton(onClick = { services.sync.clearNotice() }) { Text("OK", color = AppTheme.Warning, fontWeight = FontWeight.Bold) }
                    }
                }
            }

            if (online) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                message = services.sync.syncNow() ?: "Sync complete."
                                busy = false
                            }
                        },
                        shape = RoundedCornerShape(AppTheme.RadiusSm),
                        border = BorderStroke(1.dp, AppTheme.Border)
                    ) { Text("Sync Now", color = AppTheme.TextPrimary, fontWeight = FontWeight.SemiBold) }

                    // Manual "Push Local → Cloud" button (viewer ke liye escape hatch)
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                message = services.sync.forceUpload() ?: "Local data pushed to cloud."
                                busy = false
                            }
                        },
                        shape = RoundedCornerShape(AppTheme.RadiusSm),
                        border = BorderStroke(1.dp, AppTheme.Warning),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTheme.Warning)
                    ) { Text("Push Local → Cloud", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                }
            }

            Text(
                if (isMaster) "Master: only this device should be Master. Other devices = Viewer."
                else "Viewer: reports ka data cloud se aata hai. Delete karne ke liye Push Local → Cloud dabayein.",
                fontSize = 12.sp, color = AppTheme.TextSecondary
            )
        }
    }

    forceOfflineReason?.let { reason ->
        AlertDialog(
            onDismissRequest = { forceOfflineReason = null },
            title = { Text("Could Not Switch Offline", fontWeight = FontWeight.Bold) },
            text = { Text("$reason\n\nForce offline anyway?", color = AppTheme.TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        forceOfflineReason = null
                        scope.launch { busy = true; services.sync.disableOnline(force = true); online = services.sync.isOnlineMode(); message = "Switched to offline (forced)."; busy = false }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Danger),
                    shape = RoundedCornerShape(AppTheme.RadiusSm)
                ) { Text("Force Offline", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { forceOfflineReason = null }) { Text("Cancel") } },
            shape = RoundedCornerShape(AppTheme.RadiusLg), containerColor = AppTheme.Surface
        )
    }
}


@Composable
private fun RoleChip(
    selected: Boolean,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(AppTheme.RadiusSm),
        color = if (selected) color.copy(alpha = 0.12f) else AppTheme.SurfaceAlt,
        border = BorderStroke(1.dp, if (selected) color else AppTheme.Border),
        modifier = modifier                    // ← clean, no .run no-op, no weight
    ) {
        Column(
            Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = null, enabled = enabled, colors = RadioButtonDefaults.colors(selectedColor = color))
                Spacer(Modifier.width(4.dp))
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (selected) color else AppTheme.TextPrimary)
            }
            Text(subtitle, fontSize = 10.sp, color = AppTheme.TextSecondary)
        }
    }
}



@Composable
fun SyncStatusBar(services: AppServices) {
    val scope = rememberCoroutineScope()
    val online by services.sync.onlineMode.collectAsState()
    val uploadPending by services.sync.uploadPending.collectAsState()
    val downloadPending by services.sync.downloadPending.collectAsState()
    val lastError by services.sync.lastError.collectAsState()
    val lastSyncAt by services.sync.lastSyncAt.collectAsState()
    val status by services.sync.status.collectAsState()
    val role by services.sync.role.collectAsState()

    var busy by remember { mutableStateOf(false) }
    var showChoice by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val isMaster = role == SyncManager.ROLE_MASTER
    val hasUpload = uploadPending
    val hasDownload = downloadPending
    val errorMsg = localError ?: lastError
    val hasError = errorMsg != null
    val bothPending = hasUpload && hasDownload

    if (!online) return

// 2. Sync ON lekin network issue hai → warning banner with retry
    if (lastError != null) {
        Surface(
            color = AppTheme.WarningLight,
            shape = RoundedCornerShape(AppTheme.RadiusSm),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudOff, null, tint = AppTheme.Warning, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Network issue", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AppTheme.TextPrimary)
                    Text("Cloud se connection nahi ban raha. Retry karein.", fontSize = 11.sp, color = AppTheme.TextSecondary)
                }
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            services.sync.syncNow()
                            busy = false
                        }
                    },
                    enabled = !busy,
                    shape = RoundedCornerShape(AppTheme.RadiusSm),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Warning)
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    val bgColor = when {
        hasError -> AppTheme.DangerLight
        bothPending -> AppTheme.WarningLight
        hasUpload || hasDownload -> AppTheme.InfoLight
        else -> AppTheme.SuccessLight
    }
    val fgColor = when {
        hasError -> AppTheme.Danger
        bothPending -> AppTheme.Warning
        hasUpload || hasDownload -> AppTheme.Info
        else -> AppTheme.Success
    }
    val icon = when {
        hasError -> Icons.Default.ErrorOutline
        bothPending -> Icons.Default.WarningAmber
        hasUpload -> Icons.Default.CloudUpload
        hasDownload -> Icons.Default.CloudDownload
        else -> Icons.Default.CheckCircle
    }
    val titleText = when {
        hasError -> "Sync failed"
        busy || status == SyncStatus.Syncing -> "Syncing..."
        bothPending -> "Changes on both sides"
        hasUpload -> if (isMaster) "Unsaved changes" else "Local changes to push"
        hasDownload -> "Cloud has updates"
        else -> "Up to date"
    }

    Surface(color = bgColor, shape = RoundedCornerShape(AppTheme.RadiusSm), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(icon, fgColor, size = 32, iconSize = 16)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(titleText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fgColor)
                    Text(if (lastSyncAt > 0) "Last sync: ${formatRelativeTime(lastSyncAt)}" else "Not synced yet", fontSize = 11.sp, color = AppTheme.TextSecondary)
                }
            }

            if (hasError) {
                Button(
                    onClick = { scope.launch { busy = true; services.sync.syncNow(); busy = false } },
                    enabled = !busy,
                    shape = RoundedCornerShape(AppTheme.RadiusSm),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Danger),
                    modifier = Modifier.height(36.dp)
                ) { Text("Try Again", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            } else if (bothPending) {
                Button(
                    onClick = { showChoice = true },
                    shape = RoundedCornerShape(AppTheme.RadiusSm),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Warning),
                    modifier = Modifier.height(36.dp)
                ) { Text("Resolve Conflict", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isMaster) {
                        Button(
                            onClick = { scope.launch { busy = true; services.sync.uploadNow(); busy = false } },
                            enabled = !busy,
                            shape = RoundedCornerShape(AppTheme.RadiusSm),
                            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Info),
                            modifier = Modifier.height(36.dp)
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                            else {
                                Icon(Icons.Default.CloudUpload, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Upload Data Now", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    } else {
                        Button(
                            onClick = { scope.launch { busy = true; services.sync.downloadNow(); busy = false } },
                            enabled = !busy,
                            shape = RoundedCornerShape(AppTheme.RadiusSm),
                            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Info),
                            modifier = Modifier.height(36.dp)
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                            else {
                                Icon(Icons.Default.CloudDownload, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Download Data Now", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showChoice) {
        AlertDialog(
            onDismissRequest = { showChoice = false },
            title = { Text("Both sides have changes", fontWeight = FontWeight.Bold) },
            text = { Text("Local device has unsaved changes AND cloud has newer data. Choose what to do:", fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = { showChoice = false; scope.launch { busy = true; services.sync.uploadNow(); busy = false } },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Warning)
                ) { Text("Upload (Local Wins)", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showChoice = false; scope.launch { busy = true; services.sync.downloadNow(); busy = false } }) { Text("Download (Cloud Wins)", color = AppTheme.Info, fontWeight = FontWeight.Bold) }
            },
            shape = RoundedCornerShape(AppTheme.RadiusLg), containerColor = AppTheme.Surface
        )
    }
}

/** "just now", "5 min ago", "2 hr ago", "3 day ago" */
fun formatRelativeTime(ms: Long): String {
    val diff = System.currentTimeMillis() - ms
    val sec = diff / 1000
    return when {
        sec < 30 -> "just now"
        sec < 3600 -> "${sec / 60} min ago"
        sec < 86400 -> "${sec / 3600} hr ago"
        else -> "${sec / 86400} day ago"
    }
}