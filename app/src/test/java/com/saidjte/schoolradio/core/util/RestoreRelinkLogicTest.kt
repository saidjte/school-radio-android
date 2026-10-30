package com.saidjte.schoolradio.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * عقد قاعدة إعادة ربط المسارات بعد الاستعادة — v0.55.0.
 *
 * استعادة نسخة احتياطية على جهاز آخر أو بعد إعادة تنصيب تقتل كل صلاحيات
 * SAF الدائمة فيصبح uri القديم غير مقروء رغم أن الملفات في مساراتها —
 * والمكتبة تعود للحياة دفعة واحدة بمظلة مجلد واحد يُبنى عليها uri جديد
 * لكل معرّف مستند محفوظ. [RestoreRelinkLogic.isUnderTree] هي قاعدة قرار
 * ذلك البناء — والخطر الأكبر فيها البادئة السلسلية الكاذبة (Music/Music2).
 */
class RestoreRelinkLogicTest {

    // ===== مظلة الجذر تغطي كل مستندات جذرها =====

    @Test
    fun `مظلة الجذر primary تغطي أي مسار تحت primary`() {
        assertTrue(RestoreRelinkLogic.isUnderTree("primary:", "primary:Music/song.mp3"))
        assertTrue(RestoreRelinkLogic.isUnderTree("primary:", "primary:الفنون/نشيد.mp3"))
        assertTrue(RestoreRelinkLogic.isUnderTree("primary:", "primary:أ"))
    }

    // ===== مظلة مجلد تغطي محتوياته بكل الأعماق =====

    @Test
    fun `مظلة مجلد تغطي نفسه وكل ما تحته بكل الأعماق`() {
        assertTrue(RestoreRelinkLogic.isUnderTree("primary:Music", "primary:Music"))
        assertTrue(RestoreRelinkLogic.isUnderTree("primary:Music", "primary:Music/song.mp3"))
        assertTrue(
            RestoreRelinkLogic.isUnderTree(
                "primary:Music", "primary:Music/أناشيد/2026/الافتتاح.mp3"
            )
        )
    }

    // ===== الفخ: بادئة سلسلية كاذبة بين مقاطع متشابهة =====

    @Test
    fun `الموسيقى لا تغطي الموسيقى٢ — بادئة سلسلية كاذبة مرفوضة`() {
        assertFalse(RestoreRelinkLogic.isUnderTree("primary:Music", "primary:Music2/song.mp3"))
        assertFalse(RestoreRelinkLogic.isUnderTree("primary:Music", "primary:Music2010/أ.mp3"))
        assertFalse(RestoreRelinkLogic.isUnderTree("primary:أ", "primary:أب/c.mp3"))
    }

    @Test
    fun `مجلد شقيق خارج المظلة مرفوض`() {
        assertFalse(RestoreRelinkLogic.isUnderTree("primary:Music", "primary:Podcasts/song.mp3"))
        assertFalse(RestoreRelinkLogic.isUnderTree("primary:Music/أناشيد", "primary:Music/مؤثرات/x.mp3"))
    }

    // ===== الجذر يجب أن يتطابق حرفياً =====

    @Test
    fun `جذر آخر مرفوض — تخزين خارجي مقابل مصدر آخر`() {
        assertFalse(RestoreRelinkLogic.isUnderTree("primary:Music", "home:Music/song.mp3"))
        assertFalse(RestoreRelinkLogic.isUnderTree("downloads:", "primary:Downloads/x.mp3"))
    }

    // ===== المعرفات المشوهة ترفض بصدق =====

    @Test
    fun `معرف بلا نقطتين أو بلا جذر يرفض`() {
        assertFalse(RestoreRelinkLogic.isUnderTree("primary:Music", "song.mp3"))
        assertFalse(RestoreRelinkLogic.isUnderTree("primary:Music", ":song.mp3"))
        assertFalse(RestoreRelinkLogic.isUnderTree("song.mp3", "primary:Music/x.mp3"))
    }

    @Test
    fun `شرطة مائلة زائدة في نهاية المظلة لا تكسر المطابقة`() {
        assertTrue(RestoreRelinkLogic.isUnderTree("primary:Music/", "primary:Music/song.mp3"))
        assertTrue(RestoreRelinkLogic.isUnderTree("primary:Music//", "primary:Music/x/y.mp3"))
    }
}
