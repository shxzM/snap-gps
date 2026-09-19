package com.example.snapgps.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.example.snapgps.domain.model.GpsLocation
import com.example.snapgps.domain.repository.LocationRepository
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/** Fused Location Provider behind a domain-level [GpsLocation] API (TDD §9). */
class LocationRepositoryImpl(private val context: Context) : LocationRepository {

    private val client = LocationServices.getFusedLocationProviderClient(context)
    private val locationManager = context.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission")
    override fun locationUpdates(): Flow<GpsLocation> = callbackFlow {
        if (!hasLocationPermission()) {
            channel.close()
            return@callbackFlow
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toGps()) }
            }
        }
        try {
            // Seed with the cached fix so the UI has something immediately; the stale check
            // decides whether it is fresh enough to stamp.
            client.lastLocation.addOnSuccessListener { it?.let { loc -> trySend(loc.toGps()) } }
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            Log.d(TAG, "Location updates started")
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission revoked")
            channel.close()
        }
        awaitClose {
            client.removeLocationUpdates(callback)
            Log.d(TAG, "Location updates stopped")
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): GpsLocation? {
        if (!hasLocationPermission()) return null
        val cts = CancellationTokenSource()
        return try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()?.toGps()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Current location request failed", e)
            null
        } finally {
            // Stops the underlying request if our coroutine was cancelled (e.g. a timeout).
            cts.cancel()
        }
    }

    override fun hasLocationPermission(): Boolean =
        granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    override fun hasPreciseLocationPermission(): Boolean = granted(Manifest.permission.ACCESS_FINE_LOCATION)

    override fun locationEnabled(): Flow<Boolean> = callbackFlow {
        fun current() = locationManager?.let(LocationManagerCompat::isLocationEnabled) ?: false
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(current())
            }
        }
        trySend(current())
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun Location.toGps() = GpsLocation(
        latitude = latitude,
        longitude = longitude,
        altitude = if (hasAltitude()) altitude else null,
        accuracy = if (hasAccuracy()) accuracy else null,
        speed = if (hasSpeed()) speed else null,
        bearing = if (hasBearing()) bearing else null,
        timestamp = time
    )

    private companion object {
        const val TAG = "LocationRepository"
        const val UPDATE_INTERVAL_MS = 1_000L
        const val MIN_UPDATE_INTERVAL_MS = 500L
    }
}
