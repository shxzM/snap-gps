package com.example.snapgps.presentation.camera.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.snapgps.domain.model.DistanceUnit
import com.example.snapgps.domain.model.FlashMode
import com.example.snapgps.domain.model.LocationStatus
import com.example.snapgps.domain.model.Photo

private val onCameraIconColors
    @Composable get() = IconButtonDefaults.iconButtonColors(contentColor = Color.White)

@Composable
fun CameraTopBar(
    flashMode: FlashMode,
    hasFlashUnit: Boolean,
    locationStatus: LocationStatus,
    isPreciseLocation: Boolean,
    distanceUnit: DistanceUnit,
    onToggleFlash: () -> Unit,
    onGpsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (hasFlashUnit) {
            IconButton(onClick = onToggleFlash, colors = onCameraIconColors) {
                val (icon, label) = when (flashMode) {
                    FlashMode.OFF -> Icons.Filled.FlashOff to "Flash off"
                    FlashMode.AUTO -> Icons.Filled.FlashAuto to "Flash auto"
                    FlashMode.ON -> Icons.Filled.FlashOn to "Flash on"
                }
                Icon(icon, contentDescription = label)
            }
        } else {
            Box(Modifier.size(48.dp))
        }
        GpsStatusChip(
            status = locationStatus,
            isPrecise = isPreciseLocation,
            distanceUnit = distanceUnit,
            onClick = onGpsClick,
            modifier = Modifier.testTag("gps_status")
        )
        IconButton(onClick = onSettingsClick, colors = onCameraIconColors) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
fun CameraBottomBar(
    lastPhoto: Photo?,
    isCapturing: Boolean,
    canCapture: Boolean,
    canSwitchLens: Boolean,
    onGalleryClick: () -> Unit,
    onCaptureClick: () -> Unit,
    onSwitchCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        GalleryButton(lastPhoto, onGalleryClick)
        CaptureButton(enabled = canCapture && !isCapturing, busy = isCapturing, onClick = onCaptureClick)
        if (canSwitchLens) {
            IconButton(
                onClick = onSwitchCamera,
                colors = onCameraIconColors,
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(Icons.Filled.Cameraswitch, contentDescription = "Switch camera")
            }
        } else {
            Box(Modifier.size(56.dp))
        }
    }
}

@Composable
private fun GalleryButton(lastPhoto: Photo?, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .border(2.dp, Color.White, CircleShape)
            .clickable(role = Role.Button, onClickLabel = "Open gallery", onClick = onClick)
            .semantics { contentDescription = "Gallery" }
    ) {
        if (lastPhoto != null) {
            AsyncImage(
                model = lastPhoto.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp)
            )
        } else {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = Color.White)
        }
    }
}

@Composable
fun CaptureButton(enabled: Boolean, busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, label = "captureScale")
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(80.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(4.dp, Color.White, CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .background(if (enabled) Color.White else Color.White.copy(alpha = 0.4f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "Take photo",
                onClick = onClick
            )
            .semantics { contentDescription = "Take photo" }
            .testTag("capture_button")
    ) {
        if (busy) {
            CircularProgressIndicator(color = Color.Black, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        }
    }
}
