package com.saidjte.schoolradio.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v0.30.0 — اختبارات مصفوفة قرار صلاحية الميكروفون.
 */
class MicPermissionFlowLogicTest {

    // ===== الحالات السعيدة =====

    @Test
    fun `ممنوحة تعني البدء الفوري أيا كانت الحالة`() {
        assertEquals(MicPermissionFlowLogic.Step.START, MicPermissionFlowLogic.decide(true, false, false))
        assertEquals(MicPermissionFlowLogic.Step.START, MicPermissionFlowLogic.decide(true, true, true))
        assertEquals(MicPermissionFlowLogic.Step.START, MicPermissionFlowLogic.decide(true, false, true))
    }

    @Test
    fun `أول طلب بلا مبرر يطلب مباشرة`() {
        assertEquals(
            MicPermissionFlowLogic.Step.REQUEST,
            MicPermissionFlowLogic.decide(false, false, false)
        )
    }

    @Test
    fun `أول طلب مع مبرر يطلب مع الشرح`() {
        assertEquals(
            MicPermissionFlowLogic.Step.REQUEST_WITH_RATIONALE,
            MicPermissionFlowLogic.decide(false, true, false)
        )
    }

    // ===== ما بعد أول رفض =====

    @Test
    fun `رفض عادي مع مبرر ظاهر يعيد الطلب مع الشرح`() {
        assertEquals(
            MicPermissionFlowLogic.Step.REQUEST_WITH_RATIONALE,
            MicPermissionFlowLogic.decide(false, true, true)
        )
    }

    @Test
    fun `اختفاء المبرر بعد الطلب يعني الرفض الدائم - توجيه للإعدادات`() {
        assertEquals(
            MicPermissionFlowLogic.Step.OPEN_SETTINGS,
            MicPermissionFlowLogic.decide(false, false, true)
        )
    }

    // ===== رسائل الشرح =====

    @Test
    fun `رسالة الرفض الدائم توجه لإعدادات النظام`() {
        val msg = MicPermissionFlowLogic.rationaleMessage(permanent = true)
        assertEquals(true, msg.contains("إعدادات النظام"))
    }

    @Test
    fun `رسالة الطلب الأول تشجع على الموافقة`() {
        val msg = MicPermissionFlowLogic.rationaleMessage(permanent = false)
        assertEquals(true, msg.contains("الميكروفون"))
        assertEquals(true, msg.contains("وافق"))
    }
}
