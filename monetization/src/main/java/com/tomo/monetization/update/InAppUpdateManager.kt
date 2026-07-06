package com.tomo.monetization.update

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

object InAppUpdateManager {
    private lateinit var appUpdateManager: AppUpdateManager
    private var installStateListener: InstallStateUpdatedListener? = null

    fun init(context: Context) {
        if (::appUpdateManager.isInitialized) return
        appUpdateManager = AppUpdateManagerFactory.create(context)
        installStateListener = InstallStateUpdatedListener { state ->
            if (state.installStatus() == InstallStatus.INSTALLED) {
                appUpdateManager.completeUpdate()
            }
        }.also { appUpdateManager.registerListener(it) }
    }

    fun release() {
        installStateListener?.let { appUpdateManager.unregisterListener(it) }
        installStateListener = null
    }

    fun checkInAppUpdate(
        activityResultLauncher: ActivityResultLauncher<IntentSenderRequest>,
        forceUpdate: Boolean = false
    ): Any {
        return try {
            val appUpdateInfoTask = appUpdateManager.appUpdateInfo
            appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                    when {
                        forceUpdate && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> {
                            appUpdateManager.startUpdateFlowForResult(
                                appUpdateInfo,
                                activityResultLauncher,
                                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                            )
                        }

                        appUpdateInfo.isUpdateTypeAllowed(
                            AppUpdateType.FLEXIBLE
                        ) -> {
                            appUpdateManager.startUpdateFlowForResult(
                                appUpdateInfo,
                                activityResultLauncher,
                                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                            )
                        }
                    }
                }
            }
            appUpdateInfoTask
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
