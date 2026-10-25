package com.saidjte.schoolradio.data.remote

/**
 * v0.25.0 — حسابات تنزيل صافية قابلة للاختبار: نسبة التقدم، تسمية الحجم،
 * وقرار خنق التحديثات (كي لا تغرق الواجهة بإعادة رسم كل 64KB).
 */
object DownloadLogic {

    /** عتبة الإبلاغ عن التقدم: كل 256KB أو عند النهاية — خفيف على إعادة التركيب */
    const val REPORT_DELTA_BYTES = 256_000L

    /**
     * نسبة التقدم 0..1 — null عندما يكون الحجم الكلي مجهولاً
     * (خوادم لا ترسل Content-Length: يُعرض مؤشر غير محدد بدل نسبة كاذبة)
     */
    fun percent(downloaded: Long, total: Long): Float? {
        if (total <= 0) return null
        return (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * هل يجدر إبلاغ الواجهة الآن؟ نعم عند تجاوز الفرق عتبة الإبلاغ
     * (الفرق السالب — إعادة تنزيل — يُبلَّغ أيضاً ليعود الشريط للخلف بصدق).
     */
    fun shouldReport(lastReported: Long, current: Long): Boolean =
        kotlin.math.abs(current - lastReported) >= REPORT_DELTA_BYTES

    /** تسمية حجم مقروءة عربية — بايت/كيلوبايت/ميغابايت/غيغابايت */
    fun bytesLabel(bytes: Long): String = when {
        bytes < 0 -> ""
        bytes < 1024 -> "$bytes بايت"
        bytes < 1024 * 1024 -> {
            val kb = bytes / 1024.0
            if (kb < 10) String.format("%.1f كيلوبايت", kb) else "${kb.toInt()} كيلوبايت"
        }
        bytes < 1024L * 1024 * 1024 -> {
            val mb = bytes / (1024.0 * 1024.0)
            if (mb < 10) String.format("%.1f ميغابايت", mb) else "${mb.toInt()} ميغابايت"
        }
        else -> String.format("%.2f غيغابايت", bytes / (1024.0 * 1024 * 1024))
    }

    /** سطر حالة التنزيل الجاهز للعرض: «45% • 2.1 ميغابايت من 4.6 ميغابايت» أو «2.1 ميغابايت» */
    fun statusLine(downloaded: Long, total: Long): String {
        val p = percent(downloaded, total)
        return if (p != null && total > 0) {
            "${(p * 100).toInt()}% • ${bytesLabel(downloaded)} من ${bytesLabel(total)}"
        } else if (downloaded <= 0) {
            "قيد التنزيل…" // لا بايتات بعد وحجم مجهول — بلا «0 بايت» المربك
        } else {
            bytesLabel(downloaded)
        }
    }
}
