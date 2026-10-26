package com.saidjte.schoolradio.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.saidjte.schoolradio.core.util.ScheduleLogic
import com.saidjte.schoolradio.data.local.entity.ScheduledEventEntity

/**
 * مدير الجدولة الدقيقة عبر AlarmManager.
 *
 * لماذا AlarmManager وليس WorkManager؟
 * - WorkManager *لا يضمن* الإطلاق في الثانية المحددة (يؤجل حسب قيود النظام/البطارية).
 *   مناسب للمهام المؤجلة، لا لبدء فقرة إذاعية في 09:45:00.
 * - استراتيجية مزدوجة: AlarmManager exactAndAllowWhileIdle عند توفر صلاحية المنبهات الدقيقة
 *   (SCHEDULE_EXACT_ALARM / إعدادات النظام)، وإلا setWindow بتسامح ±10 دقائق.
 *
 * نمط عمله: أعد ضبط كل المنبهات القادمة عند (تعديل أي حدث / إقلاع الجهاز / تغيّر الوقت /
 * تحديث التطبيق). لا تخزين حالي للمنبهات — المصدر دائماً قاعدة البيانات.
 */
class SchedulerManager(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** هل صلاحية المنبهات الدقيقة ممنوحة؟ (API 31+ تحتاج موافقة من إعدادات النظام) */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()

    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= 31) {
            val i = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(android.net.Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { context.startActivity(i) } catch (_: Exception) {}
        }
    }

    /**
     * v0.13.0 — هل التطبيق معفي من تحسينات البطارية؟
     * الإعفاء يمنع قتلة الخلفية (خاصة عند مصنّعين بقصّاصات مخصصة) من إسكات
     * خدمة الأتمتة الحارسة والمنبهات — شرط عملي لجدولة إذاعية موثوقة.
     */
    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** فتح حوار النظام لطلب الإعفاء من تحسينات البطارية (رسالة النظام مهذبة ومباشرة) */
    fun requestIgnoreBatteryOptimizations() {
        try {
            val i = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(android.net.Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        } catch (_: Exception) {}
    }

    /**
     * إعادة ضبط منبهات كل الأحداث القادمة (تُستدعى بعد أي تعديل أو إقلاع).
     * تُلغي المنبهات القديمة أولاً ثم تضبط الحدث الأقرب لكل حدث مجدول.
     */
    fun rescheduleAll(events: List<ScheduledEventEntity>) {
        val now = System.currentTimeMillis()
        val pm = context.packageManager
        // إلغاء كل المنبهات القديمة (نمر على معرفات محتملة موجودة سابقاً)
        events.forEach { cancelAlarm(it.id) }
        // حدثان قد يتشاركان المعرف القديم؟ لا — المعرف فريد، الإلغاء بالمطابقة آمن

        var armed = 0
        for (event in events) {
            val trigger = ScheduleLogic.nextTriggerMillis(
                ScheduleLogic.NextTriggerInput(
                    event.startMinutes, event.weekDaysMask, event.specificDate
                ), now
            ) ?: continue
            armAlarm(event.id, trigger, exact = canScheduleExact())
            armed++
        }
        android.util.Log.i("SchedulerManager", "أُعيد ضبط $armed منبه دقيق/مرن من ${events.size}")
        // الإشارة إلى أن وحدة pm متاحة للتوسعات المستقبلية (يمنع تحذير عدم الاستخدام في بعض النسخ)
        checkNotNull(pm)
    }

    fun armAlarm(eventId: Long, triggerAtMillis: Long, exact: Boolean) {
        val pi = pendingIntent(eventId)
        if (exact) {
            // يعمل حتى في وضع توفير البطارية (ضمن حدود Doze) — الأنسب لفقرات الصباح
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAtMillis, pi
            )
        } else {
            // بلا صلاحية الدقة: نافذة تسامح 10 دقائق + تنبيه المستخدم في الواجهة
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP, triggerAtMillis, 10 * 60_000L, pi
            )
        }
    }

    fun cancelAlarm(eventId: Long) {
        alarmManager.cancel(pendingIntent(eventId))
    }

    private fun pendingIntent(eventId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE_EVENT
            putExtra(AlarmReceiver.EXTRA_EVENT_ID, eventId)
        }
        return PendingIntent.getBroadcast(
            context,
            eventId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** مكون المستقبِل — يستعمله BootReceiver لإعادة التهيئة */
    companion object {
        fun componentName(context: Context): ComponentName =
            ComponentName(context, AlarmReceiver::class.java)
    }
}
