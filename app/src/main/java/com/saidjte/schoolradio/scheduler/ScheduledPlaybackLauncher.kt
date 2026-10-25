package com.saidjte.schoolradio.scheduler

import android.content.ComponentName
import android.content.Context
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
 * v0.13.0 — مشغّل الفقرات المجدولة الموحّد.
 *
 * كان منطق إطلاق الفقرة حبيس AlarmReceiver؛ صار كائناً مشتركاً يُستدعى من
 * مسارين مكافئين تماماً:
 *  1) [AlarmReceiver] — المنبه الدقيق عند الثانية المحددة (المسار الأمثل).
 *  2) [AutomationService] — الخدمة الحارسة: تطلق الفقرة داخل نافذة السماح
 *     إن انقلب المنبه (أجهزة تقتل الخلفية) أو فاتت اللحظة بين نبضتين.
 *
 * تسلسل الأمان عند الإطلاق (مطابق للمسار القديم):
 *  1) beginExecution (قيد تفرد event+day) — يمنع تشغيل الفقرة مرتين في اليوم.
 *  2) تجميع قائمة المقاطع (قائمة تشغيل / مقطع مفرد / تسجيل من الأرشيف) واستبعاد غير المتاح.
 *  3) الإطلاق عبر MediaController على الخدمة (بنفس مشغل الواجهة — لا مشغل ثانٍ).
 *  4) مراقبة نهاية القائمة لتوثيق COMPLETED/FAILED، وضبط «على الهواء»
 *     (نص + صورة + **سكربت المذيع المرتبط v0.13.0**) لشاشة العرض.
 *  5) v0.12.0: فاصل آلي (جينغل) بعد الفقرة عند تفعيل الفواصل ووجود فقرة تالية.
 */
object ScheduledPlaybackLauncher {

    /**
     * إطلاق فقرة مجدولة بمعرفها. يعود true إن انطلقت فعلاً (أو أصبحت قيد التشغيل)
     * وfalse إن رُفضت (سبق تنفيذها اليوم) أو فشل تجميع محتواها (يُوثق FAILED).
     * آمنة للاستدعاء من أي خيط — تبني المتحكم على الخيط الرئيسي بنفسها.
     */
    suspend fun fire(context: Context, eventId: Long): Boolean {
        ServiceLocator.init(context)
        val locator = ServiceLocator
        val event = locator.schedules.eventById(eventId) ?: return false
        val dateKey = ScheduleLogic.dateKey(System.currentTimeMillis())

        // ===== 1) قفل منع التكرار =====
        if (!locator.schedules.beginExecution(eventId, dateKey)) return false

        // ===== 2) تجميع قائمة المقاطع =====
        var failNote: String? = null
        val queue: List<PlaybackConnection.QueueItem> = when {
            event.playlistId != null ->
                locator.playlists.resolvedTracks(event.playlistId).map {
                    PlaybackConnection.QueueItem.of(it)
                }
            event.recordingId != null -> {
                // تسجيل من أرشيف التسجيلات — نتحقق من وجود الملف على القرص أولاً
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
                    listOf(PlaybackConnection.QueueItem.of(t))
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
            return false
        }

        // ===== 3) الإطلاق عبر MediaController (الموضوع الرئيسي) =====
        withContext(Dispatchers.Main) {
            // الحَكَم الموحّد — أوقف أي معاينة تسجيلات قبل إطلاق الفقرة
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
                    // ضبط معلومات شاشة العرض على الهواء (صورة/نص/سكربت المذيع)
                    ServiceLocator.setOnAir(
                        OnAirInfo(
                            eventId = event.id,
                            eventTitle = event.title,
                            noteText = event.noteText,
                            imagePath = event.imagePath,
                            scriptId = event.scriptId,
                            scriptTitle = event.scriptId?.let { sid ->
                                locator.scripts.scripts.value.firstOrNull { it.id == sid }?.title
                            }
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
        return true
    }

    /**
     * ===== 4) مراقبة النهاية لتوثيق النتيجة ثم الفاصل الآلي =====
     *
     * STATE_ENDED لا يُطلق إلا عند نهاية القائمة *كاملة* — دليل الإكمال بذاته
     * (إصلاح v0.8.0 المؤسس). وبعد التوثيق يُفحص إعداد فواصل الجدول (v0.12.0).
     */
    private fun watchCompletion(controller: MediaController, event: ScheduledEventEntity, dateKey: String) {
        controller.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    controller.removeListener(this)
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        // قرار الفاصل قبل التوثيق ليدخل في الملاحظة
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
                            // v0.15.0: مهلة الانتقال — صمت مقصود بعد الفقرة قبل الجينل
                            // (قابل للضبط من محرر الفواصل) وشاشة العرض تعدّ للمذيع والمشاهدين
                            if (brk.transitionSec > 0) {
                                withContext(Dispatchers.Main) {
                                    ServiceLocator.setOnAir(
                                        OnAirInfo(
                                            eventId = event.id,
                                            eventTitle = "فاصل بين الفقرات",
                                            noteText = "انطلاق الجينل بعد ${brk.transitionSec} ثانية…",
                                            imagePath = null
                                        )
                                    )
                                }
                                kotlinx.coroutines.delay(brk.transitionMs)
                            }
                            withContext(Dispatchers.Main) { playBreak(controller, event, brk.item) }
                        }
                    }
                }
            }
        })
    }

    /**
     * قراءة إعداد الفواصل واختيار جينغل الفاصل إن وُجد — مع مهلة الانتقال المضبوطة (v0.15.0).
     * يعود null في كل حالات «لا فاصل»: تعطيل الخاصية، لا فقرة تالية اليوم،
     * أو تعذر توفير جينغل (مقطع محذوف/تصنيف فارغ) — فيبقى السلوك القديم بلا أي عطل.
     */
    private suspend fun resolveScheduledBreak(event: ScheduledEventEntity): BreakPlan? {
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
                    ?.let { listOf(BreaksLogic.PlaySpec(it.uri, it.title, it.id, it.trimStartMs)) }
                    ?: emptyList()
            schedule.breakCategoryId != null ->
                locator.library.tracks.first()
                    .filter { it.categoryId == schedule.breakCategoryId && it.isAvailable }
                    .map { BreaksLogic.PlaySpec(it.uri, it.title, it.id, it.trimStartMs) }
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
        // v0.15.0: مهلة الانتقال مضبوطة على الجدول — تُطبق قبل انطلاق الجينل
        return BreakPlan(
            PlaybackConnection.QueueItem(spec.uri, spec.title, spec.trackId, false, spec.trimStartMs),
            schedule.breakTransitionSec
        )
    }

    /**
     * تشغيل الفاصل على نفس المشغل (يجب استدعاؤه من الخيط الرئيسي):
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

/** v0.15.0: خطة الفاصل الآلي — الجينل + مهلة الانتقال قبل انطلاقه */
private data class BreakPlan(
    val item: PlaybackConnection.QueueItem,
    val transitionSec: Int
) {
    val transitionMs: Long get() = transitionSec.coerceIn(0, 300) * 1000L
}
