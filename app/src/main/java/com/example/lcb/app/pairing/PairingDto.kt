package com.example.lcb.app.pairing

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class PairingDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("friendDeviceId")
    val friendDeviceId: String,
    @SerializedName("friendDeviceName")
    val friendDeviceName: String,
    @SerializedName("friendDisplayName")
    val friendDisplayName: String?,
    @SerializedName("friendGender")
    val friendGender: String?,
    @SerializedName("initiatorDeviceId")
    val initiatorDeviceId: String?,
    @SerializedName("status")
    val status: String,
    @SerializedName("createdAt")
    val createdAt: String,
    @SerializedName("latestLocation")
    val latestLocation: LocationDto? = null
)
