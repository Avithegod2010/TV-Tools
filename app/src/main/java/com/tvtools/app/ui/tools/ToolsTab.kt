package com.tvtools.app.ui.tools

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvtools.app.TvToolsApp
import com.tvtools.app.ui.common.InfoCard
import com.tvtools.app.ui.common.rememberHaptics

@Composable
fun ToolsTab(
    onOpenFiles: () -> Unit,
    onOpenMirror: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenMusic: () -> Unit,
    viewModel: ToolsViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    val phoneApps by viewModel.phoneApps.collectAsState()
    val phoneAppsLoading by viewModel.phoneAppsLoading.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = TvToolsApp.instance
    val haptics = rememberHaptics()

    var showPhoneApps by remember { mutableStateOf(false) }
    var showUploadOptions by remember { mutableStateOf(false) }
    var showRecordDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(Unit) { viewModel.takeScreenshot() }

    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::installApk)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.uploadFiles(listOf(it)) }
    }
    val multiPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> if (uris.isNotEmpty()) viewModel.uploadFiles(uris) }
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let(viewModel::uploadFolder) }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolAction(
                    title = "Install",
                    icon = { Icon(Icons.Default.Download, null) },
                    modifier = Modifier.weight(1f),
                    onClick = { apkPicker.launch("*/*") },
                    onLongClick = {
                        viewModel.loadPhoneApps()
                        showPhoneApps = true
                    },
                )
                ToolAction(
                    title = "Upload to\nDownloads",
                    icon = { Icon(Icons.Default.Upload, null) },
                    modifier = Modifier.weight(1f),
                    onClick = { filePicker.launch("*/*") },
                    onLongClick = { showUploadOptions = true },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolAction(
                    title = "File manager",
                    icon = { Icon(Icons.Default.Folder, null) },
                    modifier = Modifier.weight(1f),
                    onClick = onOpenFiles,
                )
                ToolAction(
                    title = "Clear cache",
                    icon = { Icon(Icons.Default.Delete, null) },
                    modifier = Modifier.weight(1f),
                    onClick = viewModel::clearCache,
                )
            }

            BannerCard(
                icon = Icons.Default.Cast,
                title = "Screen mirroring",
                subtitle = "View your TV live and tap to control it",
                onClick = onOpenMirror,
            )
            BannerCard(
                icon = Icons.Default.Folder,
                title = "Storage analyzer",
                subtitle = "Find what's filling up your TV and clean it",
                onClick = onOpenStorage,
            )
            BannerCard(
                icon = Icons.Default.MusicNote,
                title = "Music manager",
                subtitle = "Browse, play and transfer music on the TV",
                onClick = onOpenMusic,
            )
            BannerCard(
                icon = Icons.Default.Videocam,
                title = "Screen record",
                subtitle = "Record the TV screen and save it to your phone",
                onClick = { showRecordDialog = true },
            )

            ui.busyLabel?.let { label ->
                InfoCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            strokeCap = StrokeCap.Round,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (ui.progress >= 0f) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { ui.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            InfoCard(Modifier.fillMaxWidth()) {
                Text("Screenshot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                val shot = ui.screenshot
                when {
                    ui.screenshotLoading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeCap = StrokeCap.Round)
                        Spacer(Modifier.width(12.dp))
                        Text("Capturing…")
                    }
                    shot != null -> {
                        Image(
                            bitmap = shot,
                            contentDescription = "TV screenshot",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        Spacer(Modifier.height(12.dp))
                        ActionButton("Update", { Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)) }) {
                            viewModel.takeScreenshot()
                        }
                        Spacer(Modifier.height(8.dp))
                        ActionButton("Share", { Icon(Icons.Default.Share, null, Modifier.size(18.dp)) }) {
                            shareScreenshot(context, viewModel)
                        }
                        Spacer(Modifier.height(8.dp))
                        ActionButton("Download", { Icon(Icons.Default.Download, null, Modifier.size(18.dp)) }) {
                            viewModel.saveScreenshotToDownloads()
                        }
                    }
                    else -> ActionButton("Take screenshot", { Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)) }) {
                        viewModel.takeScreenshot()
                    }
                }
            }

            Spacer(Modifier.height(96.dp))
        }

        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showPhoneApps) {
        PhoneAppsDialog(
            apps = phoneApps,
            loading = phoneAppsLoading,
            onDismiss = { showPhoneApps = false },
            onPick = { item ->
                showPhoneApps = false
                viewModel.installPhoneApp(item)
            },
        )
    }

    if (showUploadOptions) {
        AlertDialog(
            onDismissRequest = { showUploadOptions = false },
            title = { Text("Upload to TV") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showUploadOptions = false; multiPicker.launch("*/*") },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Pick multiple files") }
                    Button(
                        onClick = { showUploadOptions = false; folderPicker.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Pick a folder") }
                }
            },
            confirmButton = { TextButton(onClick = { showUploadOptions = false }) { Text("Cancel") } },
        )
    }

    if (showRecordDialog) {
        ScreenRecordDialog(
            onDismiss = { showRecordDialog = false },
            onStart = { seconds ->
                showRecordDialog = false
                viewModel.screenRecord(seconds)
            },
        )
    }
}

@Composable
private fun BannerCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    Card(
        onClick = { haptics(); onClick() },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToolAction(
    title: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    Card(
        modifier = modifier.pointerInput(onLongClick) {
            detectTapGestures(
                onTap = { haptics(); onClick() },
                onLongPress = { onLongClick?.let { haptics(); it() } },
            )
        },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            icon()
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            if (onLongClick != null) {
                Text(
                    "hold for more",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PhoneAppsDialog(
    apps: List<PhoneAppItem>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onPick: (PhoneAppItem) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Install a phone app on the TV") },
        text = {
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Loading phone apps…")
                }
            } else {
                LazyColumn(modifier = Modifier.height(380.dp)) {
                    items(apps, key = { it.packageName }) { item ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PhoneAppIcon(item)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(
                                    item.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            TextButton(onClick = { onPick(item) }) { Text("Install") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun PhoneAppIcon(item: PhoneAppItem) {
    val bmp = remember(item.packageName) {
        item.icon?.let { runCatching { it.toBitmap(44, 44).asImageBitmap() }.getOrNull() }
    }
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
        )
    } else {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(item.label.take(1), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun ScreenRecordDialog(onDismiss: () -> Unit, onStart: (Int) -> Unit) {
    var seconds by remember { mutableStateOf(30) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Screen record TV") },
        text = {
            Column {
                Text(
                    "Records the TV screen with `screenrecord` and saves it to your phone's Movies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60).forEach { s ->
                        FilterChip(
                            selected = seconds == s,
                            onClick = { seconds = s },
                            label = { Text("${s}s") },
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onStart(seconds) }) { Text("Start recording") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ActionButton(text: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(50)) {
        icon()
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

private fun shareScreenshot(context: android.content.Context, viewModel: ToolsViewModel) {
    try {
        val file = viewModel.screenshotFile
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share screenshot"))
    } catch (_: Exception) {
    }
}
