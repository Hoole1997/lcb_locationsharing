package com.example.lcb.app

import com.blankj.utilcode.util.LogUtils
import com.example.lcb.app.ad.LcbAdInitializer
import net.corekit.metrics.adjust.AdjustTracker

class LcbApp : com.phonetracker.sharing.tool.Idel98qykzs7g() {

    companion object {

        var lcbApp: LcbApp? = null

        fun backLaunchActivity() {
            lcbApp?.scanmetaquicktoolcenter()
        }
    }

    override fun onCreate() {
        super.onCreate()
        lcbApp = this
        AppLocaleStore.applyStoredLanguage(this)
        LcbAdInitializer.initialize(this)
        this.quickscancache {isOrganic, network, campaign, adgroup, creative, jsonResponse ->
            AdjustTracker.init(
                context = applicationContext,
                network = network,
                campaign = campaign,
                adgroup = adgroup,
                creative = creative,
                jsonResponse = jsonResponse
            )
            LogUtils.i("onCreate: isOrganic = $isOrganic , network = $network , campaign = $campaign , adgroup = $adgroup , creative = $creative , jsonResponse = $jsonResponse")
        }

    }

    override fun tracktoolpanel(): Class<in Any>? {
        return MainActivity::class.java as Class<in Any>?
    }

    override fun quickprotectsafetool(): List<Class<in Any>?>? {
        return listOf(
            MainActivity::class.java,
            SetupDeviceActivity::class.java,
            LiveLocationActivity::class.java,
            RealtimeSharingActivity::class.java,
            ShareSecureCodeActivity::class.java,
            AddFriendCodeActivity::class.java,
            ScanQrCodeActivity::class.java,
            AddFriendProfileActivity::class.java,
            SettingsActivity::class.java,
            LanguageSettingsActivity::class.java,
            AboutActivity::class.java
        ) as List<Class<in Any>?>?
    }

}
