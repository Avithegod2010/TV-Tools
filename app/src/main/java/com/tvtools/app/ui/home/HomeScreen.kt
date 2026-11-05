package com.tvtools.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvtools.app.data.TvDevice
import com.tvtools.app.ui.common.rememberHaptics
import com.tvtools.app.ui.settings.SettingsSheet

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    onConnected: (TvDevice) -> Unit,
    onOpenTool: (String) -> Unit = {},
    sharedScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedScope: androidx.compose.animation.AnimatedContentScope? = null,
    viewModel: HomeViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    val settings by com.tvtools.app.AppServices.settings.state.collectAsState()
    val guestMode = settings.guestMode
    val snackbar = remember { SnackbarHostState() }
    val haptics = rememberHaptics()

    var pairingTarget by remember { mutableStateOf<TvDevice?>(null) }
    var showManualDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.Connected -> onConnected(event.device)
                is HomeEvent.Error -> snackbar.showSnackbar(event.message)
                is HomeEvent.Message -> snackbar.showSnackbar(event.text)
                is HomeEvent.PairingRequested -> pairingTarget = event.device
                else -> {}
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(28.dp))

            // Logo (animated in)
            var logoVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { logoVisible = true }
            val logoScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (logoVisible) 1f else 0.75f,
                animationSpec = androidx.compose.animation.core.tween(450),
                label = "logo-scale",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                        alpha = logoScale
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "TV Tools",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(24.dp))

            ui.lastConnected?.let { last ->
                Text(
                    "Last connected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                val titleModifier: Modifier = if (sharedScope != null && animatedScope != null) {
                    with(sharedScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState("device-title-${last.name}"),
                            animatedScope,
                        )
                    }
                } else Modifier
                DeviceCard(
                    device = last,
                    connecting = ui.connectingTo?.host == last.host,
                    thumbPath = ui.lastThumbPath,
                    titleModifier = titleModifier,
                    onClick = { haptics(); viewModel.connect(last) },
                )
                Spacer(Modifier.height(20.dp))
            }

            if (ui.savedDevices.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "My TVs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { haptics(); viewModel.connectAllSaved() },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                    ) {
                        Text("Check all", style = MaterialTheme.typography.labelMedium)
                    }
                    Text(
                        "hold to remove",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ui.savedDevices, key = { it.host }) { saved ->
                        SavedDeviceChip(
                            device = saved,
                            connecting = ui.connectingTo?.host == saved.host,
                            status = ui.batchStatus[saved.host],
                            onClick = { haptics(); viewModel.connect(saved) },
                            onLongClick = { viewModel.removeSavedDevice(saved.host) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Select device",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (ui.scanning) {
                    RadarScan(Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                }
                IconButton(onClick = { viewModel.startDiscovery() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !ui.showAll,
                    onClick = { haptics(); viewModel.setShowAll(false) },
                    label = { Text("Android TV & Fire TV") },
                    leadingIcon = if (!ui.showAll) {
                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
                FilterChip(
                    selected = ui.showAll,
                    onClick = { haptics(); viewModel.setShowAll(true) },
                    label = { Text("All") },
                )
            }

            Text(
                "Only Android TV / Fire TV devices with ADB enabled can be found here",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(ui.visibleDevices, key = { "${it.host}:${it.port}:${it.proto}" }) { device ->
                    DeviceCard(
                        device = device,
                        connecting = ui.connectingTo?.host == device.host,
                        onClick = { haptics(); viewModel.connect(device) },
                    )
                }
                if (!ui.scanning && ui.visibleDevices.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Default.Info, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No devices found yet.\nMake sure the TV is on the same Wi-Fi\nwith ADB / Wireless debugging enabled.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { showManualDialog = true }) {
                                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add by IP address")
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            // Bottom bar: manual add + More (hidden in guest mode)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!guestMode) {
                TextButton(onClick = { showManualDialog = true }) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add IP")
                }
                }
                Spacer(Modifier.weight(1f))
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    if (!guestMode) {
                    TextButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("More")
                    }
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Add device by IP") },
                            leadingIcon = { Icon(Icons.Default.Add, null) },
                            onClick = { menuOpen = false; showManualDialog = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Wake & reconnect") },
                            leadingIcon = { Icon(Icons.Default.PowerSettingsNew, null) },
                            onClick = { menuOpen = false; viewModel.wakeAndReconnect() },
                        )
                        DropdownMenuItem(
                            text = { Text("Wake last TV (WOL only)") },
                            leadingIcon = { Icon(Icons.Default.PowerSettingsNew, null) },
                            onClick = { menuOpen = false; viewModel.wakeLastTv() },
                        )
                        DropdownMenuItem(
                            text = { Text("Share TV via QR") },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            onClick = { menuOpen = false; onOpenTool("qr") },
                        )
                        DropdownMenuItem(
                            text = { Text("Network map") },
                            leadingIcon = { Icon(Icons.Default.Info, null) },
                            onClick = { menuOpen = false; onOpenTool("netmap") },
                        )
                        DropdownMenuItem(
                            text = { Text("Connect all saved TVs") },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            onClick = { menuOpen = false; viewModel.connectAllSaved() },
                        )
                        DropdownMenuItem(
                            text = { Text("Rooms & groups") },
                            leadingIcon = { Icon(Icons.Default.Home, null) },
                            onClick = { menuOpen = false; onOpenTool("rooms") },
                        )
                        DropdownMenuItem(
                            text = { Text("Connection doctor") },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            onClick = { menuOpen = false; onOpenTool("doctor") },
                        )
                        DropdownMenuItem(
                            text = { Text("Hotspot mode (no router)") },
                            leadingIcon = { Icon(Icons.Default.Info, null) },
                            onClick = { menuOpen = false; onOpenTool("hotspot") },
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = { Icon(Icons.Default.Settings, null) },
                            onClick = { menuOpen = false; showSettings = true },
                        )
                        DropdownMenuItem(
                            text = { Text("How to enable ADB on my TV") },
                            leadingIcon = { Icon(Icons.Default.Info, null) },
                            onClick = { menuOpen = false; showAboutDialog = true },
                        )
                    }
                }
            }
        }
    }

    if (showManualDialog) {
        ManualConnectDialog(
            onDismiss = { showManualDialog = false },
            onConnect = { host, port ->
                showManualDialog = false
                viewModel.connectManual(host, port)
            },
        )
    }

    pairingTarget?.let { target ->
        PairingDialog(
            target = target,
            onDismiss = { pairingTarget = null },
            onPair = { host, port, code ->
                pairingTarget = null
                viewModel.pairThenConnect(host, port, code)
            },
        )
    }

    if (showSettings) {
        SettingsSheet(onDismiss = { showSettings = false })
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text("Got it") } },
            title = { Text("Enable ADB on your TV") },
            text = {
                Text(
                    "Android TV / Google TV / Xiaomi:\n" +
                        "Settings → Device Preferences → About → tap \"Build\" 7 times → back → " +
                        "Developer options → enable \"Wireless debugging\" (Android 11+) or \"Network debugging\".\n\n" +
                        "Amazon Fire TV:\n" +
                        "Settings → My Fire TV → About → tap the name 7 times → " +
                        "Developer Options → enable \"ADB debugging\".\n\n" +
                        "Keep your phone and TV on the same Wi-Fi network."
                )
            },
        )
    }
}

