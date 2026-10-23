package com.saidjte.schoolradio.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.saidjte.schoolradio.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * إعادة تهيئة الجدولة بعد:
 *  - إعادة تشغيل الجهاز (BOOT_COMPLETED)
 *  - تغيّر وقت/منطقة الجهاز (TIME_SET / TIMEZONE_CHANGED) — المنبهات القديمة تصبح غير صالحة
 *  - تحديث التطبيق (MY_PACKAGE_REPLACED)
 *
 * ما يفعله: قراءة الأحداث المفعلة من Room (المصدر الوحيد للحقيقة) →
 * إعادة ضبط منبهاتها القادمة → تعليم الفقرات الفائتة لهذا اليوم MISSED.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val known = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
        if (action !in known) return

        val app = context.applicationContext
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ServiceLocator.init(app)
                val events = ServiceLocator.schedules.allEvents()
                    .filter { ServiceLocator.schedules.enabledSchedules().any { s -> s.id == it.scheduleId } }
                ServiceLocator.scheduler.rescheduleAll(events)
                val missed = ServiceLocator.schedules.markMissed(System.currentTimeMillis())
                Log.i("BootReceiver", "أُعيدت تهيئة ${events.size} حدثاً؛ فائت = $missed")
            } catch (e: Exception) {
                Log.e("BootReceiver", "فشل إعادة التهيئة", e)
            } finally {
                result.finish()
            }
        }
    }
}
