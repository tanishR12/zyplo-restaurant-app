package com.zyplo.restaurant.alerts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class SirenPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    fun start() {
        stop()
        raiseAlarmVolume()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
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
            prepare()
            start()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 600, 250, 600), 0)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 600, 250, 600), 0)
        }
    }

    fun stop() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        vibrator.cancel()
    }

    private fun raiseAlarmVolume() {
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        if (max > 0) {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
        }
    }
}
