package com.saidjte.schoolradio.core.util

/**
 * v0.30.0 — منطق قرار صلاحية الميكروفون (صافٍ بلا تبعيات أندرويد ولا Compose —
 * قابل للاختبار وحدوياً). تستخدمه نقاطا البث (StudioScreen وMicQuickToggle)
 * بعد قراءة حالة Accompanist Permissions لتحويلها إلى خطوة إجرائية واحدة.
 *
 * مصفوفة القرار (نمط أندرويد القياسي):
 * - ممنوحة → ابدأ فوراً.
 * - غير ممنوحة ولم تُطلب بعد → اطلبها (مع شرح مختصر إن أظهر النظام «لماذا؟»).
 * - غير ممنوحة وطُلبت سابقاً والنظام يعرض المبرر → اطلبها مجدداً مع شرح أطول
 *   (المستخدم رفض «مرة» فقط — ضغطة رفض عابرة).
 * - غير ممنوحة وطُلبت والنظام لا يعرض المبرر → «الرفض الدائم»: النظام لن يعرض
 *   حواراً بعد الآن، فالتوجيه إلى إعدادات التطبيق هو الطريق الوحيد.
 */
object MicPermissionFlowLogic {

    enum class Step {
        /** الصلاحية ممنوحة — شغّل الميكروفون مباشرة */
        START,
        /** اطلب الصلاحية مع رسالة شرح قبل الطلب */
        REQUEST_WITH_RATIONALE,
        /** اطلب الصلاحية بلا شرح (أول مرة) */
        REQUEST,
        /** رفض دائم — افتح إعدادات التطبيق */
        OPEN_SETTINGS
    }

    /**
     * @param granted هل الصلاحية ممنوحة الآن؟
     * @param shouldShowRationale هل يعرض النظام مبرر «لماذا نحتاجها؟»؟
     * @param alreadyRequested هل طلبناها في هذه الجلسة من قبل؟
     */
    fun decide(granted: Boolean, shouldShowRationale: Boolean, alreadyRequested: Boolean): Step {
        if (granted) return Step.START
        if (!alreadyRequested) {
            return if (shouldShowRationale) Step.REQUEST_WITH_RATIONALE else Step.REQUEST
        }
        // بعد أول رفض: مبرر ظاهر = رفض عادي نعيد الطلب، مبرر مختفٍ = رفض دائم
        return if (shouldShowRationale) Step.REQUEST_WITH_RATIONALE else Step.OPEN_SETTINGS
    }

    /** رسالة الشرح قبل الطلب (تظهر في Snackbar ثم يُفتح حوار النظام) */
    fun rationaleMessage(permanent: Boolean): String =
        if (permanent) {
            "رفضت صلاحية الميكروفون نهائياً — اسمح بها من إعدادات النظام ثم أعد المحاولة"
        } else {
            "نحتاج صلاحية الميكروفون لبثك على الهواء — وافق على الطلب القادم"
        }
}
