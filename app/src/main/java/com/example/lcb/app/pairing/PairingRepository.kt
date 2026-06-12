package com.example.lcb.app.pairing

import android.content.Context
import android.location.Location
import com.example.lcb.app.DeviceInfoStore
import java.time.Instant

object PairingRepository {
    suspend fun ensureRegisteredDevice(context: Context): DeviceDto {
        val storedDeviceId = DeviceInfoStore.currentDeviceId(context)
        val storedInviteCode = DeviceInfoStore.currentInviteCode(context)
        if (!storedDeviceId.isNullOrBlank() && !storedInviteCode.isNullOrBlank()) {
            return DeviceDto(
                deviceId = storedDeviceId,
                deviceName = DeviceInfoStore.currentDeviceName(context),
                inviteCode = storedInviteCode,
                createdAt = ""
            )
        }

        val device = PairingApi.registerDevice(DeviceInfoStore.currentDeviceName(context))
        DeviceInfoStore.saveServerDevice(context, device.deviceId, device.inviteCode)
        return device
    }

    suspend fun joinPairing(
        context: Context,
        inviteCode: String,
        displayName: String?,
        gender: String?
    ): PairingDto {
        val device = ensureRegisteredDevice(context)
        return PairingApi.joinPairing(
            deviceId = device.deviceId,
            inviteCode = inviteCode.trim().uppercase(),
            displayName = displayName?.trim()?.takeIf { it.isNotEmpty() },
            gender = gender
        )
    }

    suspend fun listPairings(context: Context): List<PairingDto> {
        val device = ensureRegisteredDevice(context)
        return PairingApi.listPairings(device.deviceId)
    }

    suspend fun updateFriendProfile(
        context: Context,
        friendDeviceId: String,
        displayName: String?,
        gender: String?
    ): PairingDto {
        val device = ensureRegisteredDevice(context)
        return PairingApi.updateFriendProfile(
            deviceId = device.deviceId,
            friendDeviceId = friendDeviceId,
            request = UpdateFriendProfileRequest(
                displayName = displayName?.trim()?.takeIf { it.isNotEmpty() },
                gender = gender
            )
        )
    }

    suspend fun deletePairing(context: Context, friendDeviceId: String) {
        val device = ensureRegisteredDevice(context)
        PairingApi.deletePairing(device.deviceId, friendDeviceId)
    }

    suspend fun updateCurrentLocation(
        context: Context,
        location: Location,
        battery: Int?,
        address: String?
    ): LocationDto {
        val device = ensureRegisteredDevice(context)
        return PairingApi.updateCurrentLocation(
            deviceId = device.deviceId,
            request = LocationUpdateRequest(
                lat = location.latitude,
                lng = location.longitude,
                address = address?.takeIf { it.isNotBlank() },
                accuracy = location.accuracy.takeIf { location.hasAccuracy() }?.toDouble(),
                speed = location.speed.takeIf { location.hasSpeed() }?.toDouble(),
                bearing = location.bearing.takeIf { location.hasBearing() }?.toDouble(),
                battery = battery,
                recordedAt = Instant.ofEpochMilli(location.time).toString()
            )
        )
    }
}
