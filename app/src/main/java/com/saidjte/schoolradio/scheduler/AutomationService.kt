package com.saidjte.schoolradio.scheduler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import kotlin.coroutines.coroutineContext
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.saidjte.schoolradio.MainActivity
import com.saidjte.schoolradio.R
import com.saidjte.schoolradio.core.util.AutomationLogic
import com.saidjte.schoolradio.core.util.ScheduleLogic
import com.saidjte.schoolradio.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * v0.13.0 — خدمة الأتمتة الحارسة: ضمانة أن الفقرات المجدولة تنطلق أوتوماتيكياً
 * في مواعيدها مهما فعل النظام أو المصنّع.
 *
 * لماذا هي ضرورية؟ مسار المنبه الدقيق (AlarmManager) يعمل مثالياً على أجهزة
 * نظيفة، لكن على أجهزة كثيرة (Tecno/Infinix/Xiaomi/Samsung في أوضاع توفير
 * البطارية) تُبتلع المنبهات أو تُقتل العملية بين المنبهات. الحل الاحترافي
 * المعتمد في أنظمة الأتمتة الإذاعية: عملية بث حية دائماً.
 *
 * ماذا تفعل (نبض كل 20 ثانية):
 *  1) تفحص الفقرات المستحقة (وقتها الآن ضمن نافذة سماح دقيقتين ولم تُنفَّذ اليوم)
 *     وتطلقها عبر [ScheduledPlaybackLauncher] — نفس المشغل ونفس قفل منع التكرار،
 *     فلا ازدواجية مع مسار المنبه الدقيق أبداً.
 *  2) تحدّث إشعاراً هادئاً دائماً: «القادمة: طابور الصباح — بعد 12 دقيقة».
 *  3) تعيد ضبط منبهات النظام كل ساعة وتغيّر اليوم (احتياط يضمن حداثتها)،
 *     وتعلم الفقرات الفائتة (خارج نافذة السماح) بسجل MISSED.
 *
 * بدءها: عند فتح التطبيق (إن وُجدت جداول مفعلة) وعند إقلاع الجهاز. START_STICKY
 * يعيدها النظام إن قتلها ضغط الذاكرة. إشعارها بلا صوت (IMPORTANCE_LOW) وزر
 * إيقاف صريح فيها — من يريد إسكات الأتمتة يعرف أين.
 */
class AutomationService : Service() {

    companion object {
        const val CHANNEL_ID = "automation_service"
        const val NOTIFICATION_ID = 4201
        const val ACTION_STOP = "com.saidjte.schoolradio.ACTION_STOP_AUTOMATION"

        /** بدء الخدمة الحارسة — آمن للاستدعاء المتكرر (startForegroundService متسامح) */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context, Intent(context, AutomationService::class.java)
                )
            } catch (_: Exception) {
                // بدء خدمة أمامية مرفوض في حالات نادرة (قيود شديدة) — مسار
                // المنبه الدقيق يبقى يعمل وحده فلا شيء ينكسر
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AutomationService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null

    private var lastDayKey: String? = null
    private var lastRearm = 0L

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // يجب استدعاء startForeground خلال 5 ثوانٍ من startForegroundService —
        // نبدأ بإشعار محايد يحدثه أول نبض خلال ثوانٍ
        startForeground(NOTIFICATION_ID, buildNotification("الأتمتة نشطة • تجهيز الجدولة…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (ticker?.isActive != true) {
            ticker = scope.launch { tickLoop() }
        }
        // STICKY: إن قتل النظام الخدمة ضغطاً يعيد تشغيلها — الأتمتة لا تنكسر
        return START_STICKY
    }

    override fun onDestroy() {
        ticker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** حلقة النبض الرئيسية — كل 20 ثانية فحص كامل خفيف (استعلامات مفهرسة صغيرة) */
    private suspend fun tickLoop() {
        // مهلة قصيرة قبل أول نبض: ServiceLocator/قاعدة البيانات قد تكون تُهيأ
        delay(3_000)
        while (coroutineContext.isActive) {
            try {
                tick()
            } catch (_: Exception) {
                // أي مفاجأة (قفل DB لحظي…) لا تُميت الخدمة — النبض التالي يعيد المحاولة
            }
            delay(AutomationLogic.TICK_MILLIS)
        }
    }

    private suspend fun tick() {
        val locator = ServiceLocator
        val now = System.currentTimeMillis()
        val dateKey = ScheduleLogic.dateKey(now)

        val enabledIds = locator.schedules.enabledSchedules().map { it.id }.toSet()
        if (enabledIds.isEmpty()) {
            // لا جداول مفعلة: نبض بلا عمل — الخدمة تبقى حية (بدء الجداول الأولى
            // من التطبيق يستدعي start() فتلتقطها الخدمة الجارية تلقائياً)
            updateNotification("الأتمتة نشطة • لا جداول مفعلة")
            lastDayKey = dateKey
            return
        }
        val timings = locator.schedules.allEvents()
            .filter { it.scheduleId in enabledIds }
            .map {
                AutomationLogic.EventTiming(
                    eventId = it.id, title = it.title,
                    startMinutes = it.startMinutes, weekDaysMask = it.weekDaysMask
                )
            }

        // ===== 1) إطلاق المستحق الآن (نافذة السماح + منع التكرار في القاعدة) =====
        val executedToday: suspend (Long) -> Boolean = { id ->
            locator.schedules.executedOn(id, dateKey)
        }
        val due = AutomationLogic.dueEventIds(timings, now, executedToday)
        due.forEach { id ->
            runCatching { ScheduledPlaybackLauncher.fire(applicationContext, id) }
        }

        // ===== 2) إعادة ضبط المنبهات: تغيّر يوم أو كل ساعة (احتياط الحداثة) =====
        if (dateKey != lastDayKey || now - lastRearm > AutomationLogic.REARM_PERIOD_MILLIS) {
            locator.rearmScheduleAlarms()
            // فائت = موعده تجاوز نافذة السماح ولم يُنفَّذ (لا تلمس ما داخل النافذة)
            locator.schedules.markMissed(now, AutomationLogic.GRACE_MINUTES)
            lastDayKey = dateKey
            lastRearm = now
        }

        // ===== 3) الإشعار الحي: الفقرة القادمة وعدّادها =====
        val next = AutomationLogic.nextUpToday(timings, now, executedToday)
        updateNotification(
            AutomationLogic.notificationText(next, ScheduleLogic.minutesOfDay(now))
        )
    }

    // ===== الإشعار =====

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, "أتمتة الجدولة الإذاعية",
            NotificationManager.IMPORTANCE_LOW // بلا صوت ولا اهتزاز — حضور هادئ
        ).apply {
            description = "إبقاء الجدولة الأوتوماتيكية حية وإظهار الفقرة القادمة"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIt = PendingIntent.getService(
            this, 1,
            Intent(this, AutomationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_automation)
            .setContentTitle("إذاعة مدرسية — الأتمتة")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "إيقاف الأتمتة", stopIt)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
