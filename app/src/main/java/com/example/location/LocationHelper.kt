package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val city: String,
    val country: String
)

class LocationHelper(private val context: Context) {

    companion object {
        private const val TAG = "LocationHelper"
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LocationInfo? = withContext(Dispatchers.IO) {
        try {
            val location = suspendCancellableCoroutine<Location?> { cont ->
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    null
                ).addOnSuccessListener { loc ->
                    if (cont.isActive) cont.resume(loc)
                }.addOnFailureListener { e ->
                    Log.w(TAG, "Failed getting location: ${e.message}")
                    if (cont.isActive) cont.resume(null)
                }
            } ?: return@withContext null

            var addressLine = "Lat: ${location.latitude}, Lng: ${location.longitude}"
            var city = "Unknown"
            var country = "Unknown"

            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        addressLine = addr.getAddressLine(0) ?: addressLine
                        city = addr.locality ?: addr.subAdminArea ?: city
                        country = addr.countryName ?: country
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        addressLine = addr.getAddressLine(0) ?: addressLine
                        city = addr.locality ?: addr.subAdminArea ?: city
                        country = addr.countryName ?: country
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Geocoder reverse-lookup failed: ${e.message}")
            }

            LocationInfo(
                latitude = location.latitude,
                longitude = location.longitude,
                address = addressLine,
                city = city,
                country = country
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error obtaining location info: ${e.message}")
            null
        }
    }
}
