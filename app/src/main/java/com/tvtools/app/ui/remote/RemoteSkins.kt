package com.tvtools.app.ui.remote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvtools.app.R
import com.tvtools.app.data.NowPlaying
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Visual treatment for the circular D-pad. */
internal enum class PadStyle { SOLID, GLASS, NEON }

/**
 * A soft scalloped circle: a polygon with alternating radii. Cheap to draw and
 * gives the dial a "cookie cut" silhouette when expressive shapes are on.
 */
internal class CookieShape(
    private val lobes: Int = 14,
    private val depth: Float = 0.045f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radius = min(size.width, size.height) / 2f
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val path = Path()
        val steps = lobes * 2
        for (i in 0 until steps) {
            val angle = (Math.PI * 2.0 * i / steps) - Math.PI / 2.0
            val r = if (i % 2 == 0) radius else radius * (1f - depth)
            val x = centerX + (r * cos(angle)).toFloat()
            val y = centerY + (r * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** Physical remote body: rounded shell, brand strip and a square D-pad. */
@Composable
internal fun PhysicalRemotePad(onSend: (Int) -> Unit) {
    Surface(
        shape = RoundedCornerShape(34.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "TV TOOLS",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(7.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape)
                )
            }
            Spacer(Modifier.height(14.dp))
            ClassicPad(onSend = onSend)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CircleKey(Icons.Default.KeyboardArrowLeft, "Back") { onSend(KEY_BACK) }
                CircleKey(Icons.Default.Home, "Home") { onSend(KEY_HOME) }
                CircleKey(Icons.Default.Menu, "Menu") { onSend(KEY_MENU) }
            }
        }
    }
}

/**
 * Media-first skin: giant now-playing card with one-thumb transport, and a
 * compact navigation cluster so the remote is still fully usable.
 */
@Composable
internal fun MediaFirstPad(
    onSend: (Int) -> Unit,
    track: NowPlaying?,
) {
    val playing = track?.isPlaying == true
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.now_playing_heading),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = track?.title?.takeIf { it.isNotBlank() } ?: "Nothing playing",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (!track?.artist.isNullOrBlank()) {
                Text(
                    track?.artist.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if ((track?.durationMs ?: 0) > 0) {
                val progress = (track!!.currentPositionMs().toFloat() / track.durationMs)
                    .coerceIn(0f, 1f)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        formatMs(track.currentPositionMs()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatMs(track.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledIconButton(onClick = { onSend(KEY_MEDIA_REWIND) }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.FastRewind, stringResource(R.string.rewind))
                }
                FilledIconButton(onClick = { onSend(KEY_MEDIA_PREV) }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.SkipPrevious, stringResource(R.string.previous))
                }
                FilledIconButton(
                    onClick = { onSend(KEY_MEDIA_PLAY_PAUSE) },
                    modifier = Modifier.size(76.dp),
                ) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (playing) "Pause" else "Play",
                        modifier = Modifier.size(34.dp),
                    )
                }
                FilledIconButton(onClick = { onSend(KEY_MEDIA_NEXT) }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.SkipNext, stringResource(R.string.media_next_action))
                }
                FilledIconButton(onClick = { onSend(KEY_MEDIA_FF) }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.FastForward, stringResource(R.string.fast_forward))
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledIconButton(onClick = { onSend(KEY_DPAD_LEFT) }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.KeyboardArrowLeft, stringResource(R.string.left))
                }
                FilledIconButton(onClick = { onSend(KEY_DPAD_UP) }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.up))
                }
                FilledIconButton(onClick = { onSend(KEY_DPAD_DOWN) }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.down))
                }
                FilledIconButton(onClick = { onSend(KEY_DPAD_RIGHT) }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.KeyboardArrowRight, stringResource(R.string.right))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavPill(Icons.Default.PlayArrow, "Select") { onSend(KEY_DPAD_CENTER) }
                Spacer(Modifier.width(12.dp))
                NavPill(Icons.Default.KeyboardArrowLeft, "Back") { onSend(KEY_BACK) }
            }
        }
    }
}

/** Neon glow backdrop behind the dark dial. */
@Composable
internal fun NeonGlow(size: Dp) {
    Box(
        Modifier
            .size(size * 1.35f)
            .background(
                Brush.radialGradient(
                    listOf(Color(0x6655E7FF), Color(0x2231C6FF), Color.Transparent),
                ),
                CircleShape,
            )
    )
}

private fun formatMs(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    return "%d:%02d".format(minutes, seconds)
}
