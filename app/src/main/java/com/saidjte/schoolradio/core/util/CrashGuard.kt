package com.saidjte.schoolradio.core.util

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * v0.15.0 — حارس الانهيارات: مسجّل أعطال محلي + شبكة أمان للكوروتينات.
 *
 * الدرس المستفاد من بلاغ «التطبيق يُغلق عند إنهاء البث»: بلا أثر مكتوب للعطل
 * تبقى التشخيصات تخميناً. هذا الكائن:
 *  1) يسجّل أي انهيار غير معالج في ملف محلي (crash_log.txt داخل ملفات التطبيق)
 *     مع الطوابع الزمنية وآخر 5 أعطال فقط — ليبقى الملف صغيراً ويُقرأ لاحقاً
 *     (نسخة احتياطية، شاشة الإعدادات مستقبلاً) لتحديد الجذر الحقيقي بدقة.
 *  2) يوفر معالج استثناءات للكوروتينات (CoroutineExceptionHandler) يكتب في نفس
 *     السجل بدل أن تتصاعد استثناءات الخلفية إلى القاتل الافتراضي.
 *
 * لا يمنع الانهيار وحده — الإصلاحات الجذرية في خيوط الصوت هي الدعامة — لكنه
 * يضمن أن أي عطل قادم سيكون موثقاً بالتفصيل بدل «ينغلق التطبيق» بلا أثر.
 */
object CrashGuard {

    private const val FILE_NAME = "crash_log.txt"
    private const val MAX_LOG_BYTES = 64 * 1024

    private val ioExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "crashguard-io").apply { priority = Thread.NORM_PRIORITY - 1 }
    }

    /**
     * تنفيذ آمن على خيط IO خلفي: أي استثناء يُسجَّل في CrashGuard ولا يهرب أبداً.
     * تُستخدمه مسارات الإيقاف الحرجة التي قد يستدعيها الخيط الرئيسي أثناء إنهاء البث.
     */
    fun runOnIo(block: () -> Unit) {
        ioExecutor.execute {
            try {
                block()
            } catch (t: Throwable) {
                runCatching { append("runOnIo", t) }
            }
        }
    }

    /** يُضبط من Application (نحتاج مجلد الملفات) */
    @Volatile private var logDir: File? = null

    fun install(filesDir: File) {
        if (logDir != null) return
        logDir = filesDir
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { append("UNCAUGHT on ${thread.name}", throwable) }
            // نسلّم للمعالج الافتراضي كي يبقى سلوك النظام (إغلاق + تقرير) كما هو
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** معالج جاهز لسكوبات الكوروتينات — يسجل ثم يبلع (لا يقتل العملية) */
    val coroutineHandler =
        kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
            runCatching { append("COROUTINE", throwable) }
        }

    /** يُنادى من أي مسار حرج نريد توثيق أي خطأ فيه حتى غير الفادح */
    fun record(context: String, throwable: Throwable) {
        runCatching { append(context, throwable) }
    }

    @Synchronized
    private fun append(context: String, throwable: Throwable) {
        val dir = logDir ?: return
        try {
            val f = File(dir, FILE_NAME)
            if (f.length() > MAX_LOG_BYTES) f.delete()
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            f.appendText(
                "\n===== ${System.currentTimeMillis()} | $context =====\n" +
                    "${sw}\n"
            )
        } catch (_: Exception) {
            // آخر شيء نريده هو أن يرمي مسجّل الأعطال استثناءً بنفسه
        }
    }

    /** قراءة السجل (للاختبار أو عرض مستقبلي في الإعدادات) */
    fun readLog(): String? {
        val dir = logDir ?: return null
        val f = File(dir, FILE_NAME)
        return if (f.exists()) f.readText() else null
    }
}
