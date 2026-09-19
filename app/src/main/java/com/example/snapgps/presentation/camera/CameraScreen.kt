package com.example.snapgps.presentation.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.Surface
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.SurfaceRequest
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.snapgps.domain.format.OverlayLine
import com.example.snapgps.domain.format.OverlayLineKind
import com.example.snapgps.domain.model.CaptureState
import com.example.snapgps.domain.model.LocationStatus
import com.example.snapgps.presentation.camera.components.CameraBottomBar
import com.example.snapgps.presentation.camera.components.CameraPreview
import com.example.snapgps.presentation.camera.components.CameraTopBar
import com.example.snapgps.presentation.camera.components.LocationOverlay
import com.example.snapgps.presentation.theme.SnapGpsTheme
import com.example.snapgps.presentation.util.LightSystemBarIcons
import com.example.snapgps.presentation.util.ObserveAsEvents
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.util.Locale

private val ALL_PERMISSIONS = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)
private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

@Composable
fun CameraRoot(
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPhoto: (Long) -> Unit,
    viewModel: CameraViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // The UI owns the camera grant so the permission screen never flickers before state loads.
    var cameraGranted by remember { mutableStateOf(context.isGranted(Manifest.permission.CAMERA)) }
    var askedPermissions by rememberSaveable { mutableStateOf(false) }

    fun refreshPermissions() {
        cameraGranted = context.isGranted(Manifest.permission.CAMERA)
        viewModel.onPermissionsChanged(cameraGranted)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        askedPermissions = true
        refreshPermissions()
    }

    LaunchedEffect(Unit) {
        val needsAny = ALL_PERMISSIONS.any { !context.isGranted(it) }
        if (!askedPermissions && needsAny) permissionLauncher.launch(ALL_PERMISSIONS)
    }
    LifecycleResumeEffect(Unit) {
        refreshPermissions()
        onPauseOrDispose { }
    }

    if (cameraGranted) {
        LaunchedEffect(lifecycleOwner, state.lensFacing, state.cameraBindAttempt) {
            viewModel.bindCamera(lifecycleOwner, state.lensFacing)
        }
        DeviceRotationEffect(context, viewModel::onDeviceRotation)
    }

    fun showSnackbar(message: String, action: String? = null, onAction: () -> Unit = {}) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = action,
                duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) onAction()
        }
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is CameraEvent.PhotoSaved ->
                showSnackbar(event.note ?: "Photo saved", "View") { onOpenPhoto(event.photo.id) }
            is CameraEvent.CaptureFailed ->
                showSnackbar(event.message, "Retry", viewModel::onCaptureClicked)
            CameraEvent.CameraFailed ->
                showSnackbar("The camera couldn't start.", "Retry", viewModel::onRetryCamera)
            is CameraEvent.Message -> showSnackbar(event.text)
            CameraEvent.OpenLocationSettings ->
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            CameraEvent.RequestLocationPermission -> {
                val canAsk = !askedPermissions || LOCATION_PERMISSIONS.any {
                    activity?.shouldShowRequestPermissionRationale(it) == true
                }
                if (canAsk) permissionLauncher.launch(LOCATION_PERMISSIONS) else context.openAppSettings()
            }
        }
    }

    if (!cameraGranted) {
        val permanentlyDenied = askedPermissions &&
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false
        CameraPermissionScreen(
            permanentlyDenied = permanentlyDenied,
            onGrant = { permissionLauncher.launch(ALL_PERMISSIONS) },
            onOpenSettings = context::openAppSettings
        )
        return
    }

    CameraScreen(
        state = state,
        surfaceRequest = surfaceRequest,
        snackbarHostState = snackbarHostState,
        onCapture = viewModel::onCaptureClicked,
        onSwitchCamera = viewModel::onSwitchCamera,
        onToggleFlash = viewModel::onToggleFlash,
        onGpsClick = viewModel::onRetryLocation,
        onFocus = viewModel::onFocus,
        onZoomBy = viewModel::onZoomBy,
        onOpenGallery = onOpenGallery,
        onOpenSettings = onOpenSettings
    )
}

