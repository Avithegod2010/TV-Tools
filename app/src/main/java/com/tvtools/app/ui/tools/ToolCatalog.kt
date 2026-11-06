package com.tvtools.app.ui.tools

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

enum class ToolCategory(val label: String) {
    TRANSFER("Transfer"),
    DEVICE("Device"),
    MEDIA("Media & display"),
    LABS("Labs"),
}

/**
 * Every action the Tools tab can run. The user picks a handful for the quick
 * grid; the full catalogue is always one tap away in "All features".
 */
data class ToolDef(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val category: ToolCategory,
    val labRoute: String? = null,
)

object ToolCatalog {

    val all: List<ToolDef> = listOf(
        ToolDef(
            id = "install",
            title = "Install",
            subtitle = "Install an APK from this phone",
            icon = Icons.Default.Download,
            category = ToolCategory.TRANSFER,
        ),
        ToolDef(
            id = "upload",
            title = "Upload files",
            subtitle = "Send files to the TV's Downloads folder",
            icon = Icons.Default.Upload,
            category = ToolCategory.TRANSFER,
        ),
        ToolDef(
            id = "files",
            title = "File manager",
            subtitle = "Browse, copy and delete TV files",
            icon = Icons.Default.Folder,
            category = ToolCategory.TRANSFER,
        ),
        ToolDef(
            id = "cache",
            title = "Clear cache",
            subtitle = "Ask the TV to trim app caches",
            icon = Icons.Default.Delete,
            category = ToolCategory.DEVICE,
        ),
        ToolDef(
            id = "mirror",
            title = "Screen mirroring",
            subtitle = "View the TV live and tap to control it",
            icon = Icons.Default.Cast,
            category = ToolCategory.MEDIA,
        ),
        ToolDef(
            id = "storage",
            title = "Storage analyzer",
            subtitle = "Find what is filling up the TV",
            icon = Icons.Default.Storage,
            category = ToolCategory.DEVICE,
        ),
        ToolDef(
            id = "music",
            title = "Music",
            subtitle = "Browse, play and transfer music",
            icon = Icons.Default.MusicNote,
            category = ToolCategory.MEDIA,
        ),
        ToolDef(
            id = "record",
            title = "Screen record",
            subtitle = "Record the TV screen to this phone",
            icon = Icons.Default.Videocam,
            category = ToolCategory.MEDIA,
        ),
        ToolDef(
            id = "gallery",
            title = "Screenshots",
            subtitle = "Captured TV screenshots in one place",
            icon = Icons.Default.PhotoLibrary,
            category = ToolCategory.MEDIA,
        ),
        ToolDef(
            id = "cast",
            title = "Cast media (DLNA)",
            subtitle = "Play a local video on the TV",
            icon = Icons.Default.Cast,
            category = ToolCategory.LABS,
            labRoute = "cast",
        ),
        ToolDef(
            id = "speed",
            title = "Speed test",
            subtitle = "Measure push, pull and ping to the TV",
            icon = Icons.Default.Speed,
            category = ToolCategory.LABS,
            labRoute = "speed",
        ),
        ToolDef(
            id = "livetv",
            title = "Live TV inputs",
            subtitle = "Inspect tuner and HDMI inputs",
            icon = Icons.Default.LiveTv,
            category = ToolCategory.LABS,
            labRoute = "livetv",
        ),
        ToolDef(
            id = "mouse",
            title = "Mouse & keyboard",
            subtitle = "Use the phone as a pointer and keyboard",
            icon = Icons.Default.Mouse,
            category = ToolCategory.LABS,
            labRoute = "mouse",
        ),
        ToolDef(
            id = "health",
            title = "TV health",
            subtitle = "CPU, RAM and temperature history",
            icon = Icons.Default.MonitorHeart,
            category = ToolCategory.LABS,
            labRoute = "health",
        ),
        ToolDef(
            id = "users",
            title = "TV users",
            subtitle = "List and switch Android users",
            icon = Icons.Default.People,
            category = ToolCategory.LABS,
            labRoute = "users",
        ),
        ToolDef(
            id = "sftp",
            title = "SFTP browser",
            subtitle = "Browse the TV over SSH",
            icon = Icons.Default.Folder,
            category = ToolCategory.LABS,
            labRoute = "sftp",
        ),
        ToolDef(
            id = "ir",
            title = "IR blaster",
            subtitle = "Control other devices with the phone",
            icon = Icons.Default.SettingsRemote,
            category = ToolCategory.LABS,
            labRoute = "ir",
        ),
        ToolDef(
            id = "kids",
            title = "Kids mode",
            subtitle = "Hide selected apps from children",
            icon = Icons.Default.ChildCare,
            category = ToolCategory.LABS,
            labRoute = "kids",
        ),
        ToolDef(
            id = "launchpad",
            title = "Launchpad",
            subtitle = "Favourite TV apps in one place",
            icon = Icons.Default.RocketLaunch,
            category = ToolCategory.LABS,
            labRoute = "launchpad",
        ),
        ToolDef(
            id = "automations",
            title = "Automations",
            subtitle = "Daily cache trim, screenshots and wake",
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
