package com.saidjte.schoolradio

import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * مدقق التحديثات:
 * يسأل GitHub API عن أحدث إصدار (releases/latest) ويقارن رقمه بالإصدار المثبّت.
 * روابط APK تستعمل اسماً موحّداً: SchoolRadio_<tag>.apk
 * ونفس مفتاح التوقيع عبر الإصدارات يسمح بالتحديث فوق النسخة القديمة مباشرة.
 */
object UpdateChecker {

    const val REPO_URL = "https://github.com/saidjte/school-radio-android"
    private const val LATEST_API = "https://api.github.com/repos/saidjte/school-radio-android/releases/latest"

    data class UpdateInfo(val latestTag: String, val apkUrl: String, val notesUrl: String)

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun checkAsync(onResult: (UpdateInfo?) -> Unit) {
        executor.execute {
            val info = check()
            main.post { onResult(info) }
        }
    }

    private fun check(): UpdateInfo? {
        return try {
            val conn = URL(LATEST_API).openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            // GitHub API يشترط User-Agent
            conn.setRequestProperty("User-Agent", "SchoolRadio-Android/${BuildConfig.VERSION_NAME}")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                if (conn.responseCode != 200) return null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = org.json.JSONObject(body)
                val tag = obj.optString("tag_name", "")
                if (tag.isBlank() || !isNewer(tag, BuildConfig.VERSION_NAME)) return null
                UpdateInfo(
                    latestTag = tag,
                    apkUrl = "$REPO_URL/releases/download/$tag/SchoolRadio_$tag.apk",
                    notesUrl = "$REPO_URL/releases/tag/$tag"
                )
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** مقارنة إصدارات نمط X.Y.Z (v اختياري في البداية) */
    fun isNewer(remote: String, current: String): Boolean {
        val r = remote.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }
}
