package com.example.lcb.app

import android.location.Location

data class LocationUploadSnapshot(
    val location: Location,
    val address: String?
)
