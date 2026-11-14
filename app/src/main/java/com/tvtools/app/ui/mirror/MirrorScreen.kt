package com.tvtools.app.ui.mirror

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvtools.app.ui.common.rememberHaptics
import kotlin.math.abs
import kotlin.math.min

@Composable
fun MirrorScreen(
    deviceName: String,
    onBack: () -> Unit,
    viewModel: MirrorViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    val haptics = rememberHaptics()

    LaunchedEffect(Unit) { viewModel.start() }
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val bitmap = ui.bitmap
        when {
            ui.fastMode -> FastMirrorView(viewModel, ui)
            bitmap == null -> LoadingMirror(ui.error) { viewModel.start() }
            else -> SlowMirrorView(bitmap, viewModel, haptics)
        }

        // Top bar
        var qualityMenu by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f)) {
                androidx.compose.material3.IconButton(onClick = onBack) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(deviceName, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(
                    "Screen mirroring",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.55f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(if (ui.running) Color(0xFF54D96A) else Color.Gray, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (ui.running) "LIVE · %.1f fps".format(ui.fps) else "PAUSED",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            Box {
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f)) {
                    androidx.compose.material3.IconButton(onClick = { qualityMenu = true }) {
                        Icon(Icons.Default.Tune, "Mirror quality", tint = Color.White)
                    }
                }
                DropdownMenu(
                    expanded = qualityMenu,
                    onDismissRequest = { qualityMenu = false },
                ) {
                    MirrorQuality.entries.forEach { quality ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    (if (quality == ui.quality) "✓ " else "") + quality.label,
                                )
                            },
                            onClick = {
                                qualityMenu = false
                                viewModel.setQuality(quality)
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f)) {
                androidx.compose.material3.IconButton(onClick = { viewModel.toggle() }) {
                    Icon(
                        if (ui.running) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (ui.running) "Pause" else "Resume",
                        tint = Color.White,
                    )
                }
            }
        }

        // Bottom remote strip
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .horizontalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MirrorKey(Icons.AutoMirrored.Filled.ArrowBack) { viewModel.key(4) }
            MirrorKey(Icons.Default.Home) { viewModel.key(3) }
            MirrorKey(Icons.Default.Menu) { viewModel.key(82) }
            MirrorKey(Icons.Default.KeyboardArrowUp) { viewModel.key(19) }
            MirrorKey(Icons.Default.KeyboardArrowDown) { viewModel.key(20) }
            MirrorKey(Icons.Default.KeyboardArrowLeft) { viewModel.key(21) }
            MirrorKey(Icons.Default.KeyboardArrowRight) { viewModel.key(22) }
        }
    }
}

@Composable
private fun FastMirrorView(viewModel: MirrorViewModel, ui: MirrorUiState) {
    val haptics = rememberHaptics()
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        val videoW = min(maxW, maxH * 16f / 9f)
        val videoH = videoW * 9f / 16f
        Box(
            modifier = Modifier
                .width(with(density) { videoW.toDp() })
                .height(with(density) { videoH.toDp() }),
        ) {
            AndroidView(
                factory = { context ->
                    TextureView(context).apply {
                        isOpaque = true
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                surfaceTexture: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) {
                                viewModel.setSurface(Surface(surfaceTexture))
                            }

                            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                                viewModel.setSurface(null)
                                return true
                            }

                            override fun onSurfaceTextureSizeChanged(
                                surfaceTexture: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) {
                            }

                            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(videoW, videoH) {
                        detectTapGestures { offset ->
                            val relX = offset.x / videoW
                            val relY = offset.y / videoH
                            if (relX in 0f..1f && relY in 0f..1f) {
                                haptics()
                                viewModel.tap(relX, relY)
                            }
                        }
                    }
                    .pointerInput(videoW, videoH) {
                        var dragStart = Offset.Zero
                        var dragEnd = Offset.Zero
                        detectDragGestures(
                            onDragStart = { dragStart = it; dragEnd = it },
                            onDrag = { change, drag ->
                                change.consume()
                                dragEnd += drag
                            },
                            onDragEnd = {
                                val dx = dragEnd.x - dragStart.x
                                val dy = dragEnd.y - dragStart.y
                                if (abs(dx) > 40f || abs(dy) > 40f) {
                                    haptics()
                                    viewModel.swipe(
                                        (dragStart.x / videoW).coerceIn(0f, 1f),
                                        (dragStart.y / videoH).coerceIn(0f, 1f),
                                        (dragEnd.x / videoW).coerceIn(0f, 1f),
                                        (dragEnd.y / videoH).coerceIn(0f, 1f),
                                    )
                                }
                            },
                        )
                    },
            )

            if (!ui.hasFrame) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        ui.error ?: "Starting fast mirror…",
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                    )
                    if (ui.error != null) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.start() }) { Text("Retry") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlowMirrorView(
    bitmap: ImageBitmap,
    viewModel: MirrorViewModel,
    haptics: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val canvasW = constraints.maxWidth.toFloat()
        val canvasH = constraints.maxHeight.toFloat()
        val imgW = bitmap.width.toFloat()
        val imgH = bitmap.height.toFloat()
        val scale = min(canvasW / imgW, canvasH / imgH)
        val drawW = imgW * scale
        val drawH = imgH * scale
        val offsetX = (canvasW - drawW) / 2f
        val offsetY = (canvasH - drawH) / 2f

        var dragStart by remember { mutableStateOf(Offset.Zero) }
        var dragEnd by remember { mutableStateOf(Offset.Zero) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bitmap) {
                    detectTapGestures { offset ->
                        val relX = (offset.x - offsetX) / drawW
                        val relY = (offset.y - offsetY) / drawH
                        if (relX in 0f..1f && relY in 0f..1f) {
                            haptics()
                            viewModel.tap(relX, relY)
                        }
                    }
                }
                .pointerInput(bitmap) {
                    detectDragGestures(
                        onDragStart = { dragStart = it; dragEnd = it },
                        onDrag = { change, drag ->
                            change.consume()
                            dragEnd += drag
                        },
                        onDragEnd = {
                            val dx = dragEnd.x - dragStart.x
                            val dy = dragEnd.y - dragStart.y
                            if (abs(dx) > 40f || abs(dy) > 40f) {
                                val x1 = (dragStart.x - offsetX) / drawW
                                val y1 = (dragStart.y - offsetY) / drawH
                                val x2 = (dragEnd.x - offsetX) / drawW
                                val y2 = (dragEnd.y - offsetY) / drawH
                                if (x1 in 0f..1f && y1 in 0f..1f && x2 in 0f..1f && y2 in 0f..1f) {
                                    haptics()
                                    viewModel.swipe(x1, y1, x2, y2)
                                }
                            }
                        },
                    )
                },
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = "TV screen",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun LoadingMirror(error: String?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(
            error ?: "Starting mirror…",
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) {
                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}

@Composable
private fun MirrorKey(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.12f),
    ) {
        androidx.compose.material3.IconButton(
            onClick = { haptics(); onClick() },
            modifier = Modifier.size(44.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}
