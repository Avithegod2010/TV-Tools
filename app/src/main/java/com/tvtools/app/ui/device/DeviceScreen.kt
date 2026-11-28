package com.tvtools.app.ui.device

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvtools.app.R
import com.tvtools.app.TvToolsApp
import com.tvtools.app.adb.ConnectionStatus
import com.tvtools.app.ui.apps.AppsTab
import com.tvtools.app.ui.common.PlaybackBar
import com.tvtools.app.ui.common.rememberHaptics
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.tvtools.app.ui.info.InfoTab
import com.tvtools.app.ui.remote.RemoteControlSheet
import com.tvtools.app.ui.settings.SettingsSheet
import com.tvtools.app.ui.shell.ShellTab
import com.tvtools.app.ui.tools.ToolsTab

@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
)
@Composable
fun DeviceScreen(
    deviceName: String,
    sharedScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedScope: androidx.compose.animation.AnimatedContentScope? = null,
    onBack: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenMirror: () -> Unit,
    onOpenBackups: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenMusic: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenLab: (String) -> Unit,
    viewModel: DeviceViewModel = viewModel(),
) {
    var selectedTab by remember {
        mutableIntStateOf(if (com.tvtools.app.AppServices.settings.state.value.guestMode) 1 else 0)
    }
    var showRemote by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showExitGuest by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val snackbar = remember { SnackbarHostState() }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val ctx = com.tvtools.app.TvToolsApp.instance
    val settings by com.tvtools.app.AppServices.settings.state.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val connectedDevice by viewModel.connectedDevice.collectAsState()
    val reconnectedText = stringResource(R.string.network_reconnected)
    val guest = settings.guestMode
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600

    LaunchedEffect(wide) {
        if (wide) showRemote = true
    }

    LaunchedEffect(Unit) {
        viewModel.disconnected.collect { onBack() }
    }

    LaunchedEffect(viewModel, reconnectedText) {
        var wasConnected = viewModel.connectedDevice.value != null
        viewModel.connectedDevice.collectLatest { device ->
            val reconnected = device != null && !wasConnected
            wasConnected = device != null
            if (reconnected) snackbar.showSnackbar(reconnectedText)
        }
    }

    LaunchedEffect(guest) {
        if (guest) {
            if (selectedTab != 1 && selectedTab != 3) selectedTab = 1
            showRemote = true
        }
    }

    // The Info tab polls the TV every few seconds. Pause that while the remote is
    // open so status reads never compete with key presses on the single ADB link.
    LaunchedEffect(showRemote, selectedTab) {
        if (selectedTab == 3 && !showRemote) viewModel.startStatsPolling()
        else viewModel.stopStatsPolling()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                deviceName,
                                modifier = if (sharedScope != null && animatedScope != null) {
                                    with(sharedScope) {
                                        Modifier.sharedElement(
                                            rememberSharedContentState("device-title-$deviceName"),
                                            animatedScope,
                                        )
                                    }
                                } else Modifier,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(R.string.device_heading),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp),
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.disconnect() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.disconnect))
                    }
                },
                actions = {
                    IconButton(onClick = { haptics(); showRemote = !showRemote }) {
                        Icon(Icons.Default.SettingsRemote, contentDescription = stringResource(R.string.remote_control))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.remote_control)) },
                                leadingIcon = { Icon(Icons.Default.SettingsRemote, null) },
                                onClick = { menuOpen = false; showRemote = true },
                            )
                            if (!guest) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.screen_mirroring)) },
                                leadingIcon = { Icon(Icons.Default.Cast, null) },
                                onClick = { menuOpen = false; onOpenMirror() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.save_look_for_this_tv)) },
                                leadingIcon = { Icon(Icons.Default.Save, null) },
                                onClick = {
                                    menuOpen = false
                                    val host = connectedDevice?.preferencesHost
                                    if (host != null) {
                                        coroutineScope.launch {
                                            val existing = com.tvtools.app.AppServices.prefs
                                                .loadDeviceProfile(host)
                                            com.tvtools.app.AppServices.prefs.saveDeviceProfile(
                                                host,
                                                com.tvtools.app.AppServices.settings.currentProfile()
                                                    .copy(
                                                        layout = existing?.layout
                                                            ?: com.tvtools.app.data.RemoteLayout(),
                                                    ),
                                            )
                                            android.widget.Toast.makeText(
                                                TvToolsApp.instance,
                                                R.string.device_look_saved,
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.refresh_tv_palette)) },
                                leadingIcon = { Icon(Icons.Default.Palette, null) },
                                onClick = {
                                    menuOpen = false
                                    coroutineScope.launch {
                                        val color = kotlinx.coroutines.withContext(
                                            kotlinx.coroutines.Dispatchers.IO,
                                        ) {
                                            com.tvtools.app.ui.common.TvPalette.capture(ctx)
                                        }
                                        if (color != null) {
                                            com.tvtools.app.AppServices.settings.setTvColor(color)
                                            val host = com.tvtools.app.AppServices.adb.connectedDevice?.preferencesHost
                                            if (host != null) {
                                                val existing = com.tvtools.app.AppServices.prefs
                                                    .loadDeviceProfile(host)
                                                com.tvtools.app.AppServices.prefs.saveDeviceProfile(
                                                    host,
                                                    com.tvtools.app.AppServices.settings.currentProfile()
                                                        .copy(
                                                            layout = existing?.layout
                                                                ?: com.tvtools.app.data.RemoteLayout(),
                                                        ),
                                                )
                                            }
                                            android.widget.Toast.makeText(
                                                TvToolsApp.instance,
                                                R.string.device_palette_refreshed,
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        } else {
                                            android.widget.Toast.makeText(
                                                TvToolsApp.instance,
                                                R.string.device_connect_first,
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.forget_this_tvs_look)) },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = {
                                    menuOpen = false
                                    connectedDevice?.preferencesHost?.let { host ->
                                        coroutineScope.launch {
                                            com.tvtools.app.AppServices.prefs.forgetDeviceProfile(host)
                                            android.widget.Toast.makeText(
                                                TvToolsApp.instance,
                                                R.string.device_saved_look_removed,
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                },
                            )
                            }
                            if (guest) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.exit_guest_mode)) },
                                    onClick = { menuOpen = false; showExitGuest = true },
                                )
                            }
                            if (!guest) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.appearance_settings)) },
                                    leadingIcon = { Icon(Icons.Default.Settings, null) },
                                    onClick = { menuOpen = false; showSettings = true },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.disconnect)) },
                                onClick = { menuOpen = false; viewModel.disconnect() },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Column {
                val playback by com.tvtools.app.AppServices.playback.state.collectAsState()
                if (playback.active && playback.title.isNotBlank()) {
                    PlaybackBar(
                        state = playback,
                        onPlayPause = com.tvtools.app.AppServices.playback::toggle,
                        onPrevious = com.tvtools.app.AppServices.playback::previous,
                        onNext = com.tvtools.app.AppServices.playback::next,
                        onOpen = onOpenMusic,
                        onClose = com.tvtools.app.AppServices.playback::stop,
                    )
                }
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                if (!guest) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { haptics(); selectedTab = 0 },
                        icon = { Icon(Icons.Default.Build, null) },
                        label = { Text(stringResource(R.string.tools)) },
                    )
                }
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { haptics(); selectedTab = 1 },
                    icon = { Icon(Icons.Default.Apps, null) },
                    label = { Text(stringResource(R.string.apps)) },
                )
                if (!guest) {
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { haptics(); selectedTab = 2 },
                        icon = { Icon(Icons.Default.Terminal, null) },
                        label = { Text(stringResource(R.string.shell)) },
                    )
                }
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { haptics(); selectedTab = 3 },
                    icon = { Icon(Icons.Default.Info, null) },
                    label = { Text(stringResource(R.string.info)) },
                )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (connectedDevice == null) {
                val message = when (connectionStatus) {
                    ConnectionStatus.NO_NETWORK -> R.string.network_no_lan
                    ConnectionStatus.NETWORK_CHANGED -> R.string.network_changed
                    ConnectionStatus.RECONNECTING -> R.string.network_reconnecting
                    ConnectionStatus.UNAVAILABLE -> R.string.network_tv_unreachable
                    ConnectionStatus.AUTHORIZATION_REQUIRED -> R.string.network_authorization_needed
                    else -> R.string.network_tv_disconnected
                }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(stringResource(message), style = MaterialTheme.typography.bodySmall)
                        if (connectionStatus == ConnectionStatus.IDLE ||
                            connectionStatus == ConnectionStatus.UNAVAILABLE
                        ) {
                            TextButton(onClick = viewModel::reconnect) {
                                Text(stringResource(R.string.network_reconnect))
                            }
                        }
                    }
                }
            }
            if (guest) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.guest_mode_remote_apps_only),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { showExitGuest = true }) { Text(stringResource(R.string.exit)) }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxSize().weight(1f)) {
                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (fadeIn(tween(180)) + slideInHorizontally(tween(220)) { dir * it / 12 }) togetherWith
                                (fadeOut(tween(120)) + slideOutHorizontally(tween(180)) { -dir * it / 12 })
                        },
                        label = "tabs",
                    ) { tab ->
                        when (tab) {
                            0 -> ToolsTab(
                                deviceName = deviceName,
                                onOpenFiles = onOpenFiles,
                                onOpenMirror = onOpenMirror,
                                onOpenStorage = onOpenStorage,
                                onOpenMusic = onOpenMusic,
                                onOpenGallery = onOpenGallery,
                                onOpenLab = onOpenLab,
                            )
                            1 -> AppsTab(onOpenBackups = onOpenBackups, guest = guest)
                            2 -> ShellTab()
                            3 -> InfoTab(viewModel = viewModel)
                        }
                    }
                }
                if (wide && showRemote) {
                    androidx.compose.material3.VerticalDivider(modifier = Modifier.fillMaxHeight())
                    RemoteControlSheet(
                        deviceName = deviceName,
                        onDismiss = { showRemote = false },
                        onOpenMirror = onOpenMirror,
                        inline = true,
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight()
                            .padding(start = 4.dp),
                    )
                }
            }
        }
    }

    if (showSettings) {
        SettingsSheet(onDismiss = { showSettings = false })
    }

    if (showExitGuest) {
        AlertDialog(
            onDismissRequest = { showExitGuest = false },
            title = { Text(stringResource(R.string.exit_guest_mode_2)) },
            text = { Text(stringResource(R.string.this_brings_back_tools_shell_files_and)) },
            confirmButton = {
                TextButton(onClick = {
                    com.tvtools.app.AppServices.settings.setGuestMode(false)
                    showExitGuest = false
                }) { Text(stringResource(R.string.exit_guest_mode)) }
            },
            dismissButton = {
                TextButton(onClick = { showExitGuest = false }) { Text(stringResource(R.string.stay)) }
            },
        )
    }

    if (showRemote && !wide) {
        RemoteControlSheet(
            deviceName = deviceName,
            onDismiss = { showRemote = false },
            onOpenMirror = {
                showRemote = false
                onOpenMirror()
            },
        )
    }
}
