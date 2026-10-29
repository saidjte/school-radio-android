package com.saidjte.schoolradio.core.util

import com.saidjte.schoolradio.data.local.entity.RecordingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** عقد المصدر الجديد MIX (بث كامل) في تسميات الأرشيف — v0.49.0 */
class RecordingLogicMixTest {

    @Test
    fun `براية المصدر MIX عربية واضحة في الأرشيف`() {
        assertEquals("بث كامل", RecordingSource.MIX.labelAr)
    }

    @Test
    fun `عنوان تسجيل البث الكامل يبدأ ببث كامل ثم التاريخ`() {
        val at = 1_760_000_000_000L
        assertTrue(RecordingLogic.defaultTitle(RecordingSource.MIX, at).startsWith("بث كامل "))
    }

    @Test
    fun `ملف البث الكامل ببراية mix — مميز عن broadcast وsegment`() {
        val at = 1_760_000_000_000L
        val expectedStamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(at))
        assertEquals(
            "mix-$expectedStamp.m4a",
            RecordingLogic.newFileName(RecordingSource.MIX, at, "m4a")
        )
    }

    @Test
    fun `قراءة سجل قديم بمصدر MIX محفوظ بالاسم تعود سليمة`() {
        // سجلات الأرشيف تخزن الاسم — إعادة القراءة تعيد الثابت نفسه
        assertEquals(RecordingSource.MIX, RecordingSource.valueOf("MIX"))
    }
}
