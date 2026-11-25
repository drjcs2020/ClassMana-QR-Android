package br.com.classmana.qr

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Efeito original de entrada: tres notas suaves e ascendentes.
 * Gerado no dispositivo, sem arquivos externos nem acesso a internet.
 */
object EntrySound {
    fun play() {
        thread(name = "ClassMana-EntrySound", isDaemon = true) {
            var track: AudioTrack? = null
            try {
                val rate = 22050
                val duration = 0.62
                val count = (rate * duration).toInt()
                val pcm = ByteArray(count * 2)
                val notes = arrayOf(
                    Triple(0.00, 523.25, 0.27),
                    Triple(0.14, 659.25, 0.30),
                    Triple(0.30, 783.99, 0.30)
                )
                for (i in 0 until count) {
                    val t = i.toDouble() / rate
                    var sample = 0.0
                    for ((start, pitch, length) in notes) {
                        val elapsed = t - start
                        if (elapsed >= 0.0 && elapsed < length) {
                            val attack = (elapsed / 0.012).coerceIn(0.0, 1.0)
                            val decay = exp(-elapsed * 8.5)
                            val chime = sin(2.0 * PI * pitch * elapsed) +
                                0.16 * sin(4.0 * PI * pitch * elapsed)
                            sample += 0.18 * attack * decay * chime
                        }
                    }
                    val value = (sample.coerceIn(-0.7, 0.7) * 32767.0).toInt()
                    pcm[i * 2] = (value and 0xff).toByte()
                    pcm[i * 2 + 1] = ((value shr 8) and 0xff).toByte()
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
                val audio = track ?: return@thread
                if (audio.state == AudioTrack.STATE_INITIALIZED) {
                    audio.write(pcm, 0, pcm.size)
                    audio.play()
                    Thread.sleep(720)
                    audio.stop()
                }
            } catch (_: Exception) {
                // O audio e opcional: nunca bloquear o acesso ao portal.
            } finally {
                runCatching { track?.release() }
            }
        }
    }
}
