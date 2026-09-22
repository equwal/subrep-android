package com.honjimaku.subrep

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import okhttp3.OkHttpClient
import java.io.FileDescriptor
import java.io.PrintWriter
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Captures the sound, streams it to the engine, and gives the captions to the screen, to the
 * relay and to SubRead Overlay. A foreground service, so that it lives while the user is in
 * the player.
 */
class CaptureService : Service() {

    private lateinit var store: Store
    private val http = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
    private val main = Handler(Looper.getMainLooper())
    private val captions = HandlerThread("captions").apply { start() }
    private var engine: Socket? = null
    private var relay: Socket? = null
    private var overlay: OverlayFeed? = null
    private var record: AudioRecord? = null
    private var projection: MediaProjection? = null
    private var pump: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (!Feed.running) start(intent)
            ACTION_STOP -> stopAll()
        }
        return START_NOT_STICKY
    }

    private fun start(intent: Intent) {
        store = Store(this)
        val screen = intent.getBooleanExtra(EXTRA_SCREEN, false)
        // Android 14 and later accept the projection only from a foreground service of this type.
        foreground(screen)
        Feed.start(store.share)

        val record = try {
            if (screen) Capture.ofApps(projectionOf(intent)) else Capture.ofMicrophone()
        } catch (e: Exception) {
            Feed.fail("${e.javaClass.simpleName}: ${e.message}")
            stopAll()
            return
        }
        record.startRecording()
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            Feed.fail("The recorder did not start.")
            stopAll()
            return
        }
        this.record = record

        val lang = store.lang
        engine = Socket(http, "ws://${store.engine}", { Messages.engineHello(Capture.SAMPLE_RATE, lang, "android") }, ::onEngineText) {
            Feed.engine(it)
        }.also { it.start() }
        if (store.share) {
            val secret = store.secret
            val title = store.title
            relay = Socket(http, "${Room.RELAY}/pub/${store.room}", { Messages.relayHello(secret, lang, title) }, ::onRelayText) {
                Feed.relay(it)
            }.also { it.start() }
        }
        if (store.overlay && Overlay.installed(this)) overlay = OverlayFeed(this, Handler(captions.looper))
        pump = thread(name = "pcm") { pump(record) }
    }

    private fun projectionOf(intent: Intent): MediaProjection {
        val code = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        @Suppress("DEPRECATION")
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        } ?: throw IllegalStateException("no consent")
        val manager = getSystemService(MediaProjectionManager::class.java)
        val projection = manager.getMediaProjection(code, data) ?: throw IllegalStateException("no projection")
        // Android 14 and later need a callback before the capture starts. The user can stop the
        // capture from the status bar; then this service stops too.
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() = stopAll()
        }, main)
        this.projection = projection
        return projection
    }

    private fun pump(record: AudioRecord) {
        val frame = ByteArray(Capture.FRAME_BYTES)
        while (!Thread.currentThread().isInterrupted) {
            val count = record.read(frame, 0, frame.size, AudioRecord.READ_BLOCKING)
            if (count < 0) break
            if (count == 0) continue
            engine?.send(frame, count)
            Feed.level(Pcm.peak(frame, count))
        }
    }

    private fun onEngineText(text: String) {
        Messages.error(text)?.let { Feed.fail("engine: $it") }
        val caption = Messages.caption(text) ?: return
        Feed.caption(caption)
        relay?.send(text)
        overlay?.caption(caption)
    }

    private fun onRelayText(text: String) {
        Messages.error(text)?.let { Feed.fail("relay: $it") }
    }

    private fun stopAll() {
        pump?.interrupt()
        pump = null
        record?.let {
            runCatching { it.stop() }
            it.release()
        }
        record = null
        projection?.stop()
        projection = null
        engine?.stop()
        engine = null
        relay?.stop()
        relay = null
        overlay?.end()
        overlay = null
        Feed.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        captions.quitSafely()
        super.onDestroy()
    }

    /** For `adb shell dumpsys activity service com.honjimaku.subrep/.CaptureService`: the state, for a bug report. */
    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>?) {
        writer.println("running=${Feed.running} engine=${Feed.engineUp} relay=${Feed.relayOn}/${Feed.relayUp}")
        writer.println("overlay=${Feed.overlayAnswer} peak=${Feed.peak} error=${Feed.error}")
        writer.println(Feed.text())
    }

    private fun foreground(screen: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, CaptureService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = Icon.createWithResource(this, R.drawable.icon)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(icon)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(icon, getString(R.string.stop), stop).build())
            .build()
        when {
            Build.VERSION.SDK_INT >= 30 -> startForeground(
                NOTIFICATION_ID, notification,
                if (screen) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
            Build.VERSION.SDK_INT >= 29 -> startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST)
            else -> startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val ACTION_START = "com.honjimaku.subrep.START"
        const val ACTION_STOP = "com.honjimaku.subrep.STOP"
        const val EXTRA_SCREEN = "screen"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL = "capture"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context, screen: Boolean, resultCode: Int = 0, resultData: Intent? = null) {
            val intent = Intent(context, CaptureService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SCREEN, screen)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, CaptureService::class.java).setAction(ACTION_STOP))
        }
    }
}
