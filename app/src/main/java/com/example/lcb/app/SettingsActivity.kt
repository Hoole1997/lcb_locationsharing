package com.example.lcb.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.example.lcb.app.databinding.LayoutSettingsBinding
import com.example.lcb.app.utils.loadNative

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: LayoutSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = LayoutSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        binding.settingsToolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.settingsToolbar.setNavigationOnClickListener {
            finish()
        }

        binding.languageItem.setOnClickListener {
            startActivity(Intent(this, LanguageSettingsActivity::class.java))
        }
        binding.aboutItem.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        binding.privacyItem.setOnClickListener {
            WebPageLauncher.open(this, WebPageLauncher.PRIVACY_POLICY_URL)
        }
        loadNative(binding.settingsNativeAdContainer)
    }
}
