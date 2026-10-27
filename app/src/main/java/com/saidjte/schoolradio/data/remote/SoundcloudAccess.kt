package com.saidjte.schoolradio.data.remote

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.soundcloud.SoundcloudParsingHelper
import java.net.HttpURLConnection
import java.net.URL

/**
 * v0.44.0 — ضمان مفتاح SoundCloud (client_id) قبل أي عملية ساوندكلاود.
 *
 * الجذر المؤكد تجريبياً (دورة v0.44.0): آلية الاكتشاف في NewPipeExtractor
 * (v0.26.5 وحتى master الأحدث) ميتة أمام موقع SoundCloud الجديد —
 * صفحة soundcloud.com تُحوَّل 307 إلى m.soundcloud.com (تطبيق Next.js)،
 * وأصول الـJS لم تعد تحت مسار «assets/» على m.sndcdn.com بل _next/static/chunks،
 * والنمط `,client_id:"…"` لم يعد موجوداً في أي chunk — المفتاح الآن يعيش
 * داخل JSON «__NEXT_DATA__» في HTML الصفحة نفسها.
 * فالاكتشاف ينتهي دائماً بـ«Couldn't extract client id» وكل نداءات api-v2
 * بلا مفتاح تُرفض — فيفشل البحث في ساوندكلاود وفشل حلّ أي رابط منها
 * (البلاغ: «البحث يفشل في SoundCloud و التشغيل عبر الرابط يفشل أيضا»).
 *
 * الآلية هنا — نقطة واحدة: قراءة المفتاح من __NEXT_DATA__ ثم إثبات صحته
 * بنداء resolve حقيقي لمقطع عام ثابت (فلسفة الاستطلاع بإثبات الطلب نفسها
 * كما في probeRange: دليل عملي لا تخمين) ثم حقنه في حقل المستخرج المخبأ،
 * فتصير كل مسارات المستخرج (بحث/تدفق/حلّ روابط) تعمل بالمفتاح نفسه.
 * كتمام الصدق: إن كان الحقل معبّأة مسبقاً (نجاح اكتشاف المستخرج يوماً ما
 * أو حقنٌ سابق) لا تُلمس ولا شبكة زائدة — فحص حقل محلي خالص.
 */
internal object SoundcloudAccess {

    /** نمط المفتاح داخل __NEXT_DATA__ — معرفات SoundCloud 32 محرفاً من [A-Za-z0-9] */
    internal val NEXT_DATA_CLIENT_ID = Regex("\"clientId\":\"([A-Za-z0-9]{32})\"")

    /** مقطع عام ثابت (2007) لإثبات صلاحية المفتاح بطلب فعلي لا تخمين */
    private const val PROBE_URL = "https://api-v2.soundcloud.com/resolve" +
        "?url=https%3A%2F%2Fsoundcloud.com%2Fforss%2Fflickermood&client_id="

    private val clientIdField: java.lang.reflect.Field by lazy {
        SoundcloudParsingHelper::class.java
            .getDeclaredField("clientId")
            .apply { isAccessible = true }
    }

    /** منطق صافٍ: استخراج المفتاح من جسم صفحة SoundCloud — يُختبر بعينات HTML حقيقية الشكل */
    internal fun extractClientId(html: String): String? =
        NEXT_DATA_CLIENT_ID.find(html)?.groupValues?.get(1)

    /** القيمة المخبأة داخل المستخرج (null إن لم يكتشفها أحد بعد) */
    internal fun cachedClientId(): String? = try {
        clientIdField.get(null) as String?
    } catch (_: Exception) {
        null
    }

    /**
     * يضمن مفتاحاً صالحاً قبل أي عملية ساوندكلاود — يُستدعى من مدخلَي
     * ساوندكلاود فقط (البحث في [RemoteSearch] وحلّ الروابط في [StreamResolver]).
     * @throws RemoteImportException برسالة صادقة إن تعذر تثبيت مفتاح صالح
     */
    fun ensureValidClientId() {
        if (!cachedClientId().isNullOrEmpty()) return
        val key = discoverAndVerify() ?: throw RemoteImportException(
            "تعذر الوصول إلى ساوندكلاود حالياً — تحقق من الاتصال وأعد المحاولة"
        )
        clientIdField.set(null, key)
    }

    /** اكتشاف المفتاح من صفحة الموقع ثم إثباته بنداء resolve — null عند أي فشل صادق */
    internal fun discoverAndVerify(): String? = try {
        val html = NewPipe.getDownloader().get("https://soundcloud.com").responseBody()
        val key = extractClientId(html) ?: return null
        if (resolveProbeSucceeds(key)) key else null
    } catch (_: Exception) {
        null
    }

    /** الطلب الإثباتي: 200 وجسد JSON يبدأ بعنصر حقيقي = المفتاح صالح؛ غير ذلك مرفوض */
    internal fun resolveProbeSucceeds(key: String): Boolean = try {
        val conn = URL(PROBE_URL + key).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 12_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", BROWSER_UA)
            val code = conn.responseCode
            if (code !in 200..299) {
                false
            } else {
                conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    .trimStart().startsWith("{")
            }
        } finally {
            conn.disconnect()
        }
    } catch (_: Exception) {
        false
    }
}
