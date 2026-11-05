package com.tvtools.app.ui.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tvtools.app.AppServices
import com.tvtools.app.adb.AdbClient
import com.tvtools.app.data.TvDevice
import com.tvtools.app.ui.labs.LabScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ROOM_PRESETS = listOf("Living room", "Bedroom", "Kitchen", "Office", "Kids room", "Guest room")

/**
 * Rooms/groups: tag each saved TV with a room, then broadcast a key press to
 * every TV in that room. ADB allows one connection at a time, so the app hops
 * between TVs, sends the key, and reconnects to the TV you were using.
 */
@Composable
fun RoomsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<TvDevice>>(emptyList()) }
    var rooms by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var editing by remember { mutableStateOf<TvDevice?>(null) }
    var activeRoom by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun reload() {
        devices = AppServices.prefs.loadDevices()
        rooms = AppServices.prefs.loadDeviceRooms()
    }

    LaunchedEffect(Unit) { reload() }

    fun roomOf(device: TvDevice): String = rooms[device.host]?.takeIf { it.isNotBlank() } ?: "Unassigned"

    fun broadcast(room: String, key: Int, label: String) {
        if (busy) return
        val targets = if (room == ALL_ROOM) devices else devices.filter { roomOf(it) == room }
        if (targets.isEmpty()) {
            status = "No TVs in $room."
            return
        }
        busy = true
        scope.launch {
            val origin = AppServices.adb.connectedDevice
            var sent = 0
            for (device in targets) {
                if (AppServices.adb.connect(device.host, device.port) == AdbClient.ConnectResult.Success) {
                    runCatching { AppServices.adb.keyEvent(key) }
                    sent++
                }
            }
            if (origin != null) {
                runCatching { AppServices.adb.connect(origin.host, origin.port) }
            }
            status = "$label → $sent of ${targets.size} TV(s)"
            busy = false
            delay(2_000)
            if (!busy) status = null
        }
    }

    LabScaffold("Rooms & groups", onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                "Group your TVs by room. The remote below sends each press to every TV in the selected group.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            if (devices.isEmpty()) {
                Text(
                    "No saved TVs yet — connect to a TV first, then give it a room here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            val grouped = devices.groupBy { roomOf(it) }
            grouped.forEach { (room, list) ->
                val selected = activeRoom == room
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    room,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "${list.size} TV" + if (list.size == 1) "" else "s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(onClick = { activeRoom = if (selected) null else room }) {
                                Text(if (selected) "Hide remote" else "Group remote")
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        list.forEach { device ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    device.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    formatHostPort(device.host, device.port),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                IconButton(onClick = { editing = device }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Assign room",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                        if (selected) {
                            Spacer(Modifier.height(10.dp))
                            GroupRemote(
                                room = room,
                                busy = busy,
                                onKey = { key, label -> broadcast(room, key, label) },
                            )
                        }
                    }
                }
            }

            if (devices.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { activeRoom = if (activeRoom == ALL_ROOM) null else ALL_ROOM },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (activeRoom == ALL_ROOM) "Hide all-TVs remote" else "Broadcast to all TVs")
                }
                if (activeRoom == ALL_ROOM) {
                    Spacer(Modifier.height(10.dp))
                    GroupRemote(
                        room = ALL_ROOM,
                        busy = busy,
                        onKey = { key, label -> broadcast(ALL_ROOM, key, label) },
                    )
                }
            }

            status?.let {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    editing?.let { device ->
        RoomDialog(
            device = device,
            current = rooms[device.host].orEmpty(),
            onDismiss = { editing = null },
            onSave = { room ->
                scope.launch {
                    AppServices.prefs.saveDeviceRoom(device.host, room)
                    reload()
                    editing = null
                }
            },
        )
    }
}

private const val ALL_ROOM = "All TVs"

@Composable
private fun GroupRemote(room: String, busy: Boolean, onKey: (Int, String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteKey(Icons.Default.KeyboardArrowUp, "Up") { onKey(19, "Up") }
            RemoteKey(Icons.Default.KeyboardArrowDown, "Down") { onKey(20, "Down") }
            RemoteKey(Icons.Default.KeyboardArrowLeft, "Left") { onKey(21, "Left") }
            RemoteKey(Icons.Default.KeyboardArrowRight, "Right") { onKey(22, "Right") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteKey(Icons.Default.KeyboardArrowLeft, "Back") { onKey(4, "Back") }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(48.dp)
                    .clickable(enabled = !busy) { onKey(23, "OK") },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("OK", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
            RemoteKey(Icons.Default.Home, "Home") { onKey(3, "Home") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteKey(Icons.Default.VolumeDown, "Volume down") { onKey(25, "Vol −") }
            RemoteKey(Icons.Default.PlayArrow, "Play/pause") { onKey(85, "Play/pause") }
            RemoteKey(Icons.Default.VolumeUp, "Volume up") { onKey(24, "Vol +") }
        }
        if (busy) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Sending…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RemoteKey(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun RoomDialog(
    device: TvDevice,
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Room for ${device.name}") },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(30) },
                    label = { Text("Room name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ROOM_PRESETS.size) { index ->
                        val preset = ROOM_PRESETS[index]
                        AssistChip(
                            onClick = { value = preset },
                            label = { Text(preset) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatHostPort(host: String, port: Int): String =
    if (host.contains(':')) "[$host]:$port" else "$host:$port"
