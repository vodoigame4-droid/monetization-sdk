package com.tomo.monetization.billing

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.QueryProductDetailsParams
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class ConsumeSubsResult(
    val billingResult: BillingResult,
    val purchaseToken: String
)

suspend fun BillingClient.connect(): Boolean {
    return suspendCancellableCoroutine { continuation ->
        startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (continuation.isActive) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        continuation.resume(true)
                    } else {
                        continuation.resume(false)
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }
        })
    }
}

suspend fun BillingClient.consumeProduct(consumeParams: ConsumeParams): ConsumeSubsResult {
    return suspendCancellableCoroutine { continuation ->
        consumeAsync(consumeParams) { billingResult, purchaseToken ->
            if (continuation.isActive) {
                continuation.resume(
                    ConsumeSubsResult(
                        billingResult = billingResult,
                        purchaseToken = purchaseToken
                    )
                )
            }
        }
    }
}
