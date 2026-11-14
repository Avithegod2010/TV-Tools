package com.tvtools.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The TV + remote motif from the launcher icon, drawn in Compose so it can be
 * reused in the splash, empty states and headers without loading a bitmap.
 */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    screenTint: Color = MaterialTheme.colorScheme.surface,
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val bodyLeft = w * 0.04f
            val bodyTop = h * 0.16f
            val bodyWidth = w * 0.70f
            val bodyHeight = h * 0.54f
            val radius = CornerRadius(w * 0.08f, w * 0.08f)
            drawRoundRect(
                color = tint,
                topLeft = Offset(bodyLeft, bodyTop),
                size = Size(bodyWidth, bodyHeight),
                cornerRadius = radius,
            )
            drawRoundRect(
                color = screenTint,
                topLeft = Offset(bodyLeft + w * 0.045f, bodyTop + h * 0.05f),
                size = Size(bodyWidth - w * 0.09f, bodyHeight - h * 0.10f),
                cornerRadius = CornerRadius(w * 0.045f, w * 0.045f),
            )
            // Stand
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.30f, h * 0.74f),
                size = Size(w * 0.18f, h * 0.05f),
                cornerRadius = CornerRadius(w * 0.02f, w * 0.02f),
            )
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.22f, h * 0.79f),
                size = Size(w * 0.34f, h * 0.07f),
                cornerRadius = CornerRadius(w * 0.035f, w * 0.035f),
            )
            // Remote
            val remoteLeft = w * 0.72f
            val remoteTop = h * 0.34f
            val remoteWidth = w * 0.19f
            val remoteHeight = h * 0.46f
            drawRoundRect(
                color = tint,
                topLeft = Offset(remoteLeft, remoteTop),
                size = Size(remoteWidth, remoteHeight),
                cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
            )
            // Buttons: D-pad + two dots
            drawCircle(
                color = screenTint,
                radius = w * 0.035f,
                center = Offset(remoteLeft + remoteWidth / 2f, remoteTop + remoteHeight * 0.26f),
            )
            drawCircle(
                color = screenTint,
                radius = w * 0.018f,
                center = Offset(remoteLeft + remoteWidth / 2f, remoteTop + remoteHeight * 0.60f),
            )
            drawCircle(
                color = screenTint,
                radius = w * 0.018f,
                center = Offset(remoteLeft + remoteWidth / 2f, remoteTop + remoteHeight * 0.76f),
            )
        }
    }
}
