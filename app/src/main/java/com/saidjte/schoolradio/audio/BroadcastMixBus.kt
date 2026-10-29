package com.saidjte.schoolradio.audio

import android.os.Process
import com.saidjte.schoolradio.core.util.BroadcastTapLogic
import com.saidjte.schoolradio.core.util.CrashGuard
import java.util.Arrays
import java.util.concurrent.locks.LockSupport

/**
 * ناقل مزج تسجيل البث الكامل (v0.51.0) — القلب البديل لمعمارية الالتقاط
 * المنسوخة من v0.49.0.
 *
 * **لماذا هذا هو الجذر الصحيح؟** توثيق أندرويد يقول عن AudioPlaybackCapture
 * «Configuration for capturing audio played by *other apps*» — المنصة تمنع
 * التطبيق من التقاط صوته نفسه، فكان ملف MIX يخرج بالمايك وحده (بلاغ:
 * «التسجيل يسجل فقط المايكروفون ولا يسجل الموسيقى من المشغل»). والتطبيق
 * لا يحتاج أن يطلب صوته من أحد: كل مصدر يمر بأيديه أصلاً — تاب لكل مصدر
 * يُجمع حسابياً كما يجمعه الخلاط الصوتي على مكبرات الاستوديو.
 *
 * **المصادر الأربعة** (ما يسمعه الجمهور = مجموعها):
 *  1. [mediaTap]  — مشغل المكتبة (بعد فيدَر الموسيقى والخفض التلقائي)
 *  2. [cartTap]   — حائط الجينغل (بعد فيدَر الجينل)
 *  3. [toneTap]   — نغمة إعلان البث (بمستواها الكامل كما تُبث)
 *  4. [micTap]    — خط المايك الحي (بعد الكسب والتلاشي ومعالجة المذيع)
 *
 * **التوقيت**: خيط خلط واحد يستيقظ على الصوت الفعلي لا على الساعة — يسحب كل
 * ما توفر من كل تاب (بسقف 100ms) ويجمعه ويُذيل نومه بطول ما استهلكه من زمن
 * صوتي حقيقي، فيظل معدل الاستهلاك مطابقاً لمعدل الإنتاج الحقيقي لأجهزة
 * الصوت بلا انحراف تراكمي، وانفجارات فك الترميز تمتصها الخزانات الحلقية
 * (برح الأقدم عند الفيض). الصمت الكامل لا يكتب عينات — الأرشيف زمن محتوى لا
 * زمن جدار.
 *
 * المخرجات: دفعات مونو 48kHz عبر [start] إلى الكاتب مباشرة — نفس عقد
 * PcmAudioWriter الحالي بلا أي تعديل عليه.
 */
class BroadcastMixBus {

    val mediaTap = BroadcastTap()
    val cartTap = BroadcastTap()
    val toneTap = BroadcastTap()
    val micTap = BroadcastTap()

    private val taps = arrayOf(mediaTap, cartTap, toneTap, micTap)

    private var thread: Thread? = null
    @Volatile private var running = false

    val isActive: Boolean get() = running

    /**
     * بدء خيط الخلط — الدفعات المدموجة تخرج عبر [onPcm] إلى الكاتب.
     * @return null عند النجاح أو رسالة خطأ عربية جاهزة للعرض
     */
    fun start(onPcm: (ShortArray, Int) -> Unit): String? {
        if (running) return "تسجيل البث الكامل جارٍ بالفعل"
        taps.forEach { it.clear() }
        running = true
        taps.forEach { it.enabled = true }
        thread = Thread({ mixLoop(onPcm) }, "broadcast-mix").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
        return null
    }

    /** إيقاف الخلط وتحرير الحالة — آمن للاستدعاء المتكرر ومن أي خيط */
    fun stop() {
        running = false
        taps.forEach { it.enabled = false }
        thread?.join(1200)
        thread = null
        taps.forEach { it.clear() }
    }

    /** تغذية تاب المايك — يستدعيها RecordingManager من onPcm حلقة البث */
    fun pushMicPcm(pcm: ShortArray, n: Int) {
        if (running) micTap.push(pcm, n)
    }

    /** تغذية تاب نغمة الإعلان — يستدعيها BroadcastAnnouncer من خيط النغمة */
    fun pushTonePcm(pcm: ShortArray, n: Int) {
        if (running) toneTap.push(pcm, n)
    }

    /** حلقة الخلط — تستيقظ على الصوت الفعلي وتتابع زمن الاستهلاك لا زمن الجدار */
    private fun mixLoop(onPcm: (ShortArray, Int) -> Unit) {
        // v0.52.0: درع الخيط الصوتي — القاعدة المقدسة v0.15.0: لا خيط صوتي يموت
        // باستثناء غير معالج (المعالج الافتراضي للخيوط يغلق العملية كلها وسط
        // البث والتسجيل — المشغل والمايك والأرشيف يُقتلون ضغطة واحدة، ثم
        // تعود الأعطال مع كل محاولة). الخيط يخرج بهدوء وآلية الإيقاف تكمل عملها.
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val cap = MicrophoneController.SAMPLE_RATE / 10 // 4800 = سقف دفعة 100ms
            val mixBuf = ShortArray(cap)
            val tmp = ShortArray(cap)
            var nextDue = System.nanoTime()
            while (running) {
                Arrays.fill(mixBuf, 0.toShort())
                var maxN = 0
                for (tap in taps) {
                    val got = tap.pull(tmp, cap)
                    if (got > maxN) maxN = got
                    if (got > 0) BroadcastTapLogic.sumInto(mixBuf, tmp, got)
                }
                if (maxN == 0) {
                    // صمت كامل — إعادة معايرة موعد الاستحقاق عند أول صوت قادم
                    nextDue = System.nanoTime()
                    LockSupport.parkNanos(6_000_000L) // 6ms استفتاء خفيف لا يرق للهاتف
                    continue
                }
                onPcm(mixBuf, maxN)
                // الاستحقاق التالي = ما استُهلك من زمن صوتي حقيقي — بلا انحراف تراكمي
                nextDue += maxN * 1_000_000_000L / BroadcastTap.SAMPLE_RATE
                val wait = nextDue - System.nanoTime()
                if (wait > 0) LockSupport.parkNanos(wait) else nextDue = System.nanoTime()
            }
        } catch (t: Throwable) {
            CrashGuard.record("BroadcastMixBus", t)
        }
    }
}
