package com.example.lcb.app

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.view.WindowCompat
import com.example.lcb.app.databinding.LayoutScanQrCodeBinding
import com.example.lcb.app.pairing.InviteCodeParser
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ScanQrCodeActivity : AppCompatActivity() {
    private lateinit var binding: LayoutScanQrCodeBinding
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val processingFrame = AtomicBoolean(false)
    private val finishedScan = AtomicBoolean(false)
    private val scanner: BarcodeScanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = LayoutScanQrCodeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        binding.scanQrToolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.scanQrToolbar.navigationIcon?.setTint(Color.WHITE)
        binding.scanQrToolbar.setNavigationOnClickListener {
            finish()
        }

        ensureCameraPermission()
    }

    override fun onDestroy() {
        super.onDestroy()
        scanner.close()
        cameraExecutor.shutdown()
    }

    private fun ensureCameraPermission() {
        if (hasCameraPermission()) {
            startCamera()
            return
        }

        XXPermissions.with(this)
            .permission(cameraPermission())
            .request { _, deniedList ->
                if (deniedList.isEmpty() || hasCameraPermission()) {
                    startCamera()
                } else {
                    Toast.makeText(
                        this,
                        R.string.scan_camera_permission_needed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply {
                        setAnalyzer(cameraExecutor, ::analyzeImage)
                    }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (_: Exception) {
                Toast.makeText(this, R.string.scan_qr_failed, Toast.LENGTH_SHORT).show()
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(this))
    }

    private fun analyzeImage(imageProxy: ImageProxy) {
        if (finishedScan.get()) {
            imageProxy.close()
            return
        }
        if (!processingFrame.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            processingFrame.set(false)
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val inviteCode = barcodes
                    .asSequence()
                    .mapNotNull { barcode -> barcode.rawValue }
                    .mapNotNull(InviteCodeParser::parse)
                    .firstOrNull()
                if (inviteCode != null) {
                    finishWithResult(inviteCode)
                }
            }
            .addOnCompleteListener {
                processingFrame.set(false)
                imageProxy.close()
            }
    }

    private fun finishWithResult(inviteCode: String) {
        if (!finishedScan.compareAndSet(false, true)) return
        runOnUiThread {
            setResult(
                RESULT_OK,
                android.content.Intent().putExtra(EXTRA_SCAN_RESULT, inviteCode)
            )
            finish()
        }
    }

    private fun hasCameraPermission(): Boolean {
        return XXPermissions.isGrantedPermission(this, cameraPermission())
    }

    private fun cameraPermission(): IPermission =
        PermissionLists.getCameraPermission()

    companion object {
        const val EXTRA_SCAN_RESULT = "extra_scan_result"
    }
}