@Composable
fun CameraScreen(
    state: CameraUiState,
    surfaceRequest: SurfaceRequest?,
    snackbarHostState: SnackbarHostState,
    onCapture: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleFlash: () -> Unit,
    onGpsClick: () -> Unit,
    onFocus: (Float, Float) -> Unit,
    onZoomBy: (Float) -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit
) {
    LightSystemBarIcons()
    val shutterFlash = remember { Animatable(0f) }
    LaunchedEffect(state.captureState == CaptureState.Capturing) {
        if (state.captureState == CaptureState.Capturing) {
            shutterFlash.snapTo(0.8f)
            shutterFlash.animateTo(0f, tween(300))
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            CameraTopBar(
                flashMode = state.flashMode,
                hasFlashUnit = state.hasFlashUnit,
                locationStatus = state.locationStatus,
                isPreciseLocation = state.isPreciseLocation,
                distanceUnit = state.distanceUnit,
                onToggleFlash = onToggleFlash,
                onGpsClick = onGpsClick,
                onSettingsClick = onOpenSettings
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // 3:4 matches the default ImageCapture output, so the overlay lands where it will on the photo.
                Box(Modifier.aspectRatio(3f / 4f)) {
                    CameraPreview(
                        surfaceRequest = surfaceRequest,
                        onTapToFocus = onFocus,
                        onZoomBy = onZoomBy,
                        modifier = Modifier.fillMaxSize()
                    )
                    LocationOverlay(state.overlayLines, state.overlayConfig)
                    if (state.zoom.ratio > 1.01f) {
                        ZoomBadge(
                            state.zoom.ratio,
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp)
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = shutterFlash.value }
                            .background(Color.White)
                    )
                }
            }
            CameraBottomBar(
                lastPhoto = state.lastPhoto,
                isCapturing = state.isCapturing,
                canCapture = surfaceRequest != null,
                canSwitchLens = state.canSwitchLens,
                onGalleryClick = onOpenGallery,
                onCaptureClick = onCapture,
                onSwitchCamera = onSwitchCamera,
                modifier = Modifier.height(128.dp)
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 136.dp)
        )
    }
}

@Composable
private fun ZoomBadge(ratio: Float, modifier: Modifier = Modifier) {
    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f), modifier = modifier) {
        Text(
            text = String.format(Locale.US, "%.1fx", ratio),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun CameraPermissionScreen(
    permanentlyDenied: Boolean,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(Modifier.fillMaxSize().testTag("camera_permission_screen")) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(32.dp)
        ) {
            Icon(
                Icons.Filled.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.height(24.dp))
            Text("Camera access needed", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                "SnapGPS needs the camera to take photos. Location is optional and is only used " +
                    "to stamp your photos on this device.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))
            if (permanentlyDenied) {
                Button(onClick = onOpenSettings) { Text("Open app settings") }
            } else {
                Button(onClick = onGrant) { Text("Grant access") }
            }
        }
    }
}

/** Keeps capture rotation in sync with how the (portrait-locked) device is actually held. */
@Composable
private fun DeviceRotationEffect(context: Context, onRotation: (Int) -> Unit) {
    DisposableEffect(context) {
        var last = -1
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val rotation = when (orientation) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                if (rotation != last) {
                    last = rotation
                    onRotation(rotation)
                }
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }
}

private fun Context.isGranted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

@Preview
@Composable
private fun CameraScreenPreview() {
    SnapGpsTheme {
        CameraScreen(
            state = CameraUiState(
                hasCameraPermission = true,
                hasLocationPermission = true,
                isPreciseLocation = true,
                locationStatus = LocationStatus.Ready(7f),
                hasFlashUnit = true,
                canSwitchLens = true,
                overlayLines = listOf(
                    OverlayLine(OverlayLineKind.COORDINATES, "GPS 25.3176° N, 82.9739° E"),
                    OverlayLine(OverlayLineKind.DATE_TIME, "19 Sep 2026 - 05:42 PM"),
                    OverlayLine(OverlayLineKind.ACCURACY, "Accuracy: ±7 m")
                )
            ),
            surfaceRequest = null,
            snackbarHostState = remember { SnackbarHostState() },
            onCapture = {}, onSwitchCamera = {}, onToggleFlash = {}, onGpsClick = {},
            onFocus = { _, _ -> }, onZoomBy = {}, onOpenGallery = {}, onOpenSettings = {}
        )
    }
}

@Preview
@Composable
private fun CameraPermissionScreenPreview() {
    SnapGpsTheme { CameraPermissionScreen(permanentlyDenied = false, onGrant = {}, onOpenSettings = {}) }
}
