package com.tvtools.app.ui.tools

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvtools.app.TvToolsApp
import com.tvtools.app.ui.common.InfoCard

@Composable
fun ToolsTab(
    onOpenFiles: () -> Unit,
    onOpenMirror: () -> Unit,
    viewModel: ToolsViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = TvToolsApp.instance

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(Unit) { viewModel.takeScreenshot() }

    val apkPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(viewModel::installApk) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(viewModel::uploadToDownloads) }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Action grid (2 x 2)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolAction(
                    title = "Install",
                    icon = { Icon(Icons.Default.Download, null) },
                    modifier = Modifier.weight(1f),
                    onClick = { apkPicker.launch("application/vnd.android.package-archive") },
                )
                ToolAction(
                    title = "Upload to\nDownloads",
                    icon = { Icon(Icons.Default.Share, null) },
                    modifier = Modifier.weight(1f),
                    onClick = { filePicker.launch("*/*") },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolAction(
                    title = "File manager",
                    icon = { Icon(Icons.Default.Download, null) },
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

            // Screen mirroring banner
            Card(
                onClick = onOpenMirror,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Cast,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Screen mirroring",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "View your TV live and tap to control it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
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

            // Screenshot section
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
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeCap = StrokeCap.Round,
                        )
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

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ToolAction(
    title: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            icon()
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
    ) {
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
