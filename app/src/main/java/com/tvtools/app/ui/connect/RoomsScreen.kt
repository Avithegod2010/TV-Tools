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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tvtools.app.AppServices
import com.tvtools.app.R
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
    var roomOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    var editing by remember { mutableStateOf<TvDevice?>(null) }
    var activeRoom by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun reload() {
        devices = AppServices.prefs.loadDevices()
        rooms = AppServices.prefs.loadDeviceRooms()
        roomOrder = AppServices.prefs.loadRoomOrder()
    }

    LaunchedEffect(Unit) { reload() }

    fun roomOf(device: TvDevice): String = rooms[device.host]?.takeIf { it.isNotBlank() } ?: "Unassigned"

    fun moveRoom(room: String, delta: Int, ordered: List<String>) {
        val list = ordered.toMutableList()
        val index = list.indexOf(room)
        if (index < 0) return
        val target = (index + delta).coerceIn(0, list.lastIndex)
        if (target == index) return
        list.add(target, list.removeAt(index))
        roomOrder = list
        scope.launch { AppServices.prefs.saveRoomOrder(list) }
    }

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

    LabScaffold(stringResource(R.string.rooms_groups), onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                stringResource(R.string.group_your_tvs_by_room_the_remote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            if (devices.isEmpty()) {
                Text(
                    stringResource(R.string.no_saved_tvs_yet_connect_to_a),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            val grouped = devices.groupBy { roomOf(it) }
            val orderedRooms = roomOrder.filter { it in grouped } +
                grouped.keys.filter { it !in roomOrder }
            orderedRooms.forEachIndexed { roomIndex, room ->
                val list = grouped[room].orEmpty()
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
                                    pluralStringResource(R.plurals.tv_count, list.size, list.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = { moveRoom(room, -1, orderedRooms) },
                                enabled = roomIndex > 0,
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, "Move $room up", Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { moveRoom(room, 1, orderedRooms) },
                                enabled = roomIndex < orderedRooms.lastIndex,
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, "Move $room down", Modifier.size(16.dp))
                            }
                            OutlinedButton(onClick = { activeRoom = if (selected) null else room }) {
                                Text(if (selected) stringResource(R.string.hide_remote) else stringResource(R.string.group_remote))
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
                                        contentDescription = stringResource(R.string.assign_room),
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
                    Text(if (activeRoom == ALL_ROOM) stringResource(R.string.hide_all_tvs_remote) else stringResource(R.string.broadcast_to_all_tvs))
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
            RemoteKey(Icons.Default.KeyboardArrowUp, stringResource(R.string.up)) { onKey(19, "Up") }
            RemoteKey(Icons.Default.KeyboardArrowDown, stringResource(R.string.down)) { onKey(20, "Down") }
            RemoteKey(Icons.Default.KeyboardArrowLeft, stringResource(R.string.left)) { onKey(21, "Left") }
            RemoteKey(Icons.Default.KeyboardArrowRight, stringResource(R.string.right)) { onKey(22, "Right") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteKey(Icons.Default.KeyboardArrowLeft, stringResource(R.string.back)) { onKey(4, "Back") }
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
            RemoteKey(Icons.Default.Home, stringResource(R.string.home)) { onKey(3, "Home") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteKey(Icons.Default.VolumeDown, stringResource(R.string.volume_down)) { onKey(25, "Vol −") }
            RemoteKey(Icons.Default.PlayArrow, stringResource(R.string.play_pause)) { onKey(85, "Play/pause") }
            RemoteKey(Icons.Default.VolumeUp, stringResource(R.string.volume_up)) { onKey(24, "Vol +") }
        }
        if (busy) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.sending),
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
        title = { Text(stringResource(R.string.room_for_device, device.name)) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(30) },
                    label = { Text(stringResource(R.string.room_name)) },
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
            TextButton(onClick = { onSave(value) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun formatHostPort(host: String, port: Int): String =
    if (host.contains(':')) "[$host]:$port" else "$host:$port"
