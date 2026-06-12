package com.example.lcb.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.example.lcb.app.databinding.LayoutSetupDeviceBinding

class SetupDeviceActivity : AppCompatActivity() {
    private lateinit var binding: LayoutSetupDeviceBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = LayoutSetupDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        clearInputFocusWhenKeyboardHidden(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        binding.deviceNameInput.setText(DeviceInfoStore.currentDeviceName(this))
        binding.confirmButton.setOnClickListener {
            DeviceInfoStore.saveDeviceName(this, binding.deviceNameInput.text?.toString().orEmpty())
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        binding.setupBackButton.setOnClickListener {
            finish()
        }
    }
}
