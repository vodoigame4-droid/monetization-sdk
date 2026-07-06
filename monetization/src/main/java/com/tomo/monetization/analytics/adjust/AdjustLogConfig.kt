package com.tomo.monetization.analytics.adjust

data class AdjustLogConfig(
    val adjustToken: String,
    val isSandbox: Boolean,
    val adImpressionEvent: String? = null,
    val adApplovinEvent: String? = null
)
