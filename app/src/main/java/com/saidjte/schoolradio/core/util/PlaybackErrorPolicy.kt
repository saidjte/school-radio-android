package com.saidjte.schoolradio.core.util

import androidx.media3.common.PlaybackException

/**
 * قرار أخطاء المشغل (v0.53.0) — منطق صافٍ بلا أي تبعية أندرويد.
 *
 * بلاغ «مشكل في المشغل — ERROR_CODE_FAILED_RUNTIME_CHECK» (لقطات 19:32–19:33)
 * كشف فجوتين في مسار onPlayerError — درس v0.15.0 لم يُطبق على آخر بقعة عمياء:
 *
 *  1. **الخطأ يُعرض دون أن يُكتب جذره**: الحوار يري الأستاذ اسم الرمز ولا يسجل
 *     مكدس السبب في crash_log.txt — فكل تشخيص لاحق بقي تخميناً.
 *  2. **القفز التلقائي يعامل أخطاء البيئة كأخطاء محتوى**: لقطة المكتبة (7/7)
 *     تثبت أن عطلاً واحداً على مستوى محرك الصوت أسقط القائمة كاملة عنصراً
 *     عنصراً (القفز لكل عنصر)، وكل playPause يعيد تحضير العنصر نفسه فيولد
 *     الحوار من جديد — فيبدو «المشغل لا يعمل» كلياً.
 *
 * **دلالة القرار الحقيقية**:
 *  - **خطأ محتوى**: الملف/الرابط نفسه هو المعطوب — العنصر التالي مستقل عنه
 *    حرفياً (ملف آخر) فالقفز شفاء حقيقي يكمل الباقي كما كان منذ v0.9.
 *  - **خطأ بيئة**: عطل في محرك الصوت/الجهاز لا علاقة له بالعنصر — القفز لا
 *    يشفيه بل يضاعف الحوار، والصدق هو التوقف برسالة توجّه الأستاذ إلى تقرير
 *    العطل في الإعدادات حيث يُكتب السبب الكامل الآن (ورد cause الكامل).
 */
object PlaybackErrorPolicy {

    /**
     * هل الخطأ خاص بالمحتوى (ملف/رابط) بحيث يشفى القفز إلى العنصر التالي؟
     * عائلتا IO وPARSING حصراً: كل الخطأ عندها هو هذا الملف — أما أخطاء
     * محرك الصوت والجهاز (RUNTIME_CHECK/AUDIO_TRACK/DECODER/غير المتوقع)
     * فستتكرر مع أي عنصر يليه فلا معنى لسفك القائمة عليها.
     */
    fun skipToNext(errorCode: Int): Boolean = when (errorCode) {
        // عائلة IO: الملف غير متاح/لا صلاحية/تعذر القراءة — عناصر القائمة ملفات مستقلة
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        // عائلة PARSING: هذا الملف تالف/صيغته غير مدعومة — عطل هذا الملف وحده
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> true

        else -> false
    }

    /**
     * رسالة خطأ بيئة توجّه الأستاذ إلى العطل المكتوب — الفرق عن رسائل المحتوى
     * أنها لا تتهم الملف: الجذر في محرك الصوت/الجهاز وسبب الكامل موجود في
     * تقرير العطل (الإعدادات ← مشاركة التقرير).
     */
    fun environmentMessage(errorCodeName: String): String =
        "عطل في محرك الصوت على هذا الجهاز ($errorCodeName) — سبب الكامل مكتوب في تقرير العطل، شاركه من الإعدادات ليُحدد الجذر بدقة"
}
