package com.tvtools.app.ui.gallery

import android.content.ClipData
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.tvtools.app.AppServices
import com.tvtools.app.TvToolsApp
import com.tvtools.app.data.ScreenshotStore
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(onBack: () -> Unit) {
    val context = TvToolsApp.instance
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf(ScreenshotStore.groups(context)) }
    var filter by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Set<File>>(emptySet()) }
    var preview by remember { mutableStateOf<File?>(null) }
    var deleteTargets by remember { mutableStateOf<List<File>>(emptyList()) }

    fun reload() {
        groups = ScreenshotStore.groups(context)
        if (filter != null && groups[filter] == null) filter = null
    }

    fun toggle(file: File) {
        selected = if (file in selected) selected - file else selected + file
    }

    val files = (if (filter == null) groups.values.flatten() else groups[filter].orEmpty())
        .sortedByDescending { it.lastModified() }
    val byDate = files.groupBy {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it.lastModified()))
    }.toSortedMap(compareByDescending { it })

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selected.isEmpty()) "Screenshots" else "${selected.size} selected",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selected.isNotEmpty()) selected = emptySet() else onBack()
                    }) {
                        Icon(
                            if (selected.isEmpty()) Icons.AutoMirrored.Filled.ArrowBack
                            else Icons.Default.Close,
                            if (selected.isEmpty()) "Back" else "Clear selection",
                        )
                    }
                },
                actions = {
                    if (selected.isEmpty()) {
                        if (files.isNotEmpty()) {
                            TextButton(onClick = { selected = files.toSet() }) {
                                Text("Select all")
                            }
                        }
                    } else {
                        IconButton(onClick = { shareFiles(context, selected.toList()) }) {
                            Icon(Icons.Default.Share, "Share selected")
                        }
                        IconButton(onClick = { deleteTargets = selected.toList() }) {
                            Icon(Icons.Default.Delete, "Delete selected")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (groups.size > 1 || (groups.size == 1 && filter != null)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = filter == null,
                        onClick = { filter = null; selected = emptySet() },
                        label = { Text("All TVs") },
                    )
                    groups.keys.forEach { name ->
                        FilterChip(
                            selected = filter == name,
                            onClick = { filter = name; selected = emptySet() },
                            label = {
                                Text(
                                    if (name == ScreenshotStore.UNSORTED) "Unsorted" else name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }

            if (files.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("No screenshots yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Take a screenshot in Tools. Captures are grouped by TV and kept according to the retention setting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    byDate.forEach { (dateKey, dateFiles) ->
                        item(
                            key = "header-$dateKey",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            Text(
                                prettyDate(dateKey),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                            )
                        }
                        items(dateFiles, key = { it.absolutePath }) { file ->
                            ShotCard(
                                file = file,
                                selected = file in selected,
                                selectionMode = selected.isNotEmpty(),
                                onClick = {
                                    if (selected.isNotEmpty()) toggle(file) else preview = file
                                },
                                onLongClick = { toggle(file) },
                            )
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    preview?.let { file ->
        val bitmap = remember(file.absolutePath, file.lastModified()) {
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        }
        AlertDialog(
            onDismissRequest = { preview = null },
            title = {
                Text(
                    file.parentFile?.name?.takeIf { it != ScreenshotStore.UNSORTED } ?: "Screenshot",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            text = {
                Column {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                            .format(Date(file.lastModified())),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { shareFiles(context, listOf(file)) },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            Icon(Icons.Default.Share, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Share")
                        }
                        Button(
                            onClick = {
                                val target = file
                                preview = null
                                showOnTv(context, target)
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            Icon(Icons.Default.Cast, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Show on TV")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { preview = null }) { Text("Close") }
            },
            dismissButton = {
                TextButton(onClick = {
                    preview = null
                    deleteTargets = listOf(file)
                }) { Text("Delete") }
            },
        )
    }

    if (deleteTargets.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { deleteTargets = emptyList() },
            title = { Text("Delete ${deleteTargets.size} screenshot(s)?") },
            text = { Text("They are removed from this phone only. The TV is not touched.") },
            confirmButton = {
                Button(onClick = {
                    deleteTargets.forEach { runCatching { it.delete() } }
                    selected = selected - deleteTargets.toSet()
                    deleteTargets = emptyList()
                    preview = null
                    reload()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargets = emptyList() }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShotCard(
    file: File,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val bitmap = remember(file.absolutePath, file.lastModified()) {
        BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
    }
    Column(
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(10.dp)),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            if (selectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            Icons.Default.CheckCircle, "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        Text(
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(file.lastModified())),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp),
        )
    }
}

private fun prettyDate(key: String): String = runCatching {
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(key)
    SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(date ?: Date())
}.getOrDefault(key)

private fun shareFiles(context: android.content.Context, files: List<File>) {
    if (files.isEmpty()) return
    try {
        val uris = files.map {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/png"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, "screenshots", uris.first()).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
        }
        context.startActivity(Intent.createChooser(intent, "Share screenshots"))
    } catch (_: Exception) {
    }
}

private fun showOnTv(context: android.content.Context, file: File) {
    val adb = AppServices.adb
    if (!adb.isConnected) {
        android.widget.Toast.makeText(context, "Connect to the TV first", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        try {
            val remote = "/sdcard/Download/tvtools_preview_${System.currentTimeMillis()}.png"
            adb.pushFile(file, remote)
            adb.openFileOnTv(remote, "image/png")
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Opening on the TV", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(
                    context,
                    "Couldn't open on the TV: ${e.message}",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
}
