package com.saidjte.schoolradio.core.util

import com.saidjte.schoolradio.data.local.entity.RecordingSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * منطق صرف للتسجيلات — بلا أي تبعيات أندرويد حتى يكون قابلاً للاختبار وحدات
 * (نفس فلسفة ScheduleLogic في v0.2.0).
 */
object RecordingLogic {

    /** مدة العرض: M:SS ثم MM:SS ثم H:MM:SS حسب الطول */
    fun formatElapsed(ms: Long): String {
        val totalSeconds = ms / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%d:%02d", m, s)
        }
    }

    /** حجم مقروء: بايت/كيلوبايت/ميغابايت بمنزلة عشرية واحدة للوحدات الكبيرة */
    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format(Locale.US, "%.0f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }

    /**
     * العنوان الافتراضي للتسجيل: «بث 24/10 — 08:15» أو «فقرة 24/10 — 08:15».
     * أرقام عربية غربية ثابتة (Locale) لتجنب تفاوت المحارف بين الأجهزة.
     */
    fun defaultTitle(source: RecordingSource, at: Long): String {
        val stamp = SimpleDateFormat("d/M — HH:mm", Locale.US).format(Date(at))
        val prefix = when (source) {
            RecordingSource.PTT -> "بث"
            RecordingSource.MEMO -> "فقرة"
            RecordingSource.MIX -> "بث كامل"
        }
        return "$prefix $stamp"
    }

    /**
     * اسم ملف ASCII آمن (تجنب مشاكل بعض أنظمة الملفات/المزامنة مع العربية):
     * broadcast-20261024-0815.m4a — وv0.29.0: ‎.ogg لتسجيلات Opus HD.
     */
    fun newFileName(source: RecordingSource, at: Long, ext: String = "m4a"): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(at))
        val prefix = when (source) {
            RecordingSource.PTT -> "broadcast"
            RecordingSource.MEMO -> "segment"
            RecordingSource.MIX -> "mix"
        }
        return "$prefix-$stamp.$ext"
    }
}
