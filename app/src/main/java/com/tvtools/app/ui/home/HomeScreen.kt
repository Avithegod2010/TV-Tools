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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.PushPin
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvtools.app.R
import com.tvtools.app.adb.ConnectionStatus
import com.tvtools.app.data.TvDevice
import com.tvtools.app.ui.common.BrandMark
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
    val connectionStatus by viewModel.connectionStatus.collectAsState()
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

    LaunchedEffect(Unit) {
        viewModel.refreshMacros()
    }

    LaunchedEffect(viewModel) {
        viewModel.initialConnection.collect { device ->
            if (viewModel.acceptInitialConnection(device)) onConnected(device)
        }
    }

    DisposableEffect(viewModel) {
        viewModel.startDiscovery()
        onDispose { viewModel.stopDiscovery() }
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
                BrandMark(size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(24.dp))

            val connectionMessage = when (connectionStatus) {
                ConnectionStatus.NO_NETWORK -> R.string.network_no_lan
                ConnectionStatus.NETWORK_CHANGED -> R.string.network_changed
                ConnectionStatus.RECONNECTING -> R.string.network_reconnecting
                ConnectionStatus.UNAVAILABLE -> R.string.network_tv_unreachable
                ConnectionStatus.AUTHORIZATION_REQUIRED -> R.string.network_authorization_needed
                else -> null
            }
            if (connectionMessage != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(stringResource(connectionMessage), style = MaterialTheme.typography.bodySmall)
                        if (connectionStatus == ConnectionStatus.UNAVAILABLE && ui.lastConnected != null) {
                            TextButton(onClick = viewModel::reconnectLast) {
                                Text(stringResource(R.string.network_reconnect))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            ui.heroDevice?.let { hero ->
                Text(
                    if (ui.pinnedHost == hero.host) stringResource(R.string.pinned_tv) else stringResource(R.string.last_connected),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                val titleModifier: Modifier = if (sharedScope != null && animatedScope != null) {
                    with(sharedScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState("device-title-${hero.name}"),
                            animatedScope,
                        )
                    }
                } else Modifier
                HeroDeviceCard(
                    device = hero,
                    connecting = ui.connectingTo?.host == hero.host,
                    pinned = ui.pinnedHost == hero.host,
                    thumbPath = viewModel.thumbFile(hero.host)
                        ?.takeIf { file -> file.exists() }
                        ?.absolutePath,
                    previewBusy = ui.previewBusy,
                    nowPlaying = ui.nowPlaying,
                    titleModifier = titleModifier,
                    onConnect = { haptics(); viewModel.connect(hero) },
                    onRefresh = { haptics(); viewModel.refreshOverview(hero) },
                    onTogglePin = {
                        haptics()
                        viewModel.pinDevice(if (ui.pinnedHost == hero.host) null else hero.host)
                    },
                )
                Spacer(Modifier.height(20.dp))
            }

            if (ui.savedDevices.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.my_tvs),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { haptics(); viewModel.connectAllSaved() },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                    ) {
                        Text(stringResource(R.string.check_all), style = MaterialTheme.typography.labelMedium)
                    }
                    Text(
                        stringResource(R.string.hold_pin_reorder_remove),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(ui.savedDevices, key = { _, it -> it.host }) { index, saved ->
                        SavedDeviceChip(
                            device = saved,
                            connecting = ui.connectingTo?.host == saved.host,
                            status = ui.batchStatus[saved.host],
                            pinned = ui.pinnedHost == saved.host,
                            canMoveLeft = index > 0,
                            canMoveRight = index < ui.savedDevices.lastIndex,
                            onClick = { haptics(); viewModel.connect(saved) },
                            onPin = {
                                viewModel.pinDevice(if (ui.pinnedHost == saved.host) null else saved.host)
                            },
                            onMoveLeft = { viewModel.moveSavedDevice(saved.host, -1) },
                            onMoveRight = { viewModel.moveSavedDevice(saved.host, 1) },
                            onRemove = { viewModel.removeSavedDevice(saved.host) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (ui.macros.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.pinned_macros),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.refreshMacros() }) {
                        Text(stringResource(R.string.refresh), style = MaterialTheme.typography.labelMedium)
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ui.macros.take(8), key = { it.name }) { macro ->
                        MacroTile(macro = macro) {
                            haptics()
                            viewModel.runMacro(macro)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.select_device),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (ui.scanning) {
                    RadarScan(Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                }
                IconButton(onClick = { viewModel.startDiscovery() }) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !ui.showAll,
                    onClick = { haptics(); viewModel.setShowAll(false) },
                    label = { Text(stringResource(R.string.android_tv_fire_tv)) },
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
                    label = { Text(stringResource(R.string.all)) },
                )
            }

            Text(
                stringResource(R.string.only_android_tv_fire_tv_devices_with),
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
                            BrandMark(size = 58.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.no_devices_found_yet_make_sure_the),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { showManualDialog = true }) {
                                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.add_by_ip_address))
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
                    Text(stringResource(R.string.add_ip))
                }
                }
                Spacer(Modifier.weight(1f))
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    if (!guestMode) {
                    TextButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.more))
                    }
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_device_by_ip)) },
                            leadingIcon = { Icon(Icons.Default.Add, null) },
                            onClick = { menuOpen = false; showManualDialog = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.wake_reconnect)) },
                            leadingIcon = { Icon(Icons.Default.PowerSettingsNew, null) },
                            onClick = { menuOpen = false; viewModel.wakeAndReconnect() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.wake_last_tv_wol_only)) },
                            leadingIcon = { Icon(Icons.Default.PowerSettingsNew, null) },
                            onClick = { menuOpen = false; viewModel.wakeLastTv() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_tv_via_qr)) },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            onClick = { menuOpen = false; onOpenTool("qr") },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.network_map)) },
                            leadingIcon = { Icon(Icons.Default.Info, null) },
                            onClick = { menuOpen = false; onOpenTool("netmap") },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.connect_all_saved_tvs)) },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            onClick = { menuOpen = false; viewModel.connectAllSaved() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rooms_groups)) },
                            leadingIcon = { Icon(Icons.Default.Home, null) },
                            onClick = { menuOpen = false; onOpenTool("rooms") },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.connection_doctor)) },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            onClick = { menuOpen = false; onOpenTool("doctor") },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.mdns_service_browser)) },
                            leadingIcon = { Icon(Icons.Default.Info, null) },
                            onClick = { menuOpen = false; onOpenTool("mdns") },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.hotspot_mode_no_router)) },
                            leadingIcon = { Icon(Icons.Default.Info, null) },
                            onClick = { menuOpen = false; onOpenTool("hotspot") },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings)) },
                            leadingIcon = { Icon(Icons.Default.Settings, null) },
                            onClick = { menuOpen = false; showSettings = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.how_to_enable_adb_on_my_tv)) },
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
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text(stringResource(R.string.got_it)) } },
            title = { Text(stringResource(R.string.enable_adb_on_your_tv)) },
            text = {
                Text(
                    stringResource(R.string.adb_setup_instructions)
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
    pinned: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            ),
            modifier = Modifier
                .width(190.dp)
                .pointerInput(device.host) {
                    detectTapGestures(onTap = { onClick() }, onLongPress = { menuOpen = true })
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
                if (pinned) {
                    Icon(
                        Icons.Default.PushPin,
                        stringResource(R.string.pinned),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                } else if (connecting) {
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
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(if (pinned) stringResource(R.string.unpin_from_dashboard) else stringResource(R.string.pin_to_dashboard)) },
                leadingIcon = { Icon(Icons.Default.PushPin, null) },
                onClick = { menuOpen = false; onPin() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.move_left)) },
                enabled = canMoveLeft,
                onClick = { menuOpen = false; onMoveLeft() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.move_right)) },
                enabled = canMoveRight,
                onClick = { menuOpen = false; onMoveRight() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.remove_from_saved_tvs)) },
                onClick = { menuOpen = false; onRemove() },
            )
        }
    }
}

