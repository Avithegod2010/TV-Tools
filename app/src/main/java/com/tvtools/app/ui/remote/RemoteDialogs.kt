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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvtools.app.AppServices
import com.tvtools.app.R
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
        title = { Text(stringResource(R.string.channel_numbers)) },
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
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
        title = { Text(stringResource(R.string.live_keyboard)) },
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
                    label = { Text(stringResource(R.string.type_sent_as_you_type)) },
                    placeholder = { Text(stringResource(R.string.focus_a_search_box_on_the_tv)) },
                    singleLine = false,
                    maxLines = 3,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { startVoice() },
                        label = { Text(stringResource(R.string.speak_to_type)) },
                        leadingIcon = { Icon(Icons.Default.Mic, null, Modifier.size(16.dp)) },
                    )
                    Text(
                        stringResource(R.string.uses_the_phones_speech_recognizer_then_sends),
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
                    AssistChip(onClick = { onKey(KEY_BACK) }, label = { Text(stringResource(R.string.back)) })
                }

                if (ctrl || alt) {
                    Text(
                        if (ctrl) stringResource(R.string.ctrl_letter) else stringResource(R.string.alt_letter),
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
                    stringResource(R.string.emoji),
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

internal val EMOJI = listOf("😀", "😂", "❤️", "👍", "🔥", "🎉", "😮", "😢", "🙏", "✨")

internal data class KeyPress(val code: Int, val label: String, val at: Long)

internal fun keyLabel(context: android.content.Context, code: Int): String = when (code) {
    KEY_HOME -> context.getString(R.string.home)
    KEY_BACK -> context.getString(R.string.back)
    KEY_DPAD_UP -> context.getString(R.string.up)
    KEY_DPAD_DOWN -> context.getString(R.string.down)
    KEY_DPAD_LEFT -> context.getString(R.string.left)
    KEY_DPAD_RIGHT -> context.getString(R.string.right)
    KEY_DPAD_CENTER -> context.getString(R.string.ok)
    KEY_VOL_UP -> context.getString(R.string.volume)
    KEY_VOL_DOWN -> context.getString(R.string.volume_2)
    KEY_MUTE -> context.getString(R.string.mute)
    KEY_MENU -> context.getString(R.string.menu)
    KEY_MEDIA_PLAY_PAUSE -> context.getString(R.string.play_pause)
    KEY_MEDIA_NEXT -> context.getString(R.string.media_next_action)
    KEY_MEDIA_PREV -> context.getString(R.string.previous)
    KEY_MEDIA_REWIND -> context.getString(R.string.rewind)
    KEY_MEDIA_FF -> context.getString(R.string.fast_forward)
    KEY_CH_UP -> context.getString(R.string.channel)
    KEY_CH_DOWN -> context.getString(R.string.channel_2)
    KEY_POWER -> context.getString(R.string.power)
    KEY_SLEEP -> context.getString(R.string.sleep)
    KEY_WAKE -> context.getString(R.string.wake)
    84 -> context.getString(R.string.search)
    86 -> context.getString(R.string.stop)
    165 -> context.getString(R.string.info)
    175 -> context.getString(R.string.subtitles)
    176 -> context.getString(R.string.settings)
    178 -> context.getString(R.string.tv_input)
    219 -> context.getString(R.string.assistant)
    else -> com.tvtools.app.data.QuickKeys.catalog
        .firstOrNull { it.keyCode == code }?.let { context.getString(it.label) } ?: context.getString(R.string.unknown_remote_key, code)
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
        title = { Text(stringResource(R.string.key_history)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.session_only_undo_sends_a_sensible_inverse),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (history.isEmpty()) {
                    Text(stringResource(R.string.nothing_pressed_yet), style = MaterialTheme.typography.bodySmall)
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
            TextButton(onClick = onUndo, enabled = history.isNotEmpty()) { Text(stringResource(R.string.undo_last)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear, enabled = history.isNotEmpty()) { Text(stringResource(R.string.clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
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
        title = { Text(stringResource(R.string.broadcast_keys)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.every_key_press_is_also_sent_to),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                BroadcastRow(stringResource(R.string.off_this_tv_only), selected == null) { onSelect(null) }
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
                        stringResource(R.string.no_saved_tvs_yet_connect_to_another),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
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
        title = { Text(stringResource(R.string.quick_keys)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.these_buttons_sit_under_the_remote_reorder),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (list.isEmpty()) {
                    Text(
                        stringResource(R.string.no_quick_keys_yet_add_one_below),
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
                                        stringResource(key.label),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                                    )
                                    IconButton(
                                        onClick = { list = swapQuickKey(list, index, index - 1) },
                                        enabled = index > 0,
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowUp,
                                            contentDescription = stringResource(R.string.move_up),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    IconButton(
                                        onClick = { list = swapQuickKey(list, index, index + 1) },
                                        enabled = index < list.lastIndex,
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = stringResource(R.string.move_down),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    IconButton(onClick = {
                                        list = list.filterIndexed { i, _ -> i != index }
                                    }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(R.string.remove_quick_key, stringResource(key.label)),
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
                        Text(stringResource(R.string.add_key))
                    }
                    DropdownMenu(expanded = showAdd, onDismissRequest = { showAdd = false }) {
                        available.forEach { key ->
                            DropdownMenuItem(
                                text = { Text(stringResource(key.label)) },
                                onClick = {
                                    list = list + key.id
                                    showAdd = false
                                },
                            )
                        }
                    }
                }
                TextButton(onClick = { list = com.tvtools.app.data.QuickKeys.defaultIds }) {
                    Text(stringResource(R.string.reset_to_default))
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(list) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
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
                if (track.isPlaying) stringResource(R.string.playing) else stringResource(R.string.paused),
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
                Text(if (track.isPlaying) stringResource(R.string.pause) else stringResource(R.string.play))
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
internal data class AppShortcuts(val label: String, val keys: List<Pair<Int, Int>>)

internal fun appSpecificKeys(packageName: String?): AppShortcuts? {
    val pkg = packageName?.lowercase() ?: return null
    return when {
        "youtube" in pkg -> AppShortcuts(
            "YouTube",
            listOf(R.string.search to 84, R.string.stats to 165, R.string.subtitles to 175, R.string.stop to 86),
        )
        "netflix" in pkg -> AppShortcuts(
            "Netflix",
            listOf(R.string.search to 84, R.string.play_pause to 85, R.string.stop to 86),
        )
        "kodi" in pkg -> AppShortcuts(
            "Kodi",
            listOf(R.string.info to 165, R.string.subtitles to 175, R.string.stop to 86, R.string.search to 84),
        )
        "vlc" in pkg -> AppShortcuts(
            "VLC",
            listOf(R.string.info to 165, R.string.subtitles to 175, R.string.stop to 86),
        )
        "disney" in pkg -> AppShortcuts("Disney+", listOf(R.string.search to 84, R.string.stop to 86))
        "amazonvideo" in pkg || "primevideo" in pkg -> AppShortcuts(
            "Prime Video",
            listOf(R.string.search to 84, R.string.stop to 86),
        )
        "spotify" in pkg -> AppShortcuts(
            "Spotify",
            listOf(R.string.search to 84, R.string.previous to 88, R.string.media_next_action to 87),
        )
        "plex" in pkg -> AppShortcuts("Plex", listOf(R.string.info to 165, R.string.stop to 86))
        "twitch" in pkg -> AppShortcuts("Twitch", listOf(R.string.search to 84))
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
        title = { Text(stringResource(R.string.macros)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.record_a_few_presses_open_an_app),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (macros.isEmpty()) {
                    Text(
                        stringResource(R.string.no_macros_yet_tap_record_new_press),
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
                                                contentDescription = stringResource(R.string.replay_named_macro, macro.name),
                                            )
                                        }
                                    }
                                    IconButton(onClick = { confirmDelete = macro }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.delete_named_macro, macro.name),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.pauses_between_presses_are_kept_so_menus),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onRecord, enabled = replaying == null) { Text(stringResource(R.string.record_new)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )

    confirmDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.delete_macro_question, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target)
                    confirmDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
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
        title = { Text(stringResource(R.string.save_macro)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    pluralStringResource(R.plurals.macro_saved_count, stepCount, stepCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
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
            title = { Text(stringResource(R.string.power_for_device, deviceName)) },
            text = {
                Column {
                    PowerRow(stringResource(R.string.turn_screen_off_sleep)) { onSleep() }
                    PowerRow(
                        if (sleepMinutesLeft != null) stringResource(R.string.sleep_timer_remaining, sleepMinutesLeft)
                        else stringResource(R.string.sleep_timer_2)
                    ) { onSleepTimer() }
                    PowerRow(if (ringing) stringResource(R.string.ringing_the_tv) else stringResource(R.string.ring_tv_find_my_remote)) { onRing() }
                    PowerRow(stringResource(R.string.power_button_toggle)) { onPowerToggle() }
                    PowerRow(stringResource(R.string.wake_with_wake_on_lan)) { onWake() }
                    PowerRow(stringResource(R.string.ambient_screensaver_mode)) { onAmbient() }
                    PowerRow(stringResource(R.string.screen_timeout_30_min)) { onTimeout() }
                    PowerRow(stringResource(R.string.restart_tv_2)) { confirmRestart = true }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        )
    } else {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text(stringResource(R.string.restart_tv)) },
            text = { Text(stringResource(R.string.this_asks_the_tv_to_reboot_some)) },
            confirmButton = { Button(onClick = onRestart) { Text(stringResource(R.string.restart)) } },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text(stringResource(R.string.cancel)) } },
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
        title = { Text(stringResource(R.string.sleep_timer)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.fades_the_volume_over_the_last_20),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (activeMinutesLeft != null) {
                    Text(
                        stringResource(R.string.sleep_timer_running, activeMinutesLeft),
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
                            label = { Text(stringResource(R.string.duration_minutes, preset)) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.fade_volume_out), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.ramps_down_from_the_tvs_current_level),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = fade, onCheckedChange = { fade = it })
                }
                if (activeMinutesLeft != null) {
                    TextButton(onClick = onCancelTimer) { Text(stringResource(R.string.cancel_running_timer)) }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onStart(minutes, fade) }) {
                Text(if (activeMinutesLeft != null) stringResource(R.string.restart_timer) else stringResource(R.string.start))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
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
