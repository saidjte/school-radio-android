package com.saidjte.schoolradio.data.bundled

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات وحدة الجينلات المدمجة (v0.14.0) — سلامة البيانات قبل الزرع.
 */
class BundledJinglesTest {

    @Test fun `seven jingles bundled`() {
        assertEquals(7, BundledJingles.ALL.size)
    }

    @Test fun `asset files are unique mp3 names`() {
        val files = BundledJingles.ALL.map { it.file }
        assertEquals(files.size, files.toSet().size)
        assertTrue(files.all { it.endsWith(".mp3") })
    }

    @Test fun `titles are unique non-blank`() {
        val titles = BundledJingles.ALL.map { it.title }
        assertEquals(titles.size, titles.toSet().size)
        assertTrue(titles.all { it.isNotBlank() })
    }

    @Test fun `docId uses bundling prefix`() {
        assertEquals(
            "bundled:jingles/school_bell.mp3",
            BundledJingles.docId("school_bell.mp3")
        )
        // البادئة موحدة عبر الدالة — لا بناء يدوي في المستودع
        assertTrue(BundledJingles.ALL.all { BundledJingles.docId(it.file).startsWith(BundledJingles.DOC_PREFIX) })
    }

    @Test fun `category name is arabic and non-blank`() {
        assertTrue(BundledJingles.CATEGORY.isNotBlank())
        assertTrue(BundledJingles.CATEGORY.contains("فواصل"))
    }
}
