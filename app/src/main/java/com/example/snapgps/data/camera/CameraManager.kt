package com.example.snapgps.data.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.example.snapgps.data.media.TempFiles
import com.example.snapgps.domain.model.FlashMode
import com.example.snapgps.domain.model.LensFacing
import com.example.snapgps.domain.model.ZoomInfo
import com.example.snapgps.domain.repository.CameraRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Owns CameraX: binds Preview + ImageCapture to a lifecycle and exposes focus, zoom, flash
 * and capture. Compose only ever sees the [SurfaceRequest].
 */
class CameraManager(
    private val context: Context,
    private val tempFiles: TempFiles
) : CameraRepository {

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    override val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    private val _zoom = MutableStateFlow(ZoomInfo())
    override val zoom: StateFlow<ZoomInfo> = _zoom.asStateFlow()

    private val _hasFlashUnit = MutableStateFlow(false)
    override val hasFlashUnit: StateFlow<Boolean> = _hasFlashUnit.asStateFlow()

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var targetRotation: Int? = null

    override suspend fun availableLenses(): Set<LensFacing> {
        val provider = ProcessCameraProvider.awaitInstance(context)
        return buildSet {
            if (provider.hasCameraSafe(CameraSelector.DEFAULT_BACK_CAMERA)) add(LensFacing.BACK)
            if (provider.hasCameraSafe(CameraSelector.DEFAULT_FRONT_CAMERA)) add(LensFacing.FRONT)
        }
    }

    override suspend fun bind(lifecycleOwner: LifecycleOwner, lens: LensFacing, flashMode: FlashMode) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val selector = resolveSelector(provider, lens)

        val preview = Preview.Builder().build().apply {
            setSurfaceProvider { request -> _surfaceRequest.value = request }
        }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(flashMode.toCameraX())
            .build()
        targetRotation?.let { capture.targetRotation = it }

        // A previous bind for this owner may not have finished unwinding yet; start clean.
        provider.unbindAll()
        val boundCamera = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
        camera = boundCamera
        imageCapture = capture
        _hasFlashUnit.value = boundCamera.cameraInfo.hasFlashUnit()

        val zoomObserver = Observer<ZoomState> { state ->
            _zoom.value = ZoomInfo(state.zoomRatio, state.minZoomRatio, state.maxZoomRatio)
        }
        boundCamera.cameraInfo.zoomState.observe(lifecycleOwner, zoomObserver)
        Log.d(TAG, "Camera bound")

        try {
            awaitCancellation()
        } finally {
            boundCamera.cameraInfo.zoomState.removeObserver(zoomObserver)
            // Only unbind our own use cases: a newer bind may already own the provider.
            provider.unbind(preview, capture)
            if (imageCapture === capture) {
                imageCapture = null
                camera = null
                _surfaceRequest.value = null
            }
        }
    }

    override fun setFlashMode(mode: FlashMode) {
        imageCapture?.flashMode = mode.toCameraX()
    }

    override fun setZoomRatio(ratio: Float) {
        val info = _zoom.value
        camera?.cameraControl?.setZoomRatio(ratio.coerceIn(info.minRatio, info.maxRatio))
    }

    override fun focusAt(surfaceX: Float, surfaceY: Float) {
        val request = _surfaceRequest.value ?: return
        val control = camera?.cameraControl ?: return
        val factory = SurfaceOrientedMeteringPointFactory(
            request.resolution.width.toFloat(),
            request.resolution.height.toFloat()
        )
        val action = FocusMeteringAction.Builder(factory.createPoint(surfaceX, surfaceY))
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        control.startFocusAndMetering(action)
    }

    override fun setTargetRotation(rotation: Int) {
        targetRotation = rotation
        imageCapture?.targetRotation = rotation
    }

    override suspend fun takePicture(): File {
        val capture = imageCapture ?: throw IllegalStateException("Camera is not ready")
        val file = tempFiles.newJpeg("raw_")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        return suspendCancellableCoroutine { cont ->
            capture.takePicture(
                options,
                Dispatchers.IO.asExecutor(),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        cont.resume(file)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        file.delete()
                        cont.resumeWithException(exception)
                    }
                }
            )
        }
    }

    private fun resolveSelector(provider: ProcessCameraProvider, lens: LensFacing): CameraSelector {
        val wanted = lens.toSelector()
        if (provider.hasCameraSafe(wanted)) return wanted
        val other = if (lens == LensFacing.BACK) LensFacing.FRONT else LensFacing.BACK
        return other.toSelector()
    }

    private fun ProcessCameraProvider.hasCameraSafe(selector: CameraSelector): Boolean =
        runCatching { hasCamera(selector) }.getOrDefault(false)

    private fun LensFacing.toSelector(): CameraSelector = when (this) {
        LensFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
        LensFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
    }

    private fun FlashMode.toCameraX(): Int = when (this) {
        FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
        FlashMode.ON -> ImageCapture.FLASH_MODE_ON
        FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
    }

    private companion object {
        const val TAG = "CameraManager"
    }
}
