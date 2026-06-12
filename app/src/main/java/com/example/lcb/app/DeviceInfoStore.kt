package com.example.lcb.app

import android.content.Context

object DeviceInfoStore {
    private const val PREFS_NAME = "device_info"
    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_INVITE_CODE = "invite_code"
    private const val DEFAULT_DEVICE_NAME = "SM-520"

    fun hasDeviceName(context: Context): Boolean {
        return !preferences(context).getString(KEY_DEVICE_NAME, null).isNullOrBlank()
    }

    fun currentDeviceName(context: Context): String {
        return preferences(context).getString(KEY_DEVICE_NAME, null) ?: DEFAULT_DEVICE_NAME
    }

    fun saveDeviceName(context: Context, deviceName: String) {
        val name = deviceName.trim().ifEmpty { DEFAULT_DEVICE_NAME }
        preferences(context).edit().putString(KEY_DEVICE_NAME, name).apply()
    }

    fun currentDeviceId(context: Context): String? {
        return preferences(context).getString(KEY_DEVICE_ID, null)
    }

    fun currentInviteCode(context: Context): String? {
        return preferences(context).getString(KEY_INVITE_CODE, null)
    }

    fun saveServerDevice(context: Context, deviceId: String, inviteCode: String) {
        preferences(context).edit()
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_INVITE_CODE, inviteCode)
            .apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
