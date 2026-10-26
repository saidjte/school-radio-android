package com.saidjte.schoolradio.core.util

/**
 * v0.35.0: خطة بناء قائمة تشغيل سريعة — منطق صافٍ قابل للاختبار.
 *
 * المشكلة: كل مواضع بدء التشغيل كانت تحلّ تدفقات **كل** المقاطع البعيدة
 * (يوتوب/ساوندكلاود/Drive) قبل انطلاق أول صوت — مكتبة من عشرات المقاطع البعيدة
 * تعني دقائق انتظار بعد الضغطة، وعلى أجهزة بلا إنترنت تتحول كل محاولة حلّ
 * إلى مهلة انقطاع تتراكم.
 *
 * القرار: المقطع البعيد الذي سينطلق به التشغيل فوراً هو وحده من يُحلّ تدفقه
 * في لحظة الطلب (ضمان صحة الانطلاق — الروابط المخزنة قد تكون منتهية)،
 * وبقية المقاطع البعيدة تُجدَّد خلفياً وتحل أماكنها في القائمة الحية
 * أثناء الاستماع عبر PlaybackConnection.replaceItemUri بلا أي انتظار.
 * المقاطع المحلية لا تحتاج أي حلّ شبكي — تُشغَّل من URI المخزّن فوراً.
 */
object QueueStartLogic {

    /** خطة القائمة: فهرس الحل الفوري (إن وجد) + فهارس التجديد الخلفي بترتيب التشغيل */
    data class Plan(
        /** فهرس المقطع البعيد الذي يُحلّ تدفقه فوراً قبل الانطلاق — null إذا لا داعي */
        val syncResolveIndex: Int?,
        /** فهارس المقاطع البعيدة المؤجلة للتجديد الخلفي — بترتيب التشغيل */
        val pendingIndexes: List<Int>
    )

    /**
     * [isRemote] هل كل مقطع بعيد؟ [startIndex] فهرس مقطع الانطلاق
     * (يُقيَّد داخل الحدود تلقائياً — الفهارس الخارجة تعود إلى 0).
     * قائمة محلية كلياً تعني بلا حلّ شبكي إطلاقاً: انطلاق فوري.
     */
    fun decide(isRemote: List<Boolean>, startIndex: Int): Plan {
        if (isRemote.isEmpty()) return Plan(syncResolveIndex = null, pendingIndexes = emptyList())
        val si = startIndex.coerceIn(0, isRemote.lastIndex)
        val pending = isRemote.indices.filter { isRemote[it] && it != si }
        return Plan(
            syncResolveIndex = if (isRemote[si]) si else null,
            pendingIndexes = pending
        )
    }
}
