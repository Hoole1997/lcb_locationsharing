package com.example.lcb.app.pairing

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class PairingResponse(
    @SerializedName("pairing")
    val pairing: PairingDto
)
