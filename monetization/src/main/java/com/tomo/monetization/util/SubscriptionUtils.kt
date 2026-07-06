package com.tomo.monetization.util

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object SubscriptionUtils {
    fun formatPrice(priceAmountMicros: Long, currencyCode: String): String {
        val price = priceAmountMicros / 1_000_000.0
        return formatCurrency(price, currencyCode, Locale.getDefault())
    }

    fun formatPriceOffer(priceAmountMicros: Long, offerNumber: Int, currencyCode: String): String {
        val price = priceAmountMicros / 1_000_000.0
        val priceOffer = price / (1 - offerNumber / 100f)
        return formatCurrency(priceOffer, currencyCode, Locale.getDefault())
    }

    fun formatPricePerWeek(priceAmountMicros: Long, numberWeek: Int, currencyCode: String): String {
        val price = priceAmountMicros / 1_000_000.0
        val pricePerWeek = price / numberWeek
        return formatCurrency(pricePerWeek, currencyCode, Locale.getDefault())
    }

    private fun formatCurrency(amount: Double, currencyCode: String, locale: Locale): String {
        val currency = Currency.getInstance(currencyCode)
        val formatter = NumberFormat.getCurrencyInstance(locale)
        formatter.currency = currency

        if (currency.defaultFractionDigits == 0) {
            formatter.maximumFractionDigits = 0
            formatter.minimumFractionDigits = 0
        }

        return formatter.format(amount)
    }
}