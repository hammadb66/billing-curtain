package com.example.billing.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billing.AppServices
import com.example.billing.sync.EnableResult
import com.example.billing.sync.SyncStatus
import kotlinx.coroutines.launch

private fun SyncStatus.label(): String = when (this) {
    SyncStatus.Disabled -> "Offline: The data is only on this laptop."
    SyncStatus.Syncing -> "Syncing..."
    SyncStatus.Synced -> "Online: Everything is equal with the firebase."
    is SyncStatus.Waiting -> "Online: $pending Change yet to be sent ($reason). It will happen automatically once the internet comes on."
}

private fun SyncStatus.color(): Color = when (this) {
    SyncStatus.Disabled -> Color.Gray
    SyncStatus.Syncing -> Color(0xFF1565C0)
    SyncStatus.Synced -> AppGreen
    is SyncStatus.Waiting -> Color(0xFFE65100)
}

/** Chhota status (Billing / History screen ke upar lagane ke liye). */
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

/** Admin Panel ka ONLINE / OFFLINE switch. AdminScreen ke Column me bas `SyncPanel(services)` likh den. */
@Composable
fun SyncPanel(services: AppServices) {
    val scope = rememberCoroutineScope()
    val status by services.sync.status.collectAsState()
    var online by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var forceOfflineReason by remember { mutableStateOf<String?>(null) }
    var foreignShop by remember { mutableStateOf<String?>(null) }
    val notice by services.sync.notice.collectAsState()

    // Status badalte hi switch bhi theek kar do (maslan shop code badalne par sync khud offline ho jata hai)
    LaunchedEffect(status) { online = services.sync.isOnlineMode() }

    fun enable(confirmForeign: Boolean) {
        scope.launch {
            busy = true
            message = null
            when (val r = services.sync.enableOnline(confirmForeign)) {
                is EnableResult.Success -> message = r.note ?: "Went online. Data synced with Firebase."
                is EnableResult.Error -> message = r.message
                is EnableResult.NeedsConfirm -> foreignShop = r.oldShop
            }
            online = services.sync.isOnlineMode()
            busy = false
        }
    }

    fun toggle(on: Boolean) {
        if (busy) return
        if (on) {
            enable(confirmForeign = false)
            return
        }
        scope.launch {
            busy = true
            message = null
            val err = services.sync.disableOnline()
            if (err != null) forceOfflineReason = err else message = "Went offline. The new data from Firebase has arrived locally."
            online = services.sync.isOnlineMode()
            busy = false
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Online Sync (Firebase)", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AppGreen)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (online) "ONLINE" else "OFFLINE", fontWeight = FontWeight.Bold, color = if (online) AppGreen else Color.Gray)
                    Text(status.label(), fontSize = 12.sp, color = status.color())
                }
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = AppGreen)
                Switch(checked = online, enabled = !busy, onCheckedChange = { toggle(it) })
            }
            message?.let { Text(it, fontSize = 13.sp, color = Color.DarkGray) }
            notice?.let {
                Text(it, fontSize = 13.sp, color = Color(0xFFE65100))
                TextButton(onClick = { services.sync.clearNotice() }) { Text("OK") }
            }
            if (online) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            message = services.sync.syncNow() ?: "Sync complete."
                            busy = false
                        }
                    },
                ) { Text("Sync Now") }
            }
            Text(
                "When TURNED ON: Old data from this laptop is sent to Firebase, and data from Firebase is brought here. " +
                        "Then, every new, edited, or deleted bill is saved online (if offline, saved locally first, then auto-uploaded when back online). " +
                        "New data from Firebase updates when a bill is created, when the app opens, and on History Refresh. " +
                        "When TURNED OFF: All Firebase data is downloaded locally and runs offline.",
                fontSize = 12.sp,
                color = Color.Gray,
            )
        }
    }

    foreignShop?.let { old ->
        AlertDialog(
            onDismissRequest = { foreignShop = null },
            title = { Text("Previous Shop Data") },
            text = {
                Text(
                    "This laptop contains data for shop '$old', but the shop code is different now. " +
                            "Going online will move all these bills to the NEW shop.\n\n" +
                            "If this data does not belong to the new shop, please click Cancel."
                )
            },
            confirmButton = {
                TextButton(onClick = { foreignShop = null; enable(confirmForeign = true) }) {
                    Text("Yes, Send it to new Shop", color = Color.Red)
                }
            },
            dismissButton = { TextButton(onClick = { foreignShop = null }) { Text("Cancel") } },
        )
    }

    forceOfflineReason?.let { reason ->
        AlertDialog(
            onDismissRequest = { forceOfflineReason = null },
            title = { Text("Could Not Switch Offline") },
            text = { Text("$reason\n\nForce offline anyway? New data from Firebase will not be saved locally.") },
            confirmButton = {
                TextButton(onClick = {
                    forceOfflineReason = null
                    scope.launch {
                        busy = true
                        services.sync.disableOnline(force = true)
                        online = services.sync.isOnlineMode()
                        message = "Switched to offline (forced)."
                        busy = false
                    }
                }) { Text("Force Offline Anyway", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { forceOfflineReason = null }) { Text("Cancel") } },
        )
    }
}
