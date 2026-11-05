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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteControlSheet(
    deviceName: String,
    onDismiss: () -> Unit,
    onOpenMirror: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val adb = AppServices.adb
    val settings by AppServices.settings.state.collectAsState()
    val skin = settings.remoteSkin

    var mode by remember(skin) {
        mutableStateOf(if (skin == com.tvtools.app.data.RemoteSkin.GAMEPAD) RemoteMode.GAMEPAD else RemoteMode.DPAD)
    }
    var showTextDialog by remember { mutableStateOf(false) }
    var showPowerMenu by remember { mutableStateOf(false) }
    var showNumberPad by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var powerMessage by remember { mutableStateOf<String?>(null) }
    val sleepState by com.tvtools.app.data.SleepTimer.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var ringing by remember { mutableStateOf(false) }

    // Macro recorder
    var macros by remember { mutableStateOf<List<com.tvtools.app.data.Macro>>(emptyList()) }
    var showMacros by remember { mutableStateOf(false) }
    var showSaveMacro by remember { mutableStateOf(false) }
    var macroRecording by remember { mutableStateOf(false) }
    var recordedSteps by remember { mutableStateOf<List<com.tvtools.app.data.MacroStep>>(emptyList()) }
    var lastStepAt by remember { mutableStateOf(0L) }
    var replaying by remember { mutableStateOf<String?>(null) }

    // Quick-key strip
    var quickKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    var showQuickKeyEditor by remember { mutableStateOf(false) }

    // Multi-TV broadcast
    var savedDevices by remember { mutableStateOf<List<com.tvtools.app.data.TvDevice>>(emptyList()) }
    var deviceRooms by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var broadcastSelection by remember { mutableStateOf<String?>(null) }
    var showBroadcastDialog by remember { mutableStateOf(false) }
    var broadcastBusy by remember { mutableStateOf(false) }
    val broadcastQueue = remember { kotlinx.coroutines.channels.Channel<Int>(64) }

    // Recent presses (session only) for the history / undo dialog.
    var keyHistory by remember { mutableStateOf<List<KeyPress>>(emptyList()) }
    var showHistory by remember { mutableStateOf(false) }

    // Which app is on the TV right now (drives the contextual shortcut row).
    var foregroundApp by remember { mutableStateOf<String?>(null) }
    var nowPlaying by remember { mutableStateOf<com.tvtools.app.data.NowPlaying?>(null) }

    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            if (broadcastSelection == null) {
                // Foreground app changes rarely; media sessions can start/stop any time.
                if (tick % 3 == 0) {
                    val pkg = runCatching { adb.foregroundPackage() }.getOrNull()
                    if (pkg != null) foregroundApp = pkg
                }
                nowPlaying = runCatching { adb.nowPlaying() }.getOrNull()
            }
            tick++
            delay(6_000)
        }
    }

    LaunchedEffect(Unit) {
        macros = AppServices.prefs.loadMacros()
        quickKeys = AppServices.prefs.loadQuickKeys()
            ?: com.tvtools.app.data.QuickKeys.defaultIds
        savedDevices = AppServices.prefs.loadDevices()
        deviceRooms = AppServices.prefs.loadDeviceRooms()
    }

    // Single worker so broadcast presses queue instead of fighting over the ADB socket.
    LaunchedEffect(Unit) {
        for (code in broadcastQueue) {
            val selection = broadcastSelection ?: continue
            val all = AppServices.prefs.loadDevices()
            val rooms = AppServices.prefs.loadDeviceRooms()
            savedDevices = all
            deviceRooms = rooms
            val targets = if (selection == ALL_BROADCAST) all
            else all.filter { rooms[it.host] == selection }
            if (targets.isEmpty()) continue

            broadcastBusy = true
            val origin = adb.connectedDevice
            if (origin != null) runCatching { adb.keyEvent(code) }
            for (device in targets) {
                if (origin != null && device.host == origin.host) continue
                if (adb.connect(device.host, device.port) == AdbClient.ConnectResult.Success) {
                    runCatching { adb.keyEvent(code) }
                }
            }
            if (origin != null) runCatching { adb.connect(origin.host, origin.port) }
            broadcastBusy = false
        }
    }

    fun send(code: Int) {
        if (macroRecording) {
            val now = System.currentTimeMillis()
            val gap = if (recordedSteps.isEmpty()) 0L else (now - lastStepAt).coerceIn(0, 10_000)
            recordedSteps = recordedSteps + com.tvtools.app.data.MacroStep(code, gap)
            lastStepAt = now
        }
        keyHistory = (keyHistory + KeyPress(code, keyLabel(code), System.currentTimeMillis()))
            .takeLast(30)
        if (broadcastSelection != null) {
            broadcastQueue.trySend(code)
            return
        }
        scope.launch { adb.keyEvent(code) }
    }

    fun undoLast() {
        val last = keyHistory.lastOrNull() ?: return
        val inverse = when (last.code) {
            KEY_VOL_UP -> KEY_VOL_DOWN
            KEY_VOL_DOWN -> KEY_VOL_UP
            KEY_CH_UP -> KEY_CH_DOWN
            KEY_CH_DOWN -> KEY_CH_UP
            KEY_MUTE -> KEY_MUTE
            KEY_MEDIA_PLAY_PAUSE -> KEY_MEDIA_PLAY_PAUSE
            else -> KEY_BACK
        }
        send(inverse)
    }

    /** Maps a physical controller to TV key codes. Returns true when handled. */
    fun gamepadToTv(event: android.view.KeyEvent): Boolean {
        val mapped = when (event.keyCode) {
            android.view.KeyEvent.KEYCODE_BUTTON_A -> KEY_DPAD_CENTER
            android.view.KeyEvent.KEYCODE_BUTTON_B -> KEY_BACK
            android.view.KeyEvent.KEYCODE_BUTTON_X -> KEY_MENU
            android.view.KeyEvent.KEYCODE_BUTTON_Y -> KEY_HOME
            android.view.KeyEvent.KEYCODE_BUTTON_L1 -> KEY_MEDIA_REWIND
            android.view.KeyEvent.KEYCODE_BUTTON_R1 -> KEY_MEDIA_FF
            android.view.KeyEvent.KEYCODE_BUTTON_L2 -> KEY_VOL_DOWN
            android.view.KeyEvent.KEYCODE_BUTTON_R2 -> KEY_VOL_UP
            android.view.KeyEvent.KEYCODE_BUTTON_START -> KEY_MEDIA_PLAY_PAUSE
            android.view.KeyEvent.KEYCODE_BUTTON_THUMBL -> KEY_MUTE
            android.view.KeyEvent.KEYCODE_DPAD_UP -> KEY_DPAD_UP
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> KEY_DPAD_DOWN
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> KEY_DPAD_LEFT
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> KEY_DPAD_RIGHT
            android.view.KeyEvent.KEYCODE_DPAD_CENTER -> KEY_DPAD_CENTER
            else -> return false
        }
        if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            send(mapped)
        }
        return true
    }

    fun replayMacro(macro: com.tvtools.app.data.Macro) {
        if (replaying != null) return
        replaying = macro.name
        scope.launch {
            for (step in macro.steps) {
                if (step.delayMs > 0) delay(step.delayMs)
                adb.keyEvent(step.keyCode)
            }
            replaying = null
        }
    }

    /** Push a generated beep to the TV and let its default player ring for 12 s. */
    fun ringTv() {
        if (ringing) return
        ringing = true
        scope.launch {
            val result = runCatching {
                val file = java.io.File(context.cacheDir, "tvtools_ring.wav")
                com.tvtools.app.util.BeepWav.write(file, 12)
                adb.pushFile(file, "/sdcard/Download/tvtools_ring.wav")
                adb.openFileOnTv("/sdcard/Download/tvtools_ring.wav", "audio/x-wav")
            }
            ringing = false
            powerMessage = result.fold(
                onSuccess = {
                    "Ringing the TV for 12 seconds. Turn the TV volume up if it's quiet."
                },
                onFailure = {
                    "Couldn't ring the TV: ${it.message}. No media player answered the beep."
                },
            )
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event -> gamepadToTv(event.nativeKeyEvent) }
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Remote",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        deviceName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { showMacros = true }) {
                    Icon(Icons.Default.Bolt, contentDescription = "Macros")
                }
                IconButton(onClick = { showTextDialog = true }) {
                    Icon(Icons.Default.Keyboard, contentDescription = "Send text")
                }
                IconButton(onClick = { scope.launch { adb.assistant() } }) {
                    Icon(Icons.Default.Mic, contentDescription = "Google Assistant on the TV")
                }
                IconButton(onClick = onOpenMirror) {
                    Icon(Icons.Default.Cast, contentDescription = "Screen mirroring")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Mode switch — replaced by the recorder bar while recording so the sheet height stays stable.
            if (macroRecording) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(MaterialTheme.colorScheme.error, CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Recording macro",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "${recordedSteps.size} key(s) — press the remote, then save",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { showSaveMacro = true }, enabled = recordedSteps.isNotEmpty()) {
                            Text("Save")
                        }
                        IconButton(onClick = {
                            macroRecording = false
                            recordedSteps = emptyList()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Discard recording")
                        }
                    }
                }
            } else {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = mode == RemoteMode.DPAD,
                        onClick = { mode = RemoteMode.DPAD },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4),
                    ) { Text("D-Pad") }
                    SegmentedButton(
                        selected = mode == RemoteMode.TOUCHPAD,
                        onClick = { mode = RemoteMode.TOUCHPAD },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4),
                    ) { Text("Touch") }
                    SegmentedButton(
                        selected = mode == RemoteMode.GAMEPAD,
                        onClick = { mode = RemoteMode.GAMEPAD },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4),
                    ) { Text("Game") }
                    SegmentedButton(
                        selected = mode == RemoteMode.GESTURE,
                        onClick = { mode = RemoteMode.GESTURE },
                        shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4),
                    ) { Text("Gest.") }
                }
            }

            Spacer(Modifier.height(12.dp))

            AnimatedContent(
                targetState = mode,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "remote-mode",
            ) { current ->
                when (current) {
                    RemoteMode.DPAD -> when (skin) {
                        com.tvtools.app.data.RemoteSkin.CLASSIC -> ClassicPad(onSend = ::send)
                        com.tvtools.app.data.RemoteSkin.MINIMAL -> MinimalPad(onSend = ::send)
                        else -> DPadPad(onSend = ::send)
                    }
                    RemoteMode.TOUCHPAD -> TouchPad(onSend = ::send)
                    RemoteMode.GAMEPAD -> GamepadPad(onSend = ::send)
                    RemoteMode.GESTURE -> GestureMode(onSend = ::send)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Navigation + channel row
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavPill(Icons.AutoMirrored.Filled.ArrowBack, "Back") { send(KEY_BACK) }
                NavPill(Icons.Default.Home, "Home") { send(KEY_HOME) }
                NavPill(Icons.Default.Menu, "Menu") { send(KEY_MENU) }
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = { send(KEY_CH_DOWN) }) { Text("CH−") }
                TextButton(onClick = { send(KEY_CH_UP) }) { Text("CH+") }
                TextButton(onClick = { showNumberPad = true }) { Text("0–9") }
            }

            // Quick keys + broadcast target
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                item {
                    FilterChip(
                        selected = broadcastSelection != null,
                        onClick = { showBroadcastDialog = true },
                        label = { Text(if (broadcastBusy) "Sending…" else "Broadcast") },
                        leadingIcon = { Icon(Icons.Default.Groups, null, Modifier.size(14.dp)) },
                    )
                }
                item {
                    AssistChip(
                        onClick = { showHistory = true },
                        label = { Text("History") },
                        leadingIcon = { Icon(Icons.Default.History, null, Modifier.size(14.dp)) },
                    )
                }
                items(quickKeys.size) { index ->
                    val key = com.tvtools.app.data.QuickKeys.byId(quickKeys[index])
                    if (key != null) {
                        AssistChip(onClick = { send(key.keyCode) }, label = { Text(key.label) })
                    }
                }
                item {
                    AssistChip(
                        onClick = { showQuickKeyEditor = true },
                        label = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(14.dp)) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Media controls
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    val track = nowPlaying
                    if (track != null && (track.isPlaying || track.durationMs > 0)) {
                        NowPlayingBlock(
                            track = track,
                            onSeek = { delta -> scope.launch { adb.seekRelative(delta) } },
                            onPlayPause = { send(KEY_MEDIA_PLAY_PAUSE) },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MediaButton(Icons.Default.SkipPrevious, "Previous") { send(KEY_MEDIA_PREV) }
                        MediaButton(Icons.Default.FastRewind, "Rewind") { send(KEY_MEDIA_REWIND) }
                        MediaButton(Icons.Default.PlayArrow, "Play/Pause") { send(KEY_MEDIA_PLAY_PAUSE) }
                        MediaButton(Icons.Default.FastForward, "Fast forward") { send(KEY_MEDIA_FF) }
                        MediaButton(Icons.Default.SkipNext, "Next") { send(KEY_MEDIA_NEXT) }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Contextual shortcuts for the app currently on the TV
            appSpecificKeys(foregroundApp)?.let { extras ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            "${extras.label} shortcuts",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            extras.keys.forEach { (label, code) ->
                                AssistChip(onClick = { send(code) }, label = { Text(label) })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Volume + power
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CircleKey(Icons.AutoMirrored.Filled.VolumeDown, "Volume down", repeatable = true) { send(KEY_VOL_DOWN) }
                CircleKey(Icons.Default.VolumeOff, "Mute") { send(KEY_MUTE) }
                CircleKey(Icons.AutoMirrored.Filled.VolumeUp, "Volume up", repeatable = true) { send(KEY_VOL_UP) }
                Spacer(Modifier.width(6.dp))
                CircleKey(
                    Icons.Default.PowerSettingsNew,
                    "Power menu",
                    container = MaterialTheme.colorScheme.errorContainer,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                ) { showPowerMenu = true }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showTextDialog) {
        LiveKeyboardDialog(
            onDismiss = { showTextDialog = false },
            onText = { chunk -> scope.launch { adb.inputText(chunk) } },
            onKey = { code -> send(code) },
            onCombo = { codes -> scope.launch { adb.inputKeyCombo(*codes.toIntArray()) } },
        )
    }

    if (showNumberPad) {
        NumberPadDialog(
            onDismiss = { showNumberPad = false },
            onKey = { code -> send(code) },
        )
    }

    if (showPowerMenu) {
        PowerMenuDialog(
            deviceName = deviceName,
            sleepMinutesLeft = sleepState?.let {
                ((it.remainingMs() + 59_999) / 60_000).toInt().coerceAtLeast(1)
            },
            ringing = ringing,
            onSleepTimer = { showPowerMenu = false; showSleepTimer = true },
            onRing = { showPowerMenu = false; ringTv() },
            onDismiss = { showPowerMenu = false },
            onSleep = { showPowerMenu = false; send(KEY_SLEEP) },
            onPowerToggle = { showPowerMenu = false; send(KEY_POWER) },
            onWake = {
                showPowerMenu = false
                scope.launch {
                    val mac = AppServices.prefs.loadLastMac()
                    powerMessage = if (mac == null) {
                        "No MAC saved yet — connect to the TV once first."
                    } else if (Wol.send(mac)) {
                        "Wake packet sent ✓"
                    } else {
                        "Couldn't send the wake packet."
                    }
                }
            },
            onRestart = {
                showPowerMenu = false
                scope.launch {
                    powerMessage = try {
                        val out = adb.rebootTv().trim()
                        if (out.isBlank()) "Restart command sent" else out
                    } catch (e: Exception) {
                        "Restart failed: ${e.message}"
                    }
                }
            },
            onAmbient = {
                showPowerMenu = false
                scope.launch { powerMessage = com.tvtools.app.ui.labs.ambientStart(adb) }
            },
            onTimeout = {
                showPowerMenu = false
                scope.launch {
                    powerMessage = com.tvtools.app.ui.labs.setScreenTimeout(adb, 30 * 60 * 1000)
                }
            },
        )
    }

    powerMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { powerMessage = null },
            confirmButton = { TextButton(onClick = { powerMessage = null }) { Text("OK") } },
            text = { Text(msg) },
        )
    }

    if (showHistory) {
        KeyHistoryDialog(
            history = keyHistory,
            onUndo = {
                undoLast()
                showHistory = false
            },
            onClear = {
                keyHistory = emptyList()
                showHistory = false
            },
            onSendAgain = { press ->
                send(press.code)
                showHistory = false
            },
            onDismiss = { showHistory = false },
        )
    }

    if (showBroadcastDialog) {
        BroadcastDialog(
            devices = savedDevices,
            rooms = deviceRooms,
            selected = broadcastSelection,
            onSelect = { selection ->
                broadcastSelection = selection
                showBroadcastDialog = false
            },
            onDismiss = { showBroadcastDialog = false },
        )
    }

    if (showQuickKeyEditor) {
        QuickKeyEditorDialog(
            selected = quickKeys,
            onDismiss = { showQuickKeyEditor = false },
            onSave = { ids ->
                quickKeys = ids
                scope.launch { AppServices.prefs.saveQuickKeys(ids) }
                showQuickKeyEditor = false
            },
        )
    }

    if (showSleepTimer) {
        SleepTimerDialog(
            activeMinutesLeft = sleepState?.let {
                ((it.remainingMs() + 59_999) / 60_000).toInt().coerceAtLeast(1)
            },
            onStart = { minutes, fade ->
                com.tvtools.app.data.SleepTimer.start(minutes, if (fade) 20 else 0)
                showSleepTimer = false
            },
            onCancelTimer = {
                com.tvtools.app.data.SleepTimer.cancel()
                showSleepTimer = false
            },
            onDismiss = { showSleepTimer = false },
        )
    }

    if (showMacros) {
        MacroDialog(
            macros = macros,
            replaying = replaying,
            onReplay = { macro -> replayMacro(macro) },
            onDelete = { macro ->
                scope.launch {
                    AppServices.prefs.deleteMacro(macro.name)
                    macros = AppServices.prefs.loadMacros()
                }
            },
            onRecord = {
                showMacros = false
                recordedSteps = emptyList()
                lastStepAt = 0
                macroRecording = true
            },
            onDismiss = { showMacros = false },
        )
    }

    if (showSaveMacro) {
        SaveMacroDialog(
            defaultName = "Macro ${macros.size + 1}",
            stepCount = recordedSteps.size,
            onDismiss = { showSaveMacro = false },
            onSave = { name ->
                val macro = com.tvtools.app.data.Macro(name, recordedSteps)
                scope.launch {
                    AppServices.prefs.saveMacro(macro)
                    macros = AppServices.prefs.loadMacros()
                    showSaveMacro = false
                    macroRecording = false
                    recordedSteps = emptyList()
                    lastStepAt = 0
                    powerMessage = "Saved \"${macro.name}\" — replay it from the bolt icon."
                }
            },
        )
    }
}
