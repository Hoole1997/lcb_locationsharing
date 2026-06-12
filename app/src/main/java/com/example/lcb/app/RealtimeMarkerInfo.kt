package com.example.lcb.app

import androidx.annotation.DrawableRes

internal data class RealtimeMarkerInfo(
    val title: String,
    val address: String,
    @param:DrawableRes val bubbleBackground: Int,
    @param:DrawableRes val pointerBackground: Int
)
