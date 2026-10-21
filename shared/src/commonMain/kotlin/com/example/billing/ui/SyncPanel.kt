package com.example.billing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.billing.sync.SyncStatus
import kotlinx.coroutines.launch

private fun SyncStatus.label(): String = when (this) {
    SyncStatus.Disabled -> "Offline: Data is only on this laptop."
    SyncStatus.Syncing -> "Syncing..."
    SyncStatus.Synced -> "Online: Everything is synced with Firebase."
    is SyncStatus.Waiting -> "Online: $pending change(s) pending ($reason)."
}

private fun SyncStatus.color(): Color = when (this) {
    SyncStatus.Disabled -> Color.Gray
    SyncStatus.Syncing -> Color(0xFF1565C0)
    SyncStatus.Synced -> AppGreen
    is SyncStatus.Waiting -> Color(0xFFE65100)
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
    var online by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var forceOfflineReason by remember { mutableStateOf<String?>(null) }
    var foreignShop by remember { mutableStateOf<String?>(null) }
    val notice by services.sync.notice.collectAsState()

    LaunchedEffect(status) { online = services.sync.isOnlineMode() }

    fun enable(confirmForeign: Boolean) {
        scope.launch {
            busy = true; message = null
            when (val r = services.sync.enableOnline(confirmForeign)) {
                is EnableResult.Success -> message = r.note ?: "Went online. Data synced."
                is EnableResult.Error -> message = r.message
                is EnableResult.NeedsConfirm -> foreignShop = r.oldShop
            }
            online = services.sync.isOnlineMode(); busy = false
        }
    }

    fun toggle(on: Boolean) {
        if (busy) return
        if (on) { enable(confirmForeign = false); return }
        scope.launch {
            busy = true; message = null
            val err = services.sync.disableOnline()
            if (err != null) forceOfflineReason = err else message = "Went offline. Data downloaded."
            online = services.sync.isOnlineMode(); busy = false
        }
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Online Sync (Firebase)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (online) "ONLINE" else "OFFLINE", fontWeight = FontWeight.Bold, color = if (online) AppGreen else Color.Gray, fontSize = 15.sp)
                    Text(status.label(), fontSize = 12.sp, color = status.color())
                }
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = AppGreen)
                Switch(
                    checked = online, enabled = !busy, onCheckedChange = { toggle(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppGreen)
                )
            }
            message?.let { Text(it, fontSize = 13.sp, color = Color.DarkGray) }
            notice?.let {
                Text(it, fontSize = 13.sp, color = Color(0xFFE65100))
                TextButton(onClick = { services.sync.clearNotice() }) { Text("OK", color = Color(0xFFE65100)) }
            }
            if (online) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = { scope.launch { busy = true; message = services.sync.syncNow() ?: "Sync complete."; busy = false } },
                    shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, Color(0xFFE0E0E0))
                ) { Text("Sync Now", color = Color(0xFF2C3E50)) }
            }
            Text(
                "When TURNED ON: Old data from this laptop is sent to Firebase, and data from Firebase is brought here. Then, every new, edited, or deleted bill is saved online.",
                fontSize = 12.sp, color = Color.Gray,
            )
        }
    }

    foreignShop?.let { old ->
        AlertDialog(
            onDismissRequest = { foreignShop = null },
            title = { Text("Previous Shop Data", fontWeight = FontWeight.Bold) },
            text = { Text("This laptop contains data for shop '$old', but the shop code is different now. Going online will move all these bills to the NEW shop.\n\nIf this data does not belong to the new shop, please click Cancel.") },
            confirmButton = {
                Button(
                    onClick = { foreignShop = null; enable(confirmForeign = true) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(6.dp)
                ) { Text("Yes, Send to New Shop", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { foreignShop = null }) { Text("Cancel") } },
            shape = RoundedCornerShape(8.dp), containerColor = Color.White
        )
    }

    forceOfflineReason?.let { reason ->
        AlertDialog(
            onDismissRequest = { forceOfflineReason = null },
            title = { Text("Could Not Switch Offline", fontWeight = FontWeight.Bold) },
            text = { Text("$reason\n\nForce offline anyway? New data from Firebase will not be saved locally.") },
            confirmButton = {
                Button(
                    onClick = {
                        forceOfflineReason = null
                        scope.launch { busy = true; services.sync.disableOnline(force = true); online = services.sync.isOnlineMode(); message = "Switched to offline (forced)."; busy = false }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(6.dp)
                ) { Text("Force Offline", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { forceOfflineReason = null }) { Text("Cancel") } },
            shape = RoundedCornerShape(8.dp), containerColor = Color.White
        )
    }
}