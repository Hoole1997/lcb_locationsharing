package com.example.lcb.app

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.example.lcb.app.databinding.LayoutAboutBinding

class AboutActivity : AppCompatActivity() {
    private lateinit var binding: LayoutAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = LayoutAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        binding.aboutToolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.aboutToolbar.setNavigationOnClickListener {
            finish()
        }

        binding.aboutAppName.text = getString(R.string.app_name)
        binding.aboutVersion.text = getString(R.string.about_version_format, currentVersionName())
        binding.privacyPolicyLink.setOnClickListener {
            WebPageLauncher.open(this, WebPageLauncher.PRIVACY_POLICY_URL)
        }
        binding.termsOfServiceLink.setOnClickListener {
            WebPageLauncher.open(this, WebPageLauncher.TERMS_OF_SERVICE_URL)
        }
    }

    private fun currentVersionName(): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            packageInfo.versionName.orEmpty().ifBlank { "1.0.0" }
        } catch (_: PackageManager.NameNotFoundException) {
            BuildConfig.VERSION_NAME
        }
    }
}
