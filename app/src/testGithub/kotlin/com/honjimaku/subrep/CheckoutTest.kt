package com.honjimaku.subrep

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** The Stripe path of the GitHub build, against a local server that answers as subread.space does. */
class CheckoutTest {

    private val site = FakeSite()

    @After
    fun stop() = site.stop()

    @Test
    fun checkoutGivesThePaymentPage() {
        site.answer = { 200 to """{"url": "https://checkout.stripe.com/c/pay/cs_1"}""" }
        assertEquals("https://checkout.stripe.com/c/pay/cs_1", site.cloud().checkout("captions20"))
        assertEquals("/api/captions/checkout", site.seen[0].path)
        assertEquals("""{"pack_id":"captions20"}""", String(site.seen[0].body))
    }

    /** F-Droid builds the GitHub build, and it accepts no proprietary library. */
    @Test
    fun theGithubBuildHasNoPlayBilling() {
        assertThrows(ClassNotFoundException::class.java) { Class.forName("com.android.billingclient.api.BillingClient") }
    }
}
