package com.tvtools.app.ui.tools

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
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
    onOpenGallery: () -> Unit,
    onOpenLab: (String) -> Unit,
    viewModel: ToolsViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    val phoneApps by viewModel.phoneApps.collectAsState()
    val phoneAppsLoading by viewModel.phoneAppsLoading.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = TvToolsApp.instance
    val haptics = rememberHaptics()

    var showPhoneApps by remember { mutableStateOf(false) }
    var showUploadOptions by remember { mutableStateOf(false) }
    var showRecordDialog by remember { mutableStateOf(false) }
    var showAllFeatures by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

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

    fun openTool(tool: ToolDef) {
        when (tool.id) {
            "install" -> apkPicker.launch("*/*")
            "upload" -> filePicker.launch("*/*")
            "files" -> onOpenFiles()
            "cache" -> viewModel.clearCache()
            "mirror" -> onOpenMirror()
            "storage" -> onOpenStorage()
            "music" -> onOpenMusic()
            "record" -> showRecordDialog = true
            "gallery" -> onOpenGallery()
            else -> tool.labRoute?.let(onOpenLab)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Quick tools",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Pick the tools you use most",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { showAllFeatures = true }) { Text("All features") }
            }

            val quickTools = favorites.mapNotNull(ToolCatalog::byId)
            if (quickTools.isEmpty()) {
                InfoCard(Modifier.fillMaxWidth()) {
                    Text("Your quick grid is empty", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Open All features and tap the star next to the tools you want here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { showAllFeatures = true }) { Text("Choose tools") }
                }
            } else {
                quickTools.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { tool ->
                            ToolAction(
                                title = tool.title,
                                icon = { Icon(tool.icon, null) },
                                modifier = Modifier.weight(1f),
                                onClick = { openTool(tool) },
                                onLongClick = when (tool.id) {
                                    "install" -> ({
                                        viewModel.loadPhoneApps()
                                        showPhoneApps = true
                                    })
                                    "upload" -> ({ showUploadOptions = true })
                                    else -> null
                                },
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            Card(
                onClick = { haptics(); showAllFeatures = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "All features",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${ToolCatalog.all.size} tools and labs — search, open or pin them",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

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
                Text(
                    "Screenshot",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
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
                    else -> Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(52.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PhotoLibrary, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("No screenshot yet", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Nothing is captured in the background — the TV is only captured when you tap below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        ActionButton("Take screenshot", { Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)) }) {
                            viewModel.takeScreenshot()
                        }
                    }
                }
            }

            Text(
                "Screenshots are saved on this phone only. Change how many are kept in Settings → Screenshots.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            Spacer(Modifier.height(96.dp))
        }

        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showAllFeatures) {
        AllFeaturesSheet(
            selected = favorites,
            onToggle = viewModel::toggleFavorite,
            onOpen = { tool ->
                showAllFeatures = false
                openTool(tool)
            },
            onSelectAll = viewModel::selectAllFavorites,
            onReset = viewModel::resetFavorites,
            onDismiss = { showAllFeatures = false },
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllFeaturesSheet(
    selected: List<String>,
    onToggle: (String) -> Unit,
    onOpen: (ToolDef) -> Unit,
    onSelectAll: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        ToolCatalog.all.filter { tool ->
            query.isBlank() ||
                tool.title.contains(query, ignoreCase = true) ||
                tool.subtitle.contains(query, ignoreCase = true) ||
                tool.category.label.contains(query, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .heightIn(max = 640.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "All features",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Tap to open · star to pin on Quick tools",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { haptics(); onReset() }) { Text("Reset") }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search tools and labs") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = false,
                    onClick = { haptics(); onSelectAll() },
                    label = { Text("Select all") },
                )
                Text(
                    "${selected.size} pinned",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))

            if (filtered.isEmpty()) {
                Text(
                    "No tool matches \"$query\"",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                    ToolCategory.entries.forEach { category ->
                        val tools = filtered.filter { it.category == category }
                        if (tools.isEmpty()) return@forEach
                        item(key = "header-${category.name}") {
                            Text(
                                category.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                            )
                        }
                        items(tools, key = { it.id }) { tool ->
                            ToolRow(
                                tool = tool,
                                pinned = tool.id in selected,
                                onOpen = { haptics(); onOpen(tool) },
                                onToggle = { haptics(); onToggle(tool.id) },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ToolRow(
    tool: ToolDef,
    pinned: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .pointerInput(tool.id) { detectTapGestures(onTap = { onOpen() }) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
            modifier = Modifier.size(42.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    tool.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tool.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                tool.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggle) {
            Icon(
                if (pinned) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = if (pinned) "Remove from Quick tools" else "Pin to Quick tools",
                tint = if (pinned) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
