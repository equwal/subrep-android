package com.honjimaku.subrep

import android.content.Context

/** What the user chose. It stays when the app stops. */
class Store(context: Context) {

    private val prefs = context.getSharedPreferences("subrep", Context.MODE_PRIVATE)

    /** The engine on the computer, as `address:port`. */
    var engine: String
        get() = prefs.getString("engine", "").orEmpty()
        set(value) = prefs.edit().putString("engine", value.trim()).apply()

    var lang: String
        get() = prefs.getString("lang", "ja").orEmpty()
        set(value) = prefs.edit().putString("lang", value.trim().ifEmpty { "auto" }).apply()

    /** True: the sound of the apps on this device. False: the microphone. */
    var screen: Boolean
        get() = prefs.getBoolean("screen", true)
        set(value) = prefs.edit().putBoolean("screen", value).apply()

    var share: Boolean
        get() = prefs.getBoolean("share", true)
        set(value) = prefs.edit().putBoolean("share", value).apply()

    /** What the viewers see as the name of the stream. */
    var title: String
        get() = prefs.getString("title", "").orEmpty()
        set(value) = prefs.edit().putString("title", value.trim().take(80)).apply()

    var overlay: Boolean
        get() = prefs.getBoolean("overlay", true)
        set(value) = prefs.edit().putBoolean("overlay", value).apply()

    /** The room on the relay. Made once, so the link stays the same. */
    val room: String
        get() = prefs.getString("room", null) ?: newRoom()

    val secret: String
        get() = prefs.getString("secret", null) ?: newRoom().let { prefs.getString("secret", "").orEmpty() }

    /** A new room: the old link stops. Returns the new room id. */
    fun newRoom(): String {
        val room = Room.newId()
        prefs.edit().putString("room", room).putString("secret", Room.newSecret()).apply()
        return room
    }
}
