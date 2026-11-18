package com.example.data.repository

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PremiumUiState(
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val isPremiumActive: Boolean = false,
    val products: List<ProductDetails> = emptyList(),
    val statusMessage: String? = null
)

class BillingRepository(context: Context) : PurchasesUpdatedListener {

    companion object {
        const val PRODUCT_MONTHLY = "lt_player_premium_monthly"
        const val PRODUCT_YEARLY = "lt_player_premium_yearly"
    }

    private val _state = MutableStateFlow(PremiumUiState())
    val state: StateFlow<PremiumUiState> = _state.asStateFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()
        )
        .build()

    fun connectAndQuery() {
        if (billingClient.isReady) {
            queryProductsAndPurchases()
            return
        }
        _state.value = _state.value.copy(isLoading = true, statusMessage = null)
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.value = _state.value.copy(isConnected = true)
                    queryProductsAndPurchases()
                } else {
                    _state.value = _state.value.copy(
                        isConnected = false,
                        isLoading = false,
                        statusMessage = "Google Play Billing unavailable (${result.debugMessage.ifBlank { "Code ${result.responseCode}" }})."
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                _state.value = _state.value.copy(isConnected = false, isLoading = false)
            }
        })
    }

    fun queryProductsAndPurchases() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_YEARLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            val message = when {
                billingResult.responseCode != BillingClient.BillingResponseCode.OK ->
                    "Play Store query failed: ${billingResult.debugMessage}"
                productDetailsList.isEmpty() ->
                    "Subscription SKUs are ready to link in Google Play Console ($PRODUCT_MONTHLY, $PRODUCT_YEARLY)."
                else -> null
            }
            _state.value = _state.value.copy(
                isLoading = false,
                products = productDetailsList,
                statusMessage = message
            )
        }
        restorePurchases()
    }

    fun restorePurchases() {
        if (!billingClient.isReady) {
            connectAndQuery()
            return
        }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val active = purchases.any {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                purchases.filter {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged
                }.forEach { acknowledge(it) }
                _state.value = _state.value.copy(
                    isPremiumActive = active,
                    statusMessage = if (active) "LT PLAYER Ultimate membership active." else _state.value.statusMessage
                )
            }
        }
    }

    fun launchSubscriptionFlow(activity: Activity, productDetails: ProductDetails) {
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: run {
            _state.value = _state.value.copy(statusMessage = "No active Play Store offer token found.")
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        billingClient.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        acknowledge(purchase)
                        _state.value = _state.value.copy(
                            isPremiumActive = true,
                            statusMessage = "Thank you for upgrading to LT PLAYER Ultimate!"
                        )
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _state.value = _state.value.copy(statusMessage = "Purchase cancelled.")
            }
            else -> {
                _state.value = _state.value.copy(
                    statusMessage = "Purchase error: ${billingResult.debugMessage.ifBlank { "Code ${billingResult.responseCode}" }}"
                )
            }
        }
    }

    private fun acknowledge(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { _ -> }
        }
    }

    fun release() {
        runCatching { billingClient.endConnection() }
    }
}
