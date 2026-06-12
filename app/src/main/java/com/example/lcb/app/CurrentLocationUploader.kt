package com.example.lcb.app

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Geocoder
import android.location.Location
import android.os.BatteryManager
import com.example.lcb.app.pairing.PairingRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object CurrentLocationUploader {
    suspend fun uploadCurrentLocation(
        context: Context,
        fusedLocationClient: FusedLocationProviderClient
    ): LocationUploadSnapshot? {
        val location = currentDeviceLocation(fusedLocationClient) ?: return null
        val address = reverseGeocode(context, location)
        uploadKnownLocation(
            context = context,
            location = location,
            address = address
        )
        return LocationUploadSnapshot(location, address)
    }

    suspend fun currentDeviceLocation(
        fusedLocationClient: FusedLocationProviderClient
    ): Location? {
        return suspendCancellableCoroutine { continuation ->
            try {
                val task = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    null
                )
                task.addOnSuccessListener { location ->
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }
                task.addOnFailureListener { error ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(error)
                    }
                }
                task.addOnCanceledListener {
                    continuation.cancel()
                }
            } catch (error: SecurityException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(error)
                }
            }
        }
    }

    suspend fun uploadKnownLocation(
        context: Context,
        location: Location,
        address: String?
    ) {
        runCatching {
            PairingRepository.updateCurrentLocation(
                context = context,
                location = location,
                battery = currentBatteryLevel(context),
                address = address
            )
        }
    }

    suspend fun reverseGeocode(context: Context, location: Location): String? = withContext(Dispatchers.IO) {
        try {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
                ?.getAddressLine(0)
                ?.takeIf { it.isNotBlank() }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun currentBatteryLevel(context: Context): Int? {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return ((level * 100f) / scale).toInt().coerceIn(0, 100)
    }
}
