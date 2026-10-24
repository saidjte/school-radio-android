package com.saidjte.schoolradio.audio

/**
 * حَكَم التشغيل الموحّد (v0.8.0) — الحل الاحترافي لمشكلة العمل المتزامن.
 *
 * المشكلة قبل هذه النسخة: ثلاثة مصادر صوت مستقلة (مشغل المكتبة/القوائم في الخدمة،
 * مشغل معاينة التسجيلات، البث المباشر) لم يكن بينها منسّق — فتداخل الصوت
 * ممكن (معاينة تسجيل فوق موسيقى القائمة) ومعاينة التسجيل لم تُوقف أبداً
 * عند بدء البث المباشر رغم أن الوثيقة تدّعي ذلك.
 *
 * سياسة الأولويات المطبقة هنا (نمط «التركيز الصوتي» لكن داخل التطبيق):
 *   1. البث المباشر (الميكروفون) — أعلى أولوية: يوقف المعاينة فوراً،
 *      ويعيد قائمة المكتبة الرئيسية (مخفَضة الصوت) إن كانت متوقفة مؤقتاً بسبب معاينة.
 *   2. قائمة التشغيل الرئيسية (مكتبة/قوائم/فقرات مجدولة) — تبدأ توقف المعاينة
 *      بلا استئناف لاحق (المستخدم بدأ نية جديدة).
 *   3. معاينة التسجيل — «معاملة عابرة»: توقف القائمة الرئيسية مؤقتاً
 *      وتُعاد تلقائياً عند انتهاء المعاينة طبيعياً (كما تفعل أنظمة الوسائط مع
 *      المقاطع القصيرة). تُمنع أثناء البث المباشر لتجنب التداخل.
 *
 * التصميم قابل للاختبار وحداتياً: كل التأثيرات الخارجية عبر منفذ [PlayerPort]
 * — اختبارات JVM نقيّة بلا أجهزة أو سياقات أندرويد.
 *
 * الخيطية: جميع الدوال متزامنة داخلياً (synchronized) ويجب استدعاؤها
 * من الخيط الرئيسي عملياً (استدعاءات Compose وMediaController).
 */
class PlaybackCoordinator(private val port: PlayerPort) {

    /** منفذ التأثيرات — يُنفَّذ فوق ServiceLocator في الإنتاج، وفوق مفارق في الاختبار */
    interface PlayerPort {
        fun isMainPlaying(): Boolean
        fun pauseMain()
        fun resumeMain()
        fun stopPreview()
        fun isMicLive(): Boolean
    }

    /** نتيجة طلب بدء معاينة تسجيل */
    enum class PreviewDecision { OK, BLOCKED_LIVE }

    /** هل القائمة الرئيسية متوقفة مؤقتاً بسبب معاينة (لاستئنافها لاحقاً)؟ */
    @Volatile var mainPausedByPreview: Boolean = false
        private set

    /**
     * طلب بدء معاينة تسجيل — يُطبق سياسة العزلة قبل أي تشغيل فعلي.
     * يُرجع OK إذا أمكن المتابعة (المستدعي يبدأ المشغل بعدها)،
     * أو BLOCKED_LIVE إذا كان البث المباشر يعمل (تُعرض رسالة للمستخدم).
     */
    @Synchronized
    fun requestPreviewStart(): PreviewDecision {
        if (port.isMicLive()) return PreviewDecision.BLOCKED_LIVE
        // القائمة الرئيسية تعمل؟ أوقفها مؤقتاً مع تذكر السبب لاستئنافها
        if (port.isMainPlaying()) {
            port.pauseMain()
            mainPausedByPreview = true
        }
        // أي معاينة سابقة تعمل تتوقف فوراً (استبدال فوري)
        port.stopPreview()
        return PreviewDecision.OK
    }

    /**
     * انتهاء المعاينة (طبيعياً أو بإيقاف صريح) — يُعيد القائمة الرئيسية
     * إذا كانت هي من توقفت بسبب المعاينة فقط.
     */
    @Synchronized
    fun previewFinished() {
        if (mainPausedByPreview) {
            mainPausedByPreview = false
            // لا نستأنف إذا بدأ البث المباشر في هذه الأثناء — الميكروفون أولوية
            // (الاستئناف سيكون مخفَضاً عبر آلية ducking فلا تعارض حتى لو استؤنف)
            if (!port.isMicLive()) port.resumeMain()
        }
    }

    /**
     * طلب بدء قائمة رئيسية (مكتبة/قائمة تشغيل/تشغيل يدوي) —
     * يوقف أي معاينة ويلغي استئنافها (نية المستخدم انتقلت للمشغل الرئيسي).
     */
    @Synchronized
    fun requestMainQueueStart() {
        mainPausedByPreview = false
        port.stopPreview()
    }

    /**
     * بدأ البث المباشر — المعاينة تتوقف فوراً وتُلغى فكرة الاستئناف منها،
     * أما القائمة الرئيسية فتبقى كما هي (تُخفَّض صوتها عبر آلية ducking
     * القائمة في ServiceLocator) أو تُستأنف إن كانت متوقفة بسبب معاينة —
     * فسماع الموسيقى المخفَضة خلف الإعلان هو السلوك الإذاعي الطبيعي.
     */
    @Synchronized
    fun micStarted() {
        val wasPreviewPause = mainPausedByPreview
        mainPausedByPreview = false
        port.stopPreview()
        if (wasPreviewPause && !port.isMainPlaying()) port.resumeMain()
    }

    /** انتهى البث المباشر — لا شيء مطلوب؛ إعادة مستوى الصوت تديرها آلية ducking */
    @Synchronized
    fun micStopped() {
        // محفوظ للتماثل والاتساق المستقبلي — السياسة كاملة في micStarted
    }
}
