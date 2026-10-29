package com.saidjte.schoolradio.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.saidjte.schoolradio.MainActivity
import com.saidjte.schoolradio.R
import com.saidjte.schoolradio.core.util.CrashGuard
import com.saidjte.schoolradio.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * خدمة أمامية لتسجيل البث الكامل (v0.49.0) — نوعها mediaProjection (أندرويد 10+).
 *
 * عقد أندرويد الصارم (14+ تحديداً): يجب استدعاء startForeground بنوع
 * FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION **قبل** getMediaProjection وإلا رمى
 * النظام SecurityException — الترتيب هنا مقدّس: onCreate → startForeground →
 * onStartCommand → getMediaProjection على خيط IO.
 *
 * دورة الحياة: تبدأ بموافقة المستخدم الصريحة (حوار النظام)، وتنتهي عندما
 * يصبح التسجيل غير نشط (إيقاف يدوي أو انتهاء البث — النقطة نفسها في
 * RecordingManager تنقذ الملف) أو عند سحب المستخدم الإذن من شريط النظام
 * (projection callback → stopRecording → الحفظ ثم stopSelf).
 */
class LiveCaptureService : Service() {

    companion object {
        const val CHANNEL_ID = "broadcast_capture"
        const val NOTIFICATION_ID = 4202
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        /** بدء الخدمة بنتيجة موافقة المستخدم — يُستدعى من ردّ النشاط فقط */
        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val i = Intent(context, LiveCaptureService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData)
            ContextCompat.startForegroundService(context, i)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var projection: MediaProjection? = null
    private var started = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // أندرويد 14+ يجبر على النوع الصريح — والثابت متاح في الـ SDK المترجم دائماً
        startForeground(
            NOTIFICATION_ID, buildNotification("يجري تسجيل البث الكامل — الميكروفون وكل ما يُشغَّل"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (started) return START_NOT_STICKY
        started = true
        if (Build.VERSION.SDK_INT < 29 || intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val code = intent.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE)
        val data: Intent? = intent.getParcelableExtra(EXTRA_RESULT_DATA)
        beginCapture(code, data)
        return START_NOT_STICKY
    }

    /** فتح الالتقاط على خيط IO — الملف نفسه يديره RecordingManager بلا ازدواج */
    private fun beginCapture(resultCode: Int, resultData: Intent?) {
        scope.launch(Dispatchers.IO) {
            try {
                val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val p = mgr.getMediaProjection(resultCode, resultData ?: return@launch shutdown())
                projection = p

                // سحب المستخدم الإذن من شريط النظام (أو قتل العرض) = إيقاف حفظ شريف
                p.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        ServiceLocator.recordings.stopRecording()
                    }
                }, Handler(Looper.getMainLooper()))

                val err = ServiceLocator.recordings.startMixRecording(ProjectionCaptureSource(p))
                if (err != null) {
                    // فشل بدء التسجيل — لا بقاء بلا وظيفة: تنظيف فوري وإفصاح
                    ServiceLocator.setCaptureMessage(err)
                    shutdown()
                } else {
                    watchUntilStopped()
                }
            } catch (t: Throwable) {
                CrashGuard.record("LiveCaptureService", t)
                ServiceLocator.setCaptureMessage("تعذر بدء تسجيل البث الكامل على هذا الجهاز")
                shutdown()
            }
        }
    }

    /** الخدمة حارس وجود للتسجيل: أول ثبات على «غير يسجّل» = انتهى الدور */
    private fun watchUntilStopped() {
        scope.launch {
            ServiceLocator.recordings.isRecording.first { !it }
            shutdown()
        }
    }

    private fun shutdown() {
        runCatching { projection?.stop() }
        projection = null
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ===== الإشعار =====

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, "تسجيل البث الكامل",
            NotificationManager.IMPORTANCE_LOW // حضور هادئ — لا يقاطع البث
        ).apply {
            description = "مؤشر مستمر أثناء تسجيل البث المباشر كاملاً"
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_automation)
            .setContentTitle("إذاعة مدرسية — تسجيل البث")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }
}
