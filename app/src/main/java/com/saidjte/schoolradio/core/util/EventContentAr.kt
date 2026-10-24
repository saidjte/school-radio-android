package com.saidjte.schoolradio.core.util

/**
 * تسمية عربية موحدة لمحتوى الفقرة المجدولة — منطق نقي قابل للاختبار (v0.5.0).
 *
 * مصادر المحتوى الثلاثة (بتنافر مقصود — محرر الفقرة يضمن عدم الجمع):
 *  1) قائمة تشغيل   (playlistId)
 *  2) مقطع من المكتبة (singleTrackId)
 *  3) تسجيل من الأرشيف (recordingId)
 */
object EventContentAr {

    /** سطر وصف المحتوى المعروض في بطاقات الفقرات (شاشة الجدولة والرئيسية) */
    fun label(playlistName: String?, trackTitle: String?, recordingTitle: String?): String = when {
        playlistName != null -> "قائمة تشغيل: $playlistName"
        trackTitle != null -> "مقطع: $trackTitle"
        recordingTitle != null -> "تسجيل: $recordingTitle"
        else -> "بلا محتوى — أرفق قائمة أو مقطعاً أو تسجيلاً"
    }

    /** هل للفقرة محتوى صوتي يمكن إطلاقه أصلاً؟ (تُستخدم قبل الإطلاق وفي التلميحات) */
    fun hasContent(playlistId: Long?, trackId: Long?, recordingId: Long?): Boolean =
        playlistId != null || trackId != null || recordingId != null
}
