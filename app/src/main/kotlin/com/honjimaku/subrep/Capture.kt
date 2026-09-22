package com.honjimaku.subrep

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build

/**
 * Where the sound comes from: the apps on this device, or the microphone.
 *
 * Both give 16 kHz mono 16-bit PCM, which is what speech recognition uses. The engine takes any
 * rate, but a lower rate is less to send.
 */
object Capture {

    const val SAMPLE_RATE = 16_000

    /** 100 ms of sound: one frame to the engine. */
    const val FRAME_BYTES = SAMPLE_RATE * 2 / 10

    /**
     * The sound of the apps on this device, with the consent of the user in [projection].
     * Android gives the media, game and unknown streams. An app can refuse the capture
     * (a video app with DRM, some music apps): then there is silence.
     */
    @TargetApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission") // The screen checks RECORD_AUDIO before the service starts.
    fun ofApps(projection: MediaProjection): AudioRecord {
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        return AudioRecord.Builder()
            .setAudioFormat(format())
            .setBufferSizeInBytes(bufferBytes())
            .setAudioPlaybackCaptureConfig(config)
            .build()
    }

    /** The microphone, tuned for speech: for a television or a talk in the room. */
    @SuppressLint("MissingPermission")
    fun ofMicrophone(): AudioRecord = AudioRecord.Builder()
        .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
        .setAudioFormat(format())
        .setBufferSizeInBytes(bufferBytes())
        .build()

    private fun format() = AudioFormat.Builder()
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setSampleRate(SAMPLE_RATE)
        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
        .build()

    /** One second of sound, or more when the device needs more. */
    private fun bufferBytes(): Int {
        val least = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        return maxOf(least, SAMPLE_RATE * 2)
    }
}
