package com.saidjte.schoolradio.core.util

/**
 * v0.23.0 — مرحلة الفاصل بين الفقرات على شاشة العرض (منطق صافٍ قابل للاختبار).
 *
 * الطلب: «مؤقت تنازلي ظاهر على شاشة العرض للفواصل بين الفقرات» — تغطي مرحلتان:
 *
 *  1) [Phase.WAITING] مهلة الانتقال (v0.15.0): صمت مقصود بعد انتهاء الفقرة قبل
 *     انطلاق الجينل — تُقرأ من لحظة الانطلاق المفوّتة [OnAirInfo.breakStartsAtMs]
 *     فتعرض الشاشة «انطلاق الفاصل بعد …» بعدّ حي.
 *
 *  2) [Phase.PLAYING] الجينل يعمل الآن: العد من متبقّي المشغل [PlaybackUiState.remainingMs]
 *     مع عنوان «ينتهي الفاصل بعد …» — يُفعّل إما بعلامة [OnAirInfo.isBreak]
 *     (الفاصل المجدول) أو بوسم عنصر الفاصل في المشغل نفسه (قوائم الفواصل breaks:).
 *
 * يكمل [AirCountdown] (عدّ الفقرات منذ v0.14.0) ولا يستبدله.
 */
object BreakCountdown {

    enum class Phase { NONE, WAITING, PLAYING }

    /**
     * تحديد مرحلة الفاصل من إشارات الواجهة المتاحة.
     * الانتظار له الأولوية (له ساعة أخص) — ووسم المشغل يكفي وحده لأن يظهر
     * عدّ الفاصل حتى مع جينلات القوائم الموسومة بلا معلومات «على الهواء».
     */
    fun phase(
        onAirIsBreak: Boolean,
        currentIsBreak: Boolean,
        breakStartsAtMs: Long?,
        nowMs: Long
    ): Phase = when {
        breakStartsAtMs != null && breakStartsAtMs > nowMs -> Phase.WAITING
        onAirIsBreak || currentIsBreak -> Phase.PLAYING
        else -> Phase.NONE
    }

    /** ثواني الانتظار حتى انطلاق الجينل (تقريب لأعلى فتُعرض العدّة الكاملة أول ما تظهر) */
    fun waitSecondsLeft(startsAtMs: Long, nowMs: Long): Long =
        ((startsAtMs - nowMs + 999L) / 1000L).coerceAtLeast(0L)

    /**
     * إلحاح عدّ الفاصل أثناء تشغيله — الفواصل قصيرة بطبيعتها (5–20 ثانية)
     * فثبتاها أصغر من ثبات الفقرات في [AirCountdown.urgency]:
     * أحمر في آخر 5 ثوانٍ، كهرماني قبلها، زمردي أعلى من 15 ثانية.
     */
    fun urgency(remainingMs: Long, playing: Boolean): AirCountdown.Urgency = when {
        !playing || remainingMs <= 0L -> AirCountdown.Urgency.NORMAL
        remainingMs <= 5_000L -> AirCountdown.Urgency.FINAL
        remainingMs <= 15_000L -> AirCountdown.Urgency.SOON
        else -> AirCountdown.Urgency.NORMAL
    }
}
