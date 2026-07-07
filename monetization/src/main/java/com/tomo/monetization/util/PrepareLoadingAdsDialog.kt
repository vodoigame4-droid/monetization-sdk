package com.tomo.monetization.util

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.tomo.monetization.R

class PrepareLoadingAdsDialog(context: Context) : Dialog(context, R.style.LoadAdsDialogTheme) {

    init {
        val activity = context.findActivity()
        (activity as? LifecycleOwner)?.lifecycle?.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                try {
                    if (isShowing) {
                        dismiss()
                    }
                } catch (_: Exception) {}
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_prepare_loading_ads)
    }

    fun hideLoadingAdsText() {
        try {
            if (isShowing) {
                findViewById<TextView>(R.id.loading_dialog_tv)?.visibility = View.INVISIBLE
            }
        } catch (_: Exception) {
            // Ignore if window is destroyed
        }
    }
}
