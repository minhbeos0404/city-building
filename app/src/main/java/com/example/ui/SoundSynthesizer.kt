package com.example.ui

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

object SoundSynthesizer {
    private val scope = CoroutineScope(Dispatchers.Default)

    fun playBuildSound() {
        playTone(frequency1 = 440.0, frequency2 = 880.0, durationMs = 150)
    }

    fun playDemolishSound() {
        playTone(frequency1 = 300.0, frequency2 = 150.0, durationMs = 200)
    }

    fun playErrorSound() {
        playTone(frequency1 = 180.0, frequency2 = 180.0, durationMs = 300)
    }

    fun playLevelUpSound() {
        scope.launch {
            playTone(frequency1 = 523.25, frequency2 = 659.25, durationMs = 100)
            kotlinx.coroutines.delay(100)
            playTone(frequency1 = 659.25, frequency2 = 783.99, durationMs = 100)
            kotlinx.coroutines.delay(100)
            playTone(frequency1 = 783.99, frequency2 = 1046.50, durationMs = 200)
        }
    }

    private fun playTone(frequency1: Double, frequency2: Double, durationMs: Int) {
        scope.launch {
            try {
                val sampleRate = 8000
                val numSamples = durationMs * sampleRate / 1000
                val sample = DoubleArray(numSamples)
                val generatedSnd = ByteArray(2 * numSamples)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    // Linear interpolation between frequency1 and frequency2 for a slide effect
                    val currentFreq = frequency1 + (frequency2 - frequency1) * (i.toDouble() / numSamples)
                    sample[i] = sin(2.0 * Math.PI * currentFreq * t)
                }

                var idx = 0
                for (dVal in sample) {
                    val valShort = (dVal * 32767).toInt().toShort()
                    generatedSnd[idx++] = (valShort.toInt() and 0x00ff).toByte()
                    generatedSnd[idx++] = ((valShort.toInt() and 0xff00) ushr 8).toByte()
                }

                val audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    generatedSnd.size,
                    AudioTrack.MODE_STATIC
                )
                audioTrack.write(generatedSnd, 0, generatedSnd.size)
                audioTrack.play()
                // Let it play, then release
                scope.launch {
                    kotlinx.coroutines.delay(durationMs + 50L)
                    try {
                        audioTrack.stop()
                        audioTrack.release()
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            } catch (e: Exception) {
                // Audio failure shouldn't crash the app
            }
        }
    }
}
