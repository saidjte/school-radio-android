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
            event.playlistId != null -> {
                // v0.35.0: قائمة سريعة — حلّ فوري للأول فقط (صحة انطلاق الفقرة المجدولة)
                // والبقية تجديد خلفي حي بعد الإطلاق مباشرة
                val tracks = locator.playlists.resolvedTracks(event.playlistId)
                val plan = locator.remote.buildQueuePlan(tracks, 0)
                locator.remote.refreshPendingRemoteAsync(plan.pendingTracks)
                tracks.mapIndexed { i, t ->
                    PlaybackConnection.QueueItem(
                        plan.uris[i], t.title, t.id,
                        false, t.trimStartMs, t.trimEndMs, t.trimManual
                    )
                }
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
                    else ->
                        // v0.20.0: القص اليدوي للتسجيلات يُورَّث للفقرة المجدولة —
                        // ينطلق من نقطة البداية ويتوقف عند النهاية مثل أي مقطع مكتبة
                        listOf(
                            PlaybackConnection.QueueItem(
                                rec.filePath, rec.title, rec.id,
                                false, rec.trimStartMs, rec.trimEndMs, rec.trimManual
                            )
                        )
                }
            }
            event.singleTrackId != null -> {
                val t = locator.library.trackById(event.singleTrackId)
                if (t != null && t.isAvailable) {
                    listOf(
                        // v0.23.0: مقطع بث بعيد — تدفق طازج قبل الإطلاق
                        PlaybackConnection.QueueItem(
                            locator.remote.playableUriFor(t), t.title, t.id,
                            false, t.trimStartMs, t.trimEndMs, t.trimManual
                        )
                    )
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
            // v0.23.0: حماية صريحة — وضع الفواصل (breaks:) لقوائم يدوية لا يجوز أن
            // يبقى نشطاً فوق الفقرات المجدولة (إلا تعطلت نهايتها على محرك الحدود)
            ServiceLocator.breaksModeActive.value = false
            ServiceLocator.breakTransitionMs.value = 0L
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
                            // v0.23.0: تفريغ قائمة المشغل المشترك أيضاً — كانت تبقى ملتصقة
                            // بعد نهاية الفقرة فتمنع شاشة العرض من العودة للساعة الخاملة
                            // (stopAll يمس MediaController — على الموضوع الرئيسي حصراً)
                            withContext(Dispatchers.Main) {
                                controller.release()
                                ServiceLocator.playback.stopAll()
                            }
                        } else {
                            // v0.15.0: مهلة الانتقال — صمت مقصود بعد الفقرة قبل الجينل
                            // v0.23.0: شاشة العرض تعدّ تنازلياً حياً للانطلاق عبر
                            // breakStartsAtMs (لوحة «انطلاق الفاصل بعد …»)
                            if (brk.transitionSec > 0) {
                                withContext(Dispatchers.Main) {
                                    ServiceLocator.setOnAir(
                                        OnAirInfo(
                                            eventId = event.id,
                                            eventTitle = "فاصل بين الفقرات",
                                            noteText = "انطلاق الجينل بعد ${brk.transitionSec} ثانية…",
                                            imagePath = null,
                                            isBreak = true,
                                            breakStartsAtMs = System.currentTimeMillis() + brk.transitionMs,
                                            breakTitle = brk.item.title
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
                    ?.let {
                        listOf(
                            BreaksLogic.PlaySpec(
                                it.uri, it.title, it.id,
                                it.trimStartMs, it.trimEndMs, it.trimManual
                            )
                        )
                    }
                    ?: emptyList()
            schedule.breakCategoryId != null ->
                locator.library.tracks.first()
                    .filter { it.categoryId == schedule.breakCategoryId && it.isAvailable }
                    .map {
                        BreaksLogic.PlaySpec(
                            it.uri, it.title, it.id,
                            it.trimStartMs, it.trimEndMs, it.trimManual
                        )
                    }
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
        // v0.23.0: العنصر موسوم isBreak — شاشة العرض تعرض عدّه كباقي الجينلات
        return BreakPlan(
            PlaybackConnection.QueueItem(
                spec.uri, spec.title, spec.trackId, true,
                spec.trimStartMs, spec.trimEndMs, spec.manualTrim
            ),
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
        // v0.23.0: لوحة الفاصل الحية — العدّ من متبقّي المشغل (isBreak + breakTitle)
        ServiceLocator.setOnAir(
            OnAirInfo(
                eventId = event.id,
                eventTitle = "فاصل بين الفقرات",
                noteText = brk.title,
                imagePath = null,
                isBreak = true,
                breakTitle = brk.title
            )
        )
        controller.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    controller.removeListener(this)
                    controller.release()
                    // v0.23.0: تفريغ المشغل المشترك — العدّ الزائف «00:00» لا يبقى على الشاشة
                    ServiceLocator.playback.stopAll()
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
