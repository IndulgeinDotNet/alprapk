package com.example.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

private const val TAG = "LocationHelper"

// A cached/last-known fix older than this is treated as too stale to silently pass off as
// the vehicle's current location.
private const val MAX_STALE_LOCATION_AGE_MS = 5 * 60 * 1000L
private const val FRESH_FIX_TIMEOUT_MS = 8000L

data class GeoLocationData(
    val latitude: Double,
    val longitude: Double,
    val locationName: String,
    val speedMph: Int,
    val isStale: Boolean = false
)

class LocationHelper(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val locationManager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    fun hasLocationPermission(): Boolean {
        val finePerm = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarsePerm = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return finePerm || coarsePerm
    }

    /** True if the device has any location provider (GPS or network) turned on at all. */
    fun isLocationServicesEnabled(): Boolean {
        val manager = locationManager ?: return false
        return try {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check location provider state", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): GeoLocationData = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) {
            Log.w(TAG, "getCurrentLocation called without location permission")
            return@withContext GeoLocationData(
                latitude = 0.0,
                longitude = 0.0,
                locationName = "Location permission not granted",
                speedMph = 0,
                isStale = true
            )
        }

        if (!isLocationServicesEnabled()) {
            Log.w(TAG, "Location services are disabled on this device")
            return@withContext GeoLocationData(
                latitude = 0.0,
                longitude = 0.0,
                locationName = "Turn on device location to geotag sightings",
                speedMph = 0,
                isStale = true
            )
        }

        var realLocation: Location? = null
        var isStale = false

        // 1. Try to get a genuinely fresh fix from Play Services, bounded by a hard timeout
        // so a stuck request can't hang the whole capture flow.
        try {
            realLocation = withTimeoutOrNull(FRESH_FIX_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val cts = CancellationTokenSource()
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                        .addOnSuccessListener { loc ->
                            if (cont.isActive) cont.resume(loc)
                        }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "Fresh location fix failed", e)
                            if (cont.isActive) cont.resume(null)
                        }
                    cont.invokeOnCancellation { cts.cancel() }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting fresh location fix", e)
        }

        // 2. Fall back to the most recent cached fix (native LocationManager, then Fused
        // lastLocation), but only trust it if it isn't too old - an hour-old cached fix from
        // across town is worse than admitting we don't have a current location.
        if (realLocation == null) {
            var candidate: Location? = null
            try {
                val gpsLoc = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val netLoc = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                candidate = listOfNotNull(gpsLoc, netLoc).maxByOrNull { it.time }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading LocationManager last-known location", e)
            }

            if (candidate == null) {
                try {
                    candidate = withTimeoutOrNull(3000L) {
                        suspendCancellableCoroutine { cont ->
                            fusedLocationClient.lastLocation
                                .addOnSuccessListener { loc ->
                                    if (cont.isActive) cont.resume(loc)
                                }
                                .addOnFailureListener { e ->
                                    Log.w(TAG, "Fused lastLocation failed", e)
                                    if (cont.isActive) cont.resume(null)
                                }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error requesting fused lastLocation", e)
                }
            }

            if (candidate != null) {
                val age = System.currentTimeMillis() - candidate.time
                isStale = age > MAX_STALE_LOCATION_AGE_MS
                if (isStale) {
                    Log.w(TAG, "Only a stale cached location is available (age=${age}ms)")
                }
                realLocation = candidate
            }
        }

        if (realLocation != null) {
            val addressName = resolveRealAddress(realLocation.latitude, realLocation.longitude)
            val speed = if (realLocation.hasSpeed()) (realLocation.speed * 2.23694f).toInt() else 0
            return@withContext GeoLocationData(
                latitude = realLocation.latitude,
                longitude = realLocation.longitude,
                locationName = if (isStale) "$addressName (approximate)" else addressName,
                speedMph = speed,
                isStale = isStale
            )
        }

        Log.w(TAG, "No location available from any source")
        return@withContext GeoLocationData(
            latitude = 0.0,
            longitude = 0.0,
            locationName = "Location unavailable",
            speedMph = 0,
            isStale = true
        )
    }

    private fun resolveRealAddress(latitude: Double, longitude: Double): String {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                return formatRealAddress(addresses[0])
            }
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocoding failed for $latitude,$longitude", e)
        }
        return String.format(Locale.US, "Lat: %.5f, Lng: %.5f", latitude, longitude)
    }

    private fun formatRealAddress(addr: Address): String {
        val thoroughfare = addr.thoroughfare ?: addr.featureName ?: ""
        val subThoroughfare = addr.subThoroughfare ?: ""
        val street = if (thoroughfare.isNotBlank()) "$subThoroughfare $thoroughfare".trim() else ""
        val locality = addr.locality ?: addr.subAdminArea ?: ""
        val adminArea = addr.adminArea ?: ""

        val parts = mutableListOf<String>()
        if (street.isNotBlank()) parts.add(street)
        if (locality.isNotBlank()) parts.add(locality)
        if (adminArea.isNotBlank()) parts.add(adminArea)

        return if (parts.isNotEmpty()) parts.joinToString(", ") else String.format(Locale.US, "Lat: %.5f, Lng: %.5f", addr.latitude, addr.longitude)
    }
}
