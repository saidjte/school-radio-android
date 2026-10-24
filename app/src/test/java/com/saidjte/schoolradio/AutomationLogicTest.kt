package com.saidjte.schoolradio

import com.saidjte.schoolradio.core.util.AutomationLogic
import com.saidjte.schoolradio.core.util.AutomationLogic.EventTiming
import com.saidjte.schoolradio.core.util.ScheduleLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.Calendar

/**
 * اختبارات منطق الأتمتة الحارسة (v0.13.0) — المستحق الآن / الفقرة القادمة / نص الإشعار.
 * تشغيلها: ./gradlew :app:testDebugUnitTest
 */
class AutomationLogicTest {

    private fun millisAt(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, month - 1, day, hour, minute, 0)
        return cal.timeInMillis
    }

    private fun event(id: Long, startMin: Int, mask: Int = 0b01111111) =
        EventTiming(id, "فقرة $id", startMin, mask)

    // ===== الأحداث المستحقة =====

    @Test
    fun `حدث داخل نافذة السماح مستحق وحدث قبله ليس كذلك`() {
        // 2026-10-26 = الاثنين (ISO 1) → البت 1
        val now = millisAt(2026, 10, 26, 9, 46) // 586 دقيقة
        val events = listOf(event(1, 580), event(2, 585), event(3, 589), event(4, 583))
        val due = runBlocking {
            AutomationLogic.dueEventIds(
                events, now,
                executedToday = { false },
                graceMinutes = 2
            )
        }
        // داخل [start, start+2): 585 (585-586)، 584 لا يوجد، 589 مستقبلي،
        // 583: نافذته 583-584 — 586 خارجها فليس مستحقاً
        assertEquals(listOf(2L), due)
    }

    @Test
    fun `حد الحد الأعلى حصري — الدقيقة الأخيرة من النافذة تُطلق والبعد لا`() {
        val now = millisAt(2026, 10, 26, 9, 46) // 586
        val events = listOf(event(1, 585), event(2, 584))
        val due = runBlocking {
            AutomationLogic.dueEventIds(
                events, now, executedToday = { false }, graceMinutes = 2
            )
        }
        // 584 نافذته [584, 586) → 586 حصري خارجها؛ 585 نافذته [585, 587) → مستحق
        assertEquals(listOf(1L), due)
    }

    @Test
    fun `الحدث المنفذ اليوم لا يُطلق مرة ثانية`() {
        val now = millisAt(2026, 10, 26, 9, 45)
        val events = listOf(event(1, 585), event(2, 585))
        val due = runBlocking {
            AutomationLogic.dueEventIds(
                events, now, executedToday = { it == 1L }, graceMinutes = 2
            )
        }
        assertEquals(listOf(2L), due)
    }

    @Test
    fun `حدث يوم آخر لا يستحق اليوم`() {
        val now = millisAt(2026, 10, 26, 9, 45) // الاثنين
        val events = listOf(event(1, 585, mask = 0b0100000)) // السبت فقط
        val due = runBlocking {
            AutomationLogic.dueEventIds(
                events, now, executedToday = { false }, graceMinutes = 2
            )
        }
        assertTrue(due.isEmpty())
    }

    @Test
    fun `مستحقان يرتبان بالوقت`() {
        val now = millisAt(2026, 10, 26, 9, 45)
        val events = listOf(event(9, 585), event(3, 584))
        val due = runBlocking {
            AutomationLogic.dueEventIds(
                events, now, executedToday = { false }, graceMinutes = 2
            )
        }
        assertEquals(listOf(3L, 9L), due)
    }

    // ===== الفقرة القادمة =====

    @Test
    fun `القادمة هي أقرب وقت بعد الآن غير المنفذ`() {
        val now = millisAt(2026, 10, 26, 9, 45)
        val events = listOf(event(1, 600), event(2, 610), event(3, 580))
        val next = runBlocking { AutomationLogic.nextUpToday(events, now, executedToday = { false }) }
        assertEquals(1L, next?.eventId)
    }

    @Test
    fun `المنفذ لا يُعد قادماً`() {
        val now = millisAt(2026, 10, 26, 9, 45)
        val events = listOf(event(1, 600), event(2, 610))
        val next = runBlocking { AutomationLogic.nextUpToday(events, now, executedToday = { it == 1L }) }
        assertEquals(2L, next?.eventId)
    }

    @Test
    fun `لا قادمة اليوم يعيد null`() {
        val now = millisAt(2026, 10, 26, 9, 45)
        val events = listOf(event(1, 580))
        val next = runBlocking { AutomationLogic.nextUpToday(events, now, executedToday = { false }) }
        assertNull(next)
    }

    // ===== نص الإشعار =====

    @Test
    fun `نص الإشعار يعبر عن الدقائق المتبقية`() {
        val next = EventTiming(1, "طابور الصباح", 600, 1)
        val text = AutomationLogic.notificationText(next, minutesNow = 585)
        assertTrue(text.contains("طابور الصباح"))
        assertTrue(text.contains("15")) // 600 - 585
        assertTrue(text.contains("10:00"))
    }

    @Test
    fun `بلا قادمة يظهر نص الخمول`() {
        val text = AutomationLogic.notificationText(null, minutesNow = 585)
        assertTrue(text.contains("لا فقرات قادمة"))
    }

    // ===== تكامل مع ScheduleLogic =====

    @Test
    fun `nextTriggerMillis يوافق دقائق اليوم`() {
        val now = millisAt(2026, 10, 26, 9, 45)
        val trigger = ScheduleLogic.nextTriggerMillis(
            ScheduleLogic.NextTriggerInput(600, 0b0111111), now
        )
        assertEquals(ScheduleLogic.minutesOfDay(trigger!!), 600)
    }
}
