package com.tomo.monetization.firstopen.model

interface FOCallback {
    fun onLanguageConfirm(language: AppLanguage)
    fun onOnboardPageChanged(pageIndex: Int)
    fun onFinished()
}
