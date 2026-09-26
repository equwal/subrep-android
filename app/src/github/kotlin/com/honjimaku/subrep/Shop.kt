package com.honjimaku.subrep

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import org.json.JSONObject
import kotlin.concurrent.thread

/** Sells the hour packs on the Stripe page of subread.space. The GitHub and F-Droid builds use it. */
class Shop(
    private val activity: Activity,
    private val cloud: Cloud,
    @Suppress("UNUSED_PARAMETER") onChange: () -> Unit, // The play build uses it.
) {
    fun sells(state: Cloud.State) = state.available

    fun load(@Suppress("UNUSED_PARAMETER") packs: List<Cloud.Pack>) = Unit

    fun price(pack: Cloud.Pack): String? = pack.price

    fun buy(pack: Cloud.Pack, @Suppress("UNUSED_PARAMETER") accountId: String) {
        thread(name = "checkout") {
            val url = runCatching { cloud.checkout(pack.id) }
            activity.runOnUiThread {
                url.onSuccess { runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }
                    .onFailure { Toast.makeText(activity, activity.getString(R.string.cloud_buy_failed, it.message), Toast.LENGTH_LONG).show() }
            }
        }
    }

    fun resume() = Unit

    fun close() = Unit
}

/** The address of the Stripe payment page for [packId]. It opens in the browser. */
fun Cloud.checkout(packId: String): String =
    post("/api/captions/checkout", JSONObject().put("pack_id", packId)).getString("url")
