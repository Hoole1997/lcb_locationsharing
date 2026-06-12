package com.example.lcb.app.pairing

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class JoinPairingRequest(
    @SerializedName("inviteCode")
    val inviteCode: String,
    @SerializedName("displayName")
    val displayName: String?,
    @SerializedName("gender")
    val gender: String?
)
