package com.tomo.monetization.billing

import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync

class BillingRepository(billingClientProvider: BillingClientProvider) {

    private val billingClient = billingClientProvider.billingClient

    suspend fun getPurchases(purchaseType: String): List<Purchase>? {
        val connectIfNeeded = connectIfNeeded()
        if (!connectIfNeeded)
            return null

        val queryPurchaseParams = QueryPurchasesParams.newBuilder()
            .setProductType(purchaseType)
            .build()

        return billingClient.queryPurchasesAsync(queryPurchaseParams).purchasesList
    }

    suspend fun getProducts(productDetailsParams: QueryProductDetailsParams): QueryProductDetailsResult? {
        val connectIfNeeded = connectIfNeeded()
        if (!connectIfNeeded)
            return null

        return billingClient.queryProductDetails(productDetailsParams)
    }

    private suspend fun connectIfNeeded(): Boolean {
        return billingClient.isReady || billingClient.connect()
    }

    suspend fun consumeProduct(consumeParams: ConsumeParams): ConsumeSubsResult =
        billingClient.consumeProduct(consumeParams)

    suspend fun acknowledgePurchase(acknowledgePurchaseParams: AcknowledgePurchaseParams): BillingResult =
        billingClient.acknowledgePurchase(acknowledgePurchaseParams)

    fun getBillingClient() = billingClient
}
