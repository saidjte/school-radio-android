package com.saidjte.schoolradio.core.util

/**
 * v0.29.0 — منطق ترميز تسجيلات الميكروفون (صافٍ بلا تبعيات أندرويد — قابل للاختبار وحدوياً).
 *
 * التسجيل عالي الدقة Opus HD:
 * - Opus هو أعلى ترميزات الصوت كفاءةً في عصرنا (مشفر WebRTC/Mastodon/يوتوب الصوتي):
 *   جودة الكلام شفافة تماماً عند 128kbps مونو حيث تحتاج AAC مضاعف المعدل لتقاربها،
 *   ويتعامل مع موسيقى الجينلات المسجلة بلا «صفير» AAC عالي الترددات.
 * - خط أنابيب الميكروفون يعمل أصلاً عند 48kHz (المعدل الأصلي الوحيد لـOpus) — لا إعادة
 *   أخذ عينات ولا فقد، فالمدخلات تُرمَّز كما تُلتقط.
 * - الحاوية OGG ‎.ogg عبر MediaMuxer — مدعومة كإخراج من Android 10 (API 29) فقط،
 *   لذلك الأجهزة الأقدم (minSdk 24) تعود تلقائياً إلى AAC-LC في ‎.m4a كما سابقاً.
 * - التشغيل آمن على كل الأجهزة: فك ترميز Opus مدعوم في أندرويد منذ 5.0 (API 21) —
 *   أرشيف مختلط (قديم ‎.m4a + جديد ‎.ogg) يعمل في المشغل دون أي شرط.
 */
object RecordingCodecLogic {

    /** أول واجهة أندرويد تتيح مشفر Opus + حاوية OGG في MediaMuxer */
    const val OPUS_MIN_API = 29

    /** 128kbps مونو: شفافية كلام كاملة مع Opus (المقابل السابق AAC 128kbps قريب غير شفاف) */
    const val OPUS_BIT_RATE = 128_000

    /**
     * قرار الترميز لتسجيل قادم:
     * @param apiLevel مستوى API للجهاز (Build.VERSION.SDK_INT)
     * @param opusEncoderAvailable هل يوجد مشفر Opus عتادي/برمجي على الجهاز؟
     * @param hdPreferred تفضيل المستخدم من الإعدادات (مفتاح «عالي الدقة Opus HD»)
     */
    fun preferOpus(apiLevel: Int, opusEncoderAvailable: Boolean, hdPreferred: Boolean): Boolean =
        hdPreferred && apiLevel >= OPUS_MIN_API && opusEncoderAvailable

    /** امتداد الملف المناسب للترميز المختار */
    fun extension(usesOpus: Boolean): String = if (usesOpus) "ogg" else "m4a"

    /** نوع MIME المناسب للتسجيل (يُخزَّن مع سجل الأرشيف ويقود المشاركة/التصدير) */
    fun mimeType(usesOpus: Boolean): String = if (usesOpus) "audio/ogg" else "audio/mp4"

    /** تسمية عربية قصيرة تظهر كشارة في الأرشيف */
    fun codecLabel(usesOpus: Boolean): String = if (usesOpus) "Opus HD" else "AAC"

    /** هل هذا السجل (بـmimeType المخزن) تسجيل Opus HD؟ — للشارات في الواجهة */
    fun isOpusRecord(mimeType: String): Boolean = mimeType.equals("audio/ogg", ignoreCase = true)
}
