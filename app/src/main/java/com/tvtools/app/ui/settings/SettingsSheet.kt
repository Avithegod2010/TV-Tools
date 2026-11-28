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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tvtools.app.AppServices
import com.tvtools.app.R
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
            android.widget.Toast.makeText(context, context.getString(R.string.remote_bubble_is_on), android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.overlay_permission_is_needed_for_the_bubble),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        android.widget.Toast.makeText(
            context,
            if (granted) context.getString(R.string.media_notifications_enabled) else context.getString(R.string.notifications_stay_off),
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
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))

            SectionTitle(stringResource(R.string.appearance))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    val label = context.getString(mode.labelRes)
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { haptics(); repo.setThemeMode(mode) },
                        label = { Text(label) },
                    )
                }
            }
            Text(
                when (settings.themeMode) {
                    ThemeMode.AMOLED -> context.getString(R.string.pure_black_surfaces_for_oled_screens)
                    ThemeMode.AUTO -> context.getString(R.string.light_7_00_19_00_dark_at)
                    else -> " "
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            SectionTitle(stringResource(R.string.accent_color))
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
            Text(stringResource(settings.accent.labelRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    haptics()
                    scope.launch {
                        matchStatus = context.getString(R.string.capturing_tv_screen)
                        val color = withContext(Dispatchers.IO) {
                            com.tvtools.app.ui.common.TvPalette.capture(context)
                        }
                        if (color != null) {
                            repo.setTvColor(color)
                            matchStatus = context.getString(R.string.matched_tv_colors)
                        } else {
                            matchStatus = context.getString(R.string.connect_to_a_tv_first_then_retry)
                        }
                    }
                },
            ) { Text(stringResource(R.string.match_tv_colors_screenshot_palette)) }
            matchStatus?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle(stringResource(R.string.connection))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.auto_connect), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.reconnect_to_your_last_tv_on_launch),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LabeledSwitch(
                    label = stringResource(R.string.auto_connect),
                    checked = settings.autoConnect,
                    onCheckedChange = { repo.setAutoConnect(it) },
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle(stringResource(R.string.guest_mode))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.hand_over_the_phone_safely), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.shows_only_the_remote_and_the_app),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LabeledSwitch(
                    label = stringResource(R.string.guest_mode),
                    checked = settings.guestMode,
                    onCheckedChange = { repo.setGuestMode(it) },
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle(stringResource(R.string.floating_bubble))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.remote_bubble), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.a_draggable_shortcut_over_other_apps_tap),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LabeledSwitch(
                    label = stringResource(R.string.floating_remote_bubble),
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
            SectionTitle(stringResource(R.string.remote))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RemoteSkin.entries.forEach { skin ->
                    FilterChip(
                        selected = settings.remoteSkin == skin,
                        onClick = { haptics(); repo.setRemoteSkin(skin) },
                        label = { Text(stringResource(skin.labelRes)) },
                    )
                }
            }
            Text(
                stringResource(R.string.layout_columns_d_pad_size_hidden_rows),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(18.dp))
            SectionTitle(stringResource(R.string.text_density))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                UiDensity.entries.forEach { density ->
                    FilterChip(
                        selected = settings.uiDensity == density,
                        onClick = { haptics(); repo.setUiDensity(density) },
                        label = { Text(stringResource(density.labelRes)) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.text_size), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
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
                    Text(stringResource(R.string.high_contrast), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.stronger_outlines_and_text_for_bright_rooms),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LabeledSwitch(
                    label = stringResource(R.string.high_contrast),
                    checked = settings.highContrast,
                    onCheckedChange = { repo.setHighContrast(it) },
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.colour_blind_safe_signals), style = MaterialTheme.typography.titleSmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                ColorBlindMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.colorBlind == mode,
                        onClick = { haptics(); repo.setColorBlind(mode) },
                        label = { Text(stringResource(mode.labelRes)) },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle(stringResource(R.string.motion_shapes))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.reduced_motion), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.no_press_scaling_or_springy_movement),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LabeledSwitch(
                    label = stringResource(R.string.reduced_motion),
                    checked = settings.reducedMotion,
                    onCheckedChange = { repo.setReducedMotion(it) },
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.expressive_shapes), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.scalloped_d_pad_dial_and_soft_spring),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LabeledSwitch(
                    label = stringResource(R.string.expressive_shapes),
                    checked = settings.expressiveShapes,
                    onCheckedChange = { repo.setExpressiveShapes(it) },
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle(stringResource(R.string.sound_pack))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                SoundPack.entries.forEach { pack ->
                    FilterChip(
                        selected = settings.soundPack == pack,
                        onClick = { haptics(); repo.setSoundPack(pack) },
                        label = { Text(stringResource(pack.labelRes)) },
                    )
                }
            }
            Text(
                if (settings.sounds) context.getString(R.string.used_by_key_sounds) else context.getString(R.string.turn_on_key_sounds_above_to_hear),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(18.dp))
            SectionTitle(stringResource(R.string.media_notification))
            Text(
                stringResource(R.string.shows_what_is_playing_on_the_tv),
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
                        context.getString(R.string.notifications_are_already_allowed),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }) { Text(stringResource(R.string.allow_media_notification)) }

            Spacer(Modifier.height(18.dp))
            SectionTitle(stringResource(R.string.kids_theme))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.oversized_playful_safe), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.bigger_text_warm_palette_and_guest_mode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LabeledSwitch(
                    label = stringResource(R.string.kids_theme),
                    checked = settings.kidsTheme,
                    onCheckedChange = { repo.setKidsTheme(it) },
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle(stringResource(R.string.tv_look_profiles))
            Text(
                stringResource(R.string.a_saved_look_applies_automatically_when_you),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            val connectedHost = AppServices.adb.connectedDevice?.preferencesHost
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
                            context.getString(R.string.look_saved_for_this_tv),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            ) {
                Text(if (connectedHost == null) context.getString(R.string.connect_to_a_tv_to_save_its) else context.getString(R.string.save_current_look_for_this_tv))
            }
            if (savedDevices.isEmpty()) {
                Text(
                    stringResource(R.string.no_saved_tvs),
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
                            ThemePack.summary(profile, context),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = {
                        haptics()
                        repo.applyProfile(profile)
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.applied_device_look, device.name),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }) { Text(stringResource(R.string.apply)) }
                    TextButton(onClick = {
                        scope.launch {
                            AppServices.prefs.forgetDeviceProfile(device.host)
                            savedDevices = AppServices.prefs.loadDevices()
                            profiles = AppServices.prefs.loadDeviceProfiles()
                        }
                    }) { Text(stringResource(R.string.forget)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    haptics()
                    scope.launch {
                        val host = AppServices.adb.connectedDevice?.preferencesHost
                        val profile = if (host != null) {
                            val existing = AppServices.prefs.loadDeviceProfile(host)
                            repo.currentProfile().copy(
                                layout = existing?.layout ?: com.tvtools.app.data.RemoteLayout(),
                            )
                        } else {
                            repo.currentProfile()
                        }
                        shareTheme(context, profile)
                    }
                }) { Text(stringResource(R.string.share_look)) }
                OutlinedButton(onClick = {
                    haptics()
                    val imported = importTheme(context)
                    if (imported != null) {
                        repo.applyProfile(imported)
                        val host = AppServices.adb.connectedDevice?.preferencesHost
                        if (host != null) {
                            scope.launch {
                                AppServices.prefs.saveDeviceProfile(host, imported)
                                savedDevices = AppServices.prefs.loadDevices()
                                profiles = AppServices.prefs.loadDeviceProfiles()
                            }
                        }
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.look_imported),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.copy_a_tv_tools_look_to_the),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                }) { Text(stringResource(R.string.import_from_clipboard)) }
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle(stringResource(R.string.screenshots))
            Text(
                stringResource(R.string.screenshots_are_only_taken_when_you_tap),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.keep_the_newest), style = MaterialTheme.typography.titleSmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                listOf(10, 25, 50, 100, 0).forEach { limit ->
                    FilterChip(
                        selected = settings.screenshotLimit == limit,
                        onClick = { haptics(); repo.setScreenshotLimit(limit) },
                        label = { Text(if (limit == 0) context.getString(R.string.unlimited) else "$limit") },
                    )
                }
            }
            Text(
                if (settings.screenshotLimit == 0) {
                    context.getString(R.string.old_screenshots_are_kept_until_you_delete)
                } else {
                    context.getString(R.string.screenshot_retention_limit, settings.screenshotLimit)
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
                android.widget.Toast.makeText(context, context.getString(R.string.screenshot_gallery_cleared), android.widget.Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.clear_screenshot_gallery)) }

            Spacer(Modifier.height(18.dp))
            SectionTitle(stringResource(R.string.feedback))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HapticStrength.entries.forEach { s ->
                    FilterChip(
                        selected = settings.hapticStrength == s,
                        onClick = { repo.setHapticStrength(s); haptics() },
                        label = { Text(stringResource(s.labelRes)) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.haptic_feedback), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.vibrate_on_keys_and_buttons), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LabeledSwitch(
                    label = stringResource(R.string.haptic_feedback),
                    checked = settings.haptics,
                    onCheckedChange = { repo.setHaptics(it) },
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.key_sounds), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.soft_click_on_remote_keys), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LabeledSwitch(
                    label = stringResource(R.string.key_sounds),
                    checked = settings.sounds,
                    onCheckedChange = { repo.setSounds(it) },
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle(stringResource(R.string.app_icon))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("default" to "TV", "remote" to "Remote", "mono" to context.getString(R.string.mono)).forEach { (key, label) ->
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
                stringResource(R.string.changes_the_launcher_icon_may_take_a),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(18.dp))
            SectionTitle(stringResource(R.string.performance))
            var startupMs by remember {
                mutableStateOf(com.tvtools.app.data.Performance.lastStartupMs(context))
            }
            var jank by remember {
                mutableStateOf(com.tvtools.app.data.Performance.lastJank(context))
            }
            Text(
                startupMs?.let { context.getString(R.string.cold_start_duration, it) }
                    ?: context.getString(R.string.cold_start_not_measured_yet),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                jank?.let { (bad, total) ->
                    val percent = if (total > 0) bad * 100f / total else 0f
                    context.getString(R.string.last_session_jank, bad, total, percent.toInt())
                } ?: context.getString(R.string.frame_jank_not_measured_yet),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.measured_on_this_phone_only_nothing_is),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = {
                com.tvtools.app.data.Performance.reset(context)
                startupMs = null
                jank = null
            }) { Text(stringResource(R.string.reset_measurements)) }

            Spacer(Modifier.height(18.dp))
            SectionTitle(stringResource(R.string.diagnostics))
            var lastCrash by remember { mutableStateOf<String?>(null) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                lastCrash = com.tvtools.app.data.Diagnostics.lastCrash(context)
            }
            Text(
                if (lastCrash == null) context.getString(R.string.no_crashes_recorded_reports_stay_on_this)
                else context.getString(R.string.a_crash_report_was_saved_locally_copy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(
                        android.content.ClipData.newPlainText(
                            context.getString(R.string.tv_tools_diagnostics),
                            com.tvtools.app.data.Diagnostics.copyReport(context),
                        )
                    )
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.diagnostics_copied_nothing_was_uploaded),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }) { Text(stringResource(R.string.copy_diagnostics)) }
                if (lastCrash != null) {
                    TextButton(onClick = {
                        com.tvtools.app.data.Diagnostics.clear(context)
                        lastCrash = null
                    }) { Text(stringResource(R.string.clear_crash)) }
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
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.tv_tools_look))
        putExtra(Intent.EXTRA_TEXT, ThemePack.encode(profile))
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(share, context.getString(R.string.share_tv_tools_look))
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
