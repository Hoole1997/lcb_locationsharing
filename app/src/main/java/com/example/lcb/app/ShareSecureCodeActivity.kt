package com.example.lcb.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.lcb.app.databinding.LayoutShareSecureCodeBinding
import com.example.lcb.app.pairing.InviteCodeParser
import com.example.lcb.app.pairing.PairingRepository
import com.example.lcb.app.pairing.QrCodeRenderer
import kotlinx.coroutines.launch

class ShareSecureCodeActivity : AppCompatActivity() {
    private lateinit var binding: LayoutShareSecureCodeBinding
    private var inviteCode: String = ""
    private val addFriendProfileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = LayoutShareSecureCodeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        clearInputFocusWhenKeyboardHidden(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        binding.shareSecureCodeToolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.shareSecureCodeToolbar.setNavigationOnClickListener {
            finish()
        }

        binding.invitationCodeValue.text = getString(R.string.pairing_loading)
        binding.qrImage.visibility = View.INVISIBLE
        binding.qrImage.alpha = 0f
        binding.qrLoading.visibility = View.VISIBLE
        loadInviteCode()

        binding.copyCodeButton.setOnClickListener {
            copyText(getString(R.string.invitation_code), inviteCode)
        }
        binding.shareCodeButton.setOnClickListener {
            shareText(inviteCode)
        }
        binding.joinButton.setOnClickListener {
            val code = InviteCodeParser.parse(binding.joinCodeEditText.text.toString())
            if (code == null) {
                Toast.makeText(this, R.string.pairing_invalid_code, Toast.LENGTH_SHORT).show()
            } else {
                addFriendProfileLauncher.launch(AddFriendProfileActivity.createIntent(this, code))
            }
        }
    }

    private fun loadInviteCode() {
        lifecycleScope.launch {
            runCatching {
                PairingRepository.ensureRegisteredDevice(this@ShareSecureCodeActivity)
            }.onSuccess { device ->
                inviteCode = device.inviteCode
                binding.invitationCodeValue.text = inviteCode
                binding.qrImage.setImageBitmap(QrCodeRenderer.render(inviteCode, 600))
                showQrCode()
            }.onFailure { error ->
                binding.invitationCodeValue.text = "--"
                binding.qrLoading.visibility = View.GONE
                Toast.makeText(
                    this@ShareSecureCodeActivity,
                    error.message ?: getString(R.string.pairing_register_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showQrCode() {
        binding.qrLoading.animate().cancel()
        binding.qrImage.animate().cancel()
        binding.qrLoading.visibility = View.GONE
        binding.qrImage.apply {
            scaleX = 0.92f
            scaleY = 0.92f
            alpha = 0f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(180L)
                .start()
        }
    }

    private fun copyText(label: String, value: String) {
        if (value.isBlank()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(this, R.string.pairing_copied, Toast.LENGTH_SHORT).show()
    }

    private fun shareText(value: String) {
        if (value.isBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, value)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_secure_code)))
    }
}
