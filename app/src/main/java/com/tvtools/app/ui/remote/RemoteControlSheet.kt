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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cast
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

private enum class RemoteMode { DPAD, TOUCHPAD, GAMEPAD }

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
    var powerMessage by remember { mutableStateOf<String?>(null) }

    fun send(code: Int) {
        scope.launch { adb.keyEvent(code) }
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
                IconButton(onClick = { showTextDialog = true }) {
                    Icon(Icons.Default.Keyboard, contentDescription = "Send text")
                }
                IconButton(onClick = onOpenMirror) {
                    Icon(Icons.Default.Cast, contentDescription = "Screen mirroring")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Mode switch
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == RemoteMode.DPAD,
                    onClick = { mode = RemoteMode.DPAD },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                ) { Text("D-Pad") }
                SegmentedButton(
                    selected = mode == RemoteMode.TOUCHPAD,
                    onClick = { mode = RemoteMode.TOUCHPAD },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                ) { Text("Touch") }
                SegmentedButton(
                    selected = mode == RemoteMode.GAMEPAD,
                    onClick = { mode = RemoteMode.GAMEPAD },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                ) { Text("Game") }
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

            Spacer(Modifier.height(12.dp))

            // Media controls
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
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

            Spacer(Modifier.height(12.dp))

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
private fun PowerMenuDialog(
    deviceName: String,
    onDismiss: () -> Unit,
    onSleep: () -> Unit,
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
