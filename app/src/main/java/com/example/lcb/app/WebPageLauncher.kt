package com.example.lcb.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent

object WebPageLauncher {
    const val PRIVACY_POLICY_URL = "https://walaa98alhasan.com/privacy.html"
    const val TERMS_OF_SERVICE_URL = "https://walaa98alhasan.com/TermsOfUse.html"

    fun open(activity: Activity, url: String) {
        val uri = Uri.parse(url)
        val customTabsPackage = customTabsPackageName(activity)
        if (customTabsPackage == null) {
            openWithBrowserIntent(activity, uri)
            return
        }

        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        customTabsIntent.intent.setPackage(customTabsPackage)
        try {
            customTabsIntent.launchUrl(activity, uri)
        } catch (_: ActivityNotFoundException) {
            openWithBrowserIntent(activity, uri)
        }
    }

    private fun openWithBrowserIntent(activity: Activity, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
        activity.startActivity(intent)
    }

    private fun customTabsPackageName(activity: Activity): String? {
        val browserIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(CUSTOM_TABS_PROBE_URL)
        ).addCategory(Intent.CATEGORY_BROWSABLE)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.packageManager.queryIntentActivities(
                browserIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            activity.packageManager.queryIntentActivities(browserIntent, PackageManager.MATCH_ALL)
        }
        val packageNames = resolveInfos
            .mapNotNull { it.activityInfo?.packageName }
            .distinct()
        if (packageNames.isEmpty()) return null
        return CustomTabsClient.getPackageName(
            activity,
            packageNames,
            true
        )
    }

    private const val CUSTOM_TABS_PROBE_URL = "https://www.example.com"
}
