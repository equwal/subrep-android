package com.honjimaku.subrep

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * The one screen of the app: the engine, the language, the sound, the share link, the overlay,
 * and the button. The state of the service and the last captions are under the button.
 */
class MainActivity : Activity(), Feed.Listener {

    private lateinit var store: Store
    private lateinit var content: LinearLayout
    private lateinit var engineField: EditText
    private lateinit var langField: EditText
    private lateinit var titleField: EditText
    private lateinit var sourceGroup: RadioGroup
    private lateinit var screenRadio: RadioButton
    private lateinit var micRadio: RadioButton
    private lateinit var shareBox: CheckBox
    private lateinit var overlayBox: CheckBox
    private lateinit var linkView: TextView
    private lateinit var mainButton: Button
    private lateinit var statusView: TextView
    private lateinit var linesView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setBackgroundColor(Color.WHITE)
        }
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            setBackgroundColor(Color.WHITE)
            addView(content)
        })
        draw()
    }

    override fun onResume() {
        super.onResume()
        Feed.add(this)
        onChange()
    }

    override fun onPause() {
        Feed.remove(this)
        save()
        super.onPause()
    }

    private fun draw() {
        title(getString(R.string.app_name))
        note(getString(R.string.about))

        step(R.string.step_engine, getString(R.string.step_engine_why))
        engineField = field(store.engine, "192.168.0.9:8794", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)

        step(R.string.step_lang, getString(R.string.step_lang_why))
        langField = field(store.lang, "ja", InputType.TYPE_CLASS_TEXT)

        step(R.string.step_source, "")
        // The group keeps one button checked only when each button has an id before it joins.
        screenRadio = RadioButton(this).apply {
            id = SCREEN_ID
            text = getString(R.string.source_screen)
            setTextColor(Color.BLACK)
        }
        micRadio = RadioButton(this).apply {
            id = MIC_ID
            text = getString(R.string.source_mic)
            setTextColor(Color.BLACK)
        }
        sourceGroup = RadioGroup(this).apply {
            addView(screenRadio)
            addView(micRadio)
            check(if (store.screen) SCREEN_ID else MIC_ID)
        }
        content.addView(sourceGroup, wide())

        step(R.string.step_share, "")
        shareBox = CheckBox(this).apply {
            text = getString(R.string.share_on)
            setTextColor(Color.BLACK)
            isChecked = store.share
        }
        content.addView(shareBox, wide())
        titleField = field(store.title, getString(R.string.share_title_hint), InputType.TYPE_CLASS_TEXT)
        linkView = TextView(this).apply {
            setTextColor(Color.BLACK)
            textSize = 15f
            setTextIsSelectable(true)
        }
        content.addView(linkView, wide())
        row(
            button(getString(R.string.copy)) { copyLink() },
            button(getString(R.string.share)) { shareLink() },
            button(getString(R.string.new_link)) {
                store.newRoom()
                showLink()
            },
        )
        showLink()

        step(R.string.step_overlay, "")
        overlayBox = CheckBox(this).apply {
            text = getString(R.string.overlay_on)
            setTextColor(Color.BLACK)
            isChecked = store.overlay
        }
        if (Overlay.installed(this)) {
            content.addView(overlayBox, wide())
            content.addView(button(getString(R.string.open_overlay)) {
                Overlay.launch(this)?.let { runCatching { startActivity(it) } }
            }, LinearLayout.LayoutParams(-2, -2))
        } else {
            note(getString(R.string.overlay_missing))
            content.addView(button(getString(R.string.install_overlay)) { open(Overlay.INSTALL) }, LinearLayout.LayoutParams(-2, -2))
        }

        mainButton = button(getString(R.string.start)) { toggle() }.apply { setTypeface(typeface, Typeface.BOLD) }
        content.addView(mainButton, wide(top = 24))
        statusView = TextView(this).apply {
            setTextColor(Color.BLACK)
            textSize = 15f
        }
        content.addView(statusView, wide())
        linesView = TextView(this).apply {
            setTextColor(Color.BLACK)
            textSize = 18f
            setTextIsSelectable(true)
        }
        content.addView(linesView, wide())
    }

    private fun save() {
        store.engine = engineField.text.toString()
        store.lang = langField.text.toString()
        store.title = titleField.text.toString()
        store.screen = sourceGroup.checkedRadioButtonId != MIC_ID
        store.share = shareBox.isChecked
        store.overlay = overlayBox.isChecked
    }

    private fun toggle() {
        if (Feed.running) CaptureService.stop(this) else startCapture()
    }

    private fun startCapture() {
        save()
        if (store.engine.isBlank()) return toast(R.string.need_engine)
        val missing = buildList {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (missing.isNotEmpty()) return requestPermissions(missing.toTypedArray(), REQUEST_PERMISSIONS)
        if (!store.screen) return CaptureService.start(this, screen = false)
        if (Build.VERSION.SDK_INT < 29) return toast(R.string.need_android_10)
        // Android asks the user for the consent. The answer comes to onActivityResult.
        val manager = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_PROJECTION)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode != REQUEST_PERMISSIONS) return
        val audio = permissions.indexOf(Manifest.permission.RECORD_AUDIO)
        if (audio >= 0 && grantResults[audio] != PackageManager.PERMISSION_GRANTED) return toast(R.string.need_audio_permission)
        startCapture()
    }

    @Deprecated("The platform Activity has no other result API, and this app has no AndroidX activity.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_PROJECTION) return
        if (resultCode != RESULT_OK || data == null) return toast(R.string.capture_refused)
        CaptureService.start(this, screen = true, resultCode = resultCode, resultData = data)
    }

    override fun onChange() {
        mainButton.text = getString(if (Feed.running) R.string.stop else R.string.start)
        statusView.text = status()
        linesView.text = Feed.text()
    }

    private fun status(): String {
        if (!Feed.running) return Feed.error
        val engine = if (Feed.engineUp) "connected" else "connecting…"
        val relay = when {
            !Feed.relayOn -> "off"
            Feed.relayUp -> "connected"
            else -> "connecting…"
        }
        val overlay = when (Feed.overlayAnswer) {
            "" -> if (store.overlay && Overlay.installed(this)) "waits for a line" else "off"
            Overlay.OK -> "shows the line"
            "panel_hidden" -> "press \"Show the subtitles\" in SubRead Overlay"
            "no_overlay_permission", "no_notification_access" -> "allow steps 1 and 2 in SubRead Overlay"
            Overlay.TOO_OLD -> "update SubRead Overlay: this version takes no lines"
            else -> Feed.overlayAnswer
        }
        val bars = Feed.bars()
        val level = "▮".repeat(bars) + "▯".repeat(10 - bars)
        val error = if (Feed.error.isEmpty()) "" else "\n${Feed.error}"
        return "Engine ${store.engine}: $engine\nShare link: $relay\nSubRead Overlay: $overlay\nSound: $level$error"
    }

    private fun showLink() {
        linkView.text = Room.watchUrl(Room.RELAY, store.room)
    }

    private fun copyLink() {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("link", linkView.text))
        toast(R.string.copied)
    }

    private fun shareLink() {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, linkView.text.toString())
        runCatching { startActivity(Intent.createChooser(send, null)) }
    }

    private fun open(link: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
    }

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    // The screen is built in code: a few rows do not need a layout file each.

    private fun title(value: String) = content.addView(TextView(this).apply {
        text = value
        textSize = 26f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.BLACK)
    })

    private fun note(value: String, top: Int = 8) = content.addView(TextView(this).apply {
        text = value
        textSize = 15f
        setTextColor(Color.BLACK)
    }, wide(top))

    @SuppressLint("SetTextI18n")
    private fun step(name: Int, detail: String) {
        content.addView(View(this).apply { setBackgroundColor(Color.BLACK) }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(16) })
        content.addView(TextView(this).apply {
            text = getString(name)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.BLACK)
        }, wide(top = 12))
        if (detail.isNotEmpty()) note(detail, top = 4)
    }

    private fun field(value: String, hint: String, type: Int) = EditText(this).apply {
        setText(value)
        this.hint = hint
        inputType = type
        setTextColor(Color.BLACK)
        setHintTextColor(Color.GRAY)
        isSingleLine = true
    }.also { content.addView(it, wide()) }

    private fun row(vararg views: View) = content.addView(LinearLayout(this).apply { views.forEach { addView(it) } })

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun wide(top: Int = 8) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val REQUEST_PERMISSIONS = 1
        const val REQUEST_PROJECTION = 2
        const val SCREEN_ID = 1
        const val MIC_ID = 2
    }
}
