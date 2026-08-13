package com.tomo.monetization.billing

/**
 * Một dòng sản phẩm đã được chuẩn hoá để hiển thị trên paywall.
 *
 * Với **SUBS**: mỗi item là một offer của một base plan. `priceAmountMicros`/`priceFormat` là giá
 * của base phase (giá gốc theo chu kỳ), `offerPriceFormat` là giá của phase khuyến mãi (nếu có).
 *
 * Với **INAPP** (bao gồm gói lifetime): `basePlanId` luôn rỗng, `recurrenceMode` luôn
 * [NON_RECURRING]. `priceAmountMicros`/`priceFormat`/`formattedPrice` là **giá thực tế người dùng
 * phải trả**. Nếu offer đang giảm giá thì [originalPriceFormat] chứa giá gốc (để gạch ngang),
 * ngược lại nó rỗng.
 */
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
    /** Chỉ có với INAPP nhiều purchase option (Billing 8+). Null với SUBS. */
    val purchaseOptionId: String? = null,
    /** Token của offer được chọn. Bắt buộc phải truyền lại khi launch billing flow. */
    val offerToken: String = "",
    /** Giá gốc trước khuyến mãi của INAPP; rỗng khi không có khuyến mãi. */
    val originalPriceFormat: String = "",
) {
    companion object {
        const val NON_RECURRING = 3
    }
}
