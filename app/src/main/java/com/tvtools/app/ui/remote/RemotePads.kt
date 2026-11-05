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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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

// ---------------- Pads and remote keys ----------------
// ---------------- D-Pad ----------------

@Composable
internal fun DPadPad(onSend: (Int) -> Unit) {
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
internal fun TouchPad(onSend: (Int) -> Unit) {
    val haptics = rememberHaptics()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                RoundedCornerShape(28.dp),
            )
            .semantics {
                contentDescription = "Touchpad. Drag to navigate, tap to select."
                role = Role.Button
                onClick {
                    onSend(KEY_DPAD_CENTER)
                    true
                }
            }
            .pointerInput(Unit) {
                val swipeThreshold = 60.dp.toPx()
                val scrollThreshold = 30.dp.toPx()
                val pinchThreshold = 36.dp.toPx()
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    val startedAt = android.os.SystemClock.uptimeMillis()
                    var lastCentroid = first.position
                    var maxPointers = 1
                    var travel = 0f
                    var dragAcc = Offset.Zero
                    var scrollAcc = 0f
                    var pinchBase = 0f
                    var pinchAcc = 0f
                    var didNavigate = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) {
                            event.changes.forEach { it.consume() }
                            break
                        }
                        maxPointers = maxOf(maxPointers, pressed.size)
                        val centroid = pressed.fold(Offset.Zero) { acc, change -> acc + change.position } /
                            pressed.size.toFloat()
                        val delta = centroid - lastCentroid
                        lastCentroid = centroid
                        travel += delta.getDistance()

                        if (pressed.size == 1) {
                            pinchBase = 0f
                            pinchAcc = 0f
                            dragAcc += delta
                            if (abs(dragAcc.x) > swipeThreshold || abs(dragAcc.y) > swipeThreshold) {
                                val key = if (abs(dragAcc.x) > abs(dragAcc.y)) {
                                    if (dragAcc.x > 0) KEY_DPAD_RIGHT else KEY_DPAD_LEFT
                                } else {
                                    if (dragAcc.y > 0) KEY_DPAD_DOWN else KEY_DPAD_UP
                                }
                                onSend(key)
                                haptics()
                                dragAcc = Offset.Zero
                                didNavigate = true
                            }
                        } else {
                            dragAcc = Offset.Zero
                            // Two fingers: vertical scroll
                            if (abs(delta.y) > 1.5f) {
                                scrollAcc += delta.y
                                if (abs(scrollAcc) > scrollThreshold) {
                                    onSend(if (scrollAcc > 0) KEY_DPAD_DOWN else KEY_DPAD_UP)
                                    haptics()
                                    scrollAcc = 0f
                                    didNavigate = true
                                }
                            }
                            // Pinch: volume
                            if (pressed.size == 2) {
                                val span = (pressed[0].position - pressed[1].position).getDistance()
                                if (pinchBase == 0f) {
                                    pinchBase = span
                                } else {
                                    pinchAcc += span - pinchBase
                                    pinchBase = span
                                    if (abs(pinchAcc) > pinchThreshold) {
                                        onSend(if (pinchAcc > 0) KEY_VOL_UP else KEY_VOL_DOWN)
                                        haptics()
                                        pinchAcc = 0f
                                        didNavigate = true
                                    }
                                }
                            } else {
                                pinchBase = 0f
                                pinchAcc = 0f
                            }
                        }
                        event.changes.forEach { it.consume() }
                    }

                    val duration = android.os.SystemClock.uptimeMillis() - startedAt
                    if (!didNavigate && duration < 320 && travel < swipeThreshold / 2) {
                        haptics()
                        when (maxPointers) {
                            2 -> onSend(KEY_BACK)
                            3 -> onSend(KEY_HOME)
                            else -> onSend(KEY_DPAD_CENTER)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.TouchApp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Drag to navigate · Tap to select",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "2 fingers: scroll · pinch: volume",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                "2-finger tap: back · 3-finger tap: home",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------- Gesture pad ----------------

@Composable
internal fun GesturePad(onSend: (Int) -> Unit) {
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
            .semantics {
                contentDescription = "Gesture pad. Tap for OK. Swipe up for volume, down for home, left for back, right for menu."
                role = Role.Button
                onClick {
                    onSend(KEY_DPAD_CENTER)
                    true
                }
            }
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
internal fun GestureMode(onSend: (Int) -> Unit) {
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
internal fun ClassicPad(onSend: (Int) -> Unit) {
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
internal fun MinimalPad(onSend: (Int) -> Unit) {
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
internal fun GamepadPad(onSend: (Int) -> Unit) {
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
        Spacer(Modifier.height(6.dp))
        Text(
            "Physical controller: A = OK · B = back · X = menu · Y = home",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
// ---------------- building blocks ----------------

@Composable
internal fun HoldKeyButton(
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
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick {
                    onClick()
                    true
                }
            }
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
internal fun NavPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    TextButton(onClick = { haptics(); onClick() }) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

@Composable
internal fun MediaButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    IconButton(onClick = { haptics(); onClick() }) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(26.dp))
    }
}

@Composable
internal fun CircleKey(
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
