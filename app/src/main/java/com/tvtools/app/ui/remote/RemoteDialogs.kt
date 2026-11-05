package com.tvtools.app.ui.remote

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvtools.app.AppServices
import com.tvtools.app.adb.AdbClient
import com.tvtools.app.ui.common.rememberHaptics
import com.tvtools.app.util.Wol
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

// ---------------- Remote dialogs and helpers ----------------
// ---------------- Number pad ----------------

@Composable
internal fun NumberPadDialog(onDismiss: () -> Unit, onKey: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Channel / numbers") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(-1, 0, -2)).forEach { row ->
                    Row {
                        row.forEach { digit ->
                            when (digit) {
                                -1 -> Spacer(Modifier.size(64.dp))
                                -2 -> TextButton(onClick = { onKey(23) }) { Text("OK") }
                                else -> TextButton(onClick = { onKey(7 + digit) }) { Text("$digit", fontSize = 20.sp) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
@Composable
internal fun LiveKeyboardDialog(
    onDismiss: () -> Unit,
    onText: (String) -> Unit,
    onKey: (Int) -> Unit,
    onCombo: (List<Int>) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    var voiceError by remember { mutableStateOf<String?>(null) }

    val voiceLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
        if (result.resultCode == android.app.Activity.RESULT_OK && !spoken.isNullOrBlank()) {
            val chunk = if (text.isBlank()) spoken else " $spoken"
            text += chunk
            onText(chunk)
            voiceError = null
        } else if (result.resultCode == android.app.Activity.RESULT_OK) {
            voiceError = "No speech recognized — try again."
        }
    }

    fun startVoice() {
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak — text goes to the TV")
        }
        try {
            voiceLauncher.launch(intent)
        } catch (_: Exception) {
            voiceError = "No speech recognition app on this phone."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Live keyboard") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { next ->
                        val old = text
                        when {
                            next.length > old.length && next.startsWith(old) -> {
                                onText(next.substring(old.length))
                            }
                            next.length < old.length && old.startsWith(next) -> {
                                repeat(old.length - next.length) { onKey(67) } // DEL
                            }
                            next != old -> {
                                repeat(old.length) { onKey(67) }
                                onText(next)
                            }
                        }
                        text = next
                    },
                    label = { Text("Type — sent as you type") },
                    placeholder = { Text("Focus a search box on the TV first") },
                    singleLine = false,
                    maxLines = 3,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { startVoice() },
                        label = { Text("Speak to type") },
                        leadingIcon = { Icon(Icons.Default.Mic, null, Modifier.size(16.dp)) },
                    )
                    Text(
                        "Uses the phone's speech recognizer, then sends the text.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                voiceError?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = ctrl, onClick = { ctrl = !ctrl }, label = { Text("Ctrl") })
                    FilterChip(selected = alt, onClick = { alt = !alt }, label = { Text("Alt") })
                    AssistChip(onClick = { onKey(111) }, label = { Text("Esc") })
                    AssistChip(onClick = { onKey(61) }, label = { Text("Tab") })
                    AssistChip(onClick = { onKey(66) }, label = { Text("Enter") })
                    AssistChip(onClick = { onKey(67) }, label = { Text("Del") })
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = { onKey(19) }, label = { Text("↑") })
                    AssistChip(onClick = { onKey(20) }, label = { Text("↓") })
                    AssistChip(onClick = { onKey(21) }, label = { Text("←") })
                    AssistChip(onClick = { onKey(22) }, label = { Text("→") })
                    AssistChip(onClick = { onKey(KEY_DPAD_CENTER) }, label = { Text("OK") })
                    AssistChip(onClick = { onKey(KEY_BACK) }, label = { Text("Back") })
                }

                if (ctrl || alt) {
                    Text(
                        if (ctrl) "Ctrl + letter" else "Alt + letter",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(26) { index ->
                            val letter = ('A' + index)
                            AssistChip(
                                onClick = {
                                    val meta = if (ctrl) 113 else 57 // META_CTRL_LEFT / META_ALT_LEFT
                                    onCombo(listOf(meta, 29 + index))
                                },
                                label = { Text("$letter") },
                            )
                        }
                    }
                }

                Text(
                    "Emoji",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(EMOJI.size) { index ->
                        AssistChip(onClick = { onText(EMOJI[index]) }, label = { Text(EMOJI[index]) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

internal val EMOJI = listOf("😀", "😂", "❤️", "👍", "🔥", "🎉", "😮", "😢", "🙏", "✨")

internal data class KeyPress(val code: Int, val label: String, val at: Long)

internal fun keyLabel(code: Int): String = when (code) {
    KEY_HOME -> "Home"
    KEY_BACK -> "Back"
    KEY_DPAD_UP -> "Up"
    KEY_DPAD_DOWN -> "Down"
    KEY_DPAD_LEFT -> "Left"
    KEY_DPAD_RIGHT -> "Right"
    KEY_DPAD_CENTER -> "OK"
    KEY_VOL_UP -> "Volume +"
    KEY_VOL_DOWN -> "Volume −"
    KEY_MUTE -> "Mute"
    KEY_MENU -> "Menu"
    KEY_MEDIA_PLAY_PAUSE -> "Play/pause"
    KEY_MEDIA_NEXT -> "Next"
    KEY_MEDIA_PREV -> "Previous"
    KEY_MEDIA_REWIND -> "Rewind"
    KEY_MEDIA_FF -> "Fast forward"
    KEY_CH_UP -> "Channel +"
    KEY_CH_DOWN -> "Channel −"
    KEY_POWER -> "Power"
    KEY_SLEEP -> "Sleep"
    KEY_WAKE -> "Wake"
    84 -> "Search"
    86 -> "Stop"
    165 -> "Info"
    175 -> "Subtitles"
    176 -> "Settings"
    178 -> "TV input"
    219 -> "Assistant"
    else -> com.tvtools.app.data.QuickKeys.catalog
        .firstOrNull { it.keyCode == code }?.label ?: "Key $code"
}

@Composable
internal fun KeyHistoryDialog(
    history: List<KeyPress>,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onSendAgain: (KeyPress) -> Unit,
    onDismiss: () -> Unit,
) {
    val formatter = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Key history") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Session only. Undo sends a sensible inverse: volume and channel flip, mute and play toggle, everything else goes back.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (history.isEmpty()) {
                    Text("Nothing pressed yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(
                        Modifier
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        history.asReversed().forEach { press ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSendAgain(press) }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    press.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    formatter.format(java.util.Date(press.at)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUndo, enabled = history.isNotEmpty()) { Text("Undo last") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear, enabled = history.isNotEmpty()) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

internal const val ALL_BROADCAST = "All TVs"

@Composable
internal fun BroadcastDialog(
    devices: List<com.tvtools.app.data.TvDevice>,
    rooms: Map<String, String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val roomNames = rooms.values.filter { it.isNotBlank() }.distinct().sorted()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Broadcast keys") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Every key press is also sent to the TVs in the chosen group. " +
                        "ADB allows one connection at a time, so presses arrive a moment apart.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                BroadcastRow("Off (this TV only)", selected == null) { onSelect(null) }
                if (devices.isNotEmpty()) {
                    BroadcastRow("All TVs (${devices.size})", selected == ALL_BROADCAST) {
                        onSelect(ALL_BROADCAST)
                    }
                }
                roomNames.forEach { room ->
                    val count = devices.count { rooms[it.host] == room }
                    BroadcastRow("$room ($count)", selected == room) { onSelect(room) }
                }
                if (devices.isEmpty()) {
                    Text(
                        "No saved TVs yet — connect to another TV first.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
internal fun BroadcastRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun QuickKeyEditorDialog(
    selected: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    var list by remember { mutableStateOf(selected) }
    var showAdd by remember { mutableStateOf(false) }
    val available = com.tvtools.app.data.QuickKeys.catalog.filter { it.id !in list }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick keys") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "These buttons sit under the remote. Reorder with the arrows, or remove the ones you never use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (list.isEmpty()) {
                    Text(
                        "No quick keys yet — add one below.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Column(
                    Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    list.forEachIndexed { index, id ->
                        val key = com.tvtools.app.data.QuickKeys.byId(id)
                        if (key != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        key.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                                    )
                                    IconButton(
                                        onClick = { list = swapQuickKey(list, index, index - 1) },
                                        enabled = index > 0,
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Move up",
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    IconButton(
                                        onClick = { list = swapQuickKey(list, index, index + 1) },
                                        enabled = index < list.lastIndex,
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Move down",
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    IconButton(onClick = {
                                        list = list.filterIndexed { i, _ -> i != index }
                                    }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove ${key.label}",
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Box {
                    OutlinedButton(
                        onClick = { showAdd = true },
                        enabled = available.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add key")
                    }
                    DropdownMenu(expanded = showAdd, onDismissRequest = { showAdd = false }) {
                        available.forEach { key ->
                            DropdownMenuItem(
                                text = { Text(key.label) },
                                onClick = {
                                    list = list + key.id
                                    showAdd = false
                                },
                            )
                        }
                    }
                }
                TextButton(onClick = { list = com.tvtools.app.data.QuickKeys.defaultIds }) {
                    Text("Reset to default")
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(list) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

internal fun swapQuickKey(list: List<String>, from: Int, to: Int): List<String> {
    if (from !in list.indices || to !in list.indices) return list
    return list.toMutableList().also {
        val tmp = it[from]
        it[from] = it[to]
        it[to] = tmp
    }
}

@Composable
internal fun NowPlayingBlock(
    track: com.tvtools.app.data.NowPlaying,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (track.artist.isNotBlank()) {
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                if (track.isPlaying) "Playing" else "Paused",
                style = MaterialTheme.typography.labelSmall,
                color = if (track.isPlaying) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val duration = track.durationMs
        if (duration > 0) {
            val position = track.currentPositionMs().coerceIn(0, duration)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { position.toFloat() / duration.toFloat() },
                modifier = Modifier.fillMaxWidth().height(4.dp),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(
                    formatPlayback(position),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    formatPlayback(duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { onSeek(-10_000) },
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) { Text("−10s") }
            TextButton(
                onClick = { onSeek(10_000) },
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) { Text("+10s") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onPlayPause) {
                Text(if (track.isPlaying) "Pause" else "Play")
            }
        }
    }
}

internal fun formatPlayback(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}


/** Key codes: 84 = SEARCH, 86 = STOP, 87/88 = next/previous, 165 = INFO, 175 = CAPTIONS. */
internal data class AppShortcuts(val label: String, val keys: List<Pair<String, Int>>)

internal fun appSpecificKeys(packageName: String?): AppShortcuts? {
    val pkg = packageName?.lowercase() ?: return null
    return when {
        "youtube" in pkg -> AppShortcuts(
            "YouTube",
            listOf("Search" to 84, "Stats" to 165, "Subtitles" to 175, "Stop" to 86),
        )
        "netflix" in pkg -> AppShortcuts(
            "Netflix",
            listOf("Search" to 84, "Play/pause" to 85, "Stop" to 86),
        )
        "kodi" in pkg -> AppShortcuts(
            "Kodi",
            listOf("Info" to 165, "Subtitles" to 175, "Stop" to 86, "Search" to 84),
        )
        "vlc" in pkg -> AppShortcuts(
            "VLC",
            listOf("Info" to 165, "Subtitles" to 175, "Stop" to 86),
        )
        "disney" in pkg -> AppShortcuts("Disney+", listOf("Search" to 84, "Stop" to 86))
        "amazonvideo" in pkg || "primevideo" in pkg -> AppShortcuts(
            "Prime Video",
            listOf("Search" to 84, "Stop" to 86),
        )
        "spotify" in pkg -> AppShortcuts(
            "Spotify",
            listOf("Search" to 84, "Previous" to 88, "Next" to 87),
        )
        "plex" in pkg -> AppShortcuts("Plex", listOf("Info" to 165, "Stop" to 86))
        "twitch" in pkg -> AppShortcuts("Twitch", listOf("Search" to 84))
        else -> null
    }
}


@Composable
internal fun MacroDialog(
    macros: List<com.tvtools.app.data.Macro>,
    replaying: String?,
    onReplay: (com.tvtools.app.data.Macro) -> Unit,
    onDelete: (com.tvtools.app.data.Macro) -> Unit,
    onRecord: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf<com.tvtools.app.data.Macro?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Macros") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Record a few presses — open an app, start an episode — then replay them with one tap.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (macros.isEmpty()) {
                    Text(
                        "No macros yet. Tap \"Record new\", press keys on the remote, then save.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        macros.forEach { macro ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            macro.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            macro.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (replaying == macro.name) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(12.dp))
                                    } else {
                                        IconButton(onClick = { onReplay(macro) }) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = "Replay ${macro.name}",
                                            )
                                        }
                                    }
                                    IconButton(onClick = { confirmDelete = macro }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete ${macro.name}",
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Text(
                    "Pauses between presses are kept, so menus have time to open.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onRecord, enabled = replaying == null) { Text("Record new") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )

    confirmDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete \"${target.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target)
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun SaveMacroDialog(
    defaultName: String,
    stepCount: Int,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save macro") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Name") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "$stepCount key press(es) recorded, including the pauses between them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}


@Composable
internal fun PowerMenuDialog(
    deviceName: String,
    sleepMinutesLeft: Int?,
    ringing: Boolean,
    onDismiss: () -> Unit,
    onSleep: () -> Unit,
    onSleepTimer: () -> Unit,
    onRing: () -> Unit,
    onPowerToggle: () -> Unit,
    onWake: () -> Unit,
    onRestart: () -> Unit,
    onAmbient: () -> Unit,
    onTimeout: () -> Unit,
) {
    var confirmRestart by remember { mutableStateOf(false) }
    if (!confirmRestart) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Power — $deviceName") },
            text = {
                Column {
                    PowerRow("Turn screen off (sleep)") { onSleep() }
                    PowerRow(
                        if (sleepMinutesLeft != null) "Sleep timer — ${sleepMinutesLeft}m left"
                        else "Sleep timer…"
                    ) { onSleepTimer() }
                    PowerRow(if (ringing) "Ringing the TV…" else "Ring TV (find my remote)") { onRing() }
                    PowerRow("Power button (toggle)") { onPowerToggle() }
                    PowerRow("Wake with Wake-on-LAN") { onWake() }
                    PowerRow("Ambient / screensaver mode") { onAmbient() }
                    PowerRow("Screen timeout → 30 min") { onTimeout() }
                    PowerRow("Restart TV") { confirmRestart = true }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        )
    } else {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text("Restart TV?") },
            text = { Text("This asks the TV to reboot. Some TVs only allow this when rooted.") },
            confirmButton = { Button(onClick = onRestart) { Text("Restart") } },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun SleepTimerDialog(
    activeMinutesLeft: Int?,
    onStart: (minutes: Int, fade: Boolean) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    val presets = listOf(15, 30, 45, 60, 90)
    var minutes by remember { mutableStateOf(30) }
    var fade by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Fades the volume over the last 20 seconds, then puts the TV to sleep.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (activeMinutesLeft != null) {
                    Text(
                        "Timer running — sleeping in about $activeMinutesLeft min.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presets.size) { index ->
                        val preset = presets[index]
                        FilterChip(
                            selected = minutes == preset,
                            onClick = { minutes = preset },
                            label = { Text("$preset min") },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Fade volume out", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Ramps down from the TV's current level",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = fade, onCheckedChange = { fade = it })
                }
                if (activeMinutesLeft != null) {
                    TextButton(onClick = onCancelTimer) { Text("Cancel running timer") }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onStart(minutes, fade) }) {
                Text(if (activeMinutesLeft != null) "Restart timer" else "Start")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
internal fun PowerRow(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
    }
}

/** Small loading overlay used when switching modes. */
@Composable
internal fun RemoteLoading() {
    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
