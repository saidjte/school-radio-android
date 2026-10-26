package com.saidjte.schoolradio.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * مستقبل المنبه الدقيق: يُطلق الفقرة المجدولة في موعدها.
 *
 * v0.13.0 — المنطق كله انتقل إلى [ScheduledPlaybackLauncher] المشترك بين مسارين:
 *  - هذا المستقبل: المنبه الدقيق عند الثانية المحددة (المسار الأمثل).
 *  - [AutomationService]: الخدمة الحارسة — تطلق الفقرة داخل نافذة السماح
 *    إن انقلب المنبه أو فاتت اللحظة (أجهزة تقتل الخلفية). قيد تفرد
 *    (event + يوم) في قاعدة البيانات يمنع الإطلاق المزدوج بين المسارين.
 *
 * الصلاحيات والمطابقة:
 * - إضافة USE_EXACT_ALARM (v0.13.0) تجعل صلاحية المنبهات الدقيقة ممنوحة تلقائياً
 *   على أندرويد 13/14+ (التطبيق خارج متجر Play فلا تخضع لمراجعة سياساته)،
 *   فتعمل setExactAndAllowWhileIdle دائماً + الإعفاء المؤقت لبدء الخدمة الأمامية.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_FIRE_EVENT = "com.saidjte.schoolradio.ACTION_FIRE_EVENT"
        const val EXTRA_EVENT_ID = "event_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE_EVENT) return
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        if (eventId <= 0L) return

        val app = context.applicationContext
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ScheduledPlaybackLauncher.fire(app, eventId)
            } finally {
                result.finish()
            }
        }
    }
}
