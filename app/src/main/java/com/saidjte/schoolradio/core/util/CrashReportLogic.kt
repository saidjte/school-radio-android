package com.saidjte.schoolradio.core.util

/**
 * v0.37.0 — تقرير الأعطال: قراءة سجل CrashGuard وتشكيله للعرض والمشاركة.
 *
 * الدرس المستفاد من بلاغ «ينهار التطبيق» المتكرر: بلا أثر مكتوب يبقى التشخيص تخميناً،
 * والسجل موجود على الجهاز منذ v0.15.0 لكن لا أداة تُخرجه للمستخدم.
 * هذا المنطق الصافي يحوّل النص الخام إلى: عدد أعطال + آخر عطل (المسار والزمن)
 * + أسطر معاينة + نص مشاركة جاهز — وكل ما يخص الأندرويد (FileProvider/Intent)
 * يبقى في طبقة الواجهة.
 *
 * صيغة أسطر السجل (من CrashGuard.append):
 *   "\n===== 1730000000000 | recordings.playback =====\n"
 *   "java.lang.IllegalStateException: …\n\tat …"
 */
object CrashReportLogic {

    /** عطل واحد موثق في السجل */
    data class CrashEntry(
        /** لحظة التوثيق (epoch ms) — من ترويسة السطر */
        val timestampMs: Long,
        /** سياق التوثيق — مثل recordings.playback أو preview.toggle */
        val context: String,
        /** أول سطر غير فارغ من التتبع — اسم الاستثناء ورسالته عادة */
        val headline: String
    )

    const val NO_CRASHES_MESSAGE = "لا أعطال موثقة على هذا الجهاز — التطبيق يعمل نظيفاً"

    private val HEADER = Regex(
        pattern = "^\\s*=====\\s*(\\d+)\\s*\\|\\s*(.+?)\\s*=====\\s*$",
        options = setOf(RegexOption.MULTILINE)
    )

    /** تفكيك السجل الخام إلى قائمة أعطال بترتيب التوثيق (الأقدم أولاً) */
    fun parse(log: String?): List<CrashEntry> {
        if (log.isNullOrBlank()) return emptyList()
        val matches = HEADER.findAll(log).toList()
        if (matches.isEmpty()) return emptyList()
        val entries = mutableListOf<CrashEntry>()
        for (i in matches.indices) {
            val m = matches[i]
            val bodyStart = m.range.last + 1
            val bodyEnd = if (i + 1 < matches.size) matches[i + 1].range.first else log.length
            val headline = log.substring(bodyStart, bodyEnd.coerceAtMost(log.length))
                .lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() } ?: ""
            entries += CrashEntry(
                timestampMs = m.groupValues[1].toLongOrNull() ?: 0L,
                context = m.groupValues[2].trim(),
                headline = headline
            )
        }
        return entries
    }

    /**
     * سطر ملخص بطاقة الإعدادات — مثال:
     * «أعطال موثقة: 3 — آخرها "preview.toggle" قبل يومين»
     * null عند لا أعطال (تُعرض رسالة النظافة بدلاً منه).
     */
    fun summary(log: String?, nowMs: Long): String? {
        val entries = parse(log)
        if (entries.isEmpty()) return null
        val last = entries.last()
        val ageMin = (((nowMs - last.timestampMs).coerceAtLeast(0)) / 60000L).toInt()
        val age = when {
            ageMin >= 1440 -> TimeEditLogic.pluralAr(ageMin / 1440, "يوم", "يومين", "أيام")
            ageMin >= 60 -> TimeEditLogic.pluralAr(ageMin / 60, "ساعة", "ساعتين", "ساعات")
            else -> TimeEditLogic.durationAr(ageMin)
        }
        return "أعطال موثقة: ${entries.size} — آخرها «${last.context}» قبل $age"
    }

    /** أسطر معاينة داخل الحوار — الأحدث أولاً بحد أقصى [maxEntries] */
    fun previewLines(log: String?, maxEntries: Int = 5): List<String> =
        parse(log).takeLast(maxEntries).reversed().map { e ->
            "«${e.context}» — ${e.headline.take(90)}"
        }

    /** نص المشاركة الكامل — يُرسل مع ملف السجل نفسه (نص قابل للقراءة في أي تطبيق) */
    fun shareText(log: String?): String? {
        if (log.isNullOrBlank()) return null
        return "تقرير أعطال — إذاعة مدرسية احترافي\n${log.trimEnd()}\n"
    }
}
