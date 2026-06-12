package com.example.lcb.app

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.lcb.app.databinding.DialogLocationPermissionBinding
import com.example.lcb.app.databinding.LayoutHomeBinding
import com.example.lcb.app.utils.loadNative
import com.example.lcb.app.utils.runAfterOptionalInterstitial
import com.google.android.gms.location.LocationServices
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: LayoutHomeBinding
    private var locationPermissionDialog: Dialog? = null
    private var locationUploadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!DeviceInfoStore.hasDeviceName(this)) {
            startActivity(Intent(this, SetupDeviceActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        binding = LayoutHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        binding.homeToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }

        val openLiveLocation = android.view.View.OnClickListener {
            openFeatureWithAd(Intent(this, LiveLocationActivity::class.java))
        }
        binding.liveCard.setOnClickListener(openLiveLocation)
        binding.liveTitle.setOnClickListener(openLiveLocation)
        binding.liveSubtitle.setOnClickListener(openLiveLocation)

        val openRealtimeSharing = android.view.View.OnClickListener {
            openFeatureWithAd(Intent(this, RealtimeSharingActivity::class.java))
        }
        binding.realtimeCard.setOnClickListener(openRealtimeSharing)
        binding.realtimeTitle.setOnClickListener(openRealtimeSharing)
        binding.realtimeSubtitle.setOnClickListener(openRealtimeSharing)

        binding.secureCodeCard.setOnClickListener {
            openFeatureWithAd(Intent(this, ShareSecureCodeActivity::class.java))
        }

        loadNative(binding.homeNativeAdContainer)
        updateLocationPermissionDialog()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) {
            updateLocationPermissionDialog()
            uploadCurrentLocationIfPermitted()
        }
    }

    override fun onDestroy() {
        locationUploadJob?.cancel()
        dismissLocationPermissionDialog()
        super.onDestroy()
    }

    private fun updateLocationPermissionDialog() {
        if (hasLocationPermission()) {
            dismissLocationPermissionDialog()
        } else {
            showLocationPermissionDialog()
        }
    }

    private fun showLocationPermissionDialog() {
        if (locationPermissionDialog?.isShowing == true || isFinishing) return

        val dialogBinding = DialogLocationPermissionBinding.inflate(layoutInflater)
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(dialogBinding.root)
            setCanceledOnTouchOutside(false)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setDimAmount(0.6f)
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setLayout(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
                )
                attributes = attributes.apply {
                    gravity = Gravity.CENTER
                    y = -22.dp
                }
            }
        }

        dialogBinding.cancelPermissionButton.setOnClickListener {
            dialog.dismiss()
        }
        dialogBinding.enablePermissionButton.setOnClickListener {
            dialog.dismiss()
            requestLocationPermission()
        }
        dialog.setOnDismissListener {
            if (locationPermissionDialog === dialog) {
                locationPermissionDialog = null
            }
        }

        locationPermissionDialog = dialog
        dialog.show()
        dialog.window?.apply {
            setLayout(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            attributes = attributes.apply {
                gravity = Gravity.CENTER
                y = -22.dp
            }
        }
    }

    private fun dismissLocationPermissionDialog() {
        locationPermissionDialog?.dismiss()
        locationPermissionDialog = null
    }

    private fun requestLocationPermission() {
        XXPermissions.with(this)
            .permission(coarseLocationPermission())
            .permission(fineLocationPermission())
            .request { _, deniedList ->
                if (deniedList.isEmpty()) {
                    dismissLocationPermissionDialog()
                    uploadCurrentLocationIfPermitted()
                } else {
                    updateLocationPermissionDialog()
                }
            }
    }

    private fun uploadCurrentLocationIfPermitted() {
        if (!hasLocationPermission()) return
        if (locationUploadJob?.isActive == true) return

        locationUploadJob = lifecycleScope.launch {
            runCatching {
                CurrentLocationUploader.uploadCurrentLocation(
                    context = this@MainActivity,
                    fusedLocationClient = LocationServices.getFusedLocationProviderClient(this@MainActivity)
                )
            }
        }
    }

    override fun onBackPressed() {
        LcbApp.backLaunchActivity()
    }

    private fun openFeatureWithAd(intent: Intent) {
        runAfterOptionalInterstitial {
            startActivity(intent)
        }
    }

    private fun hasLocationPermission(): Boolean {
        return XXPermissions.isGrantedPermission(this, coarseLocationPermission()) ||
            XXPermissions.isGrantedPermission(this, fineLocationPermission())
    }

    private fun coarseLocationPermission(): IPermission =
        PermissionLists.getAccessCoarseLocationPermission()

    private fun fineLocationPermission(): IPermission =
        PermissionLists.getAccessFineLocationPermission()
}

private val Int.dp: Int
    get() = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
