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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Tune
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


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RemoteControlSheet(
    deviceName: String,
    onDismiss: () -> Unit,
    onOpenMirror: () -> Unit,
    inline: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val adb = AppServices.adb
    val settings by AppServices.settings.state.collectAsState()
    val skin = settings.remoteSkin

    // Open the shared input shell now, not on the first key press.
    LaunchedEffect(Unit) { runCatching { adb.warmUpInput() } }

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

    // Per-TV remote layout (columns, D-pad size, hidden sections, quick order)
    var layout by remember { mutableStateOf(com.tvtools.app.data.RemoteLayout()) }
    var layoutHost by remember { mutableStateOf<String?>(null) }
    var layoutBackup by remember { mutableStateOf(layout) }
    var showLayoutEditor by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

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
        // Give the sheet a moment before the first (fairly heavy) dumpsys runs.
        delay(1_500)
        var tick = 0
        while (true) {
            // Never let status polling compete with the user's key presses: on a
            // cheap TV `dumpsys media_session` can take seconds and delay input.
            if (broadcastSelection == null && !broadcastBusy && !adb.recentlyInteracted(3_000)) {
                // Foreground app changes rarely; media sessions can start/stop any time.
                if (tick % 4 == 0) {
                    val pkg = runCatching { adb.foregroundPackage() }.getOrNull()
                    if (pkg != null) foregroundApp = pkg
                }
                nowPlaying = runCatching { adb.nowPlaying() }.getOrNull()
            }
            tick++
            delay(12_000)
        }
    }

    LaunchedEffect(Unit) {
        macros = AppServices.prefs.loadMacros()
        savedDevices = AppServices.prefs.loadDevices()
        deviceRooms = AppServices.prefs.loadDeviceRooms()
        val storedQuick = AppServices.prefs.loadQuickKeys()
        var host = adb.connectedDevice?.preferencesHost
        if (host == null) {
            delay(600)
            host = adb.connectedDevice?.preferencesHost
        }
        val profile = host?.let { AppServices.prefs.loadDeviceProfile(it) }
        layoutHost = host
        layout = profile?.layout ?: com.tvtools.app.data.RemoteLayout(
            quickOrder = storedQuick ?: com.tvtools.app.data.QuickKeys.defaultIds,
        )
        quickKeys = layout.quickOrder
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
        keyHistory = (keyHistory + KeyPress(code, keyLabel(context, code), System.currentTimeMillis()))
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

    /** Apply a layout immediately and persist it on this TV's profile. */
    fun applyLayout(next: com.tvtools.app.data.RemoteLayout) {
        layout = next
        quickKeys = next.quickOrder
        scope.launch {
            AppServices.prefs.saveQuickKeys(next.quickOrder)
            val host = adb.connectedDevice?.preferencesHost ?: layoutHost ?: return@launch
            val profile = AppServices.prefs.loadDeviceProfile(host)
                ?: AppServices.settings.currentProfile()
            AppServices.prefs.saveDeviceProfile(host, profile.copy(layout = next))
        }
    }

    // One-handed mode lives at the bottom of the scroller; jump there on open.
    LaunchedEffect(layout.oneHanded) {
        if (layout.oneHanded) {
            delay(350)
            runCatching { scrollState.animateScrollTo(scrollState.maxValue) }
        }
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
                adb.keyEventExact(step.keyCode)
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
                    context.getString(R.string.ringing_the_tv_for_12_seconds_turn)
                },
                onFailure = {
                    context.getString(R.string.ring_tv_failed, it.message.orEmpty())
                },
            )
        }
    }

    val padContent: @Composable () -> Unit = {
        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "remote-mode",
        ) { current ->
            when (current) {
                RemoteMode.DPAD -> when (skin) {
                    com.tvtools.app.data.RemoteSkin.CLASSIC -> PhysicalRemotePad(onSend = ::send)
                    com.tvtools.app.data.RemoteSkin.MINIMAL -> MinimalPad(onSend = ::send)
                    com.tvtools.app.data.RemoteSkin.GLASS -> DPadPad(
                        onSend = ::send,
                        size = layout.dpadSize.dp,
                        expressive = layout.expressive,
                        style = PadStyle.GLASS,
                    )
                    com.tvtools.app.data.RemoteSkin.NEON -> DPadPad(
                        onSend = ::send,
                        size = layout.dpadSize.dp,
                        expressive = layout.expressive,
                        style = PadStyle.NEON,
                    )
                    com.tvtools.app.data.RemoteSkin.MEDIA -> MediaFirstPad(
                        onSend = ::send,
                        track = nowPlaying,
                    )
                    else -> DPadPad(
                        onSend = ::send,
                        size = layout.dpadSize.dp,
                        expressive = layout.expressive,
                    )
                }
                RemoteMode.TOUCHPAD -> TouchPad(onSend = ::send)
                RemoteMode.GAMEPAD -> GamepadPad(onSend = ::send)
                RemoteMode.GESTURE -> GestureMode(onSend = ::send)
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }
    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event -> gamepadToTv(event.nativeKeyEvent) }
                .verticalScroll(scrollState)
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
                        stringResource(R.string.remote),
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
                    Icon(Icons.Default.Bolt, contentDescription = stringResource(R.string.macros))
                }
                IconButton(onClick = {
                    layoutBackup = layout
                    showLayoutEditor = true
                }) {
                    Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.customise_remote_layout))
                }
                IconButton(onClick = { showTextDialog = true }) {
                    Icon(Icons.Default.Keyboard, contentDescription = stringResource(R.string.send_text))
                }
                IconButton(onClick = { scope.launch { adb.assistant() } }) {
                    Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.google_assistant_on_the_tv))
                }
                IconButton(onClick = onOpenMirror) {
                    Icon(Icons.Default.Cast, contentDescription = stringResource(R.string.screen_mirroring))
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
                                stringResource(R.string.recording_macro),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                pluralStringResource(R.plurals.macro_recording_count, recordedSteps.size, recordedSteps.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { showSaveMacro = true }, enabled = recordedSteps.isNotEmpty()) {
                            Text(stringResource(R.string.save))
                        }
                        IconButton(onClick = {
                            macroRecording = false
                            recordedSteps = emptyList()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.discard_recording))
                        }
                    }
                }
            } else {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = mode == RemoteMode.DPAD,
                        onClick = { mode = RemoteMode.DPAD },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4),
                    ) { Text(stringResource(R.string.d_pad)) }
                    SegmentedButton(
                        selected = mode == RemoteMode.TOUCHPAD,
                        onClick = { mode = RemoteMode.TOUCHPAD },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4),
                    ) { Text(stringResource(R.string.touch)) }
                    SegmentedButton(
                        selected = mode == RemoteMode.GAMEPAD,
                        onClick = { mode = RemoteMode.GAMEPAD },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4),
                    ) { Text(stringResource(R.string.game)) }
                    SegmentedButton(
                        selected = mode == RemoteMode.GESTURE,
                        onClick = { mode = RemoteMode.GESTURE },
                        shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4),
                    ) { Text(stringResource(R.string.gest)) }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (!layout.oneHanded) {
                padContent()
                Spacer(Modifier.height(12.dp))
            }

            // Navigation + channel keys (column count is user-selectable)
            if ("nav" !in layout.hidden) {
                Column(Modifier.fillMaxWidth()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        maxItemsInEachRow = layout.columns,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        NavPill(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) { send(KEY_BACK) }
                        NavPill(Icons.Default.Home, stringResource(R.string.home)) { send(KEY_HOME) }
                        NavPill(Icons.Default.Menu, stringResource(R.string.menu)) { send(KEY_MENU) }
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        maxItemsInEachRow = layout.columns,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        TextButton(onClick = { send(KEY_CH_DOWN) }) { Text(stringResource(R.string.ch)) }
                        TextButton(onClick = { send(KEY_CH_UP) }) { Text(stringResource(R.string.ch_2)) }
                        TextButton(onClick = { showNumberPad = true }) { Text("0–9") }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // Quick keys + broadcast target
            if ("quick" !in layout.hidden) {
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    item {
                        FilterChip(
                            selected = broadcastSelection != null,
                            onClick = { showBroadcastDialog = true },
                            label = { Text(if (broadcastBusy) stringResource(R.string.sending) else stringResource(R.string.broadcast)) },
                            leadingIcon = { Icon(Icons.Default.Groups, null, Modifier.size(14.dp)) },
                        )
                    }
                    item {
                        AssistChip(
                            onClick = { showHistory = true },
                            label = { Text(stringResource(R.string.history)) },
                            leadingIcon = { Icon(Icons.Default.History, null, Modifier.size(14.dp)) },
                        )
                    }
                    items(quickKeys.size) { index ->
                        val key = com.tvtools.app.data.QuickKeys.byId(quickKeys[index])
                        if (key != null) {
                            AssistChip(onClick = { send(key.keyCode) }, label = { Text(stringResource(key.label)) })
                        }
                    }
                    item {
                        AssistChip(
                            onClick = { showQuickKeyEditor = true },
                            label = { Text(stringResource(R.string.edit)) },
                            leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(14.dp)) },
                        )
                    }
                    item {
                        AssistChip(
                            onClick = {
                                layoutBackup = layout
                                showLayoutEditor = true
                            },
                            label = { Text(stringResource(R.string.layout)) },
                            leadingIcon = { Icon(Icons.Default.Tune, null, Modifier.size(14.dp)) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Media controls
            if ("media" !in layout.hidden) {
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
                            MediaButton(Icons.Default.SkipPrevious, stringResource(R.string.previous)) { send(KEY_MEDIA_PREV) }
                            MediaButton(Icons.Default.FastRewind, stringResource(R.string.rewind)) { send(KEY_MEDIA_REWIND) }
                            MediaButton(Icons.Default.PlayArrow, stringResource(R.string.play_pause_2)) { send(KEY_MEDIA_PLAY_PAUSE) }
                            MediaButton(Icons.Default.FastForward, stringResource(R.string.fast_forward)) { send(KEY_MEDIA_FF) }
                            MediaButton(Icons.Default.SkipNext, stringResource(R.string.media_next_action)) { send(KEY_MEDIA_NEXT) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Contextual shortcuts for the app currently on the TV
            if ("app" !in layout.hidden) {
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
                                stringResource(R.string.app_shortcuts, extras.label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                maxItemsInEachRow = layout.columns,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                extras.keys.forEach { (label, code) ->
                                    AssistChip(onClick = { send(code) }, label = { Text(stringResource(label)) })
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            // One-handed dock: the pad sits below the media/nav cluster, near the thumb.
            if (layout.oneHanded) {
                Text(
                    stringResource(R.string.one_handed_pad),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                padContent()
                Spacer(Modifier.height(12.dp))
            }

            // Volume + power
            if ("volume" !in layout.hidden) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CircleKey(Icons.AutoMirrored.Filled.VolumeDown, stringResource(R.string.volume_down), repeatable = true) {
                        send(KEY_VOL_DOWN)
                    }
                    CircleKey(Icons.Default.VolumeOff, stringResource(R.string.mute)) { send(KEY_MUTE) }
                    CircleKey(Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.volume_up), repeatable = true) {
                        send(KEY_VOL_UP)
                    }
                    Spacer(Modifier.width(6.dp))
                    CircleKey(
                        Icons.Default.PowerSettingsNew,
                        stringResource(R.string.power_menu),
                        container = MaterialTheme.colorScheme.errorContainer,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    ) { showPowerMenu = true }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (inline) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            modifier = modifier.fillMaxHeight(),
        ) {
            body()
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
        ) {
            body()
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
                        context.getString(R.string.no_mac_saved_yet_connect_to_the)
                    } else if (Wol.send(mac)) {
                        context.getString(R.string.wake_packet_sent)
                    } else {
                        context.getString(R.string.couldnt_send_the_wake_packet)
                    }
                }
            },
            onRestart = {
                showPowerMenu = false
                scope.launch {
                    powerMessage = try {
                        val out = adb.rebootTv().trim()
                        if (out.isBlank()) context.getString(R.string.restart_command_sent) else out
                    } catch (e: Exception) {
                        context.getString(R.string.tv_restart_failed, e.message.orEmpty())
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
                applyLayout(layout.copy(quickOrder = ids))
                showQuickKeyEditor = false
            },
        )
    }

    if (showLayoutEditor) {
        RemoteLayoutEditorDialog(
            layout = layout,
            onPreview = { layout = it },
            onDismiss = {
                layout = layoutBackup
                quickKeys = layoutBackup.quickOrder
                showLayoutEditor = false
            },
            onSave = { next ->
                applyLayout(next)
                showLayoutEditor = false
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
            defaultName = context.getString(R.string.default_macro_name, macros.size + 1),
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
                    powerMessage = context.getString(R.string.macro_saved, macro.name)
                }
            },
        )
    }
}
