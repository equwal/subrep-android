package com.honjimaku.subrep

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock

/**
 * SubRead Overlay: the panel over the player. It takes a line with the method `line` of its
 * content provider, and answers in the key `live` whether it shows it.
 */
object Overlay {

    const val PACKAGE = "space.subread.overlay"
    const val INSTALL = "https://github.com/equwal/subread-overlay/releases/latest"
    private const val AUTHORITY = "space.subread.overlay.player"
    private val uri: Uri = Uri.parse("content://$AUTHORITY")

    const val OK = "ok"
    const val NOT_INSTALLED = "not_installed"

    /** The overlay's answer for a version that has no `line` method. */
    const val TOO_OLD = "too_old"

    fun installed(context: Context): Boolean = context.packageManager.resolveContentProvider(AUTHORITY, 0) != null

    fun launch(context: Context): Intent? = context.packageManager.getLaunchIntentForPackage(PACKAGE)

    fun line(context: Context, text: String, partial: Boolean): String =
        call(context, "line", text, Bundle().apply { putBoolean("partial", partial) })

    fun end(context: Context): String = call(context, "end", null, null)

    private fun call(context: Context, method: String, arg: String?, extras: Bundle?): String {
        val answer = try {
            context.contentResolver.call(uri, method, arg, extras)
        } catch (e: Exception) {
            return NOT_INSTALLED
        } ?: return NOT_INSTALLED
        return answer.getString("live") ?: TOO_OLD
    }
}

/**
 * Sends the captions to the overlay from its own thread, because a provider call waits for
 * the other app. Partial lines go at most every [PARTIAL_GAP_MS]: the overlay draws each line,
 * and an e-ink screen cannot draw five times a second.
 */
class OverlayFeed(private val context: Context, private val handler: Handler) {

    private var pendingPartial: String? = null
    private var lastAt = 0L
    private val flush = Runnable {
        pendingPartial?.let { send(it, partial = true) }
        pendingPartial = null
    }

    fun caption(caption: Caption) {
        handler.post {
            when (caption.type) {
                "final" -> {
                    handler.removeCallbacks(flush)
                    pendingPartial = null
                    send(caption.text, partial = false)
                }
                "partial" -> {
                    pendingPartial = caption.text
                    handler.removeCallbacks(flush)
                    handler.postDelayed(flush, (PARTIAL_GAP_MS - (SystemClock.elapsedRealtime() - lastAt)).coerceAtLeast(0))
                }
            }
        }
    }

    fun end() {
        handler.removeCallbacks(flush)
        handler.post { Overlay.end(context) }
    }

    private fun send(text: String, partial: Boolean) {
        lastAt = SystemClock.elapsedRealtime()
        Feed.overlay(Overlay.line(context, text, partial))
    }

    private companion object {
        const val PARTIAL_GAP_MS = 700L
    }
}
