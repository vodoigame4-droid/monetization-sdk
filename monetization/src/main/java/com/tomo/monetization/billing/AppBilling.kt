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
import com.tomo.monetization.util.EventTracking
import com.tomo.monetization.util.SubscriptionUtils
import kotlinx.coroutines.Dispatchers
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

    private val _isLifetimePurchasedFlow = MutableStateFlow(false)

    /** true khi user đang sở hữu một trong các product id đã khai báo ở [registerProducts]. */
    val isLifetimePurchasedFlow: StateFlow<Boolean> = _isLifetimePurchasedFlow.asStateFlow()

    val isLifetimePurchased: Boolean get() = _isLifetimePurchasedFlow.value

    var onPurchaseAcknowledged: ((Purchase) -> Unit)? = null

    /** Các product id INAPP là gói vĩnh viễn (remove ads / pro lifetime). Không bao giờ được consume. */
    @Volatile
    private var lifetimeProductIds: Set<String> = emptySet()

    /** Các product id INAPP dạng tiêu hao (coin, gem...). Không tính vào trạng thái ad-free. */
    @Volatile
    private var consumableProductIds: Set<String> = emptySet()

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

    /**
     * Khai báo phân loại các sản phẩm INAPP. Nên gọi ngay sau [init].
     *
     * - [lifetime]: các gói vĩnh viễn. Chúng được tính là ad-free, được auto-acknowledge, và bị
     *   [consumePurchase] từ chối để tránh vô tình xoá quyền của user.
     * - [consumable]: các gói tiêu hao. Không tính vào ad-free và không bị auto-acknowledge
     *   (việc consume đã bao hàm acknowledge).
     *
     * Nếu không khai báo [lifetime], mọi purchase INAPP hợp lệ sẽ được coi là ad-free (hành vi cũ).
     */
    fun registerProducts(
        lifetime: Collection<String> = emptySet(),
        consumable: Collection<String> = emptySet(),
    ) {
        lifetimeProductIds = lifetime.toSet()
        consumableProductIds = consumable.toSet()
        val overlap = lifetimeProductIds intersect consumableProductIds
        if (overlap.isNotEmpty()) {
            Log.e(TAG, "registerProducts: product id vừa lifetime vừa consumable: $overlap")
        }
    }

    private fun Purchase.isLifetime() = products.any { it in lifetimeProductIds }

    private fun Purchase.isConsumable() = products.any { it in consumableProductIds }

    suspend fun checkPurchased() = withContext(Dispatchers.IO) {
        val subs = billing.getPurchases(BillingClient.ProductType.SUBS)
        val inApps = billing.getPurchases(BillingClient.ProductType.INAPP)
        if (subs == null || inApps == null) {
            // Billing connection failed — preserve current state rather than downgrading a paying user to ad-supported
            Log.w(TAG, "checkPurchased: billing connection failed, preserving current ad-free state=${isPurchasedAdFree}")
            return@withContext isPurchasedAdFree
        }
        Log.d(TAG, "Purchased: ${subs + inApps}")

        val validSubs = subs.filter { it.purchaseState == PURCHASED }
        val validInApps = inApps.filter { it.purchaseState == PURCHASED }
        val pendingPurchases = (subs + inApps).filter { it.purchaseState == PENDING }

        if (pendingPurchases.isNotEmpty()) {
            Log.d(TAG, "checkPurchased: Found ${pendingPurchases.size} PENDING purchases (e.g. Slow Test Card)")
        }

        // Không auto-acknowledge hàng tiêu hao: consume mới là bước đúng và nó đã bao hàm acknowledge.
        (validSubs + validInApps)
            .filter { !it.isAcknowledged && !it.isConsumable() }
            .forEach { unacknowledgedPurchase ->
                Log.d(TAG, "checkPurchased: Auto-acknowledging unacknowledged purchase: ${unacknowledgedPurchase.products}")
                acknowledgePurchase(unacknowledgedPurchase)
            }

        val lifetimePurchases = validInApps.filter { it.isLifetime() }
        _isLifetimePurchasedFlow.value = lifetimePurchases.isNotEmpty()

        // Chưa khai báo lifetime id -> giữ hành vi cũ (mọi INAPP đều là ad-free).
        val entitlingInApps = if (lifetimeProductIds.isEmpty()) {
            validInApps.filter { !it.isConsumable() }
        } else {
            lifetimePurchases
        }

        val hasPurchase = validSubs.isNotEmpty() || entitlingInApps.isNotEmpty()
        _isAdFreeFlow.value = hasPurchase
        return@withContext hasPurchase
    }

    /**
     * Trả về các purchase INAPP đang sở hữu của những gói lifetime đã khai báo ở [registerProducts].
     * Trả về list rỗng nếu không kết nối được billing.
     */
    suspend fun getLifetimePurchases(): List<Purchase> = withContext(Dispatchers.IO) {
        val inApps = billing.getPurchases(BillingClient.ProductType.INAPP)
        if (inApps == null) {
            Log.w(TAG, "getLifetimePurchases: billing connection failed")
            return@withContext emptyList()
        }
        return@withContext inApps.filter { it.purchaseState == PURCHASED && it.isLifetime() }
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
            onPurchaseAcknowledged?.invoke(purchase)
        } else {
            EventTracking.logEvent("purchased_not_acknowledged") {
                param("code", result.responseCode.toLong())
                param("msg", result.debugMessage)
                param("purchase_package_id", productId ?: "")
            }
        }
        return result
    }

    /**
     * Consume một sản phẩm INAPP tiêu hao (coin, gem...).
     *
     * Purchase thuộc nhóm lifetime đã khai báo ở [registerProducts] sẽ **bị từ chối** và trả về
     * [BillingResponseCode.DEVELOPER_ERROR] — consume gói lifetime đồng nghĩa với việc xoá vĩnh
     * viễn quyền của user. Nếu cần reset gói lifetime khi test, dùng
     * [consumeLifetimePurchaseForTesting].
     */
    suspend fun consumePurchase(purchase: Purchase): BillingResult {
        if (purchase.isLifetime()) {
            val msg = "Refused to consume lifetime product ${purchase.products}. " +
                "Use consumeLifetimePurchaseForTesting() if this is intentional."
            Log.e(TAG, "consumePurchase: $msg")
            return BillingResult.newBuilder()
                .setResponseCode(BillingResponseCode.DEVELOPER_ERROR)
                .setDebugMessage(msg)
                .build()
        }
        return consumeInternal(purchase)
    }

    private suspend fun consumeInternal(purchase: Purchase): BillingResult {
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        val result = billing.consumeProduct(consumeParams)
        Log.d(TAG, "consume: ${purchase.products} -> ${result.billingResult.responseCode} ${result.billingResult.debugMessage}")
        return result.billingResult
    }

    /**
     * **Chỉ dùng cho testing.** Consume toàn bộ gói lifetime đang sở hữu để có thể mua lại
     * (license tester / test card). Sau khi consume sẽ chạy lại [checkPurchased] để đồng bộ state.
     *
     * Nên bọc trong `if (BuildConfig.DEBUG)` ở phía app — gọi nhầm trên production sẽ xoá quyền
     * vĩnh viễn của user.
     *
     * @param productId chỉ consume đúng product id này; bỏ trống để consume mọi gói lifetime.
     * @return danh sách productId đã consume thành công.
     */
    suspend fun consumeLifetimePurchaseForTesting(productId: String? = null): List<String> =
        withContext(Dispatchers.IO) {
            Log.w(TAG, "consumeLifetimePurchaseForTesting: TESTING ONLY — revoking lifetime entitlement")
            val targets = getLifetimePurchases()
                .filter { productId == null || productId in it.products }

            if (targets.isEmpty()) {
                Log.w(TAG, "consumeLifetimePurchaseForTesting: no owned lifetime purchase (productId=$productId)")
                return@withContext emptyList()
            }

            val consumed = targets.filter { purchase ->
                consumeInternal(purchase).responseCode == BillingResponseCode.OK
            }.flatMap { it.products }

            checkPurchased()
            Log.w(TAG, "consumeLifetimePurchaseForTesting: consumed $consumed")
            return@withContext consumed
        }

    suspend fun getSubsProductsList(productIds: List<String>): List<IAPBillingItem> =
        withContext(Dispatchers.IO) {
            val details = queryProductDetails(productIds, BillingClient.ProductType.SUBS)
            Log.d(TAG, "getSubsProductsList: $details")
            return@withContext handleSubsProductDetails(details)
        }

    suspend fun getInAppProductsList(productIds: List<String>): List<IAPBillingItem> =
        withContext(Dispatchers.IO) {
            val details = queryProductDetails(productIds, BillingClient.ProductType.INAPP)
            Log.d(TAG, "getInAppProductsList: $details")
            return@withContext handleInAppProductDetails(details)
        }

    /**
     * Query một lần cho toàn bộ product id (Play hỗ trợ batch) và sắp xếp kết quả theo đúng thứ tự
     * [productIds] truyền vào. Id không tồn tại sẽ bị Play bỏ qua chứ không làm hỏng cả query.
     */
    private suspend fun queryProductDetails(
        productIds: List<String>,
        productType: String,
    ): List<ProductDetails> {
        val distinctIds = productIds.filter { it.isNotBlank() }.distinct()
        if (distinctIds.isEmpty()) return emptyList()

        val query = QueryProductDetailsParams.newBuilder()
            .setProductList(
                distinctIds.map { productId ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(productType)
                        .build()
                }
            ).build()

        val result = billing.getProducts(query)
        if (result == null) {
            Log.w(TAG, "queryProductDetails($productType): billing connection failed, returning empty")
            return emptyList()
        }
        if (result.billingResult.responseCode != BillingResponseCode.OK) {
            Log.w(
                TAG,
                "queryProductDetails($productType): code=${result.billingResult.responseCode} " +
                    "msg=${result.billingResult.debugMessage}"
            )
        }

        val details = result.productDetailsList.orEmpty()
        val missing = distinctIds - details.map { it.productId }.toSet()
        if (missing.isNotEmpty()) {
            Log.w(TAG, "queryProductDetails($productType): not found on Play Console: $missing")
        }
        return details.sortedBy { distinctIds.indexOf(it.productId) }
    }

    private fun handleSubsProductDetails(allProductDetails: List<ProductDetails>): List<IAPBillingItem> {
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
                    offerToken = details.offerToken,
                )
                items.add(subscriptionPlan)
            }
        }
        return items
    }

    /**
     * INAPP không có `subscriptionOfferDetails`. Billing 8+ cho phép một sản phẩm one-time có nhiều
     * purchase option/offer, nên ưu tiên `oneTimePurchaseOfferDetailsList` và fallback về
     * `oneTimePurchaseOfferDetails` cho sản phẩm cấu hình kiểu cũ.
     */
    private fun handleInAppProductDetails(allProductDetails: List<ProductDetails>): List<IAPBillingItem> {
        return allProductDetails.flatMap { productDetails ->
            // Mỗi purchase option giữ lại 1 offer, ưu tiên offer khuyến mãi (có offerId).
            @Suppress("DEPRECATION")
            val offers = productDetails.oneTimePurchaseOfferDetailsList.orEmpty()
                .groupBy { it.purchaseOptionId }
                .values
                .mapNotNull { group -> group.firstOrNull { it.offerId != null } ?: group.firstOrNull() }
                .ifEmpty {
                    // Fallback cho sản phẩm one-time cấu hình kiểu cũ (không có purchase option).
                    listOfNotNull(productDetails.oneTimePurchaseOfferDetails)
                }

            if (offers.isEmpty()) {
                Log.w(TAG, "handleInAppProductDetails: no one-time offer for ${productDetails.productId}")
            }

            offers.mapNotNull { offer ->
                val currencyCode = offer.priceCurrencyCode
                if (currencyCode.isNullOrEmpty()) {
                    Log.w(TAG, "handleInAppProductDetails: missing currency for ${productDetails.productId}")
                    return@mapNotNull null
                }
                val priceAmountMicros = offer.priceAmountMicros

                val fullPriceMicros = offer.fullPriceMicros
                val originalPriceFormat = if (fullPriceMicros != null && fullPriceMicros > priceAmountMicros) {
                    SubscriptionUtils.formatPrice(fullPriceMicros, currencyCode).lowercase()
                } else {
                    ""
                }

                IAPBillingItem(
                    productId = productDetails.productId,
                    basePlanId = "",
                    priceAmountMicros = priceAmountMicros,
                    priceCurrencyCode = currencyCode,
                    priceFormat = SubscriptionUtils.formatPrice(priceAmountMicros, currencyCode).lowercase(),
                    formattedPrice = offer.formattedPrice.orEmpty(),
                    recurrenceMode = IAPBillingItem.NON_RECURRING,
                    productType = productDetails.productType,
                    offerPriceFormat = "",
                    offerId = offer.offerId,
                    purchaseOptionId = offer.purchaseOptionId,
                    offerToken = offer.offerToken.orEmpty(),
                    originalPriceFormat = originalPriceFormat,
                )
            }
        }
    }

    fun purchaseIAPBillingItem(
        activity: Activity,
        billingItem: IAPBillingItem,
    ) {
        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
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
                if (billingResult.responseCode != BillingResponseCode.OK) {
                    Log.e(
                        TAG,
                        "purchaseIAPBillingItem: query failed code=${billingResult.responseCode} " +
                            "msg=${billingResult.debugMessage}"
                    )
                    return@queryProductDetailsAsync
                }

                val productResult = queryProductDetailsResult.productDetailsList
                var offerToken = billingItem.offerToken
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
                    productResult.firstOrNull { it.productId == billingItem.productId }
                        ?.also { details ->
                            // Billing 8+: one-time product có purchase option thì bắt buộc có offerToken.
                            val offers = details.oneTimePurchaseOfferDetailsList.orEmpty()
                            val match = offers.firstOrNull { it.offerToken == billingItem.offerToken }
                                ?: offers.firstOrNull { it.purchaseOptionId == billingItem.purchaseOptionId }
                                ?: offers.firstOrNull()
                            match?.offerToken?.let { offerToken = it }
                        }
                }

                if (productDetails == null) {
                    Log.e(TAG, "purchaseIAPBillingItem: product not found ${billingItem.productId}")
                    return@queryProductDetailsAsync
                }

                val paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                if (offerToken.isNotEmpty()) {
                    paramsBuilder.setOfferToken(offerToken)
                }
                val productDetailsParamsList = listOf(paramsBuilder.build())

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
