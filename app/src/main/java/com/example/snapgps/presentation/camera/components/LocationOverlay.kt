package com.example.snapgps.presentation.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.min
import com.example.snapgps.domain.format.OverlayLayout
import com.example.snapgps.domain.format.OverlayLine
import com.example.snapgps.domain.format.OverlayLineKind
import com.example.snapgps.domain.model.OverlayConfig
import com.example.snapgps.domain.model.OverlayPosition

/**
 * Live overlay on the viewfinder. Sized with the same ratios as the bitmap renderer, so it is a
 * scale model of what will be burned into the photo.
 */
@Composable
fun LocationOverlay(
    lines: List<OverlayLine>,
    config: OverlayConfig,
    modifier: Modifier = Modifier
) {
    if (lines.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxSize()) {
        val base = min(maxWidth, maxHeight)
        val textSize = base * OverlayLayout.TEXT_SIZE_RATIO
        val fontSize = with(LocalDensity.current) { textSize.toSp() }
        val alignment = when (config.position) {
            OverlayPosition.TOP_LEFT -> Alignment.TopStart
            OverlayPosition.TOP_RIGHT -> Alignment.TopEnd
            OverlayPosition.BOTTOM_LEFT -> Alignment.BottomStart
            OverlayPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
        }
        val style = TextStyle(
            color = Color.White,
            fontSize = fontSize,
            lineHeight = fontSize * OverlayLayout.LINE_SPACING,
            shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 2f)
        )
        Column(
            modifier = Modifier
                .align(alignment)
                .padding(base * OverlayLayout.MARGIN_RATIO)
                .widthIn(max = maxWidth * OverlayLayout.MAX_WIDTH_RATIO)
                .background(
                    Color.Black.copy(alpha = config.opacity.coerceIn(0f, 1f)),
                    RoundedCornerShape(textSize * OverlayLayout.CORNER_TO_TEXT)
                )
                .padding(textSize * OverlayLayout.PADDING_TO_TEXT)
        ) {
            lines.forEach { line ->
                val emphasized = line.kind == OverlayLineKind.COORDINATES || line.kind == OverlayLineKind.NO_LOCATION
                Text(
                    text = line.text,
                    style = style,
                    fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Preview(widthDp = 300, heightDp = 400, backgroundColor = 0xFF556B2F, showBackground = true)
@Composable
private fun LocationOverlayPreview() {
    LocationOverlay(
        lines = listOf(
            OverlayLine(OverlayLineKind.COORDINATES, "GPS 25.3176° N, 82.9739° E"),
            OverlayLine(OverlayLineKind.ADDRESS, "Varanasi, Uttar Pradesh, India"),
            OverlayLine(OverlayLineKind.DATE_TIME, "19 Sep 2026 - 05:42 PM"),
            OverlayLine(OverlayLineKind.ALTITUDE, "Altitude: 81 m"),
            OverlayLine(OverlayLineKind.ACCURACY, "Accuracy: ±8 m")
        ),
        config = OverlayConfig()
    )
}
