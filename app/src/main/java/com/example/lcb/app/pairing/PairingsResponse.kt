package com.example.lcb.app.pairing

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class PairingsResponse(
    @SerializedName("pairings")
    val pairings: List<PairingDto>
)
