package com.saidjte.schoolradio.core.util

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * عقد قرار أخطاء المشغل (v0.53.0) — بلاغ «مشكل في المشغل» بلقطات 7/7 أثبت أن
 * القفزة التلقائية كانت تعامل أخطاء البيئة كأخطاء محتوى فأسقطت القائمة كاملة.
 */
class PlaybackErrorPolicyTest {

    // ===== أخطاء المحتوى: القفز شفاء حقيقي (سلوك v0.9 محفوظ) =====

    @Test
    fun `content IO errors skip to next item`() {
        assertTrue(PlaybackErrorPolicy.skipToNext(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
        assertTrue(PlaybackErrorPolicy.skipToNext(PlaybackException.ERROR_CODE_IO_NO_PERMISSION))
        assertTrue(PlaybackErrorPolicy.skipToNext(PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
    }

    @Test
    fun `content parsing errors skip to next item`() {
        assertTrue(PlaybackErrorPolicy.skipToNext(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED))
        assertTrue(PlaybackErrorPolicy.skipToNext(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED))
        assertTrue(PlaybackErrorPolicy.skipToNext(PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED))
        assertTrue(PlaybackErrorPolicy.skipToNext(PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED))
    }

    // ===== أخطاء البيئة: القفز لا يشفي عطلاً سيصيب كل عنصر =====

    @Test
    fun `blag root cause runtime check does NOT skip`() {
        // رمز البلاغ الحرفي — عطل محرك الصوت لا الملف: القفز كانت تسفك القائمة 7/7
        assertFalse(PlaybackErrorPolicy.skipToNext(PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK))
    }

    @Test
    fun `environment errors do NOT skip`() {
        assertFalse(PlaybackErrorPolicy.skipToNext(PlaybackException.ERROR_CODE_UNSPECIFIED))
        assertFalse(PlaybackErrorPolicy.skipToNext(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED))
        assertFalse(PlaybackErrorPolicy.skipToNext(PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED))
        assertFalse(PlaybackErrorPolicy.skipToNext(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED))
        assertFalse(PlaybackErrorPolicy.skipToNext(PlaybackException.ERROR_CODE_DECODING_FAILED))
        assertFalse(PlaybackErrorPolicy.skipToNext(PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW))
        // رمز غير معروف للمستقبل = بيئة حتى يثبت العكس (الافتراض الآمن)
        assertFalse(PlaybackErrorPolicy.skipToNext(-12345))
    }

    // ===== رسالة البيئة توجّه لتقرير العطل المكتوب حديثاً =====

    @Test
    fun `environment message carries the code name and points to the report`() {
        val m = PlaybackErrorPolicy.environmentMessage("ERROR_CODE_FAILED_RUNTIME_CHECK")
        assertTrue(m.contains("ERROR_CODE_FAILED_RUNTIME_CHECK"))
        assertTrue(m.contains("تقرير العطل"))
    }

    // ===== تكامل دلالي: رموز المحتوى الثلاثة الشائعة في رسائل الإصدارات السابقة =====

    @Test
    fun `legacy message mapping codes stay within skip semantics`() {
        // الرسائل القديمة (الملف غير متاح/صيغة تالف/فشل فك الترميز) — أول عائلتين
        // محتوى (قفز)، وفشل تهيئة فك الترميز بيئة (توقف صادق)
        assertTrue(PlaybackErrorPolicy.skipToNext(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
        assertTrue(PlaybackErrorPolicy.skipToNext(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED))
        assertFalse(PlaybackErrorPolicy.skipToNext(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED))
        assertEquals(
            false,
            PlaybackErrorPolicy.skipToNext(PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK)
        )
    }
}
