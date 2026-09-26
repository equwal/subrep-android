package com.honjimaku.subrep

import android.app.Activity

/**
 * The shop of the Google Play build. Google Play allows no other payment for digital goods, so this
 * build has no Stripe page. It sells no hours yet. It uses the hours that the account has.
 */
@Suppress("UNUSED_PARAMETER")
class Shop(activity: Activity, cloud: Cloud, onChange: () -> Unit) {
    fun sells(state: Cloud.State) = state.available

    fun load(packs: List<Cloud.Pack>) = Unit

    fun price(pack: Cloud.Pack): String? = null

    fun buy(pack: Cloud.Pack, accountId: String) = Unit

    fun resume() = Unit

    fun close() = Unit
}
