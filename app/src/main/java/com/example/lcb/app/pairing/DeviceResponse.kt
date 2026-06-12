package com.example.lcb.app.pairing

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class DeviceResponse(
    @SerializedName("device")
    val device: DeviceDto
)
