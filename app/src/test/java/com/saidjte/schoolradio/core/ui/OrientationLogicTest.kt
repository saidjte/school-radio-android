package com.saidjte.schoolradio.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** اختبارات المنطق التكيفي للوضع الأفقي — v0.21.0 */
class OrientationLogicTest {

    // ---- useSideNavigation ----

    @Test
    fun `هاتف عادي عمودي يستخدم الشريط السفلي`() {
        // شاشة 360×800dp عمودية
        assertFalse(OrientationLogic.useSideNavigation(360, 800))
    }

    @Test
    fun `هاتف مدير أفقيا يستخدم القائمة الجانبية`() {
        // نفس الهاتف مُدار: 800×360
        assertTrue(OrientationLogic.useSideNavigation(800, 360))
    }

    @Test
    fun `جهاز لوحي عمودي يستخدم القائمة الجانبية حتى بلا دوران`() {
        // لوحي بعرض 600dp+ في الوضع العمودي (عتبة Material 3 medium)
        assertTrue(OrientationLogic.useSideNavigation(600, 960))
        assertTrue(OrientationLogic.useSideNavigation(800, 1280))
    }

    @Test
    fun `هاتف صغير قديم مدير أفقيا يدخل في النمط الجانبي`() {
        // 320×533 عمودياً → 533×320 أفقياً (أقل من عتبة اللوحي لكنه أفقي)
        assertTrue(OrientationLogic.useSideNavigation(533, 320))
    }

    @Test
    fun `شاشة مربعة تحته العتبة تبقى شريطا سفليا`() {
        assertFalse(OrientationLogic.useSideNavigation(480, 480))
    }

    // ---- isLandscape ----

    @Test
    fun `isLandscape يفرق الاتجاهين بدقة`() {
        assertTrue(OrientationLogic.isLandscape(800, 360))
        assertFalse(OrientationLogic.isLandscape(360, 800))
        assertFalse(OrientationLogic.isLandscape(600, 600))
    }

    // ---- compactRailFits ----

    @Test
    fun `سبعة بنود تتسع في ارتفاع هاتف أفقي قياسي`() {
        // 7 بنود × 47dp = 329dp ≤ 360dp
        assertTrue(OrientationLogic.compactRailFits(itemCount = 7, availableHeightDp = 360))
    }

    @Test
    fun `بنود أكثر من ارتفاع الشاشة لا تتسع`() {
        assertFalse(OrientationLogic.compactRailFits(itemCount = 10, availableHeightDp = 360))
        assertFalse(OrientationLogic.compactRailFits(itemCount = 7, availableHeightDp = 300))
    }

    // ---- العتبة والثوابت ----

    @Test
    fun `عتبة اللوحي 600dp كما في Material 3`() {
        assertEquals(600, OrientationLogic.TABLET_MIN_WIDTH_DP)
    }
}
