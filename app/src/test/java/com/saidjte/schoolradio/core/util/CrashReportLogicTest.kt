package com.saidjte.schoolradio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات تقرير الأعطال (v0.37.0) — تفكيك سجل CrashGuard وتشكيل الملخص والمشاركة.
 * عينات الدخل مطابقة حرفياً لصيغة CrashGuard.append.
 */
class CrashReportLogicTest {

    private fun logOf(vararg blocks: Pair<Long, String>): String =
        blocks.joinToString("") { (ts, ctx) ->
            "\n===== $ts | $ctx =====\n" +
                "java.lang.IllegalStateException: فشل غير متوقع\n\tat com.saidjte.schoolradio.Test.method(Test.kt:1)\n"
        }

    // ===== parse =====

    @Test
    fun `parse يرجع قائمة فارغة لنص فارغ أو null`() {
        assertTrue(CrashReportLogic.parse(null).isEmpty())
        assertTrue(CrashReportLogic.parse("").isEmpty())
        assertTrue(CrashReportLogic.parse("   \n  ").isEmpty())
    }

    @Test
    fun `parse يفكك عطلاً واحداً بالسياق والزمن والسطر الأول`() {
        val log = "\n===== 1730000000000 | recordings.playback =====\n" +
            "java.lang.IllegalStateException: boom\n\tat a.b.C.d(C.kt:9)\n"
        val entries = CrashReportLogic.parse(log)
        assertEquals(1, entries.size)
        assertEquals(1730000000000L, entries[0].timestampMs)
        assertEquals("recordings.playback", entries[0].context)
        assertTrue(entries[0].headline.startsWith("java.lang.IllegalStateException"))
        assertTrue(entries[0].headline.contains("boom"))
    }

    @Test
    fun `parse يفكك عدة أعطال بترتيب التوثيق ولا يخلط بينها`() {
        val log = logOf(
            1730000000000L to "recordings.playback",
            1730003600000L to "preview.toggle",
            1730007200000L to "playback.pushState"
        )
        val entries = CrashReportLogic.parse(log)
        assertEquals(3, entries.size)
        assertEquals(listOf("recordings.playback", "preview.toggle", "playback.pushState"),
            entries.map { it.context })
    }

    @Test
    fun `parse يتجاوز ترويسة مشوهة بلا طابع زمني رقمي بأمان`() {
        val log = "\n===== not-a-number | ctx =====\njava.lang.Error: x\n"
        val entries = CrashReportLogic.parse(log)
        // الترويسة المشوهة تُتخطى بلا استثناء ولا عطل مفبرك — CrashGuard لا يكتبها أصلاً
        assertTrue(entries.isEmpty())
    }

    // ===== summary =====

    @Test
    fun `summary يرجع null عند لا أعطال`() {
        assertNull(CrashReportLogic.summary(null, nowMs = 1730007200000L))
        assertNull(CrashReportLogic.summary("", nowMs = 1730007200000L))
    }

    @Test
    fun `summary يذكر العدد وسياق آخر عطل والزمن بالدقائق`() {
        val log = logOf(1729990000000L to "recordings.playback")
        val now = 1729990000000L + 5 * 60_000L // بعد 5 دقائق
        val s = CrashReportLogic.summary(log, now)!!
        assertTrue(s.contains("أعطال موثقة: 1"))
        assertTrue(s.contains("«recordings.playback»"))
        assertTrue(s.contains("قبل"))
        assertTrue(s.contains("5 دقائق"))
    }

    @Test
    fun `summary يستعمل صيغة الأيام للأعطال القديمة`() {
        val log = logOf(1729900000000L to "coordinator.resumeMain")
        val now = 1729900000000L + 3 * 86_400_000L // بعد 3 أيام
        val s = CrashReportLogic.summary(log, now)!!
        assertTrue(s.contains("قبل 3 أيام"))
    }

    @Test
    fun `summary يستعمل صيغة الساعات للفترات المتوسطة`() {
        val log = logOf(1729990000000L to "preview.toggle")
        val now = 1729990000000L + 2 * 3_600_000L // بعد ساعتين
        val s = CrashReportLogic.summary(log, now)!!
        assertTrue(s.contains("قبل ساعتين"))
    }

    @Test
    fun `summary يختار آخر عطل دائماً لا الأول`() {
        val log = logOf(
            1730000000000L to "old.context",
            1730007200000L to "new.context"
        )
        val now = 1730007200000L + 60_000L
        val s = CrashReportLogic.summary(log, now)!!
        assertTrue(s.contains("«new.context»"))
        assertTrue(!s.contains("old.context"))
    }

    // ===== previewLines / shareText =====

    @Test
    fun `previewLines يعرض الأحدث أولاً بحد أقصى مطلوب`() {
        val log = logOf(
            1730000000000L to "a", 1730003600000L to "b", 1730007200000L to "c"
        )
        val lines = CrashReportLogic.previewLines(log, maxEntries = 2)
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("«c»"))
        assertTrue(lines[1].startsWith("«b»"))
    }

    @Test
    fun `shareText يرجع null عند لا أعطال ويضم ترويسة عند وجودها`() {
        assertNull(CrashReportLogic.shareText(null))
        assertNull(CrashReportLogic.shareText(""))
        val text = CrashReportLogic.shareText("===== 1 | x =====\njava.lang.Error: y")!!
        assertTrue(text.startsWith("تقرير أعطال"))
        assertTrue(text.contains("java.lang.Error: y"))
    }

    // ===== رسالة النظافة =====

    @Test
    fun `رسالة لا أعطال جاهزة للعرض`() {
        assertTrue(CrashReportLogic.NO_CRASHES_MESSAGE.contains("نظيفاً"))
    }
}
