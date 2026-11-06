package com.tvtools.app.ui.music

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvtools.app.AppServices
import com.tvtools.app.data.MusicItem
import com.tvtools.app.ui.common.LoadingRow
import com.tvtools.app.ui.common.PlaybackBar
import com.tvtools.app.ui.common.formatBytes
import com.tvtools.app.ui.common.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    onBack: () -> Unit,
    viewModel: MusicViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    val playback by AppServices.playback.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var deleteTarget by remember { mutableStateOf<MusicItem?>(null) }
    var showQueue by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { viewModel.refreshPlayback() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> if (uris.isNotEmpty()) viewModel.upload(uris) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Music", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showQueue = true }) {
                        BadgedBox(
                            badge = {
                                if (ui.queue.isNotEmpty()) {
                                    Badge { Text("${ui.queue.size}") }
                                }
                            },
                        ) {
                            Icon(Icons.Default.QueueMusic, "Queue")
                        }
                    }
                    IconButton(onClick = viewModel::scan) {
                        Icon(Icons.Default.Refresh, "Rescan")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picker.launch("audio/*") },
                icon = { Icon(Icons.Default.Upload, null) },
                text = { Text("Upload") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
        bottomBar = {
            if (playback.active && playback.title.isNotBlank()) {
                PlaybackBar(
                    state = playback,
                    onPlayPause = viewModel::playPause,
                    onPrevious = viewModel::previous,
                    onNext = viewModel::next,
                    onOpen = { showQueue = true },
                    onClose = viewModel::stopPlayback,
                    modifier = Modifier.navigationBarsPadding(),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            MusicHero(
                trackCount = ui.items.size,
                totalBytes = ui.totalBytes,
                onShuffle = {
                    ui.items.randomOrNull()?.let(viewModel::playOnTv)
                },
            )

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = ui.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search your TV music") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (ui.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Default.Close, "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
            )

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MusicSort.entries.forEach { sort ->
                    FilterChip(
                        selected = ui.sort == sort,
                        onClick = { viewModel.setSort(sort) },
                        label = { Text(sort.label) },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (ui.query.isBlank()) "${ui.items.size} tracks"
                    else "${ui.visibleItems.size} of ${ui.items.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ui.busyLabel?.let { label ->
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { if (ui.progress >= 0) ui.progress else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            if (ui.scanningMore && ui.items.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier.width(96.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Checking Downloads for more music…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            when {
                ui.loading -> LoadingRow("Scanning the TV for music…")
                ui.items.isEmpty() && ui.scanningMore -> LoadingRow("Scanning /sdcard/Music and Downloads…")
                ui.items.isEmpty() -> EmptyMusic(onUpload = { picker.launch("audio/*") })
                ui.visibleItems.isEmpty() -> Column(
                    Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Default.Search, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(34.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("No track matches \"${ui.query}\"", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Try a title, folder or file name.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(ui.visibleItems, key = { it.path }) { item ->
                        TrackRow(
                            item = item,
                            onPlay = { viewModel.playOnTv(item) },
                            onPlayNext = { viewModel.playNext(item) },
                            onAddToQueue = { viewModel.addToQueue(item) },
                            onDownload = { viewModel.download(item) },
                            onDelete = { deleteTarget = item },
                        )
                    }
                    item { Spacer(Modifier.height(96.dp)) }
                }
            }
        }
    }

    if (showQueue) {
        QueueSheet(
            queue = ui.queue,
            currentIndex = ui.queueIndex,
            onPlay = { index ->
                viewModel.playQueueAt(index)
                showQueue = false
            },
            onRemove = viewModel::removeFromQueue,
            onClear = viewModel::clearQueue,
            onDismiss = { showQueue = false },
        )
    }

    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete from the TV?") },
            text = { Text("${item.title} will be removed from ${item.path}.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.delete(item)
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MusicHero(trackCount: Int, totalBytes: Long, onShuffle: () -> Unit) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer,
                        )
                    )
                )
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(58.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.MusicNote, null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Music on your TV",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        if (trackCount == 0) "Nothing found yet"
                        else "$trackCount tracks · ${formatBytes(totalBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                    Text(
                        "Tap a track to play it through the TV",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
                if (trackCount > 0) {
                    FilledTonalButton(
                        onClick = onShuffle,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp,
                            vertical = 6.dp,
                        ),
                    ) {
                        Icon(Icons.Default.Shuffle, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    item: MusicItem,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        onClick = onPlay,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackCover(item, 52)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        if (item.artist.isNotBlank()) append(item.artist)
                        if (item.album.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(item.album)
                        }
                        if (item.durationMs > 0) {
                            if (isNotEmpty()) append(" · ")
                            append(formatDuration(item.durationMs))
                        }
                        if (isEmpty()) {
                            if (item.extension.isNotBlank()) append(item.extension.uppercase())
                            if (item.size > 0) {
                                if (isNotEmpty()) append(" · ")
                                append(formatBytes(item.size))
                            }
                            if (item.folder.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append(item.folder)
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledIconButton(onClick = onPlay, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.PlayArrow, "Play on TV")
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Play on TV") },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                        onClick = { menuOpen = false; onPlay() },
                    )
                    DropdownMenuItem(
                        text = { Text("Play next") },
                        leadingIcon = { Icon(Icons.Default.PlaylistPlay, null) },
                        onClick = { menuOpen = false; onPlayNext() },
                    )
                    DropdownMenuItem(
                        text = { Text("Add to queue") },
                        leadingIcon = { Icon(Icons.Default.PlaylistAdd, null) },
                        onClick = { menuOpen = false; onAddToQueue() },
                    )
                    DropdownMenuItem(
                        text = { Text("Download to phone") },
                        leadingIcon = { Icon(Icons.Default.Download, null) },
                        onClick = { menuOpen = false; onDownload() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete from TV") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackCover(item: MusicItem, size: Int) {
    val scheme = MaterialTheme.colorScheme
    val palettes = listOf(
        scheme.primaryContainer to scheme.tertiaryContainer,
        scheme.secondaryContainer to scheme.primaryContainer,
        scheme.tertiaryContainer to scheme.secondaryContainer,
    )
    val pair = palettes[Math.floorMod(item.path.hashCode(), palettes.size)]
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 4).dp))
            .background(Brush.linearGradient(listOf(pair.first, pair.second))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            item.title.take(1).uppercase(),
            color = scheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize = (size / 3).sp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(
    queue: List<MusicItem>,
    currentIndex: Int,
    onPlay: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .heightIn(max = 560.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Queue",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (queue.isEmpty()) "Nothing queued"
                        else "${queue.size} tracks · tap one to play",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onClear, enabled = queue.isNotEmpty()) {
                    Text("Clear")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
            if (queue.isEmpty()) {
                Text(
                    "Open the ⋮ menu on any track and choose \"Play next\" or \"Add to queue\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 28.dp),
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth()) {
                    itemsIndexed(queue) { index, item ->
                        val current = index == currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    else androidx.compose.ui.graphics.Color.Transparent
                                )
                                .clickable { onPlay(index) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(22.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    if (current) "Playing" else
                                        item.artist.ifBlank { item.folder.ifBlank { formatBytes(item.size) } },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { onRemove(index) }) {
                                Icon(Icons.Default.Close, "Remove from queue")
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun EmptyMusic(onUpload: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(84.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.MusicNote, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("No music on the TV yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tracks in /sdcard/Music and /sdcard/Download show up here. Upload from this phone to get started.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Button(onClick = onUpload) {
            Icon(Icons.Default.Upload, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Upload music")
        }
    }
}
