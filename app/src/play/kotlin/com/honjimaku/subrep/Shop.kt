package com.honjimaku.subrep

import android.app.Activity
import android.widget.Toast
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import java.util.concurrent.Executors

/**
 * Sells the hour packs with Google Play Billing, as Google Play requires. subread.space checks each
 * purchase with Google, adds its hours once and consumes it. This class never consumes.
 */
class Shop(
    private val activity: Activity,
    private val cloud: Cloud,
    private val onChange: () -> Unit,
) : PurchasesUpdatedListener {

    private val client = BillingClient.newBuilder(activity)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    /** The product details of the last query, by product id. */
    @Volatile private var products: Map<String, ProductDetails> = emptyMap()

    /** The pack ids of the last load, for the query after the billing setup. */
    @Volatile private var ids: List<String> = emptyList()

    init {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingResponseCode.OK) return
                query()
                resume()
            }

            // enableAutoServiceReconnection connects again at the next call.
            override fun onBillingServiceDisconnected() = Unit
        })
    }

    fun sells(state: Cloud.State) = state.playAvailable

    /** Asks Google Play for the prices at each refresh. Google says not to keep ProductDetails. */
    fun load(packs: List<Cloud.Pack>) {
        ids = packs.map { it.id }
        query()
    }

    /** The Google Play price of [pack], or null to hide the pack. */
    fun price(pack: Cloud.Pack): String? = products[pack.id]?.let { offer(it) }?.formattedPrice

    fun buy(pack: Cloud.Pack, accountId: String) {
        val details = products[pack.id] ?: return
        val token = offer(details)?.offerToken ?: return
        if (accountId.isEmpty()) return
        val product = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(token)
            .build()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(product))
            // The subread.space account that gets the hours. Its id is random and not a secret.
            // The device cookie is the secret, and it never goes to Google.
            .setObfuscatedAccountId(accountId)
            .build()
        val result = client.launchBillingFlow(activity, params)
        // Google Play gives the result of the purchase to onPurchasesUpdated. A code here means
        // that the purchase sheet did not open.
        if (result.responseCode != BillingResponseCode.OK) onPurchasesUpdated(result, null)
    }

    /** Sends each paid purchase that subread.space did not consume yet. It shows no message. */
    fun resume() = sendOpen(owned = false)

    fun close() = client.endConnection()

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> for (purchase in purchases.orEmpty()) {
                if (redeemable(purchase.purchaseState)) {
                    send(purchase, loud = true)
                } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                    say(activity.getString(R.string.play_pending))
                }
            }
            BillingResponseCode.USER_CANCELED -> Unit
            // Google Play has an earlier purchase of this pack that subread.space did not consume yet.
            BillingResponseCode.ITEM_ALREADY_OWNED -> sendOpen(owned = true)
            else -> say(activity.getString(R.string.cloud_buy_failed, result.debugMessage))
        }
    }

    /**
     * Asks Google Play for the purchases that subread.space did not consume yet, and sends each paid
     * one. [owned]: the user tapped a pack that Google Play still holds, so tell the user what happens.
     */
    private fun sendOpen(owned: Boolean) {
        val params = QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingResponseCode.OK) return@queryPurchasesAsync
            purchases.filter { redeemable(it.purchaseState) }.forEach { send(it, loud = owned) }
            if (owned) {
                val pending = purchases.any { it.purchaseState == Purchase.PurchaseState.PENDING }
                say(activity.getString(if (pending) R.string.play_pending else R.string.play_owned))
            }
        }
    }

    /** The one offer of a pack: one Buy option, and no discount offers. */
    private fun offer(details: ProductDetails) = details.oneTimePurchaseOfferDetailsList?.firstOrNull()

    private fun query() {
        val list = ids
        if (list.isEmpty()) return
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            list.map { QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(ProductType.INAPP).build() },
        ).build()
        client.queryProductDetailsAsync(params) { result, found ->
            // On a failure, keep the last prices. The next refresh asks again.
            if (result.responseCode != BillingResponseCode.OK) return@queryProductDetailsAsync
            products = found.productDetailsList.associateBy { it.productId }
            activity.runOnUiThread { onChange() }
        }
    }

    private fun send(purchase: Purchase, loud: Boolean) {
        // A one-time purchase holds one product.
        val product = purchase.products.firstOrNull() ?: return
        SENDER.execute {
            runCatching { cloud.redeem(product, purchase.purchaseToken) }
                .onSuccess { Feed.cloudLeft(it) }
                .onFailure { if (loud) say(activity.getString(R.string.play_not_added, it.message.orEmpty())) }
        }
    }

    private fun say(text: String) = activity.runOnUiThread { Toast.makeText(activity, text, Toast.LENGTH_LONG).show() }

    companion object {
        /**
         * One thread for the whole app sends the purchases one after another. So two credits of one
         * account never run at the same time on the server.
         */
        private val SENDER = Executors.newSingleThreadExecutor()

        /** Only a paid purchase goes to subread.space. The library numbers PURCHASED as 1, the server API as 0. */
        internal fun redeemable(state: Int) = state == Purchase.PurchaseState.PURCHASED
    }
}
