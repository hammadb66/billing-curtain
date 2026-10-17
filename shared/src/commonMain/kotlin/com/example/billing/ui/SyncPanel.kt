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
    SyncStatus.Disabled -> "Offline: data sirf is laptop me hai"
    SyncStatus.Syncing -> "Sync ho raha hai..."
    SyncStatus.Synced -> "Online: sab kuch Firebase ke saath barabar"
    is SyncStatus.Waiting -> "Online: $pending tabdili bhejna baqi ($reason). Net aate hi khud ho jayegi."
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
                is EnableResult.Success -> message = r.note ?: "Online ho gaya. Data Firebase ke saath sync ho gaya."
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
            if (err != null) forceOfflineReason = err else message = "Offline ho gaya. Firebase ka naya data local me aa gaya."
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
                            message = services.sync.syncNow() ?: "Sync mukammal."
                            busy = false
                        }
                    },
                ) { Text("Sync Now") }
            }
            Text(
                "ON karne par: is laptop ka purana data Firebase par jata hai aur Firebase ka data yahan aata hai. " +
                    "Phir har naya / edit / delete bill online save hota hai (net na ho to pehle local me, net aane par khud upload). " +
                    "Firebase se naya data bill banne par, app khulne par, aur History ke Refresh button par aata hai. " +
                    "OFF karne par: Firebase ka saara data local me utar kar offline chalta hai.",
                fontSize = 12.sp,
                color = Color.Gray,
            )
        }
    }

    foreignShop?.let { old ->
        AlertDialog(
            onDismissRequest = { foreignShop = null },
            title = { Text("Purane shop ka data") },
            text = {
                Text(
                    "Is laptop par shop '$old' ka data hai, lekin ab shop code alag hai. " +
                        "Online karne par ye saare bills NAYE shop me chale jayenge.\n\n" +
                        "Agar ye data naye shop ka nahi hai to Cancel karein."
                )
            },
            confirmButton = {
                TextButton(onClick = { foreignShop = null; enable(confirmForeign = true) }) {
                    Text("Haan, naye shop me bhejo", color = Color.Red)
                }
            },
            dismissButton = { TextButton(onClick = { foreignShop = null }) { Text("Cancel") } },
        )
    }

    forceOfflineReason?.let { reason ->
        AlertDialog(
            onDismissRequest = { forceOfflineReason = null },
            title = { Text("Offline nahi ho saka") },
            text = { Text("$reason\n\nPhir bhi offline karein? Firebase ka naya data local me nahi aayega.") },
            confirmButton = {
                TextButton(onClick = {
                    forceOfflineReason = null
                    scope.launch {
                        busy = true
                        services.sync.disableOnline(force = true)
                        online = services.sync.isOnlineMode()
                        message = "Offline kar diya (force)."
                        busy = false
                    }
                }) { Text("Phir bhi offline karo", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { forceOfflineReason = null }) { Text("Cancel") } },
        )
    }
}
