package com.example.lcb.app

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun Activity.clearInputFocusWhenKeyboardHidden(rootView: View) {
    rootView.isFocusable = true
    rootView.isFocusableInTouchMode = true

    var wasKeyboardVisible = false
    ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
        val isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
        if (wasKeyboardVisible && !isKeyboardVisible) {
            view.post {
                currentFocus?.clearFocus()
                view.requestFocus()
            }
        }
        wasKeyboardVisible = isKeyboardVisible
        insets
    }
}
