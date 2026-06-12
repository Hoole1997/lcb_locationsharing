package com.example.lcb.app.pairing

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class LocationDto(
    @SerializedName("deviceId")
    val deviceId: String,
    @SerializedName("lat")
    val lat: Double,
    @SerializedName("lng")
    val lng: Double,
    @SerializedName("address")
    val address: String?,
    @SerializedName("accuracy")
    val accuracy: Double?,
    @SerializedName("speed")
    val speed: Double?,
    @SerializedName("bearing")
    val bearing: Double?,
    @SerializedName("battery")
    val battery: Int?,
    @SerializedName("recordedAt")
    val recordedAt: String?,
    @SerializedName("updatedAt")
    val updatedAt: String?
)
