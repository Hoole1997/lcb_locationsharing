package com.example.lcb.app.pairing

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class DeviceDto(
    @SerializedName("deviceId")
    val deviceId: String,
    @SerializedName("deviceName")
    val deviceName: String,
    @SerializedName("inviteCode")
    val inviteCode: String,
    @SerializedName("createdAt")
    val createdAt: String
)
