package com.tomo.monetization.firstopen

import android.content.Context
import androidx.core.content.edit

class FOSharedPref(context: Context) {
    private val sharePref = context.applicationContext
        .getSharedPreferences("monetization_pref", Context.MODE_PRIVATE)

    fun doneFirstOpen(): Boolean {
        return sharePref.getBoolean(KEY_DONE_FIRST_OPEN, false)
    }

    fun setDoneFirstOpen(doneFO: Boolean) {
        sharePref.edit { putBoolean(KEY_DONE_FIRST_OPEN, doneFO) }
    }

    companion object {
        const val KEY_DONE_FIRST_OPEN = "KEY_DONE_FIRST_OPEN"

        @Volatile
        private var instance: FOSharedPref? = null

        fun initPrefs(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = FOSharedPref(context)
                    }
                }
            }
        }

        fun getInstance(): FOSharedPref {
            return instance ?: error("SharedPref not initialized!")
        }
    }
}