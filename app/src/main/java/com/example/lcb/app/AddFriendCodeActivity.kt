package com.example.lcb.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.example.lcb.app.databinding.LayoutAddFriendCodeBinding
import com.example.lcb.app.pairing.InviteCodeParser
import com.example.lcb.app.utils.loadNative

class AddFriendCodeActivity : AppCompatActivity() {
    private lateinit var binding: LayoutAddFriendCodeBinding
    private val addFriendProfileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            finish()
        }
    }
    private val scanQrLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val rawValue = result.data?.getStringExtra(ScanQrCodeActivity.EXTRA_SCAN_RESULT)
        val code = rawValue?.let(InviteCodeParser::parse)
        if (code != null) {
            binding.inviteCodeEditText.setText(code)
            addFriendProfileLauncher.launch(AddFriendProfileActivity.createIntent(this, code))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = LayoutAddFriendCodeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        clearInputFocusWhenKeyboardHidden(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        binding.addFriendCodeToolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.addFriendCodeToolbar.setNavigationOnClickListener {
            finish()
        }

        binding.nextButton.setOnClickListener {
            val code = InviteCodeParser.parse(binding.inviteCodeEditText.text.toString())
            if (code == null) {
                Toast.makeText(this, R.string.pairing_invalid_code, Toast.LENGTH_SHORT).show()
            } else {
                addFriendProfileLauncher.launch(AddFriendProfileActivity.createIntent(this, code))
            }
        }
        binding.scanQrButton.setOnClickListener {
            scanQrLauncher.launch(Intent(this, ScanQrCodeActivity::class.java))
        }
        loadNative(binding.addFriendCodeNativeAdContainer)
    }
}
