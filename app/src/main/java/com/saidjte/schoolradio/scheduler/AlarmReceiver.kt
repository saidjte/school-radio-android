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
import com.saidjte.schoolradio.core.util.ScheduleLogic
import com.saidjte.schoolradio.data.local.entity.ExecutionStatus
import com.saidjte.schoolradio.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * مستقبل المنبه الدقيق: يُطلق الفقرة المجدولة في موعدها.
 *
 * تسلسل الأمان عند الإطلاق:
 *  1) beginExecution (قيد تفرد event+day) — يمنع تشغيل الفقرة نفسها مرتين.
 *  2) تجميع قائمة المقاطع (قائمة تشغيل أو مقطع مفرد) واستبعاد غير المتاح.
 *  3) الإطلاق عبر MediaController على الخدمة (بنفس مشغل الواجهة — لا مشغل ثانٍ).
 *  4) مراقبة نهاية القائمة لتوثيق COMPLETED أو FAILED مع ملاحظة عربية.
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
        val queue: List<PlaybackConnection.QueueItem> = when {
            event.playlistId != null ->
                locator.playlists.resolvedTracks(event.playlistId).map {
                    PlaybackConnection.QueueItem(it.uri, it.title, it.id)
                }
            event.singleTrackId != null -> {
                val t = locator.library.trackById(event.singleTrackId)
                if (t != null && t.isAvailable) {
                    listOf(PlaybackConnection.QueueItem(t.uri, t.title, t.id))
                } else emptyList()
            }
            else -> emptyList()
        }

        if (queue.isEmpty()) {
            locator.schedules.markStatus(
                eventId, dateKey, ExecutionStatus.FAILED,
                "لا توجد مقاطع متاحة في القائمة المرتبطة بهذه الفقرة"
            )
            return
        }

        // ===== 3) الإطلاق عبر MediaController (الموضوع الرئيسي) =====
        withContext(Dispatchers.Main) {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener({
                try {
                    val controller = future.get()
                    controller.setMediaItems(queue.map { it.toMediaItem() })
                    controller.repeatMode = Player.REPEAT_MODE_OFF
                    controller.prepare()
                    controller.play()
                    watchCompletion(controller, eventId, dateKey)
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

    /** ===== 4) مراقبة النهاية لتوثيق النتيجة ثم تحرير المتحكم ===== */
    private fun watchCompletion(controller: MediaController, eventId: Long, dateKey: String) {
        val lastQueueIndex = controller.mediaItemCount - 1
        val isLastItem = controller.currentMediaItemIndex >= lastQueueIndex
        controller.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && isLastItem) {
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        ServiceLocator.schedules.markStatus(
                            eventId, dateKey, ExecutionStatus.COMPLETED, "شُغّلت كاملة"
                        )
                    }
                    controller.removeListener(this)
                    controller.release()
                }
            }
        })
    }
}
