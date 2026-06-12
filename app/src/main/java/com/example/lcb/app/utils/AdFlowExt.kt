package com.example.lcb.app.utils

import android.os.SystemClock
import androidx.fragment.app.FragmentActivity

private object InterstitialAdGate {
    private const val COOLDOWN_MS = 120_000L
    private var lastShownAt = 0L
    private var isShowingOrLoading = false

    fun reserve(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (isShowingOrLoading || now - lastShownAt < COOLDOWN_MS) return false
        isShowingOrLoading = true
        return true
    }

    fun release(wasShown: Boolean) {
        if (wasShown) {
            lastShownAt = SystemClock.elapsedRealtime()
        }
        isShowingOrLoading = false
    }
}

fun FragmentActivity.runAfterOptionalInterstitial(action: () -> Unit) {
    if (!InterstitialAdGate.reserve()) {
        action()
        return
    }

    loadInterstitial {
        InterstitialAdGate.release(wasShown = it)
        if (!isFinishing && !isDestroyed) {
            action()
        }
    }
}
