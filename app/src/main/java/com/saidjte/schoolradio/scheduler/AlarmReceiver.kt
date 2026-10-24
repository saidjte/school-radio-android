package com.saidjte.schoolradio.scheduler

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.saidjte.schoolradio.audio.PlaybackConnection
import com.saidjte.schoolradio.audio.PlaybackService
import com.saidjte.schoolradio.core.util.BreaksLogic
import com.saidjte.schoolradio.core.util.ScheduleLogic
import com.saidjte.schoolradio.data.local.entity.ExecutionStatus
import com.saidjte.schoolradio.data.local.entity.ScheduledEventEntity
import com.saidjte.schoolradio.di.OnAirInfo
import com.saidjte.schoolradio.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * مستقبل المنبه الدقيق: يُطلق الفقرة المجدولة في موعدها.
 *
 * تسلسل الأمان عند الإطلاق:
 *  1) beginExecution (قيد تفرد event+day) — يمنع تشغيل الفقرة نفسها مرتين.
 *  2) تجميع قائمة المقاطع (قائمة تشغيل / مقطع مفرد / **تسجيل من الأرشيف v0.5.0**)
 *     واستبعاد غير المتاح.
 *  3) الإطلاق عبر MediaController على الخدمة (بنفس مشغل الواجهة — لا مشغل ثانٍ).
 *  4) مراقبة نهاية القائمة لتوثيق COMPLETED أو FAILED مع ملاحظة عربية،
 *     وضبط معلومات «على الهواء» (نص + صورة) لشاشة العرض.
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
                fire(app, eventId)
            } finally {
                result.finish()
            }
        }
    }

    private suspend fun fire(context: Context, eventId: Long) {
        ServiceLocator.init(context)
        val locator = ServiceLocator
        val event = locator.schedules.eventById(eventId) ?: return
        val dateKey = ScheduleLogic.dateKey(System.currentTimeMillis())

        // ===== 1) قفل منع التكرار =====
        if (!locator.schedules.beginExecution(eventId, dateKey)) return

        // ===== 2) تجميع قائمة المقاطع =====
        var failNote: String? = null
        val queue: List<PlaybackConnection.QueueItem> = when {
            event.playlistId != null ->
                locator.playlists.resolvedTracks(event.playlistId).map {
                    PlaybackConnection.QueueItem(it.uri, it.title, it.id)
                }
            event.recordingId != null -> {
                // v0.5.0: تسجيل من أرشيف التسجيلات — نتحقق من وجود الملف على القرص أولاً
                val rec = locator.recordingsRepo.byId(event.recordingId)
                when {
                    rec == null -> {
                        failNote = "التسجيل المرتبط بهذه الفقرة لم يعد موجوداً في الأرشيف"
                        emptyList()
                    }
                    !java.io.File(rec.filePath).exists() -> {
                        failNote = "ملف التسجيل «${rec.title}» غير موجود على الجهاز"
                        emptyList()
                    }
                    else -> listOf(PlaybackConnection.QueueItem(rec.filePath, rec.title, rec.id))
                }
            }
            event.singleTrackId != null -> {
                val t = locator.library.trackById(event.singleTrackId)
                if (t != null && t.isAvailable) {
                    listOf(PlaybackConnection.QueueItem(t.uri, t.title, t.id))
                } else {
                    failNote = "المقطع المرتبط بهذه الفقرة غير متاح — أعد ربطه من المكتبة"
                    emptyList()
                }
            }
            else -> emptyList()
        }

        if (queue.isEmpty()) {
            locator.schedules.markStatus(
                eventId, dateKey, ExecutionStatus.FAILED,
                failNote ?: "لا توجد مقاطع متاحة في القائمة المرتبطة بهذه الفقرة"
            )
            return
        }

        // ===== 3) الإطلاق عبر MediaController (الموضوع الرئيسي) =====
        withContext(Dispatchers.Main) {
            // v0.8.0: الحَكَم الموحّد — أوقف أي معاينة تسجيلات قبل إطلاق الفقرة
            // (نفس المشغل الرئيسي، لا تداخل صوتي أبداً)
            ServiceLocator.onScheduledPlaybackStarting()
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener({
                try {
                    val controller = future.get()
                    controller.setMediaItems(queue.map { it.toMediaItem() })
                    controller.repeatMode = Player.REPEAT_MODE_OFF
                    controller.prepare()
                    controller.play()
                    // v0.5.0: ضبط معلومات شاشة العرض على الهواء (صورة/نص الفقرة)
                    ServiceLocator.setOnAir(
                        OnAirInfo(
                            eventId = event.id,
                            eventTitle = event.title,
                            noteText = event.noteText,
                            imagePath = event.imagePath
                        )
                    )
                    watchCompletion(controller, event, dateKey)
                } catch (e: Exception) {
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        ServiceLocator.schedules.markStatus(
                            eventId, dateKey, ExecutionStatus.FAILED,
                            "فشل الاتصال بالمشغل: ${e.message ?: "خطأ غير معروف"}"
                        )
                    }
                }
            }, MoreExecutors.directExecutor())
        }
    }

    /**
     * ===== 4) مراقبة النهاية لتوثيق النتيجة ثم الفاصل الآلي =====
     *
     * إصلاح v0.8.0: كان isLastItem يُحسب مرة واحدة عند التسجيل (قبل أي انتقال بين
     * المقاطع، الفهرس لا يزال 0) فلا تُوثّق أي فقرة متعددة المقاطع كـ COMPLETED أبداً.
     * الصحيح: STATE_ENDED لا يُطلق إلا عند نهاية القائمة *كاملة* — دليل الإكمال بذاته.
     *
     * v0.12.0 — الفواصل المجدولة: بعد توثيق الإكمال يُفحص إعداد فواصل الجدول،
     * فإن وُجد جينغل ووُجدت فقرة تالية اليوم انطلق الفاصل آلياً على نفس المشغل
     * (نمط الأتمتة الإذاعية: فقرة ← جينغة انتقال ← فقرة) ثم تُخلّى شاشة العرض.
     */
    private fun watchCompletion(controller: MediaController, event: ScheduledEventEntity, dateKey: String) {
        controller.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    controller.removeListener(this)
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        // v0.12.0: قرار الفاصل قبل التوثيق ليدخل في الملاحظة
                        val brk = runCatching { resolveScheduledBreak(event) }.getOrNull()
                        ServiceLocator.schedules.markStatus(
                            event.id, dateKey, ExecutionStatus.COMPLETED,
                            if (brk != null) "شُغّلت كاملة • انطلق الفاصل الآلي" else "شُغّلت كاملة"
                        )
                        if (brk == null) {
                            withContext(Dispatchers.Main) { controller.release() }
                            // انتهت الفقرة (ولا فاصل) — شاشة العرض تعود إلى الوضع الخامل
                            ServiceLocator.setOnAir(null)
                        } else {
                            withContext(Dispatchers.Main) { playBreak(controller, event, brk) }
                        }
                    }
                }
            }
        })
    }

    /**
     * v0.12.0 — قراءة إعداد الفواصل واختيار جينغل الفاصل إن وُجد.
     * يعود null في كل حالات «لا فاصل»: تعطيل الخاصية، لا فقرة تالية اليوم،
     * أو تعذر توفير جينغل (مقطع محذوف/تصنيف فارغ) — فيبقى السلوك القديم بلا أي عطل.
     */
    private suspend fun resolveScheduledBreak(event: ScheduledEventEntity): PlaybackConnection.QueueItem? {
        val locator = ServiceLocator
        val schedule = locator.schedules.scheduleById(event.scheduleId) ?: return null
        if (!schedule.breakEnabled) return null
        val now = System.currentTimeMillis()
        // فاصل «بين» الفقرات فقط: يجب وجود فقرة لاحقة اليوم لنفس الجدول
        val hasMore = BreaksLogic.hasNextSegmentToday(
            locator.schedules.allEvents()
                .filter { it.scheduleId == schedule.id }
                .map { BreaksLogic.EventTiming(it.id, it.startMinutes, it.weekDaysMask) },
            currentEventId = event.id,
            dayBit = ScheduleLogic.dayBitOf(now),
            minutesNow = ScheduleLogic.minutesOfDay(now)
        )
        if (!hasMore) return null
        // تجميع تجمع الجينغلات: مقطع محدد أو تصنيف كامل (المتاح فقط)
        val pool: List<BreaksLogic.PlaySpec> = when {
            schedule.breakTrackId != null ->
                locator.library.trackById(schedule.breakTrackId)
                    ?.takeIf { it.isAvailable }
                    ?.let { listOf(BreaksLogic.PlaySpec(it.uri, it.title, it.id)) }
                    ?: emptyList()
            schedule.breakCategoryId != null ->
                locator.library.tracks.first()
                    .filter { it.categoryId == schedule.breakCategoryId && it.isAvailable }
                    .map { BreaksLogic.PlaySpec(it.uri, it.title, it.id) }
            else -> emptyList()
        }
        val spec = BreaksLogic.resolveBreakSpec(
            BreaksLogic.BreakConfig(
                enabled = true,
                trackId = schedule.breakTrackId,
                categoryId = schedule.breakCategoryId
            ),
            pool
        ) ?: return null
        return PlaybackConnection.QueueItem(spec.uri, spec.title, spec.trackId)
    }

    /**
     * v0.12.0 — تشغيل الفاصل على نفس المشغل (يجب استدعاؤه من الخيط الرئيسي):
     * تُبقى شاشة العرض حية بعبارة «فاصل بين الفقرات» واسم الجينغل،
     * وبعد انتهائه يُحرّر المتحكم وتعود الشاشة إلى ساعة الخمول.
     */
    private fun playBreak(
        controller: MediaController,
        event: ScheduledEventEntity,
        brk: PlaybackConnection.QueueItem
    ) {
        controller.setMediaItems(listOf(brk.toMediaItem()))
        controller.repeatMode = Player.REPEAT_MODE_OFF
        controller.prepare()
        controller.play()
        ServiceLocator.setOnAir(
            OnAirInfo(
                eventId = event.id,
                eventTitle = "فاصل بين الفقرات",
                noteText = brk.title,
                imagePath = null
            )
        )
        controller.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    controller.removeListener(this)
                    controller.release()
                    ServiceLocator.setOnAir(null)
                }
            }
        })
    }
}
