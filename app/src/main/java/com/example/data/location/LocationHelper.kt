package com.example.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class GeoLocationData(
    val latitude: Double,
    val longitude: Double,
    val locationName: String,
    val speedMph: Int
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

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): GeoLocationData = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) {
            return@withContext GeoLocationData(
                latitude = 0.0,
                longitude = 0.0,
                locationName = "Location Permission Required",
                speedMph = 0
            )
        }

        var realLocation: Location? = null

        // 1. Try Google Play Services FusedLocationProviderClient first
        try {
            realLocation = suspendCancellableCoroutine { cont ->
                val cts = CancellationTokenSource()
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
                cont.invokeOnCancellation { cts.cancel() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Fallback to Android native LocationManager (GPS or Network) if Fused was null
        if (realLocation == null && locationManager != null) {
            try {
                val gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val netLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                realLocation = gpsLoc ?: netLoc
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Fallback to Fused lastLocation
        if (realLocation == null) {
            try {
                realLocation = suspendCancellableCoroutine { cont ->
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { loc ->
                            if (cont.isActive) cont.resume(loc)
                        }
                        .addOnFailureListener {
                            if (cont.isActive) cont.resume(null)
                        }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // If real GPS location was acquired, format real address
        if (realLocation != null && (realLocation.latitude != 0.0 || realLocation.longitude != 0.0)) {
            val addressName = resolveRealAddress(realLocation.latitude, realLocation.longitude)
            val speed = if (realLocation.hasSpeed()) (realLocation.speed * 2.23694f).toInt() else 0
            return@withContext GeoLocationData(
                latitude = realLocation.latitude,
                longitude = realLocation.longitude,
                locationName = addressName,
                speedMph = speed
            )
        }

        // Location hardware unavailable or acquiring signal
        return@withContext GeoLocationData(
            latitude = 0.0,
            longitude = 0.0,
            locationName = "GPS Signal Acquiring...",
            speedMph = 0
        )
    }

    private fun resolveRealAddress(latitude: Double, longitude: Double): String {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    return formatRealAddress(addresses[0])
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    return formatRealAddress(addresses[0])
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
