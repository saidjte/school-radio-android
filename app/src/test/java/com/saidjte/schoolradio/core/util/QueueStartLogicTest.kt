package com.saidjte.schoolradio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * اختبارات QueueStartLogic — خطة القوائم السريعة (v0.35.0).
 * العقد: حلّ شبكي فوري لمقطع الانطلاق البعيد وحده، والبقية تجديد خلفي،
 * والمحلية كلياً بلا أي حلّ شبكي.
 */
class QueueStartLogicTest {

    @Test
    fun `قائمة محلية كليا - بلا حل فوري وبلا تجديد خلفي`() {
        val plan = QueueStartLogic.decide(listOf(false, false, false), 1)
        assertNull(plan.syncResolveIndex)
        assertEquals(emptyList<Int>(), plan.pendingIndexes)
    }

    @Test
    fun `قائمة بعيدة كليا - مقطع الانطلاق يحل فورا والبقية مؤجلة بترتيبها`() {
        val plan = QueueStartLogic.decide(listOf(true, true, true, true), 2)
        assertEquals(2, plan.syncResolveIndex)
        assertEquals(listOf(0, 1, 3), plan.pendingIndexes)
    }

    @Test
    fun `مقطع انطلاق محلي وسط بعيدة - بلا حل فوري والبقية كلها مؤجلة`() {
        val plan = QueueStartLogic.decide(listOf(true, false, true), 1)
        assertNull(plan.syncResolveIndex)
        assertEquals(listOf(0, 2), plan.pendingIndexes)
    }

    @Test
    fun `مقطع انطلاق بعيد وسط محلية - حل فوري للمطلوب فقط`() {
        val plan = QueueStartLogic.decide(listOf(false, true, false, false), 1)
        assertEquals(1, plan.syncResolveIndex)
        assertEquals(emptyList<Int>(), plan.pendingIndexes)
    }

    @Test
    fun `فهرس انطلاق سالب يقيَّد إلى الصفر`() {
        val plan = QueueStartLogic.decide(listOf(true, true), -5)
        assertEquals(0, plan.syncResolveIndex)
        assertEquals(listOf(1), plan.pendingIndexes)
    }

    @Test
    fun `فهرس انطلاق خارج الحدود يقيَّد لآخر عنصر`() {
        val plan = QueueStartLogic.decide(listOf(false, true), 99)
        assertEquals(1, plan.syncResolveIndex)
        assertEquals(emptyList<Int>(), plan.pendingIndexes)
    }

    @Test
    fun `قائمة فارغة - خطة معطلة بلا استثناء`() {
        val plan = QueueStartLogic.decide(emptyList(), 0)
        assertNull(plan.syncResolveIndex)
        assertEquals(emptyList<Int>(), plan.pendingIndexes)
    }

    @Test
    fun `مقطع وحيد بعيد - حل فوري بلا مؤجلات`() {
        val plan = QueueStartLogic.decide(listOf(true), 0)
        assertEquals(0, plan.syncResolveIndex)
        assertEquals(emptyList<Int>(), plan.pendingIndexes)
    }

    @Test
    fun `مقطع وحيد محلي - تشغيل فردي بلا أي حل شبكي`() {
        // v0.48.0: عقد «تشغيل فردي» للمقاطع المحلية — قائمة بعنصر واحد
        // تعني انطلاقاً فورياً من URI المخزن وبلا تجديد خلفي إطلاقاً
        val plan = QueueStartLogic.decide(listOf(false), 0)
        assertNull(plan.syncResolveIndex)
        assertEquals(emptyList<Int>(), plan.pendingIndexes)
    }
}
