package com.tvtools.app.ui.remote

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvtools.app.AppServices
import com.tvtools.app.ui.common.rememberHaptics
import com.tvtools.app.util.Wol
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

// Android KEYCODEs
private const val KEY_HOME = 3
private const val KEY_BACK = 4
private const val KEY_DPAD_UP = 19
private const val KEY_DPAD_DOWN = 20
private const val KEY_DPAD_LEFT = 21
private const val KEY_DPAD_RIGHT = 22
private const val KEY_DPAD_CENTER = 23
private const val KEY_VOL_UP = 24
private const val KEY_VOL_DOWN = 25
private const val KEY_POWER = 26
private const val KEY_MENU = 82
private const val KEY_MEDIA_PLAY_PAUSE = 85
private const val KEY_MEDIA_NEXT = 87
private const val KEY_MEDIA_PREV = 88
private const val KEY_MEDIA_REWIND = 89
private const val KEY_MEDIA_FF = 90
private const val KEY_SLEEP = 223
private const val KEY_WAKE = 224
private const val KEY_MUTE = 164
private const val KEY_CH_UP = 166
private const val KEY_CH_DOWN = 167
private const val KEY_GAMEPAD_A = 96
private const val KEY_GAMEPAD_B = 97
private const val KEY_GAMEPAD_X = 99
private const val KEY_GAMEPAD_Y = 100
private const val KEY_GAMEPAD_START = 108
private const val KEY_GAMEPAD_SELECT = 109

private enum class RemoteMode { DPAD, TOUCHPAD, GAMEPAD, GESTURE }

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

    // Which app is on the TV right now (drives the contextual shortcut row).
    var foregroundApp by remember { mutableStateOf<String?>(null) }
    var nowPlaying by remember { mutableStateOf<com.tvtools.app.data.NowPlaying?>(null) }

    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            if (tick % 3 == 0) {
                val pkg = runCatching { adb.foregroundPackage() }.getOrNull()
                if (pkg != null) foregroundApp = pkg
            }
            nowPlaying = runCatching { adb.nowPlaying() }.getOrNull()
            tick++
            delay(4_000)
        }
    }

    LaunchedEffect(Unit) {
        macros = AppServices.prefs.loadMacros()
        quickKeys = AppServices.prefs.loadQuickKeys()
            ?: com.tvtools.app.data.QuickKeys.defaultIds
    }

    fun send(code: Int) {
        if (macroRecording) {
            val now = System.currentTimeMillis()
            val gap = if (recordedSteps.isEmpty()) 0L else (now - lastStepAt).coerceIn(0, 10_000)
            recordedSteps = recordedSteps + com.tvtools.app.data.MacroStep(code, gap)
            lastStepAt = now
        }
        scope.launch { adb.keyEvent(code) }
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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

            // Quick keys the user picked
            if (quickKeys.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
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

// ---------------- D-Pad ----------------

@Composable
private fun DPadPad(onSend: (Int) -> Unit) {
    Box(
        modifier = Modifier.size(260.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
            modifier = Modifier.size(260.dp),
        ) {}

        HoldKeyButton(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp),
            onClick = { onSend(KEY_DPAD_UP) },
            repeatable = true,
        ) { Icon(Icons.Default.KeyboardArrowUp, "Up", Modifier.size(34.dp)) }

        HoldKeyButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp),
            onClick = { onSend(KEY_DPAD_DOWN) },
            repeatable = true,
        ) { Icon(Icons.Default.KeyboardArrowDown, "Down", Modifier.size(34.dp)) }

        HoldKeyButton(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 6.dp),
            onClick = { onSend(KEY_DPAD_LEFT) },
            repeatable = true,
        ) { Icon(Icons.Default.KeyboardArrowLeft, "Left", Modifier.size(34.dp)) }

        HoldKeyButton(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp),
            onClick = { onSend(KEY_DPAD_RIGHT) },
            repeatable = true,
        ) { Icon(Icons.Default.KeyboardArrowRight, "Right", Modifier.size(34.dp)) }

        HoldKeyButton(
            modifier = Modifier.align(Alignment.Center),
            onClick = { onSend(KEY_DPAD_CENTER) },
            container = MaterialTheme.colorScheme.primary,
            tint = MaterialTheme.colorScheme.onPrimary,
            size = 88,
        ) { Text("OK", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
    }
}

