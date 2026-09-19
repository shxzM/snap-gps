package com.example.snapgps.presentation.camera.components

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Live viewfinder with tap-to-focus and pinch-to-zoom. */
@Composable
fun CameraPreview(
    surfaceRequest: SurfaceRequest?,
    onTapToFocus: (surfaceX: Float, surfaceY: Float) -> Unit,
    onZoomBy: (scale: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnTap by rememberUpdatedState(onTapToFocus)
    val currentOnZoom by rememberUpdatedState(onZoomBy)
    val transformer = remember { MutableCoordinateTransformer() }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusKey by remember { mutableStateOf(0) }

    Box(
        modifier = modifier
            .background(Color.Black)
            .semantics { contentDescription = "Camera preview. Tap to focus, pinch to zoom." }
    ) {
        surfaceRequest?.let { request ->
            CameraXViewfinder(
                surfaceRequest = request,
                coordinateTransformer = transformer,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { tap ->
                            val surfacePoint = with(transformer) { tap.transform() }
                            currentOnTap(surfacePoint.x, surfacePoint.y)
                            focusPoint = tap
                            focusKey++
                        }
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            if (zoom != 1f) currentOnZoom(zoom)
                        }
                    }
            )
        }
        focusPoint?.let { point ->
            FocusRing(point, focusKey, onFinished = { focusPoint = null })
        }
    }
}

@Composable
private fun FocusRing(center: Offset, key: Int, onFinished: () -> Unit) {
    val size = 64.dp
    val half = with(LocalDensity.current) { (size / 2).toPx() }
    val scale = remember(key) { Animatable(1.4f) }
    val alpha = remember(key) { Animatable(1f) }
    LaunchedEffect(key) {
        scale.animateTo(1f, tween(200))
        delay(700)
        alpha.animateTo(0f, tween(300))
        onFinished()
    }
    Box(
        Modifier
            .offset { IntOffset((center.x - half).roundToInt(), (center.y - half).roundToInt()) }
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
            .border(2.dp, Color.White, CircleShape)
    )
}
