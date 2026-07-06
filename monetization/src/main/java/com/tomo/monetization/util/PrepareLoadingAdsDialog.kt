package com.tomo.monetization.util

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.tomo.monetization.R

class PrepareLoadingAdsDialog(context: Context) : Dialog(context, R.style.LoadAdsDialogTheme) {

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
