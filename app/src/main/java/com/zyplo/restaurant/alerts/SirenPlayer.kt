package com.zyplo.restaurant.alerts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class SirenPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private val audio = context.getSystemService(AudioManager::class.java)
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    fun start() {
        stop()
        raiseAlarmVolume()
        requestFocus()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        player = MediaPlayer().apply {
            setDataSource(context, uri)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            isLooping = true
            setVolume(1f, 1f)
            setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            prepare()
            start()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 220, 700), 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 700, 220, 700), 0)
        }
    }

    fun stop() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        vibrator.cancel()
        abandonFocus()
    }

    private fun raiseAlarmVolume() {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        if (max > 0) audio.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
    }

    private fun requestFocus() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener { }
                .build()
            audio.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audio.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audio.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audio.abandonAudioFocus(null)
        }
    }
}
