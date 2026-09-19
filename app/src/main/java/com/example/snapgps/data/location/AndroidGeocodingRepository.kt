package com.example.snapgps.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import com.example.snapgps.domain.repository.GeocodingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Optional address lookup (TDD §12). Every failure — offline, no backend, timeout — yields null
 * so coordinate stamping is never blocked by it.
 */
class AndroidGeocodingRepository(context: Context) : GeocodingRepository {

    private val geocoder = Geocoder(context, Locale.getDefault())

    override suspend fun getAddress(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        return try {
            withTimeoutOrNull(TIMEOUT_MS) { lookup(latitude, longitude) }?.firstOrNull()?.format()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Address lookup unavailable")
            null
        }
    }

    private suspend fun lookup(latitude: Double, longitude: Double): List<Address> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses)
                    override fun onError(errorMessage: String?) = cont.resume(emptyList())
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1).orEmpty()
            }
        }

    private fun Address.format(): String? {
        val parts = listOfNotNull(subLocality, locality ?: subAdminArea, adminArea, countryName)
            .filter { it.isNotBlank() }
            .distinct()
        return if (parts.isNotEmpty()) parts.joinToString(", ") else getAddressLine(0)
    }

    private companion object {
        const val TAG = "Geocoding"
        const val TIMEOUT_MS = 5_000L
    }
}
