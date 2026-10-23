package com.saidjte.schoolradio

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * مستودع الكتالوج:
 * 1) يحاول جلب أحدث كتالوج من المستودع (content/catalog.json على فرع main)
 * 2) عند الفشل يستعمل آخر نسخة مخبأة محلياً
 * 3) وإلا يعود إلى النسخة المدمجة داخل التطبيق (assets/catalog.json)
 *
 * بهذه الطريقة تُحدَّث الفقرات والجدول من طرف إدارة الإذاعة دون إصدار APK جديد.
 */
object CatalogRepository {

    /** رابط الكتالوج الحيّ على GitHub (يُحدَّث عند كل تعديل للمحتوى) */
    private const val REMOTE_URL =
        "https://raw.githubusercontent.com/saidjte/school-radio-android/main/content/catalog.json"

    private val executor = Executors.newSingleThreadExecutor()

    fun loadAsync(context: Context, callback: (Catalog, String) -> Unit) {
        val appContext = context.applicationContext
        executor.execute {
            val (catalog, source) = load(appContext)
            android.os.Handler(appContext.mainLooper).post { callback(catalog, source) }
        }
    }

    private fun load(context: Context): Pair<Catalog, String> {
        // 1) النسخة الحيّة من GitHub
        fetchRemote()?.let { json ->
            try {
                val catalog = Catalog.fromJson(JSONObject(json))
                // أخبئ النسخة الصالحة للاستعمال عند انقطاع الاتصال
                runCatching {
                    File(context.filesDir, "catalog_cache.json").writeText(json)
                }
                return catalog to "remote"
            } catch (_: Exception) {
                // JSON غير صالح — نتابع إلى المخبأ
            }
        }

        // 2) آخر نسخة صالحة مخبأة محلياً
        val cache = File(context.filesDir, "catalog_cache.json")
        if (cache.exists()) {
            runCatching {
                return Catalog.fromJson(JSONObject(cache.readText())) to "cache"
            }
        }

        // 3) النسخة المدمجة مع التطبيق
        val bundled = context.assets.open("catalog.json").bufferedReader().use { it.readText() }
        return Catalog.fromJson(JSONObject(bundled)) to "bundled"
    }

    private fun fetchRemote(): String? {
        return try {
            val conn = URL(REMOTE_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.setRequestProperty("User-Agent", "SchoolRadio-Android/${BuildConfig.VERSION_NAME}")
            try {
                if (conn.responseCode == 200) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else null
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}
