package br.com.classmana.qr

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Efeito curto e original de moedas caindo, gerado localmente.
 * Nao depende de internet nem de arquivo de audio externo.
 */
object CoinSound {
    fun play() {
        thread(name = "ClassMana-CoinSound", isDaemon = true) {
            var track: AudioTrack? = null
            try {
                val rate = 22050
                val duration = 0.72
                val count = (rate * duration).toInt()
                val pcm = ByteArray(count * 2)
                val impacts = arrayOf(
                    Pair(0.00, 1550.0),
                    Pair(0.13, 1190.0),
                    Pair(0.27, 1720.0),
                    Pair(0.41, 1320.0)
                )
                for (i in 0 until count) {
                    val t = i.toDouble() / rate
                    var sample = 0.0
                    for ((start, pitch) in impacts) {
                        val elapsed = t - start
                        if (elapsed >= 0.0 && elapsed < 0.24) {
                            val decay = exp(-elapsed * 23.0)
                            val shimmer = sin(2.0 * PI * pitch * elapsed) +
                                0.34 * sin(2.0 * PI * pitch * 2.38 * elapsed)
                            sample += 0.22 * decay * shimmer
                        }
                    }
                    val s = (sample.coerceIn(-0.95, 0.95) * 32767.0).toInt()
                    pcm[i * 2] = (s and 0xff).toByte()
                    pcm[i * 2 + 1] = ((s shr 8) and 0xff).toByte()
                }
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size)
                    .build()
                val audio = track
                if (audio.state == AudioTrack.STATE_INITIALIZED) {
                    audio.write(pcm, 0, pcm.size)
                    audio.play()
                    Thread.sleep(800)
                    audio.stop()
                }
            } catch (_: Exception) {
                // Falhas de audio nunca impedem o login ou o registro de presenca.
            } finally {
                runCatching { track?.release() }
            }
        }
    }
}
