package com.tomo.monetization

import android.content.Context
import androidx.startup.Initializer
import com.tomo.monetization.ads.app_open.AppOpenResumeManager
import com.tomo.monetization.billing.AppBilling
import com.tomo.monetization.consent.ConsentManager
import com.tomo.monetization.update.InAppUpdateManager

class MonetizationInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        ConsentManager.init(context)
        InAppUpdateManager.init(context)
        NetworkManager.init(context)
        AppOpenResumeManager.init(context)
        AppBilling.init(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>?>?> {
        return emptyList()
    }
}