// ---------------- Touchpad ----------------

@Composable
private fun TouchPad(onSend: (Int) -> Unit) {
    val haptics = rememberHaptics()
    var dragTotal by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                RoundedCornerShape(28.dp),
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onSend(KEY_DPAD_CENTER) })
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragTotal = Offset.Zero },
                    onDrag = { change, drag ->
                        change.consume()
                        dragTotal += drag
                        val threshold = 70.dp.toPx()
                        if (abs(dragTotal.x) > threshold || abs(dragTotal.y) > threshold) {
                            val key = if (abs(dragTotal.x) > abs(dragTotal.y)) {
                                if (dragTotal.x > 0) KEY_DPAD_RIGHT else KEY_DPAD_LEFT
                            } else {
                                if (dragTotal.y > 0) KEY_DPAD_DOWN else KEY_DPAD_UP
                            }
                            onSend(key)
                            haptics()
                            dragTotal = Offset.Zero
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.TouchApp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Swipe to navigate · Tap to select",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------- Gesture pad ----------------

@Composable
private fun GesturePad(onSend: (Int) -> Unit) {
    val haptics = rememberHaptics()
    var dragTotal by remember { mutableStateOf(Offset.Zero) }
    var lastAction by remember { mutableStateOf("Swipe in a direction") }

    Box(
        modifier = Modifier
            .size(260.dp)
            .background(
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                CircleShape,
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    haptics()
                    lastAction = "OK"
                    onSend(KEY_DPAD_CENTER)
                })
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragTotal = Offset.Zero },
                    onDrag = { change, drag ->
                        change.consume()
                        dragTotal += drag
                    },
                    onDragEnd = {
                        val threshold = 60.dp.toPx()
                        val dx = dragTotal.x
                        val dy = dragTotal.y
                        if (abs(dx) < threshold && abs(dy) < threshold) {
                            lastAction = "Too short — swipe further"
                            return@detectDragGestures
                        }
                        haptics()
                        if (abs(dx) > abs(dy)) {
                            if (dx > 0) {
                                lastAction = "Menu"
                                onSend(KEY_MENU)
                            } else {
                                lastAction = "Back"
                                onSend(KEY_BACK)
                            }
                        } else {
                            if (dy < 0) {
                                lastAction = "Volume +"
                                onSend(KEY_VOL_UP)
                            } else {
                                lastAction = "Home"
                                onSend(KEY_HOME)
                            }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Volume +",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 22.dp),
        )
        Text(
            "Home",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
        )
        Text(
            "Back",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 22.dp),
        )
        Text(
            "Menu",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 22.dp),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.TouchApp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Tap = OK",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                lastAction,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------- Air mouse (gesture mode) ----------------

@Composable
private fun GestureMode(onSend: (Int) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val adb = AppServices.adb
    var airMouse by remember { mutableStateOf(false) }
    var sensorReady by remember { mutableStateOf(true) }
    val screen = remember { intArrayOf(1920, 1080) }
    val pointer = remember { intArrayOf(960, 540) }

    LaunchedEffect(Unit) {
        runCatching { adb.getScreenSize() }.onSuccess { (w, h) ->
            screen[0] = w
            screen[1] = h
            pointer[0] = w / 2
            pointer[1] = h / 2
        }
    }

    DisposableEffect(airMouse) {
        if (!airMouse) return@DisposableEffect onDispose {}
        val manager = context.getSystemService(android.content.Context.SENSOR_SERVICE)
            as? android.hardware.SensorManager
        val sensor = manager?.getDefaultSensor(android.hardware.Sensor.TYPE_GYROSCOPE)
            ?: manager?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        if (manager == null || sensor == null) {
            sensorReady = false
            airMouse = false
            return@DisposableEffect onDispose {}
        }
        val gyro = sensor.type == android.hardware.Sensor.TYPE_GYROSCOPE
        var accX = 0f
        var accY = 0f
        var lastSent = 0L
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                // Gyro reports rad/s; accelerometer reports gravity on each axis.
                val rawX = if (gyro) -event.values[1] else event.values[0] / 9.81f
                val rawY = if (gyro) event.values[0] else event.values[1] / 9.81f
                accX += rawX
                accY += rawY
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastSent < 120L) return
                lastSent = now
                val gain = if (gyro) 260f else 26f
                val stepX = (accX * gain).coerceIn(-60f, 60f)
                val stepY = (accY * gain).coerceIn(-60f, 60f)
                accX = 0f
                accY = 0f
                pointer[0] = (pointer[0] + stepX.toInt()).coerceIn(0, screen[0])
                pointer[1] = (pointer[1] + stepY.toInt()).coerceIn(0, screen[1])
                val x = pointer[0]
                val y = pointer[1]
                scope.launch { runCatching { adb.mouseMove(x, y) } }
            }

            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
        }
        manager.registerListener(
            listener,
            sensor,
            android.hardware.SensorManager.SENSOR_DELAY_GAME,
        )
        // Show the cursor straight away.
        scope.launch { runCatching { adb.mouseMove(pointer[0], pointer[1]) } }
        onDispose {
            manager.unregisterListener(listener)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GesturePad(onSend = onSend)
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = airMouse,
                onClick = { airMouse = !airMouse },
                label = { Text("Air mouse") },
            )
            if (airMouse) {
                OutlinedButton(onClick = {
                    val x = pointer[0]
                    val y = pointer[1]
                    scope.launch {
                        runCatching {
                            adb.mouseDown(x, y)
                            delay(60)
                            adb.mouseUp(x, y)
                        }
                    }
                }) { Text("Click") }
                Text(
                    "Tilt the phone",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!sensorReady) {
            Text(
                "This phone has no motion sensor for air mouse.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

// ---------------- Classic (square) skin ----------------

@Composable
private fun ClassicPad(onSend: (Int) -> Unit) {
    val square = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HoldKeyButton(onClick = { onSend(KEY_DPAD_UP) }, repeatable = true, container = square) {
            Icon(Icons.Default.KeyboardArrowUp, "Up", Modifier.size(30.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            HoldKeyButton(onClick = { onSend(KEY_DPAD_LEFT) }, repeatable = true, container = square) {
                Icon(Icons.Default.KeyboardArrowLeft, "Left", Modifier.size(30.dp))
            }
            HoldKeyButton(
                onClick = { onSend(KEY_DPAD_CENTER) },
                container = MaterialTheme.colorScheme.primary,
                tint = MaterialTheme.colorScheme.onPrimary,
                size = 76,
            ) { Text("OK", fontWeight = FontWeight.Bold) }
            HoldKeyButton(onClick = { onSend(KEY_DPAD_RIGHT) }, repeatable = true, container = square) {
                Icon(Icons.Default.KeyboardArrowRight, "Right", Modifier.size(30.dp))
            }
        }
        HoldKeyButton(onClick = { onSend(KEY_DPAD_DOWN) }, repeatable = true, container = square) {
            Icon(Icons.Default.KeyboardArrowDown, "Down", Modifier.size(30.dp))
        }
    }
}

// ---------------- Minimal skin ----------------

@Composable
private fun MinimalPad(onSend: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NavPill(Icons.Default.KeyboardArrowLeft, "Left") { onSend(KEY_DPAD_LEFT) }
            NavPill(Icons.Default.KeyboardArrowUp, "Up") { onSend(KEY_DPAD_UP) }
            NavPill(Icons.Default.KeyboardArrowDown, "Down") { onSend(KEY_DPAD_DOWN) }
            NavPill(Icons.Default.KeyboardArrowRight, "Right") { onSend(KEY_DPAD_RIGHT) }
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = { onSend(KEY_DPAD_CENTER) }, shape = CircleShape, modifier = Modifier.size(84.dp)) {
            Text("OK", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

// ---------------- Gamepad ----------------

@Composable
private fun GamepadPad(onSend: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(260.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.size(260.dp),
            ) {}

            HoldKeyButton(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                onClick = { onSend(KEY_GAMEPAD_Y) },
                container = androidx.compose.ui.graphics.Color(0xFFB58B00),
            ) { Text("Y", fontWeight = FontWeight.Bold, fontSize = 18.sp) }

            HoldKeyButton(
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp),
                onClick = { onSend(KEY_GAMEPAD_X) },
                container = androidx.compose.ui.graphics.Color(0xFF1565C0),
            ) { Text("X", fontWeight = FontWeight.Bold, fontSize = 18.sp) }

            HoldKeyButton(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
                onClick = { onSend(KEY_GAMEPAD_B) },
                container = androidx.compose.ui.graphics.Color(0xFFB3261E),
            ) { Text("B", fontWeight = FontWeight.Bold, fontSize = 18.sp) }

            HoldKeyButton(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                onClick = { onSend(KEY_GAMEPAD_A) },
                container = androidx.compose.ui.graphics.Color(0xFF2E7D32),
            ) { Text("A", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            NavPill(Icons.Default.KeyboardArrowLeft, "Select") { onSend(KEY_GAMEPAD_SELECT) }
            NavPill(Icons.Default.KeyboardArrowRight, "Start") { onSend(KEY_GAMEPAD_START) }
        }
    }
}

// ---------------- Number pad ----------------

@Composable
private fun NumberPadDialog(onDismiss: () -> Unit, onKey: (Int) -> Unit) {
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

// ---------------- building blocks ----------------

@Composable
private fun HoldKeyButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    repeatable: Boolean = false,
    container: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    size: Int = 64,
    content: @Composable () -> Unit,
) {
    val haptics = rememberHaptics()
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "press")

    LaunchedEffect(pressed) {
        if (pressed && repeatable) {
            while (pressed) {
                delay(150)
                onClick()
            }
        }
    }

    Box(
        modifier = modifier
            .size(size.dp)
            .scale(scale)
            .background(container.copy(alpha = 0.95f), CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    haptics()
                    onClick()
                    tryAwaitRelease()
                    pressed = false
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides tint
        ) { content() }
    }
}

@Composable
private fun NavPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    TextButton(onClick = { haptics(); onClick() }) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

@Composable
private fun MediaButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    IconButton(onClick = { haptics(); onClick() }) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun CircleKey(
    icon: ImageVector,
    label: String,
    repeatable: Boolean = false,
    container: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondaryContainer,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSecondaryContainer,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "circle-press")

    LaunchedEffect(pressed) {
        if (pressed && repeatable) {
            while (pressed) {
                delay(150)
                onClick()
            }
        }
    }

    FilledIconButton(
        onClick = { haptics(); onClick() },
        modifier = Modifier
            .size(52.dp)
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                })
            },
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = container, contentColor = tint),
    ) {
        Icon(icon, contentDescription = label)
    }
}

@Composable
private fun LiveKeyboardDialog(
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

private val EMOJI = listOf("😀", "😂", "❤️", "👍", "🔥", "🎉", "😮", "😢", "🙏", "✨")

@Composable
private fun QuickKeyEditorDialog(
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

private fun swapQuickKey(list: List<String>, from: Int, to: Int): List<String> {
    if (from !in list.indices || to !in list.indices) return list
    return list.toMutableList().also {
        val tmp = it[from]
        it[from] = it[to]
        it[to] = tmp
    }
}

@Composable
private fun NowPlayingBlock(
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

private fun formatPlayback(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}


/** Key codes: 84 = SEARCH, 86 = STOP, 87/88 = next/previous, 165 = INFO, 175 = CAPTIONS. */
private data class AppShortcuts(val label: String, val keys: List<Pair<String, Int>>)

private fun appSpecificKeys(packageName: String?): AppShortcuts? {
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
private fun MacroDialog(
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
private fun SaveMacroDialog(
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
private fun PowerMenuDialog(
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
private fun SleepTimerDialog(
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
private fun PowerRow(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
    }
}

/** Small loading overlay used when switching modes. */
@Composable
private fun RemoteLoading() {
    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
