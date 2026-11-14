package com.tvtools.app.ui.settings

import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tvtools.app.AppServices
import com.tvtools.app.TvToolsApp
import com.tvtools.app.data.Accent
import com.tvtools.app.data.ColorBlindMode
import com.tvtools.app.data.DeviceProfile
import com.tvtools.app.data.HapticStrength
import com.tvtools.app.data.RemoteSkin
import com.tvtools.app.data.SoundPack
import com.tvtools.app.data.ThemeMode
import com.tvtools.app.data.ThemePack
import com.tvtools.app.data.TvDevice
import com.tvtools.app.data.UiDensity
import com.tvtools.app.ui.common.rememberHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val accentColors = mapOf(
    Accent.GREEN to Color(0xFF166D32),
    Accent.BLUE to Color(0xFF1565C0),
    Accent.VIOLET to Color(0xFF6A4CBF),
    Accent.ORANGE to Color(0xFFB45309),
    Accent.DYNAMIC to Color(0xFF8A8F98),
    Accent.TV to Color(0xFF3A7BD5),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    val settings by AppServices.settings.state.collectAsState()
    val repo = AppServices.settings
    val haptics = rememberHaptics()
    val context = TvToolsApp.instance
    val scope = rememberCoroutineScope()
    var matchStatus by remember { mutableStateOf<String?>(null) }
    var savedDevices by remember { mutableStateOf<List<TvDevice>>(emptyList()) }
    var profiles by remember { mutableStateOf<Map<String, DeviceProfile>>(emptyMap()) }

    LaunchedEffect(Unit) {
        savedDevices = AppServices.prefs.loadDevices()
        profiles = AppServices.prefs.loadDeviceProfiles()
    }

    val overlayPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        if (android.provider.Settings.canDrawOverlays(context)) {
            repo.setBubble(true)
            com.tvtools.app.service.OverlayBubbleService.start(context)
            android.widget.Toast.makeText(context, "Remote bubble is on", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(
                context,
                "Overlay permission is needed for the bubble",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        android.widget.Toast.makeText(
            context,
            if (granted) "Media notifications enabled" else "Notifications stay off",
            android.widget.Toast.LENGTH_SHORT,
        ).show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))

            SectionTitle("Appearance")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    ThemeMode.SYSTEM to "System",
                    ThemeMode.LIGHT to "Light",
                    ThemeMode.DARK to "Dark",
                    ThemeMode.AMOLED to "AMOLED",
                    ThemeMode.AUTO to "Auto",
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { haptics(); repo.setThemeMode(mode) },
                        label = { Text(label) },
                    )
                }
            }
            Text(
                when (settings.themeMode) {
                    ThemeMode.AMOLED -> "Pure-black surfaces for OLED screens"
                    ThemeMode.AUTO -> "Light 7:00–19:00, dark at night"
                    else -> " "
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            SectionTitle("Accent color")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Accent.entries.forEach { accent ->
                    val selected = settings.accent == accent
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (accent == Accent.TV) Color(settings.tvColor)
                                else accentColors[accent] ?: MaterialTheme.colorScheme.primary
                            )
                            .border(
                                width = if (selected) 3.dp else 0.dp,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = CircleShape,
                            )
                            .selectable(selected = selected) {
                                haptics()
                                repo.setAccent(accent)
                            },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(settings.accent.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    haptics()
                    scope.launch {
                        matchStatus = "Capturing TV screen…"
                        val color = withContext(Dispatchers.IO) {
                            com.tvtools.app.ui.common.TvPalette.capture(context)
                        }
                        if (color != null) {
                            repo.setTvColor(color)
                            matchStatus = "Matched TV colors ✓"
                        } else {
                            matchStatus = "Connect to a TV first, then retry."
                        }
                    }
                },
            ) { Text("Match TV colors (screenshot palette)") }
            matchStatus?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle("Connection")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-connect", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Reconnect to your last TV on launch and when Wi-Fi returns",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LabeledSwitch(
                    label = "Auto-connect",
                    checked = settings.autoConnect,
                    onCheckedChange = { repo.setAutoConnect(it) },
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle("Guest mode")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hand over the phone safely", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Shows only the remote and the app launcher — hides Tools, Shell, Files and destructive app actions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LabeledSwitch(
                    label = "Guest mode",
                    checked = settings.guestMode,
                    onCheckedChange = { repo.setGuestMode(it) },
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle("Floating bubble")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Remote bubble", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "A draggable shortcut over other apps — tap it to jump back to the remote",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LabeledSwitch(
                    label = "Floating remote bubble",
                    checked = settings.bubble,
                    onCheckedChange = { enabled ->
                        if (!enabled) {
                            repo.setBubble(false)
                            com.tvtools.app.service.OverlayBubbleService.stop(context)
                        } else if (android.provider.Settings.canDrawOverlays(context)) {
                            repo.setBubble(true)
                            com.tvtools.app.service.OverlayBubbleService.start(context)
                        } else {
                            overlayPermissionLauncher.launch(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}"),
                                )
                            )
                        }
                    },
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle("Remote")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RemoteSkin.entries.forEach { skin ->
                    FilterChip(
                        selected = settings.remoteSkin == skin,
                        onClick = { haptics(); repo.setRemoteSkin(skin) },
                        label = { Text(skin.label) },
                    )
                }
            }
            Text(
                "Layout — columns, D-pad size, hidden rows and quick keys — is edited from the Tune icon in the remote and saved per TV.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(18.dp))
            SectionTitle("Text & density")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                UiDensity.entries.forEach { density ->
                    FilterChip(
                        selected = settings.uiDensity == density,
                        onClick = { haptics(); repo.setUiDensity(density) },
                        label = { Text(density.label) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Text size", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("${(settings.fontScale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            }
            Slider(
                value = settings.fontScale,
                onValueChange = { repo.setFontScale(it) },
                valueRange = 0.8f..2f,
                steps = 5,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("High contrast", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Stronger outlines and text for bright rooms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LabeledSwitch(
                    label = "High contrast",
                    checked = settings.highContrast,
                    onCheckedChange = { repo.setHighContrast(it) },
                )
            }

            Spacer(Modifier.height(10.dp))
            Text("Colour-blind safe signals", style = MaterialTheme.typography.titleSmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                ColorBlindMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.colorBlind == mode,
                        onClick = { haptics(); repo.setColorBlind(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle("Motion & shapes")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Reduced motion", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "No press scaling or springy movement",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LabeledSwitch(
                    label = "Reduced motion",
                    checked = settings.reducedMotion,
                    onCheckedChange = { repo.setReducedMotion(it) },
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Expressive shapes", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Scalloped D-pad dial and soft spring feedback",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LabeledSwitch(
                    label = "Expressive shapes",
                    checked = settings.expressiveShapes,
                    onCheckedChange = { repo.setExpressiveShapes(it) },
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle("Sound pack")
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                SoundPack.entries.forEach { pack ->
                    FilterChip(
                        selected = settings.soundPack == pack,
                        onClick = { haptics(); repo.setSoundPack(pack) },
                        label = { Text(pack.label) },
                    )
                }
            }
            Text(
                if (settings.sounds) "Used by key sounds." else "Turn on key sounds above to hear it.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(18.dp))
            SectionTitle("Media notification")
            Text(
                "Shows what is playing on the TV with previous / play-pause / next, outside the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = {
                haptics()
                val needsPermission = android.os.Build.VERSION.SDK_INT >= 33 &&
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS,
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                if (needsPermission) {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    android.widget.Toast.makeText(
                        context,
                        "Notifications are already allowed",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }) { Text("Allow media notification") }

            Spacer(Modifier.height(18.dp))
            SectionTitle("Kids theme")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Oversized, playful, safe", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Bigger text, warm palette and guest mode so destructive actions stay locked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LabeledSwitch(
                    label = "Kids theme",
                    checked = settings.kidsTheme,
                    onCheckedChange = { repo.setKidsTheme(it) },
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle("TV look profiles")
            Text(
                "A saved look applies automatically when you open that TV.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            val connectedHost = AppServices.adb.connectedDevice?.host
            OutlinedButton(
                enabled = connectedHost != null,
                onClick = {
                    val host = connectedHost ?: return@OutlinedButton
                    haptics()
                    scope.launch {
                        val existing = AppServices.prefs.loadDeviceProfile(host)
                        AppServices.prefs.saveDeviceProfile(
                            host,
                            repo.currentProfile().copy(
                                layout = existing?.layout ?: com.tvtools.app.data.RemoteLayout(),
                            ),
                        )
                        savedDevices = AppServices.prefs.loadDevices()
                        profiles = AppServices.prefs.loadDeviceProfiles()
                        android.widget.Toast.makeText(
                            context,
                            "Look saved for this TV",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            ) {
                Text(if (connectedHost == null) "Connect to a TV to save its look" else "Save current look for this TV")
            }
            if (savedDevices.isEmpty()) {
                Text(
                    "No saved TVs yet.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            savedDevices.forEach { device ->
                val profile = profiles[device.host] ?: return@forEach
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(device.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            ThemePack.summary(profile),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = {
                        haptics()
                        repo.applyProfile(profile)
                        android.widget.Toast.makeText(
                            context,
                            "Applied ${device.name}'s look",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }) { Text("Apply") }
                    TextButton(onClick = {
                        scope.launch {
                            AppServices.prefs.forgetDeviceProfile(device.host)
                            savedDevices = AppServices.prefs.loadDevices()
                            profiles = AppServices.prefs.loadDeviceProfiles()
                        }
                    }) { Text("Forget") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    haptics()
                    val host = AppServices.adb.connectedDevice?.host
                    val profile = if (host != null) {
                        val existing = AppServices.prefs.loadDeviceProfile(host)
                        repo.currentProfile().copy(
                            layout = existing?.layout ?: com.tvtools.app.data.RemoteLayout(),
                        )
                    } else {
                        repo.currentProfile()
                    }
                    shareTheme(context, profile)
                }) { Text("Share look") }
                OutlinedButton(onClick = {
                    haptics()
                    val imported = importTheme(context)
                    if (imported != null) {
                        repo.applyProfile(imported)
                        val host = AppServices.adb.connectedDevice?.host
                        if (host != null) {
                            scope.launch {
                                AppServices.prefs.saveDeviceProfile(host, imported)
                                savedDevices = AppServices.prefs.loadDevices()
                                profiles = AppServices.prefs.loadDeviceProfiles()
                            }
                        }
                        android.widget.Toast.makeText(
                            context,
                            "Look imported",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            "Copy a TV Tools look to the clipboard first",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                }) { Text("Import from clipboard") }
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle("Screenshots")
            Text(
                "Screenshots are only taken when you tap the button in Tools — never in the background.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text("Keep the newest", style = MaterialTheme.typography.titleSmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                listOf(10, 25, 50, 100, 0).forEach { limit ->
                    FilterChip(
                        selected = settings.screenshotLimit == limit,
                        onClick = { haptics(); repo.setScreenshotLimit(limit) },
                        label = { Text(if (limit == 0) "Unlimited" else "$limit") },
                    )
                }
            }
            Text(
                if (settings.screenshotLimit == 0) {
                    "Old screenshots are kept until you delete them."
                } else {
                    "Older screenshots are deleted automatically after ${settings.screenshotLimit} captures."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                haptics()
                scope.launch(Dispatchers.IO) {
                    com.tvtools.app.data.ScreenshotStore.clear(context)
                }
                android.widget.Toast.makeText(context, "Screenshot gallery cleared", android.widget.Toast.LENGTH_SHORT).show()
            }) { Text("Clear screenshot gallery") }

            Spacer(Modifier.height(18.dp))
            SectionTitle("Feedback")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HapticStrength.entries.forEach { s ->
                    FilterChip(
                        selected = settings.hapticStrength == s,
                        onClick = { repo.setHapticStrength(s); haptics() },
                        label = { Text(s.label) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Haptic feedback", style = MaterialTheme.typography.titleSmall)
                    Text("Vibrate on keys and buttons", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LabeledSwitch(
                    label = "Haptic feedback",
                    checked = settings.haptics,
                    onCheckedChange = { repo.setHaptics(it) },
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Key sounds", style = MaterialTheme.typography.titleSmall)
                    Text("Soft click on remote keys", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LabeledSwitch(
                    label = "Key sounds",
                    checked = settings.sounds,
                    onCheckedChange = { repo.setSounds(it) },
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle("App icon")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("default" to "TV", "remote" to "Remote", "mono" to "Mono").forEach { (key, label) ->
                    FilterChip(
                        selected = settings.appIcon == key,
                        onClick = {
                            haptics()
                            repo.setAppIcon(key)
                            setLauncherIcon(context, key)
                        },
                        label = { Text(label) },
                    )
                }
            }
            Text(
                "Changes the launcher icon (may take a moment to refresh).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(18.dp))
            SectionTitle("Performance")
            var startupMs by remember {
                mutableStateOf(com.tvtools.app.data.Performance.lastStartupMs(context))
            }
            var jank by remember {
                mutableStateOf(com.tvtools.app.data.Performance.lastJank(context))
            }
            Text(
                startupMs?.let { "Cold start: $it ms (process → first frame)" }
                    ?: "Cold start: not measured yet",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                jank?.let { (bad, total) ->
                    val percent = if (total > 0) bad * 100f / total else 0f
                    "Jank: $bad of $total frames (${percent.toInt()}%) last session"
                } ?: "Frame jank: not measured yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Measured on this phone only. Nothing is uploaded.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = {
                com.tvtools.app.data.Performance.reset(context)
                startupMs = null
                jank = null
            }) { Text("Reset measurements") }

            Spacer(Modifier.height(18.dp))
            SectionTitle("Diagnostics")
            var lastCrash by remember { mutableStateOf<String?>(null) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                lastCrash = com.tvtools.app.data.Diagnostics.lastCrash(context)
            }
            Text(
                if (lastCrash == null) "No crashes recorded. Reports stay on this phone."
                else "A crash report was saved locally. Copy it into a bug report if you like.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(
                        android.content.ClipData.newPlainText(
                            "TV Tools diagnostics",
                            com.tvtools.app.data.Diagnostics.copyReport(context),
                        )
                    )
                    android.widget.Toast.makeText(
                        context,
                        "Diagnostics copied — nothing was uploaded",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }) { Text("Copy diagnostics") }
                if (lastCrash != null) {
                    TextButton(onClick = {
                        com.tvtools.app.data.Diagnostics.clear(context)
                        lastCrash = null
                    }) { Text("Clear crash") }
                }
            }

            Spacer(Modifier.height(18.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                "TV Tools • v" + runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull().orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

/** A switch that announces its row label to TalkBack. */
@Composable
private fun LabeledSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.semantics { contentDescription = label },
    )
}

/** Share the live look as portable text (no files, no network). */
private fun shareTheme(context: Context, profile: DeviceProfile) {
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "TV Tools look")
        putExtra(Intent.EXTRA_TEXT, ThemePack.encode(profile))
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(share, "Share TV Tools look")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun importTheme(context: Context): DeviceProfile? {
    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    val text = clip.getItemAt(0).coerceToText(context)?.toString() ?: return null
    return ThemePack.decode(text)
}

/** Switch between launcher activity-aliases (TV / Remote / Mono icons). */
private fun setLauncherIcon(context: Context, which: String) {
    val pm = context.packageManager
    listOf(
        "MainDefault" to (which == "default"),
        "MainRemote" to (which == "remote"),
        "MainMono" to (which == "mono"),
    ).forEach { (alias, enable) ->
        runCatching {
            pm.setComponentEnabledSetting(
                ComponentName(context, "${context.packageName}.$alias"),
                if (enable) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