@Composable
private fun SavedDeviceChip(
    device: TvDevice,
    connecting: Boolean,
    status: BatchState?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        ),
        modifier = Modifier
            .width(190.dp)
            .pointerInput(device.host) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() })
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Tv, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    formatEndpoint(device.host, device.port),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    strokeCap = StrokeCap.Round,
                )
            } else if (status != null) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when (status) {
                                BatchState.ONLINE -> MaterialTheme.colorScheme.primary
                                BatchState.CHECKING -> MaterialTheme.colorScheme.tertiary
                                BatchState.OFFLINE -> MaterialTheme.colorScheme.outline
                            }
                        )
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: TvDevice,
    connecting: Boolean,
    thumbPath: String? = null,
    titleModifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val thumb = thumbPath?.let { path ->
                remember(path) { android.graphics.BitmapFactory.decodeFile(path) }
            }
            if (thumb != null) {
                androidx.compose.foundation.Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .width(64.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            } else {
                TvIcon()
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.name,
                    modifier = titleModifier,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    buildString {
                        append(formatEndpoint(device.host, device.port))
                        if (device.pairing) append("  •  pairing")
                        else if (device.proto == com.tvtools.app.data.DeviceProto.TLS) append("  •  wireless debugging")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    strokeCap = StrokeCap.Round,
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RadarScan(modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "radar")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1500, easing = androidx.compose.animation.core.LinearEasing)
        ),
        label = "radar-progress",
    )
    val color = MaterialTheme.colorScheme.primary
    androidx.compose.foundation.Canvas(modifier) {
        repeat(3) { i ->
            val p = ((progress + i / 3f) % 1f)
            drawCircle(
                color = color.copy(alpha = (1f - p) * 0.55f),
                radius = size.minDimension / 2f * p,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

@Composable
private fun TvIcon() {
    Icon(
        imageVector = Icons.Default.Tv,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(26.dp),
    )
}

@Composable
private fun ManualConnectDialog(
    onDismiss: () -> Unit,
    onConnect: (host: String, port: Int) -> Unit,
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("5555") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect by IP") },
        text = {
            Column {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.trim() },
                    label = { Text("IP address") },
                    placeholder = { Text("192.168.1.50 or [fe80::1]:5555") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit) },
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "5555 for Fire TV & network ADB. For Android 11+ use the port shown in Wireless debugging. " +
                        "IPv6 addresses can include the port: [fe80::1]:5555.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val (parsedHost, parsedPort) = parseHostPort(host, port.toIntOrNull() ?: 5555)
                    onConnect(parsedHost, parsedPort)
                },
                enabled = host.isNotBlank() && (host.contains(':') || host.count { it == '.' } == 3),
            ) { Text("Connect") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Splits manual input into host + port. Accepts:
 *  - `192.168.1.5` (uses [defaultPort])
 *  - `192.168.1.5:5555`
 *  - `fe80::1` / `2001:db8::1` (uses [defaultPort])
 *  - `[fe80::1]:5555` (bracketed IPv6 with a port)
 */
private fun parseHostPort(raw: String, defaultPort: Int): Pair<String, Int> {
    val text = raw.trim()
        .removePrefix("adb://")
        .removePrefix("tcp://")
        .removeSuffix("/")
    if (text.startsWith("[")) {
        val close = text.indexOf(']')
        if (close > 0) {
            val host = text.substring(1, close)
            val port = text.substring(close + 1)
                .removePrefix(":")
                .toIntOrNull()
                ?: defaultPort
            return host to port
        }
        return text.trim('[', ']') to defaultPort
    }
    // Bare IPv6 (more than one colon) has no port part.
    if (text.count { it == ':' } > 1) return text to defaultPort
    val colon = text.lastIndexOf(':')
    if (colon > 0) {
        val maybePort = text.substring(colon + 1).toIntOrNull()
        if (maybePort != null) return text.substring(0, colon) to maybePort
    }
    return text to defaultPort
}

/** Shows IPv6 literals in brackets so host:port stays unambiguous. */
private fun formatEndpoint(host: String, port: Int): String =
    if (host.contains(':')) "[$host]:$port" else "$host:$port"

@Composable
private fun PairingDialog(
    target: TvDevice,
    onDismiss: () -> Unit,
    onPair: (host: String, port: Int, code: String) -> Unit,
) {
    var host by remember { mutableStateOf(target.host) }
    var port by remember { mutableStateOf(target.port.takeIf { target.pairing }?.toString() ?: "") }
    var code by remember { mutableStateOf("") }
    var rememberedPort by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(target.host) {
        if (port.isBlank()) {
            val saved = com.tvtools.app.AppServices.prefs.loadPairingPort(target.host)
            rememberedPort = saved
            if (saved != null) port = saved.toString()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pair with TV") },
        text = {
            Column {
                Text(
                    "On the TV open: Developer options → Wireless debugging → \"Pair device with pairing code\". " +
                        "Enter the IP, port and 6-digit code shown there.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.trim() },
                    label = { Text("IP address") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit) },
                    label = { Text("Pairing port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                rememberedPort?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Remembered from last pairing ($it). Just enter the code — the port can change if the TV restarted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    label = { Text("6-digit pairing code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val (parsedHost, parsedPort) = parseHostPort(host, port.toIntOrNull() ?: 0)
                    onPair(parsedHost, if (parsedPort > 0) parsedPort else port.toIntOrNull() ?: 0, code)
                },
                enabled = host.isNotBlank() && (port.toIntOrNull() ?: 0) > 0 && code.length == 6,
            ) { Text("Pair & connect") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
