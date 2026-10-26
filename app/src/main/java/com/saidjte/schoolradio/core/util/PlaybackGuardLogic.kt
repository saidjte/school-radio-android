package com.saidjte.schoolradio.core.util

/**
 * v0.33.0 — حارس تشغيل التسجيلات (منطق صافٍ بلا اعتماديات أندرويد).
 *
 * بعد بلاغ «التطبيق ينغلق عند تشغيل تسجيل»: كل قرار رقمي في مسار بناء
 * المقطع يمر من هنا — قصّ محصّن بحدود صحيحة دائماً مهما كانت القيم المخزنة
 * تالفة، ورسائل عربية واضحة لكل فشل بدل انهيار صامت يُفقد المستخدم التحكم.
 */
object PlaybackGuardLogic {

    /**
     * نتيجة تنقية القص: بداية آمنة ونهاية اختيارية
     * (endMs = null يعني شغّل حتى النهاية الطبيعية بلا قص).
     */
    data class Clip(val startMs: Long, val endMs: Long?)

    /**
     * تنقية قيم القص المخزنة قبل بناء ClippingConfiguration:
     *  - البداية: ≥ 0، وإذا كانت المدة معروفة فلن تتجاوز (المدة − أقل جزء مسموع)
     *  - النهاية: تُقبل فقط إذا كانت بعد البداية، وإذا كانت المدة معروفة
     *    فلن تتجاوزها — وإلا تُسقط (تشغيل حتى النهاية الطبيعية)
     *  - مدة غير معروفة (≤ 0): الحدود الرقمية فقط دون تقييد بالمدة
     */
    fun sanitizeClip(trimStartMs: Long, trimEndMs: Long, knownDurationMs: Long): Clip {
        // نفس سلوك النسخ السابقة: مدة معروفة → قيد ضمنها؛ مدة مجهولة → حد رقمي فقط
        val start = if (knownDurationMs > 0)
            trimStartMs.coerceIn(
                0L, (knownDurationMs - TrimLogic.MIN_SEGMENT_MS).coerceAtLeast(0L)
            )
        else trimStartMs.coerceAtLeast(0L)
        val end = if (trimEndMs > start &&
            (knownDurationMs <= 0L || trimEndMs <= knownDurationMs)
        ) trimEndMs else null
        return Clip(start, end)
    }

    /** رسالة فشل البدء الفوري (استثناء متزامن من المشغل عند البناء/التحضير) */
    fun startFailureMessage(): String =
        "تعذر بدء تشغيل هذا التسجيل — أعد المحاولة، وإن تكررت فأعد تسجيله من جديد"

    /** رسالة فشل التشغيل غير المتزامن (صيغة غير مدعومة/فك ترميز/ملف تالف) */
    fun decodeFailureMessage(): String =
        "توقف التشغيل: قد تكون صيغة هذا الملف غير مدعومة على هذا الجهاز أو أن الملف تالفاً"
}
