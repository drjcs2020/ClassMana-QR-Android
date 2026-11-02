package br.com.classmana.qr

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object ClassManaUpdateChecker {
    private const val endpoint = "https://api.github.com/repos/drjcs2020/ClassMana-QR-Android/releases/latest"

    fun check(activity: Activity) {
        thread {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("User-Agent", "ClassMana-Android")
                    setRequestProperty("Accept", "application/vnd.github+json")
                }
                if (conn.responseCode != 200) return@thread
                val release = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@thread
                val availableName = release.optString("tag_name").removePrefix("v")
                val available = availableName.split(".").mapNotNull { it.toIntOrNull() }
                if (available.size != 3) return@thread
                val currentName = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: return@thread
                val current = currentName.split(".").mapNotNull { it.toIntOrNull() }
                if (current.size != 3) return@thread
                if (available[0] < current[0] || (available[0] == current[0] && available[1] < current[1])) return@thread
                if (available[0] == current[0] && available[1] == current[1] && available[2] <= current[2]) return@thread
                val assets = release.optJSONArray("assets")
                var link = release.optString("html_url")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        if (asset.optString("name").endsWith(".apk", true)) {
                            link = asset.optString("browser_download_url")
                            break
                        }
                    }
                }
                if (!link.startsWith("https://github.com/")) return@thread
                val updateUrl = link
                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    AlertDialog.Builder(activity)
                        .setTitle("Atualização do ClassMana")
                        .setMessage("Nova versão disponível: " + availableName + "\nInstalada: " + currentName + "\nDeseja baixar?")
                        .setPositiveButton("Atualizar agora") { _, _ ->
                            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl)))
                        }
                        .setNegativeButton("Depois", null)
                        .show()
                }
            } catch (_: Exception) {
                // Em caso de falha de rede, continuar sem interromper o aplicativo.
            } finally {
                conn?.disconnect()
            }
        }
    }
}
