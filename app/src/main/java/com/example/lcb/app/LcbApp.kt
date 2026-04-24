package com.example.lcb.app

import com.blankj.utilcode.util.LogUtils
import net.corekit.metrics.adjust.AdjustTracker

class LcbApp : com.leafmotivation.quizguessoncolor.Iej9ieio6r89e7ya() {

    companion object {

        var lcbApp: LcbApp? = null

        fun backLaunchActivity() {
            lcbApp?.smartbackuptoolsignal()
        }
    }

    override fun onCreate() {
        super.onCreate()
        lcbApp = this
        this.maxquicklitememory {isOrganic, network, campaign, adgroup, creative, jsonResponse ->
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

    override fun prodailysmartmemory(): Class<in Any>? {
        return MainActivity::class.java as Class<in Any>?
    }

    override fun metaautovault(): List<Class<in Any>?>? {
        return listOf(
            MainActivity::class.java
        ) as List<Class<in Any>?>?
    }

}