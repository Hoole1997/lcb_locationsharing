package com.example.lcb.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.lcb.app.databinding.LayoutAddFriendProfileBinding
import com.example.lcb.app.pairing.PairingDto
import com.example.lcb.app.pairing.PairingRepository
import kotlinx.coroutines.launch

class AddFriendProfileActivity : AppCompatActivity() {
    private lateinit var binding: LayoutAddFriendProfileBinding
    private var selectedGender = Gender.MALE
    private val isEditMode: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_EDIT_MODE, false)
    }
    private val inviteCode: String by lazy {
        intent.getStringExtra(EXTRA_INVITE_CODE).orEmpty()
    }
    private val friendDeviceId: String by lazy {
        intent.getStringExtra(EXTRA_FRIEND_DEVICE_ID).orEmpty()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = LayoutAddFriendProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        clearInputFocusWhenKeyboardHidden(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        binding.addFriendProfileToolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.addFriendProfileToolbar.setNavigationOnClickListener {
            finish()
        }
        configureInitialProfile()

        binding.maleCard.setOnClickListener {
            updateGender(Gender.MALE)
        }
        binding.femaleCard.setOnClickListener {
            updateGender(Gender.FEMALE)
        }
        binding.saveButton.setOnClickListener {
            saveProfile()
        }
    }

    private fun configureInitialProfile() {
        if (!isEditMode) {
            updateGender(Gender.MALE)
            return
        }

        binding.addFriendProfileTitle.setText(R.string.friend_edit_title)
        binding.displayNameEditText.setText(intent.getStringExtra(EXTRA_FRIEND_DISPLAY_NAME).orEmpty())
        updateGender(Gender.fromApiValue(intent.getStringExtra(EXTRA_FRIEND_GENDER)))
    }

    private fun saveProfile() {
        if (isEditMode) {
            updateFriendProfile()
        } else {
            savePairing()
        }
    }

    private fun savePairing() {
        if (inviteCode.isBlank()) {
            Toast.makeText(this, R.string.pairing_invalid_code, Toast.LENGTH_SHORT).show()
            return
        }
        binding.saveButton.isEnabled = false
        binding.saveButton.text = getString(R.string.pairing_loading)
        val displayName = binding.displayNameEditText.text?.toString().orEmpty()
        lifecycleScope.launch {
            runCatching {
                PairingRepository.joinPairing(
                    context = this@AddFriendProfileActivity,
                    inviteCode = inviteCode,
                    displayName = displayName,
                    gender = selectedGender.apiValue
                )
            }.onSuccess { pairing ->
                Toast.makeText(
                    this@AddFriendProfileActivity,
                    getString(R.string.pairing_success_with_name, pairing.displayName()),
                    Toast.LENGTH_SHORT
                ).show()
                setResult(RESULT_OK)
                finish()
            }.onFailure { error ->
                binding.saveButton.isEnabled = true
                binding.saveButton.text = getString(R.string.pairing_save)
                Toast.makeText(
                    this@AddFriendProfileActivity,
                    error.message ?: getString(R.string.pairing_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun updateFriendProfile() {
        if (friendDeviceId.isBlank()) {
            Toast.makeText(this, R.string.pairing_failed, Toast.LENGTH_SHORT).show()
            return
        }
        binding.saveButton.isEnabled = false
        binding.saveButton.text = getString(R.string.pairing_loading)
        val displayName = binding.displayNameEditText.text?.toString().orEmpty()
        lifecycleScope.launch {
            runCatching {
                PairingRepository.updateFriendProfile(
                    context = this@AddFriendProfileActivity,
                    friendDeviceId = friendDeviceId,
                    displayName = displayName,
                    gender = selectedGender.apiValue
                )
            }.onSuccess {
                Toast.makeText(
                    this@AddFriendProfileActivity,
                    R.string.friend_profile_updated,
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }.onFailure { error ->
                binding.saveButton.isEnabled = true
                binding.saveButton.text = getString(R.string.pairing_save)
                Toast.makeText(
                    this@AddFriendProfileActivity,
                    error.message ?: getString(R.string.pairing_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun updateGender(gender: Gender) {
        selectedGender = gender
        val maleSelected = selectedGender == Gender.MALE
        binding.maleCard.setBackgroundResource(
            if (maleSelected) R.drawable.bg_gender_selected else R.drawable.bg_gender_unselected
        )
        binding.femaleCard.setBackgroundResource(
            if (maleSelected) R.drawable.bg_gender_unselected else R.drawable.bg_gender_selected
        )
        binding.maleLabel.setTextColor(getColor(if (maleSelected) R.color.pairing_primary else R.color.pairing_secondary_text))
        binding.femaleLabel.setTextColor(getColor(if (maleSelected) R.color.pairing_secondary_text else R.color.pairing_primary))
    }

    private enum class Gender {
        MALE,
        FEMALE;

        val apiValue: String
            get() = when (this) {
                MALE -> "male"
                FEMALE -> "female"
            }

        companion object {
            fun fromApiValue(value: String?): Gender {
                return if (value == FEMALE.apiValue) FEMALE else MALE
            }
        }
    }

    private fun com.example.lcb.app.pairing.PairingDto.displayName(): String {
        return friendDisplayName?.takeIf { it.isNotBlank() } ?: friendDeviceName
    }

    companion object {
        private const val EXTRA_INVITE_CODE = "extra_invite_code"
        private const val EXTRA_EDIT_MODE = "extra_edit_mode"
        private const val EXTRA_FRIEND_DEVICE_ID = "extra_friend_device_id"
        private const val EXTRA_FRIEND_DISPLAY_NAME = "extra_friend_display_name"
        private const val EXTRA_FRIEND_GENDER = "extra_friend_gender"

        fun createIntent(context: Context, inviteCode: String): Intent {
            return Intent(context, AddFriendProfileActivity::class.java)
                .putExtra(EXTRA_INVITE_CODE, inviteCode)
        }

        fun createEditIntent(context: Context, pairing: PairingDto): Intent {
            return Intent(context, AddFriendProfileActivity::class.java)
                .putExtra(EXTRA_EDIT_MODE, true)
                .putExtra(EXTRA_FRIEND_DEVICE_ID, pairing.friendDeviceId)
                .putExtra(
                    EXTRA_FRIEND_DISPLAY_NAME,
                    pairing.friendDisplayName?.takeIf { it.isNotBlank() } ?: pairing.friendDeviceName
                )
                .putExtra(EXTRA_FRIEND_GENDER, pairing.friendGender)
        }
    }
}
