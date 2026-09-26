package com.honjimaku.subrep

import com.android.billingclient.api.Purchase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The part of the Google Play shop that needs no device with Google Play. */
class ShopTest {

    @Test
    fun onlyAPurchasedPurchaseGoesToTheSite() {
        assertTrue(Shop.redeemable(Purchase.PurchaseState.PURCHASED))
        assertFalse(Shop.redeemable(Purchase.PurchaseState.PENDING))
        assertFalse(Shop.redeemable(Purchase.PurchaseState.UNSPECIFIED_STATE))
        // The server API numbers a paid purchase 0. The library does not.
        assertFalse(Shop.redeemable(0))
    }
}
