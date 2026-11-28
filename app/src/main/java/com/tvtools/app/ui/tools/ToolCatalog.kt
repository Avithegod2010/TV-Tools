package com.tvtools.app.ui.tools

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import com.tvtools.app.R

enum class ToolCategory(@StringRes val label: Int) {
    TRANSFER(R.string.transfer),
    DEVICE(R.string.device),
    MEDIA(R.string.media_display),
    LABS(R.string.labs),
}

/**
 * Every action the Tools tab can run. The user picks a handful for the quick
 * grid; the full catalogue is always one tap away in "All features".
 */
data class ToolDef(
    val id: String,
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val icon: ImageVector,
    val category: ToolCategory,
    val labRoute: String? = null,
)

object ToolCatalog {

    val all: List<ToolDef> = listOf(
        ToolDef(
            id = "install",
            title = R.string.install,
            subtitle = R.string.install_an_apk_from_this_phone,
            icon = Icons.Default.Download,
            category = ToolCategory.TRANSFER,
        ),
        ToolDef(
            id = "upload",
            title = R.string.upload_files,
            subtitle = R.string.send_files_to_the_tvs_downloads_folder,
            icon = Icons.Default.Upload,
            category = ToolCategory.TRANSFER,
        ),
        ToolDef(
            id = "files",
            title = R.string.file_manager,
            subtitle = R.string.browse_copy_and_delete_tv_files,
            icon = Icons.Default.Folder,
            category = ToolCategory.TRANSFER,
        ),
        ToolDef(
            id = "cache",
            title = R.string.clear_cache,
            subtitle = R.string.ask_the_tv_to_trim_app_caches,
            icon = Icons.Default.Delete,
            category = ToolCategory.DEVICE,
        ),
        ToolDef(
            id = "mirror",
            title = R.string.screen_mirroring,
            subtitle = R.string.view_the_tv_live_and_tap_to,
            icon = Icons.Default.Cast,
            category = ToolCategory.MEDIA,
        ),
        ToolDef(
            id = "storage",
            title = R.string.storage_analyzer,
            subtitle = R.string.find_what_is_filling_up_the_tv,
            icon = Icons.Default.Storage,
            category = ToolCategory.DEVICE,
        ),
        ToolDef(
            id = "music",
            title = R.string.music,
            subtitle = R.string.browse_play_and_transfer_music,
            icon = Icons.Default.MusicNote,
            category = ToolCategory.MEDIA,
        ),
        ToolDef(
            id = "record",
            title = R.string.screen_record,
            subtitle = R.string.record_the_tv_screen_to_this_phone,
            icon = Icons.Default.Videocam,
            category = ToolCategory.MEDIA,
        ),
        ToolDef(
            id = "gallery",
            title = R.string.screenshots,
            subtitle = R.string.captured_tv_screenshots_in_one_place,
            icon = Icons.Default.PhotoLibrary,
            category = ToolCategory.MEDIA,
        ),
        ToolDef(
            id = "cast",
            title = R.string.cast_media_dlna,
            subtitle = R.string.play_a_local_video_on_the_tv,
            icon = Icons.Default.Cast,
            category = ToolCategory.LABS,
            labRoute = "cast",
        ),
        ToolDef(
            id = "speed",
            title = R.string.speed_test,
            subtitle = R.string.measure_push_pull_and_ping_to_the,
            icon = Icons.Default.Speed,
            category = ToolCategory.LABS,
            labRoute = "speed",
        ),
        ToolDef(
            id = "livetv",
            title = R.string.live_tv_inputs,
            subtitle = R.string.inspect_tuner_and_hdmi_inputs,
            icon = Icons.Default.LiveTv,
            category = ToolCategory.LABS,
            labRoute = "livetv",
        ),
        ToolDef(
            id = "mouse",
            title = R.string.mouse_keyboard,
            subtitle = R.string.use_the_phone_as_a_pointer_and,
            icon = Icons.Default.Mouse,
            category = ToolCategory.LABS,
            labRoute = "mouse",
        ),
        ToolDef(
            id = "health",
            title = R.string.tv_health,
            subtitle = R.string.cpu_ram_and_temperature_history,
            icon = Icons.Default.MonitorHeart,
            category = ToolCategory.LABS,
            labRoute = "health",
        ),
        ToolDef(
            id = "users",
            title = R.string.tv_users,
            subtitle = R.string.list_and_switch_android_users,
            icon = Icons.Default.People,
            category = ToolCategory.LABS,
            labRoute = "users",
        ),
        ToolDef(
            id = "sftp",
            title = R.string.sftp_browser,
            subtitle = R.string.browse_the_tv_over_ssh,
            icon = Icons.Default.Folder,
            category = ToolCategory.LABS,
            labRoute = "sftp",
        ),
        ToolDef(
            id = "ir",
            title = R.string.ir_blaster,
            subtitle = R.string.control_other_devices_with_the_phone,
            icon = Icons.Default.SettingsRemote,
            category = ToolCategory.LABS,
            labRoute = "ir",
        ),
        ToolDef(
            id = "kids",
            title = R.string.kids_mode,
            subtitle = R.string.hide_selected_apps_from_children,
            icon = Icons.Default.ChildCare,
            category = ToolCategory.LABS,
            labRoute = "kids",
        ),
        ToolDef(
            id = "launchpad",
            title = R.string.launchpad,
            subtitle = R.string.favourite_tv_apps_in_one_place,
            icon = Icons.Default.RocketLaunch,
            category = ToolCategory.LABS,
            labRoute = "launchpad",
        ),
        ToolDef(
            id = "automations",
            title = R.string.automations,
            subtitle = R.string.daily_cache_trim_screenshots_and_wake,
            icon = Icons.Default.Schedule,
            category = ToolCategory.LABS,
            labRoute = "automations",
        ),
    )

    /** Sensible first-run quick grid; still fully editable. */
    val defaultIds: List<String> = listOf(
        "install", "upload", "files", "mirror", "gallery", "music",
    )

    fun byId(id: String): ToolDef? = all.firstOrNull { it.id == id }

    fun normalized(ids: List<String>): List<String> = ids.distinct().filter { byId(it) != null }
}
