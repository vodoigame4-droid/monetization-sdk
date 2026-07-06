package com.tomo.monetization.billing

import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class BillingUpdateListener : PurchasesUpdatedListener {
    private val _purchaseUpdate = MutableStateFlow<Pair<BillingResult, List<Purchase>>?>(null)
    val purchaseUpdate = _purchaseUpdate.asStateFlow()

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        val newList = mutableListOf<Purchase>()
        newList.addAll(purchases ?: emptyList())
        _purchaseUpdate.value = Pair(billingResult, newList)
    }
}
