package com.example.lcb.app.pairing

import com.example.lcb.app.BuildConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object PairingApi {
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun registerDevice(deviceName: String): DeviceDto {
        return post(
            path = "/devices/register",
            body = RegisterDeviceRequest(deviceName),
            responseClass = DeviceResponse::class.java
        ).device
    }

    suspend fun joinPairing(
        deviceId: String,
        inviteCode: String,
        displayName: String?,
        gender: String?
    ): PairingDto {
        return post(
            path = "/pairings/join",
            body = JoinPairingRequest(inviteCode, displayName, gender),
            responseClass = PairingResponse::class.java,
            deviceId = deviceId
        ).pairing
    }

    suspend fun listPairings(deviceId: String): List<PairingDto> {
        return get(
            path = "/pairings",
            responseClass = PairingsResponse::class.java,
            deviceId = deviceId
        ).pairings
    }

    suspend fun updateCurrentLocation(
        deviceId: String,
        request: LocationUpdateRequest
    ): LocationDto {
        return post(
            path = "/locations/current",
            body = request,
            responseClass = LocationResponse::class.java,
            deviceId = deviceId
        ).location
    }

    suspend fun updateFriendProfile(
        deviceId: String,
        friendDeviceId: String,
        request: UpdateFriendProfileRequest
    ): PairingDto {
        return patch(
            path = "/pairings/$friendDeviceId/profile",
            body = request,
            responseClass = PairingResponse::class.java,
            deviceId = deviceId
        ).pairing
    }

    suspend fun deletePairing(deviceId: String, friendDeviceId: String) {
        delete(
            path = "/pairings/$friendDeviceId",
            deviceId = deviceId
        )
    }

    private suspend fun <T : Any> get(
        path: String,
        responseClass: Class<T>,
        deviceId: String
    ): T = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${BuildConfig.SERVER_BASE_URL.trimEnd('/')}$path")
            .get()
            .header("x-device-id", deviceId)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    gson.fromJson(responseBody, ErrorResponse::class.java).error
                }.getOrNull()
                throw IOException(message ?: "Request failed: ${response.code}")
            }
            gson.fromJson(responseBody, responseClass)
        }
    }

    private suspend fun <T : Any> post(
        path: String,
        body: Any,
        responseClass: Class<T>,
        deviceId: String? = null
    ): T = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url("${BuildConfig.SERVER_BASE_URL.trimEnd('/')}$path")
            .post(gson.toJson(body).toRequestBody(jsonMediaType))
            .header("Content-Type", "application/json")

        if (!deviceId.isNullOrBlank()) {
            requestBuilder.header("x-device-id", deviceId)
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    gson.fromJson(responseBody, ErrorResponse::class.java).error
                }.getOrNull()
                throw IOException(message ?: "Request failed: ${response.code}")
            }
            gson.fromJson(responseBody, responseClass)
        }
    }

    private suspend fun <T : Any> patch(
        path: String,
        body: Any,
        responseClass: Class<T>,
        deviceId: String
    ): T = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${BuildConfig.SERVER_BASE_URL.trimEnd('/')}$path")
            .patch(gson.toJson(body).toRequestBody(jsonMediaType))
            .header("Content-Type", "application/json")
            .header("x-device-id", deviceId)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    gson.fromJson(responseBody, ErrorResponse::class.java).error
                }.getOrNull()
                throw IOException(message ?: "Request failed: ${response.code}")
            }
            gson.fromJson(responseBody, responseClass)
        }
    }

    private suspend fun delete(path: String, deviceId: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${BuildConfig.SERVER_BASE_URL.trimEnd('/')}$path")
            .delete()
            .header("x-device-id", deviceId)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    gson.fromJson(responseBody, ErrorResponse::class.java).error
                }.getOrNull()
                throw IOException(message ?: "Request failed: ${response.code}")
            }
        }
    }
}
