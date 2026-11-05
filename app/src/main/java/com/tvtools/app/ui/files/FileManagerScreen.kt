package com.tvtools.app.ui.files

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvtools.app.ui.common.LoadingRow
import com.tvtools.app.ui.common.formatBytes
import com.tvtools.app.ui.common.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    onBack: () -> Unit,
    viewModel: FilesViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var deleteTarget by remember { mutableStateOf<FileItem?>(null) }
    var showMkdir by remember { mutableStateOf(false) }
    var transferMove by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::upload)
    }

    val selecting = ui.selection.isNotEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text("${ui.selection.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Check, "Done")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Default.SelectAll, "Select all")
                        }
                        IconButton(onClick = { viewModel.downloadSelected() }) {
                            Icon(Icons.Default.Download, "Download")
                        }
                        IconButton(onClick = { transferMove = false; showTransfer = true }) {
                            Icon(Icons.Default.Folder, "Copy to")
                        }
                        IconButton(onClick = { viewModel.deleteSelected() }) {
                            Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                )
            } else {
                TopAppBar(
                    title = { Text("File manager", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = { if (viewModel.isAtRoot) onBack() else viewModel.up() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::toggleGrid) {
                            Icon(
                                if (ui.grid) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = "Toggle grid",
                            )
                        }
                        IconButton(onClick = { showMkdir = true }) {
                            Icon(Icons.Default.Create, "New folder")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { picker.launch("*/*") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) { Icon(Icons.Default.Add, "Upload file") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Breadcrumbs(
                path = ui.path,
                onNavigate = { viewModel.navigateTo(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )

            if (ui.uploadProgress >= 0f) {
                LinearProgressIndicator(
                    progress = { ui.uploadProgress },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(4.dp))
            }

            when {
                ui.loading -> LoadingRow("Opening…")
                ui.entries.isEmpty() -> Text(
                    "Empty folder",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ui.grid -> androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                    modifier = Modifier.weight(1f).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(ui.entries, key = { it.path }) { item ->
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (selecting) viewModel.toggleSelection(item.path)
                                    else if (item.isDir) viewModel.navigateTo(item.path)
                                    else viewModel.preview(item)
                                }
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.height(84.dp).fillMaxWidth()) {
                                FileThumb(item)
                                if (item.path in ui.selection) {
                                    Checkbox(checked = true, onCheckedChange = { viewModel.toggleSelection(item.path) })
                                }
                            }
                            Text(
                                item.name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
                else -> LazyColumn(Modifier.weight(1f)) {
                    items(ui.entries, key = { it.path }) { item ->
                        FileRow(
                            item = item,
                            selected = item.path in ui.selection,
                            selecting = selecting,
                            onClick = {
                                if (selecting) viewModel.toggleSelection(item.path)
                                else if (item.isDir) viewModel.navigateTo(item.path)
                                else viewModel.preview(item)
                            },
                            onLongClick = { viewModel.toggleSelection(item.path) },
                            onDownload = { viewModel.enqueueDownload(item) },
                            onRename = { renameTarget = item },
                            onDelete = { deleteTarget = item },
                        )
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }

            if (ui.downloadLabel != null || ui.queueRemaining > 0) {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer)) {
                    LinearProgressIndicator(
                        progress = { if (ui.downloadProgress >= 0f) ui.downloadProgress else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            buildString {
                                append(ui.downloadLabel ?: "Downloading…")
                                if (ui.queueRemaining > 0) append("  (+${ui.queueRemaining} queued)")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    if (showTransfer) {
        TextInputDialog(
            title = if (transferMove) "Move selected to" else "Copy selected to",
            initial = "/sdcard/Download",
            confirmLabel = if (transferMove) "Move" else "Copy",
            onDismiss = { showTransfer = false },
            onConfirm = { dest ->
                if (transferMove) viewModel.moveSelected(dest) else viewModel.copySelected(dest)
                showTransfer = false
            },
        )
    }

    renameTarget?.let { target ->
        TextInputDialog(
            title = "Rename",
            initial = target.name,
            confirmLabel = "Rename",
            onDismiss = { renameTarget = null },
            onConfirm = { viewModel.rename(target, it); renameTarget = null },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${target.name}?") },
            text = {
                Text(
                    if (target.isDir) "The folder and all its contents will be deleted from the TV."
                    else "The file will be deleted from the TV."
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.delete(target); deleteTarget = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }

    if (showMkdir) {
        TextInputDialog(
            title = "New folder",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { showMkdir = false },
            onConfirm = { viewModel.mkdir(it); showMkdir = false },
        )
    }

    preview?.let { p ->
        PreviewDialog(p, onDismiss = { viewModel.dismissPreview() })
    }
}

@Composable
private fun PreviewDialog(p: FilePreview, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Preview") },
        text = {
            when (p) {
                is FilePreview.Image -> {
                    val bmp = remember(p.file.absolutePath) {
                        BitmapFactory.decodeFile(p.file.absolutePath)?.asImageBitmap()
                    }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                        )
                    } else {
                        Text("Couldn't decode image")
                    }
                }
                is FilePreview.Text -> {
                    Text(
                        p.content,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .height(360.dp)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    )
                }
                is FilePreview.Message -> Text(p.text)
            }
        },
    )
}

@Composable
private fun Breadcrumbs(path: String, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val rel = path.removePrefix("/sdcard").trim('/')
    val parts = if (rel.isBlank()) emptyList() else rel.split("/")
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Home",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (parts.isEmpty()) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onNavigate("/sdcard") },
        )
        var accum = "/sdcard"
        parts.forEachIndexed { index, part ->
            accum += "/$part"
            val target = accum
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                part,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (index == parts.lastIndex) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onNavigate(target) },
            )
        }
    }
}

@Composable
private fun FileRow(
    item: FileItem,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.surface
            )
            .pointerInput(item.path, selecting) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() })
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
            Spacer(Modifier.width(4.dp))
        }
        FileThumb(item)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (item.isDir) formatDate(item.mtime)
                else "${formatBytes(item.size)}  ${formatDate(item.mtime)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!selecting) {
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, "Actions")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (!item.isDir) {
                        DropdownMenuItem(
                            text = { Text("Download") },
                            leadingIcon = { Icon(Icons.Default.Download, null) },
                            onClick = { menuOpen = false; onDownload() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = { menuOpen = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun FileThumb(item: FileItem) {
    val thumb = item.thumbPath
    val bitmap = remember(thumb) { thumb?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    when {
        bitmap != null -> Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)),
        )
        item.isDir -> Icon(
            Icons.Default.Folder,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(42.dp),
        )
        else -> Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Download, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    item.name.substringAfterLast('.', "").uppercase().take(4),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = {
            Button(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
