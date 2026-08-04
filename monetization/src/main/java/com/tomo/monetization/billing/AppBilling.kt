package com.tomo.monetization.billing

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.Purchase.PurchaseState.PENDING
import com.android.billingclient.api.Purchase.PurchaseState.PURCHASED
import com.android.billingclient.api.QueryProductDetailsParams
import com.google.common.collect.ImmutableList
import com.tomo.monetization.util.EventTracking
import com.tomo.monetization.util.SubscriptionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

@SuppressLint("StaticFieldLeak")
object AppBilling {
    private const val TAG = "AppBilling"

    private var appContext: Context? = null

    private val _isAdFreeFlow = MutableStateFlow(false)
    val isAdFreeFlow: StateFlow<Boolean> = _isAdFreeFlow.asStateFlow()

    val isPurchasedAdFree: Boolean get() = _isAdFreeFlow.value

    val billingUpdateListener: BillingUpdateListener by lazy {
        BillingUpdateListener()
    }

    val billingClientProvider: BillingClientProvider by lazy {
        val context = appContext
            ?: throw IllegalStateException("AppBilling.init(context) must be called before accessing properties!")
        BillingClientProvider(context, billingUpdateListener)
    }

    val billing: BillingRepository by lazy {
        BillingRepository(billingClientProvider)
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun checkPurchased() = withContext(Dispatchers.IO) {
        val subs = billing.getPurchases(BillingClient.ProductType.SUBS)
        val inApps = billing.getPurchases(BillingClient.ProductType.INAPP)
        if (subs == null || inApps == null) {
            // Billing connection failed — preserve current state rather than downgrading a paying user to ad-supported
            Log.w(TAG, "checkPurchased: billing connection failed, preserving current ad-free state=${isPurchasedAdFree}")
            return@withContext isPurchasedAdFree
        }
        val allPurchases = subs + inApps
        Log.d(TAG, "Purchased: $allPurchases")

        val validPurchases = allPurchases.filter { it.purchaseState == PURCHASED }
        val pendingPurchases = allPurchases.filter { it.purchaseState == PENDING }

        if (pendingPurchases.isNotEmpty()) {
            Log.d(TAG, "checkPurchased: Found ${pendingPurchases.size} PENDING purchases (e.g. Slow Test Card)")
        }

        validPurchases.filter { !it.isAcknowledged }.forEach { unacknowledgedPurchase ->
            Log.d(TAG, "checkPurchased: Auto-acknowledging unacknowledged purchase: ${unacknowledgedPurchase.products}")
            acknowledgePurchase(unacknowledgedPurchase)
        }

        val hasPurchase = validPurchases.isNotEmpty()
        _isAdFreeFlow.value = hasPurchase
        return@withContext hasPurchase
    }

    suspend fun acknowledgePurchase(purchase: Purchase): BillingResult? {
        val purchaseToken = purchase.purchaseToken
        val orderId = purchase.orderId
        val productId = purchase.products.firstOrNull()

        val half = purchaseToken.length / 2
        val tokenPart1 = purchaseToken.take(half)
        val tokenPart2 = purchaseToken.substring(half)

        if (purchase.purchaseState != PURCHASED || purchase.isAcknowledged)
            return null

        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        val result = billing.acknowledgePurchase(acknowledgePurchaseParams)
        Log.d(TAG, "acknowledgePurchase: $productId $orderId $result")
        if (result.responseCode == BillingResponseCode.OK) {
            EventTracking.logEvent("confirm_purchased_with_google") {
                param("purchase_order_id", orderId ?: "")
                param("purchase_token_part_1", tokenPart1)
                param("purchase_token_part_2", tokenPart2)
                param("purchase_package_id", productId ?: "")
            }
        } else {
            EventTracking.logEvent("purchased_not_acknowledged") {
                param("code", result.responseCode.toLong())
                param("msg", result.debugMessage)
                param("purchase_package_id", productId ?: "")
            }
        }
        return result
    }

    // Consumable product inapp
    suspend fun consumePurchase(purchase: Purchase): BillingResult {
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        val result = billing.consumeProduct(consumeParams)
        return result.billingResult
    }

    suspend fun getSubsProductsList(productIds: List<String>): List<IAPBillingItem> = withContext(Dispatchers.IO) {
        val allProductDetails = productIds.map { productId ->
            async {
                val query = QueryProductDetailsParams.newBuilder()
                    .setProductList(
                        ImmutableList.of(
                            QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(productId)
                                .setProductType(BillingClient.ProductType.SUBS)
                                .build(),
                        )
                    ).build()
                billing.getProducts(query)?.productDetailsList.orEmpty()
            }
        }.awaitAll().flatten()

        Log.d(TAG, "getSubsProductsList: $allProductDetails")

        return@withContext handleProductDetails(allProductDetails)
    }

    suspend fun getInAppProductsList(productIds: List<String>): List<IAPBillingItem> = withContext(Dispatchers.IO) {
        val allProductDetails = productIds.map { productId ->
            async {
                val query = QueryProductDetailsParams.newBuilder()
                    .setProductList(
                        ImmutableList.of(
                            QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(productId)
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build(),
                        )
                    ).build()
                billing.getProducts(query)?.productDetailsList.orEmpty()
            }
        }.awaitAll().flatten()

        Log.d(TAG, "getInAppProductsList: $allProductDetails")

        return@withContext handleProductDetails(allProductDetails)
    }

    private fun handleProductDetails(allProductDetails: List<ProductDetails>): List<IAPBillingItem> {
        val allOffers = allProductDetails.flatMap { productDetails ->
            productDetails.subscriptionOfferDetails.orEmpty().map { offer ->
                productDetails to offer
            }
        }

        // Priority offer có offerId, if haven't offer of basePlan then take basePlan
        val listSubscription = buildList {
            val byBasePlan = allOffers.groupBy { it.second.basePlanId }
            byBasePlan.forEach { (_, offersOfPlan) ->
                val withOffer = offersOfPlan.filter { it.second.offerId != null }
                if (withOffer.isNotEmpty()) addAll(withOffer)
                else add(offersOfPlan.first())
            }
        }

        val items = mutableListOf<IAPBillingItem>()
        listSubscription.forEach { (productDetails, details) ->
            val phases = details.pricingPhases.pricingPhaseList
            val basePhase = phases.firstOrNull { it.recurrenceMode == 1 && it.priceAmountMicros > 0 }
            val offerPhaseWithPrice = phases.firstOrNull { it.recurrenceMode == 2 && it.priceAmountMicros > 0 }
            basePhase?.let { pricingPhase ->
                val offerPriceFormat = offerPhaseWithPrice?.let { p ->
                    SubscriptionUtils.formatPrice(p.priceAmountMicros, p.priceCurrencyCode).lowercase()
                } ?: ""

                val subscriptionPlan = IAPBillingItem(
                    productId = productDetails.productId,
                    basePlanId = details.basePlanId,
                    priceAmountMicros = pricingPhase.priceAmountMicros,
                    priceCurrencyCode = pricingPhase.priceCurrencyCode,
                    productType = productDetails.productType,
                    priceFormat = SubscriptionUtils.formatPrice(
                        pricingPhase.priceAmountMicros, pricingPhase.priceCurrencyCode
                    ).lowercase(),
                    formattedPrice = pricingPhase.formattedPrice,
                    recurrenceMode = pricingPhase.recurrenceMode,
                    offerId = details.offerId,
                    offerPriceFormat = offerPriceFormat,
                )
                items.add(subscriptionPlan)
            }
        }
        return items
    }

    fun purchaseIAPBillingItem(
        activity: Activity,
        billingItem: IAPBillingItem,
    ) {
        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ImmutableList.of(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(billingItem.productId)
                        .setProductType(billingItem.productType)
                        .build(),
                )
            ).build()

        if (!billing.getBillingClient().isReady) {
            Log.e(TAG, "BillingClient is not ready")
            return
        }

        billing.getBillingClient()
            .queryProductDetailsAsync(queryProductDetailsParams) { billingResult, queryProductDetailsResult ->
                if (billingResult.responseCode == BillingResponseCode.OK) {
                    val productResult = queryProductDetailsResult.productDetailsList
                    var offerToken = ""
                    val productDetails = if (billingItem.productType == BillingClient.ProductType.SUBS) {
                        productResult.firstOrNull { productDetails ->
                            productDetails.subscriptionOfferDetails?.any {
                                if (it.basePlanId == billingItem.basePlanId) {
                                    offerToken = it.offerToken
                                    true
                                } else {
                                    false
                                }
                            } == true
                        }
                    } else {
                        productResult.firstOrNull { productDetails ->
                            productDetails.productId == billingItem.productId
                        }
                    }

                    productDetails?.let {
                        val productDetailsParamsList = if (billingItem.productType == BillingClient.ProductType.SUBS) {
                            listOf(
                                BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(it)
                                    .setOfferToken(offerToken)
                                    .build()
                            )
                        } else {
                            listOf(
                                BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(it)
                                    .build()
                            )
                        }
                        try {
                            val billingFlowParams = BillingFlowParams.newBuilder()
                                .setProductDetailsParamsList(productDetailsParamsList)
                                .build()

                            billing.getBillingClient().launchBillingFlow(activity, billingFlowParams)
                        } catch (e: NullPointerException) {
                            Toast.makeText(appContext, "Billing error", Toast.LENGTH_SHORT).show()
                            Log.e(TAG, "PendingIntent is null: ${e.message}")
                        }
                    }
                }
            }
    }
}
