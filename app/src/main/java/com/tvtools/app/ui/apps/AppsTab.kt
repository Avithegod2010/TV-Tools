package com.tvtools.app.ui.apps

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvtools.app.R
import com.tvtools.app.data.TvApp
import com.tvtools.app.ui.common.formatBytes

@Composable
fun AppsTab(onOpenBackups: () -> Unit, guest: Boolean = false, viewModel: AppsViewModel = viewModel()) {
    val ui by viewModel.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (ui.selection.isNotEmpty()) {
                    Text(
                        stringResource(R.string.items_selected, ui.selection.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::clearSelection) { Text(stringResource(R.string.cancel)) }
                } else {
                    Text(
                        stringResource(R.string.installed_apps),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (ui.iconsTotal > 0 && ui.iconsLoaded < ui.iconsTotal) {
                        Text(
                            stringResource(R.string.app_icons_loaded, ui.iconsLoaded, ui.iconsTotal),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    IconButton(onClick = onOpenBackups, enabled = !guest) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = if (guest) "Backups hidden in guest mode" else "App backups",
                            tint = if (guest) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (ui.selection.isNotEmpty() && !guest) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = viewModel::selectAll) { Text(stringResource(R.string.all)) }
                    TextButton(onClick = viewModel::backupSelected) { Text(stringResource(R.string.backup)) }
                    TextButton(onClick = viewModel::downloadSelectedApks) { Text("APKs") }
                    TextButton(onClick = viewModel::backupDataSelected) { Text(stringResource(R.string.data)) }
                    TextButton(onClick = viewModel::forceStopSelected) { Text(stringResource(R.string.stop)) }
                    TextButton(onClick = viewModel::clearDataSelected) { Text(stringResource(R.string.clear_data)) }
                    TextButton(onClick = viewModel::disableSelected) { Text(stringResource(R.string.disable)) }
                    TextButton(onClick = viewModel::uninstallSelected) { Text(stringResource(R.string.uninstall)) }
                }
            }

            ui.busyLabel?.let { label ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }

            OutlinedTextField(
                value = ui.search,
                onValueChange = viewModel::setSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.search)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (ui.search.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearch("") }) {
                            Icon(Icons.Default.Clear, null)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = ui.sort == AppSort.SIZE,
                    onClick = { if (ui.sort != AppSort.SIZE) viewModel.toggleSort() },
                    label = { Text(stringResource(R.string.sort_by_size)) },
                )
                FilterChip(
                    selected = ui.onlyRunning,
                    onClick = viewModel::toggleRunning,
                    label = { Text(stringResource(R.string.running_apps)) },
                )
            }

            when {
                ui.loading -> com.tvtools.app.ui.common.LoadingRow("Loading apps…")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(ui.visible, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            busy = ui.busyPkg == app.packageName,
                            selected = app.packageName in ui.selection,
                            selecting = ui.selection.isNotEmpty(),
                            onClick = {
                                if (ui.selection.isNotEmpty()) viewModel.toggleSelection(app.packageName)
                                else viewModel.launch(app)
                            },
                            onLongClick = { if (!guest) viewModel.toggleSelection(app.packageName) },
                            onAction = { action -> handleAction(action, app, viewModel) },
                            onVisible = { viewModel.ensureDetails(app) },
                            guest = guest,
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

private enum class AppAction { LAUNCH, UNINSTALL, DOWNLOAD, FORCE_STOP, CLEAR_DATA, PERMISSIONS, TOGGLE, LOAD_ICON }

private fun handleAction(action: AppAction, app: TvApp, vm: AppsViewModel) {
    when (action) {
        AppAction.LAUNCH -> vm.launch(app)
        AppAction.UNINSTALL -> vm.uninstall(app)
        AppAction.DOWNLOAD -> vm.downloadApk(app)
        AppAction.FORCE_STOP -> vm.forceStop(app)
        AppAction.CLEAR_DATA -> vm.clearData(app)
        AppAction.PERMISSIONS -> vm.openOnTv(app)
        AppAction.TOGGLE -> vm.toggleEnabled(app)
        AppAction.LOAD_ICON -> vm.loadIconNow(app)
    }
}

@Composable
private fun AppRow(
    app: TvApp,
    busy: Boolean,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAction: (AppAction) -> Unit,
    onVisible: () -> Unit,
    guest: Boolean,
) {
    LaunchedEffect(app.packageName) { onVisible() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.surface
            )
            .pointerInput(app.packageName, selecting) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() })
            }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
            Spacer(Modifier.width(2.dp))
        }
        AppIcon(app)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.label ?: app.packageName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    if (!app.versionName.isNullOrBlank()) {
                        append(app.versionName)
                        append("  •  ")
                    }
                    append(formatBytes(app.sizeBytes))
                    if (app.isSystemApp) append("  •  system")
                    if (!app.enabled) append("  •  disabled")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                strokeCap = StrokeCap.Round,
            )
        } else {
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.actions))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.launch)) }, onClick = { menuOpen = false; onAction(AppAction.LAUNCH) })
                    if (guest) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.permissions_on_tv)) }, onClick = { menuOpen = false; onAction(AppAction.PERMISSIONS) })
                    } else {
                    if (!app.isSystemApp) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.uninstall)) }, onClick = { menuOpen = false; onAction(AppAction.UNINSTALL) })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.download_apk)) }, onClick = { menuOpen = false; onAction(AppAction.DOWNLOAD) })
                    if (app.iconPath == null) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.load_icon)) }, onClick = { menuOpen = false; onAction(AppAction.LOAD_ICON) })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.force_stop)) }, onClick = { menuOpen = false; onAction(AppAction.FORCE_STOP) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.clear_data)) }, onClick = { menuOpen = false; onAction(AppAction.CLEAR_DATA) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.permissions_on_tv)) }, onClick = { menuOpen = false; onAction(AppAction.PERMISSIONS) })
                    DropdownMenuItem(
                        text = { Text(if (app.enabled) stringResource(R.string.disable) else stringResource(R.string.enable)) },
                        onClick = { menuOpen = false; onAction(AppAction.TOGGLE) },
                    )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(app: TvApp) {
    val iconPath = app.iconPath
    val bitmap = remember(iconPath) {
        iconPath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
    } else {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    (app.label ?: app.packageName).take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
