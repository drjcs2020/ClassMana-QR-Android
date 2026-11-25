package br.com.classmana.qr

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import java.io.File
import kotlin.concurrent.thread

/**
 * Som ORIGINAL de login enviado pelo professor.
 * Os bytes do MP3 sao recuperados do arquivo de assets; nenhum tom,
 * velocidade, timbre ou trecho do audio e modificado.
 */
object EntrySound {
    fun play(context: Context) {
        val appContext = context.applicationContext
        thread(name = "ClassMana-LoginOriginal", isDaemon = true) {
            var player: MediaPlayer? = null
            try {
                val audioFile = File(appContext.cacheDir, "classmana_login_original.mp3")
                if (!audioFile.isFile || audioFile.length() != 56425L) {
                    val encoded = appContext.assets.open("classmana_login_original.b64")
                        .bufferedReader(Charsets.US_ASCII).use { it.readText() }
                    audioFile.writeBytes(Base64.decode(encoded.trim(), Base64.DEFAULT))
                }
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(audioFile.absolutePath)
                    setOnCompletionListener { finished -> finished.release() }
                    setOnErrorListener { failed, _, _ ->
                        failed.release()
                        true
                    }
                    prepare()
                    start()
                }
            } catch (error: Exception) {
                Log.w("ClassManaLoginAudio", "Falha ao tocar MP3 original de login", error)
                runCatching { player?.release() }
            }
        }
    }
}
