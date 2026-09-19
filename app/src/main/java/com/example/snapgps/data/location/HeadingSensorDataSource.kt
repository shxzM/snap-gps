package com.example.snapgps.data.location

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.snapgps.domain.format.CardinalDirection
import com.example.snapgps.domain.repository.HeadingRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Compass heading from the rotation-vector sensor, for the direction the camera faces. */
class HeadingSensorDataSource(context: Context) : HeadingRepository {

    private val sensorManager = context.getSystemService(SensorManager::class.java)

    override fun headingUpdates(): Flow<Float> = callbackFlow {
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) {
            channel.close()
            return@callbackFlow
        }
        val rotation = FloatArray(9)
        val remapped = FloatArray(9)
        val orientation = FloatArray(3)
        var smoothed: Float? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                // Phone held upright with the camera facing forward.
                SensorManager.remapCoordinateSystem(rotation, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
                SensorManager.getOrientation(remapped, orientation)
                val azimuth = CardinalDirection.normalize(Math.toDegrees(orientation[0].toDouble()).toFloat())
                val next = smoothed?.let { smoothAngle(it, azimuth) } ?: azimuth
                smoothed = next
                trySend(next)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    override fun declination(latitude: Double, longitude: Double, altitudeM: Double, timeMs: Long): Float =
        GeomagneticField(latitude.toFloat(), longitude.toFloat(), altitudeM.toFloat(), timeMs).declination

    /** Low-pass filter that takes the short way around 0°/360°. */
    private fun smoothAngle(previous: Float, current: Float): Float {
        val delta = ((current - previous + 540f) % 360f) - 180f
        return CardinalDirection.normalize(previous + SMOOTHING * delta)
    }

    private companion object {
        const val SMOOTHING = 0.2f
    }
}
