package com.saidjte.schoolradio.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.44.0 — اختبارات منطق ضمان مفتاح SoundCloud (client_id).
 *
 * الخلفية الموثقة تجريبياً: SoundCloud نقلت المفتاح من أصول JS
 * (النمط القديم في NewPipeExtractor: `script[src*="sndcdn.com/assets/"]`
 * + `,client_id:"…"`) إلى JSON __NEXT_DATA__ داخل HTML صفحة
 * m.soundcloud.com الجديدة — فانكسر اكتشاف المستخرج وماتت كل نداءات
 * api-v2. هذه الاختبارات تثبت أن استخراجنا للنمط الجديد صادق مع
 * أشكال الصفحة الواقعية ويرفض الأشكال الناقصة.
 */
class SoundcloudAccessTest {

    /** عينة حقيقية الشكل: __NEXT_DATA__ كما ظهرت في صفحة m.soundcloud.com (2026-10) */
    private val realShapedHtml = """
        <!doctype html><html><head><title>SoundCloud</title></head><body>
        <div id="root"></div>
        <script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{}},"page":"/",
        "runtimeConfig":{"clientId":"KKzJxmw11tYpCs6T24P4uUYhqmjalG6M","apiOrigin":"https://api-v2.soundcloud.com"},
        "buildId":"x1y2z3","isFallback":false}</script>
        </body></html>
    """.trimIndent()

    @Test fun `extracts clientId from real-shaped NEXT_DATA`() {
        assertEquals(
            "KKzJxmw11tYpCs6T24P4uUYhqmjalG6M",
            SoundcloudAccess.extractClientId(realShapedHtml)
        )
    }

    @Test fun `extracts clientId with surrounding attributes`() {
        val html = """<script>window.__NEXT_DATA__={"runtimeConfig":{"clientId":"AbcDef123456GhiJklMn012345678901"}};</script>"""
        assertEquals("AbcDef123456GhiJklMn012345678901", SoundcloudAccess.extractClientId(html))
    }

    @Test fun `rejects html without NEXT_DATA clientId`() {
        assertNull(SoundcloudAccess.extractClientId("<html><body>no data here</body></html>"))
        assertNull(SoundcloudAccess.extractClientId(""))
    }

    @Test fun `rejects malformed or short clientIds`() {
        // أقصر من 32 — معرّف قديم بصيغة أخرى لا نلتقطه بالتخمين
        assertNull(
            SoundcloudAccess.extractClientId(
                """{"runtimeConfig":{"clientId":"short123"}}"""
            )
        )
        // 32 محرفاً لكن فيه شرطة (ليس نمط معرفات SoundCloud الحالي)
        assertNull(
            SoundcloudAccess.extractClientId(
                """{"runtimeConfig":{"clientId":"AbcDef123456GhiJklMn0123456789-1"}}"""
            )
        )
    }

    @Test fun `client id pattern is 32 base62 chars`() {
        val key = "KKzJxmw11tYpCs6T24P4uUYhqmjalG6M"
        assertEquals(32, key.length)
        assertTrue(key.all { it.isLetterOrDigit() })
        // الصيغة القديمة التي كانت تُستخرج من أصول JS (بفاصلة سابقة) لا تطابق نسيجنا الجديد
        assertFalse(SoundcloudAccess.NEXT_DATA_CLIENT_ID.containsMatchIn(""",client_id:"$key""""))
    }
}
