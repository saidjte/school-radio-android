package com.saidjte.schoolradio

import com.saidjte.schoolradio.data.cartwall.CartPad
import com.saidjte.schoolradio.data.cartwall.CartWallStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات تخزين حائط الجينغل (v0.11.0) — تحقق من:
 *  - دقة دورة الترميز/فك الترميز (serialize → parse → نفس التخطيط)
 *  - المقاومة للتلف: أسطر معطوبة تُتجاهل ولا تُسقط البقية
 *  - إرجاع الافتراضي الآمن على نص فارغ/مشوه
 *  - حدود المصفوفة: المواضع الناقصة تُملأ، خارج النطاق يُرفض
 */
class CartWallStoreTest {

    @Test
    fun `defaults هي 16 رقاقة كاملة عبر صفحتين بلا مقاطع`() {
        val pads = CartWallStore.defaults()
        assertEquals(CartWallStore.PAGES * CartWallStore.PADS_PER_PAGE, pads.size)
        assertEquals(2, pads.map { it.page }.distinct().count())
        pads.forEach {
            assertTrue("الرقاقة الافتراضية يجب أن تكون فارغة", it.isEmpty)
            assertNull(it.trackId)
        }
        // مفاتيح فريدة
        assertEquals(pads.size, pads.map { it.key }.distinct().count())
    }

    @Test
    fun `دورة ترميز وفك ترميز تحفظ كل الخصائص`() {
        val pads = CartWallStore.defaults().toMutableList()
        pads[0] = pads[0].copy(trackId = 12L, colorIndex = 5, loop = true)
        pads[9] = pads[9].copy(trackId = 77L, colorIndex = 2, loop = false)

        val restored = CartWallStore.parse(CartWallStore.serialize(pads))
        assertEquals(pads.size, restored.size)
        assertEquals(12L, restored[0].trackId)
        assertEquals(5, restored[0].colorIndex)
        assertTrue(restored[0].loop)
        assertEquals(77L, restored[9].trackId)
        assertFalse(restored[9].loop)
        // رقاقة لم تُعدل تبقى فارغة
        assertNull(restored[5].trackId)
    }

    @Test
    fun `نص فارغ أو مشوه يعيد الافتراضي الآمن`() {
        assertEquals(CartWallStore.defaults(), CartWallStore.parse(null))
        assertEquals(CartWallStore.defaults(), CartWallStore.parse(""))
        assertEquals(CartWallStore.defaults(), CartWallStore.parse("  \n "))
        // نص عشوائي — لا استثناء
        val random = CartWallStore.parse("hello world;###;1:2")
        assertEquals(CartWallStore.defaults().size, random.size)
    }

    @Test
    fun `أسطر معطوبة تُتجاهل والسليمة تبقى`() {
        // سطر صحيح واحد بين سطري قمامة — المسار الوحيد السليم يُحفظ
        val raw = "garbage;0:3:42:6:1;also-bad"
        val pads = CartWallStore.parse(raw)
        val hit = pads.first { it.page == 0 && it.index == 3 }
        assertEquals(42L, hit.trackId)
        assertEquals(6, hit.colorIndex)
        assertTrue(hit.loop)
    }

    @Test
    fun `مواضع خارج النطاق تُرفض والناقصة تُملأ افتراضياً`() {
        // صفحة 5 وموضع 99 خارجا النطاق — يُتجاهلان؛ فالناتج 16 افتراضياً
        val pads = CartWallStore.parse("5:99:1:0:0;0:0:9:1:0;0:99:1:0:0")
        assertEquals(CartWallStore.defaults().size, pads.size)
        assertEquals(9L, pads[0].trackId)
        // ألوان خارج 0..7 تُحصر ضمن اللوحة
        val clamped = CartWallStore.parse("1:2:3:55:0")
        assertEquals(7, clamped.first { it.page == 1 && it.index == 2 }.colorIndex)
    }

    @Test
    fun `مفتاح الرقاقة يتطابق مع نمط صفحة-موضع`() {
        val pad = CartPad(index = 4, page = 1, trackId = 3L)
        assertEquals("1:4", pad.key)
        assertFalse(pad.isEmpty)
    }
}
