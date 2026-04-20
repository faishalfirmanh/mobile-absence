package com.example.absen_android.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float
)

sealed class LocationResult {
    data class Success(val location: DeviceLocation) : LocationResult()
    data class Error(val message: String) : LocationResult()
}

object LocationUtils {

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    // Check if mock/fake GPS app is enabled in developer options
    fun isMockLocationEnabled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                // Check all providers for mock locations
                val providers = locationManager.getProviders(true)
                providers.any { provider ->
                    try {
                        val testLocation = locationManager.getLastKnownLocation(provider)
                        testLocation?.isFromMockProvider == true
                    } catch (e: Exception) {
                        false
                    }
                }
            } else {
                // For older Android versions check developer settings
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ALLOW_MOCK_LOCATION
                ) != "0"
            }
        } catch (e: Exception) {
            false
        }
    }

    // Check if location itself is mocked
    private fun Location.isMocked(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            this.isMock
        } else {
            @Suppress("DEPRECATION")
            this.isFromMockProvider
        }
    }

    suspend fun getCurrentLocation(context: Context): LocationResult {
        if (!hasLocationPermission(context)) {
            return LocationResult.Error("Izin lokasi belum diberikan")
        }

        // Block if mock location is enabled at system level
        if (isMockLocationEnabled(context)) {
            return LocationResult.Error("MOCK_LOCATION_DETECTED")
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val cancellationTokenSource = CancellationTokenSource()

        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                cancellationTokenSource.cancel()
            }
            try {
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token
                ).addOnSuccessListener { location: Location? ->
                    if (location == null) {
                        continuation.resume(LocationResult.Error("Lokasi tidak tersedia"))
                        return@addOnSuccessListener
                    }

                    // Block if location itself is mocked
                    if (location.isMocked()) {
                        continuation.resume(LocationResult.Error("MOCK_LOCATION_DETECTED"))
                        return@addOnSuccessListener
                    }

                    continuation.resume(
                        LocationResult.Success(
                            DeviceLocation(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                altitude = location.altitude,
                                accuracy = location.accuracy
                            )
                        )
                    )
                }.addOnFailureListener { exception: Exception ->
                    continuation.resumeWithException(exception)
                }
            } catch (e: SecurityException) {
                continuation.resume(LocationResult.Error("Izin lokasi ditolak"))
            }
        }
    }
}