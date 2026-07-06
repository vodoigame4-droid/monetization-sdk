package com.tomo.monetization.util

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import com.tomo.monetization.R

class OpenResumeLoadingDialog(context: Context) : Dialog(context, R.style.LoadAdsDialogTheme) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_resume_loading_ads)
    }
}
