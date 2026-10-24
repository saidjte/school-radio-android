package com.saidjte.schoolradio.core.util

import java.util.Locale

/**
 * v0.14.0 — منطق تصدير/مشاركة التسجيلات (صافي، بلا سياق أندرويد — قابل للاختبار).
 *
 * يفصل قواعد تسمية الملف المصدر عن كود الإدخال/الإخراج الفعلي في
 * [com.saidjte.schoolradio.feature.recordings.RecordingShareHelper].
 */
object RecordingExportLogic {

    /** الامتداد المناسب لنوع MIME للتسجيل (audio/mp4 = حاوية m4a شائعة في MediaRecorder) */
    fun extensionOf(mimeType: String): String = when (mimeType.lowercase(Locale.US)) {
        "audio/mp4", "audio/m4a", "audio/mp4a-latm" -> "m4a"
        "audio/aac" -> "aac"
        "audio/3gpp" -> "3gp"
        "audio/ogg" -> "ogg"
        "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
        "audio/mpeg", "audio/mp3" -> "mp3"
        else -> "m4a"
    }

    /**
     * اسم ملف آمن للتصدير من عنوان تسجيل: يحذف المحارف الممنوعة في أسماء
     * الملفات (و\/:*?"<>| والمحارف الضابطة)، يحدّ الطول، ويضيف الامتداد.
     * فارغ تماماً؟ يعود للاسم الافتراضي.
     */
    fun safeFileName(title: String, createdAtMillis: Long, mimeType: String): String {
        val ext = extensionOf(mimeType)
        val stamp = AppFormat.westernDigits(
            java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date(createdAtMillis))
        )
        val cleaned = title
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(60)
            .trimEnd(' ', '.')
        val base = cleaned.ifEmpty { "تسجيل" }
        return AppFormat.westernDigits("$base - $stamp.$ext")
    }

    /** مجلد التصدير داخل Downloads (ثابت ليتعرف عليه المستخدم دائماً) */
    const val EXPORT_SUBDIR = "الإذاعة المدرسية"

    /** رسالة نجاح التصدير بمسار مقروء */
    fun successMessage(fileName: String): String =
        "صُدِّر التسجيل إلى Downloads/$EXPORT_SUBDIR/$fileName"
}
