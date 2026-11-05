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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvtools.app.TvToolsApp
import com.tvtools.app.ui.apps.AppsTab
import com.tvtools.app.ui.common.rememberHaptics
import kotlinx.coroutines.launch
import com.tvtools.app.ui.info.InfoTab
import com.tvtools.app.ui.remote.RemoteControlSheet
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
    var selectedTab by remember { mutableIntStateOf(0) }
    var showRemote by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val ctx = com.tvtools.app.TvToolsApp.instance

    LaunchedEffect(Unit) {
        viewModel.disconnected.collect { onBack() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                                "DEVICE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp),
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.disconnect() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Disconnect")
                    }
                },
                actions = {
                    IconButton(onClick = { haptics(); showRemote = true }) {
                        Icon(Icons.Default.SettingsRemote, contentDescription = "Remote control")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Remote control") },
                                leadingIcon = { Icon(Icons.Default.SettingsRemote, null) },
                                onClick = { menuOpen = false; showRemote = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Screen mirroring") },
                                leadingIcon = { Icon(Icons.Default.Cast, null) },
                                onClick = { menuOpen = false; onOpenMirror() },
                            )
                            DropdownMenuItem(
                                text = { Text("Save accent for this TV") },
                                onClick = {
                                    menuOpen = false
                                    val host = com.tvtools.app.AppServices.adb.connectedDevice?.host
                                    if (host != null) {
                                        coroutineScope.launch {
                                            com.tvtools.app.AppServices.prefs.saveDeviceAccent(
                                                host,
                                                com.tvtools.app.AppServices.settings.state.value.accent.name,
                                            )
                                            android.widget.Toast.makeText(
                                                TvToolsApp.instance,
                                                "Accent saved for this TV",
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Disconnect") },
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
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { haptics(); selectedTab = 0 },
                    icon = { Icon(Icons.Default.Build, null) },
                    label = { Text("Tools") },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { haptics(); selectedTab = 1 },
                    icon = { Icon(Icons.Default.Apps, null) },
                    label = { Text("Apps") },
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { haptics(); selectedTab = 2 },
                    icon = { Icon(Icons.Default.Terminal, null) },
                    label = { Text("Shell") },
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { haptics(); selectedTab = 3 },
                    icon = { Icon(Icons.Default.Info, null) },
                    label = { Text("Info") },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
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
                        onOpenFiles = onOpenFiles,
                        onOpenMirror = onOpenMirror,
                        onOpenStorage = onOpenStorage,
                        onOpenMusic = onOpenMusic,
                        onOpenGallery = onOpenGallery,
                        onOpenLab = onOpenLab,
                    )
                    1 -> AppsTab(onOpenBackups = onOpenBackups)
                    2 -> ShellTab()
                    3 -> InfoTab(viewModel = viewModel)
                }
            }
        }
    }

    if (showRemote) {
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
