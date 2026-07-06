package com.tomo.monetization.util

import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

internal object CommonUtil {
    fun hideNavigationBars(window: Window) {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    fun showNavigationBars(window: Window) {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.apply {
            show(WindowInsetsCompat.Type.navigationBars())
        }
    }
}
