package com.tomo.monetization.firstopen.model

data class AppLanguage(
    val iconFlag: Int,
    val languageName: String,
    val languageCode: String,
    val isSelected: Boolean = false
)
