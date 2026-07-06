package com.tomo.monetization.billing

data class IAPBillingItem(
    val productId: String,
    val basePlanId: String,
    val priceAmountMicros: Long,
    val priceCurrencyCode: String,
    val priceFormat: String,
    val formattedPrice: String,
    val recurrenceMode: Int,
    val productType: String,
    val offerPriceFormat: String,
    val offerId: String? = null,
)
