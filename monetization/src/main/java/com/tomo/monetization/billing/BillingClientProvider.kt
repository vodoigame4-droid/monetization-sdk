package com.tomo.monetization.billing

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.PurchasesUpdatedListener

class BillingClientProvider(
    context: Context,
    updateListener: PurchasesUpdatedListener
) {
    val billingClient = BillingClient.newBuilder(context)
        .enableAutoServiceReconnection()
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enablePrepaidPlans()
                .enableOneTimeProducts()
                .build()
        )
        .setListener(updateListener)
        .build()
}
