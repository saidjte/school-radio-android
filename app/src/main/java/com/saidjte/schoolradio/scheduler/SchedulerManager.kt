package com.saidjte.schoolradio.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.saidjte.schoolradio.core.util.ReminderLogic
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
     *
     * v0.28.0: تضبط أيضاً **منبهات التنبيه** للفقرات المؤرخة المفعّل تنبيهها —
     * إشعار تذكير قبل الانطلاق (ساعة/يوم) عبر AlarmManager مستقل بمعرف طلب مميز،
     * فلا يتداخل مع منبهات الإطلاق ولا يُطلق الفقرة — يُعلم فقط.
     */
    fun rescheduleAll(events: List<ScheduledEventEntity>) {
        val now = System.currentTimeMillis()
        val pm = context.packageManager
        // إلغاء كل المنبهات القديمة (نمر على معرفات محتملة موجودة سابقاً) —
        // منبهات الإطلاق ومنبهات التنبيه معاً (v0.28.0)
        events.forEach { cancelAlarm(it.id); cancelReminder(it.id) }
        // حدثان قد يتشاركان المعرف القديم؟ لا — المعرف فريد، الإلغاء بالمطابقة آمن

        var armed = 0
        var reminders = 0
        for (event in events) {
            val trigger = ScheduleLogic.nextTriggerMillis(
                ScheduleLogic.NextTriggerInput(
                    event.startMinutes, event.weekDaysMask, event.specificDate
                ), now
            ) ?: continue
            armAlarm(event.id, trigger, exact = canScheduleExact())
            armed++
            // v0.28.0: منبه التنبيه للفقرة المؤرخة — لحظة التنبيه ما زالت قادمة فقط
            if (ReminderLogic.shouldArm(
                    event.specificDate != null, event.reminderBeforeMinutes, trigger, now
                )
            ) {
                armReminder(
                    event.id,
                    ReminderLogic.reminderAt(trigger, event.reminderBeforeMinutes!!),
                    exact = canScheduleExact()
                )
                reminders++
            }
        }
        android.util.Log.i(
            "SchedulerManager",
            "أُعيد ضبط $armed منبه إطلاق و$reminders منبه تنبيه من ${events.size}"
        )
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

    /**
     * v0.28.0 — منبه التنبيه (إشعار تذكير) قبل الفقرة المؤرخة:
     * استراتيجية الدقة نفسها منبهات الإطلاق (exact أو نافذة ±10 دقائق)،
     * لكنه يستدعي مسار الإشعار في AlarmReceiver دون تشغيل أي وسائط.
     */
    fun armReminder(eventId: Long, atMillis: Long, exact: Boolean) {
        val pi = reminderPendingIntent(eventId)
        if (exact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } else {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP, atMillis, 10 * 60_000L, pi
            )
        }
    }

    /** إلغاء منبه التنبيه لهذه الفقرة (يُستدعى دائماً قبل إعادة الضبط) */
    fun cancelReminder(eventId: Long) {
        alarmManager.cancel(reminderPendingIntent(eventId))
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

    /**
     * PendingIntent مستقل تماماً عن منبه الإطلاق: أمر مختلف (FIRE_REMINDER)
     * ومعرف طلب مزاح بمقدار ثابت — لا تصادم حتى لو تطابق الرقم مع حدث آخر.
     */
    private fun reminderPendingIntent(eventId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE_REMINDER
            putExtra(AlarmReceiver.EXTRA_EVENT_ID, eventId)
        }
        return PendingIntent.getBroadcast(
            context,
            (eventId + REMINDER_REQUEST_OFFSET).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** مكون المستقبِل — يستعمله BootReceiver لإعادة التهيئة */
    companion object {
        /** إزاحة معرف طلب منبهات التنبيه لضمان التمييز عن منبهات الإطلاق */
        const val REMINDER_REQUEST_OFFSET = 1_000_000L

        fun componentName(context: Context): ComponentName =
            ComponentName(context, AlarmReceiver::class.java)
    }
}
