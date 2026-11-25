package br.com.classmana.qr

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Efeito original de entrada: quatro notas audiveis, ascendentes, em cerca de 1 segundo.
 * Gerado no dispositivo, sem arquivos externos nem acesso a internet.
 */
object EntrySound {
    fun play() {
        thread(name = "ClassMana-EntrySound", isDaemon = true) {
            var track: AudioTrack? = null
            try {
                val rate = 44100
                val duration = 1.20
                val count = (rate * duration).toInt()
                val pcm = ByteArray(count * 2)
                val notes = arrayOf(
                    Triple(0.00, 523.25, 0.45),
                    Triple(0.19, 659.25, 0.49),
                    Triple(0.40, 783.99, 0.52),
                    Triple(0.64, 1046.50, 0.51)
                )
                for (i in 0 until count) {
                    val t = i.toDouble() / rate
                    var sample = 0.0
                    for ((start, pitch, length) in notes) {
                        val elapsed = t - start
                        if (elapsed >= 0.0 && elapsed < length) {
                            val attack = (elapsed / 0.018).coerceIn(0.0, 1.0)
                            val decay = exp(-elapsed * 4.7)
                            val chime = sin(2.0 * PI * pitch * elapsed) +
                                0.22 * sin(4.0 * PI * pitch * elapsed)
                            sample += 0.34 * attack * decay * chime
                        }
                    }
                    val value = (sample.coerceIn(-0.93, 0.93) * 32767.0).toInt()
                    pcm[i * 2] = (value and 0xff).toByte()
                    pcm[i * 2 + 1] = ((value shr 8) and 0xff).toByte()
                }
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
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
                    audio.setVolume(0.98f)
                    audio.write(pcm, 0, pcm.size)
                    audio.play()
                    Thread.sleep(1350)
                    audio.stop()
                }
            } catch (e: Exception) {
                android.util.Log.w("ClassManaEntrySound", "Falha ao reproduzir som de entrada", e)
                // O audio e opcional: nunca bloquear o acesso ao portal.
            } finally {
                runCatching { track?.release() }
            }
        }
    }
}