@Composable
private fun HeroDeviceCard(
    device: TvDevice,
    connecting: Boolean,
    pinned: Boolean,
    thumbPath: String?,
    previewBusy: Boolean,
    nowPlaying: com.tvtools.app.data.NowPlaying?,
    titleModifier: Modifier,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onTogglePin: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(184.dp)
            ) {
                val thumb = thumbPath?.let { path ->
                    remember(path, java.io.File(path).lastModified()) {
                        android.graphics.BitmapFactory.decodeFile(path)
                    }
                }
                if (thumb != null) {
                    androidx.compose.foundation.Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = stringResource(R.string.live_preview_of_device, device.name),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                        MaterialTheme.colorScheme.secondaryContainer,
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Tv,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.size(64.dp),
                        )
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.68f))
                            )
                        )
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        device.name,
                        modifier = titleModifier,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                    Text(
                        buildString {
                            append(formatEndpoint(device.host, device.port))
                            if (device.pairing) append("  •  pairing")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.82f),
                    )
                    val track = nowPlaying
                    if (track != null && track.title.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                listOfNotNull(track.title, track.artist.takeIf { it.isNotBlank() })
                                    .joinToString(" — "),
                                style = MaterialTheme.typography.labelSmall,
                                color = androidx.compose.ui.graphics.Color.White,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (pinned) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.pinned),
                                style = MaterialTheme.typography.labelSmall,
                                color = androidx.compose.ui.graphics.Color.White,
                            )
                        }
                    }
                }
                if (previewBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(30.dp),
                        strokeWidth = 2.5.dp,
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onConnect) {
                    if (connecting) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Tv, null, Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.connect))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRefresh, enabled = !previewBusy) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (previewBusy) stringResource(R.string.refreshing) else stringResource(R.string.preview))
                }
                IconButton(onClick = onTogglePin) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = if (pinned) stringResource(R.string.unpin_this_tv) else stringResource(R.string.pin_this_tv_to_the_dashboard),
                        tint = if (pinned) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MacroTile(
    macro: com.tvtools.app.data.Macro,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .width(150.dp)
            .height(78.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                Icons.Default.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                macro.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
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
                // Key on mtime so a fresh explicit screenshot replaces the old thumb.
                remember(path, java.io.File(path).lastModified()) {
                    android.graphics.BitmapFactory.decodeFile(path)
                }
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
        title = { Text(stringResource(R.string.connect_by_ip)) },
        text = {
            Column {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.trim() },
                    label = { Text(stringResource(R.string.ip_address)) },
                    placeholder = { Text("192.168.1.50 or [fe80::1]:5555") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.port)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.text_5555_for_fire_tv_network_adb_for),
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
            ) { Text(stringResource(R.string.connect)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

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
        title = { Text(stringResource(R.string.pair_with_tv)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.on_the_tv_open_developer_options_wireless),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.trim() },
                    label = { Text(stringResource(R.string.ip_address)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.pairing_port)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                rememberedPort?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.remembered_pairing_port, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    label = { Text(stringResource(R.string.six_digit_pairing_code)) },
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
            ) { Text(stringResource(R.string.pair_connect)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
