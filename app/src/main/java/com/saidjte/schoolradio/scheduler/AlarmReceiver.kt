package com.saidjte.schoolradio.scheduler

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.saidjte.schoolradio.MainActivity
import com.saidjte.schoolradio.R
import com.saidjte.schoolradio.core.util.EventDateLogic
import com.saidjte.schoolradio.core.util.ReminderLogic
import com.saidjte.schoolradio.di.ServiceLocator
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
 *    إن انقلبت المنبه أو فاتت اللحظة (أجهزة تقتل الخلفية). قيد تفرد
 *    (event + يوم) في قاعدة البيانات يمنع الإطلاق المزدوج بين المسارين.
 *
 * v0.28.0 — مسار ثانٍ: [ACTION_FIRE_REMINDER] منبه تنبيه (تذكير) قبل الفقرة
 * المؤرخة — يبني إشعاراً عربياً أنيقاً («فقرة «حفل التخرج» — غداً الساعة 9:45
 * صباحاً») دون تشغيل أي وسائط، وبنقرة تفتح التطبيق مباشرة.
 *
 * الصلاحيات والمطابقة:
 * - إضافة USE_EXACT_ALARM (v0.13.0) تجعل صلاحية المنبهات الدقيقة ممنوحة تلقائياً
 *   على أندرويد 13/14+ (التطبيق خارج متجر Play فلا تخضع لمراجعة سياساته)،
 *   فتعمل setExactAndAllowWhileIdle دائماً + الإعفاء المؤقت لبدء الخدمة الأمامية.
 * - إشعار التنبيه يحترم صلاحية POST_NOTIFICATIONS (أندرويد 13+): إن رُفضت
 *   يُتجاهل الإشعار بهدوء — المنبه نفسه لا يُعطّل، وحين تُمنح الصلاحية تعود
 *   التنبيهات القادمة للعمل فوراً.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_FIRE_EVENT = "com.saidjte.schoolradio.ACTION_FIRE_EVENT"
        const val ACTION_FIRE_REMINDER = "com.saidjte.schoolradio.ACTION_FIRE_REMINDER"
        const val EXTRA_EVENT_ID = "event_id"

        /** قناة إشعارات تنبيه الفقرات المؤرخة (v0.28.0) */
        const val CHANNEL_REMINDERS = "segment_reminders"

        /** إزاحة معرف طلب نافذة الإشعار لضمان التمييز عن منبهات الإطلاق والتنبيه */
        private const val CONTENT_REQUEST_OFFSET = 2_000_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            ACTION_FIRE_EVENT -> {
                val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
                if (eventId <= 0L) return
                val result = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        ScheduledPlaybackLauncher.fire(app, eventId)
                    } finally {
                        result.finish()
                    }
                }
            }
            ACTION_FIRE_REMINDER -> {
                val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
                if (eventId <= 0L) return
                val result = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        postReminderNotification(app, eventId)
                    } finally {
                        result.finish()
                    }
                }
            }
        }
    }

    /**
     * بناء ونشر إشعار التذكير — آمن من كل الحالات:
     * الفقرة حُذفت / أصبحت أسبوعية / بلا تاريخ → لا إشعار.
     */
    private suspend fun postReminderNotification(context: Context, eventId: Long) {
        runCatching {
            ServiceLocator.init(context)
            val event = ServiceLocator.schedules.eventById(eventId) ?: return
            val dateKey = event.specificDate ?: return
            val now = System.currentTimeMillis()

            // صلاحية الإشعارات (أندرويد 13+) — إن رُفضت لا ننهار ولا نُقاطع
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_REMINDERS,
                        "تنبيهات الفقرات المؤرخة",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "تذكير قبل انطلاق فقرة ذات موعد واحد (حفل، احتفال…)"
                    }
                )
            }

            val relative = EventDateLogic.relativeLabelAr(dateKey, now) ?: return
            val dateAr = EventDateLogic.formatDateAr(dateKey) ?: dateKey
            val text = ReminderLogic.notificationTextAr(
                event.title, relative, dateAr, event.startMinutes
            )

            // النقرة تفتح التطبيق (singleTask — لا نسخ مكررة)
            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val contentPi = PendingIntent.getActivity(
                context,
                (eventId + CONTENT_REQUEST_OFFSET).toInt(),
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_stat_automation)
                .setContentTitle(ReminderLogic.NOTIFICATION_TITLE)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(contentPi)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            nm.notify((eventId + CONTENT_REQUEST_OFFSET).toInt(), notification)
        }
    }
}
