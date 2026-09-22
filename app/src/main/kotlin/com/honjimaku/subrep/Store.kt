package com.honjimaku.subrep

import android.content.Context

/** What the user chose. It stays when the app stops. */
class Store(context: Context) {

    private val prefs = context.getSharedPreferences("subrep", Context.MODE_PRIVATE)

    /** The id of the speech model, see [Model]. */
    var model: String
        get() = prefs.getString("model", Model.BASE.id).orEmpty()
        set(value) = prefs.edit().putString("model", value).apply()

    var lang: String
        get() = prefs.getString("lang", "ja").orEmpty()
        set(value) = prefs.edit().putString("lang", value.trim().lowercase().ifEmpty { "auto" }).apply()

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

    /** True: the cloud makes the captions, with hours from subread.space. False: the phone. */
    var cloud: Boolean
        get() = prefs.getBoolean("cloud", false)
        set(value) = prefs.edit().putBoolean("cloud", value).apply()

    /** The device cookie of subread.space: the account that holds the cloud hours. */
    var device: String
        get() = prefs.getString("device", "").orEmpty()
        set(value) = prefs.edit().putString("device", value).apply()

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
