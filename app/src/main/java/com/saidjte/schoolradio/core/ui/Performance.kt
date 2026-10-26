package com.saidjte.schoolradio.core.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * v0.31.0 — وضع الأداء عبر CompositionLocal.
 *
 * true = الواجهة المبسطة: ألوان مصمتة بلا هالات أورورا ولا زجاج متدرج
 * ولا حركات زخرفية — تُقدَّم من جذر التطبيق في MainActivity من الإعدادات.
 * المكونات الزخرفية تقرأه محلياً فتتكيّف بلا تمرير معاملات عبر كل الشاشات.
 */
val LocalPerformanceMode = staticCompositionLocalOf { false }
