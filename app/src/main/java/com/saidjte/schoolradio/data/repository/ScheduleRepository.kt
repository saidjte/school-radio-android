package com.saidjte.schoolradio.data.repository

import com.saidjte.schoolradio.data.local.AppDatabase
import com.saidjte.schoolradio.data.local.entity.ExecutionStatus
import com.saidjte.schoolradio.data.local.entity.RadioScheduleEntity
import com.saidjte.schoolradio.data.local.entity.ScheduleExecutionEntity
import com.saidjte.schoolradio.data.local.entity.ScheduledEventEntity
import com.saidjte.schoolradio.core.util.ScheduleLogic
import kotlinx.coroutines.flow.Flow

/**
 * مستودع الجدولة: جداول متعددة، أحداث بأيام أسبوعية، وسجل تنفيذ يمنع التكرار ويوثق الفائت.
 */
class ScheduleRepository(private val db: AppDatabase) {
    private val dao = db.scheduleDao()

    val schedules: Flow<List<RadioScheduleEntity>> = dao.observeAll()
    val events: Flow<List<ScheduledEventEntity>> = dao.observeEvents()
    val recentExecutions: Flow<List<ScheduleExecutionEntity>> = dao.observeRecentExecutions(50)

    suspend fun createSchedule(name: String): Long = dao.insert(RadioScheduleEntity(name = name))
    suspend fun renameSchedule(schedule: RadioScheduleEntity, newName: String) =
        dao.update(schedule.copy(name = newName))
    suspend fun setEnabled(schedule: RadioScheduleEntity, enabled: Boolean) =
        dao.update(schedule.copy(enabled = enabled))
    suspend fun deleteSchedule(id: Long) = dao.deleteById(id)

    /** v0.12.0: قراءة جدول واحد — يحتاجها مستقبل المنبه لقراءة إعداد الفواصل */
    suspend fun scheduleById(id: Long): RadioScheduleEntity? = dao.scheduleById(id)

    /**
     * v0.12.0: حفظ إعداد الفواصل المجدولة لجدول ما.
     * تعطيل الفواصل لا يتطلب إعادة ضبط المنبهات — الإعداد يُقرأ لحظة نهاية كل فقرة.
     */
    suspend fun setBreakConfig(
        schedule: RadioScheduleEntity,
        enabled: Boolean,
        trackId: Long?,
        categoryId: Long?
    ) = dao.update(
        schedule.copy(
            breakEnabled = enabled,
            breakTrackId = trackId,
            breakCategoryId = categoryId
        )
    )

    suspend fun addEvent(event: ScheduledEventEntity): Long = dao.insertEvent(event)
    suspend fun updateEvent(event: ScheduledEventEntity) = dao.updateEvent(event)
    suspend fun deleteEvent(id: Long) = dao.deleteEventById(id)
    suspend fun eventById(id: Long): ScheduledEventEntity? = dao.eventById(id)
    suspend fun allEvents(): List<ScheduledEventEntity> = dao.allEvents()
    suspend fun enabledSchedules(): List<RadioScheduleEntity> = dao.enabledSchedules()

    /** أحداث اليوم الحالي حسب ساعة الجهاز (الجداول المفعلة فقط) */
    suspend fun todayEvents(nowMillis: Long): List<ScheduledEventEntity> =
        dao.eventsForDayBit(ScheduleLogic.dayBitOf(nowMillis))

    /**
     * تدفق أحداث اليوم للواجهة — يجدد القراءة كل 30 ثانية
     * (ينتقل تلقائياً بين أيام الأسبوع ويكتشف تغيّر اليوم).
     */
    fun todayEventsFlow(): Flow<List<ScheduledEventEntity>> = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(todayEvents(System.currentTimeMillis()))
            kotlinx.coroutines.delay(30_000)
        }
    }

    /**
     * تسجيل محاولة تشغيل — يُستخدم كمصدر الحقيقة لمنع التكرار.
     * IGNORE على القيد (event_id, date_key) يعيد -1 إن سُجل مسبقاً.
     */
    suspend fun markRunning(eventId: Long, dateKey: String, note: String? = null): Boolean {
        val rowId = dao.insertExecution(
            ScheduleExecutionEntity(
                eventId = eventId, dateKey = dateKey,
                status = ExecutionStatus.RUNNING,
                firedAt = System.currentTimeMillis(), note = note
            )
        )
        return rowId != -1L
    }

    /**
     * بدء تنفيذ مع إتاحة إعادة المحاولة بعد فشل سابق:
     * - لا سجل لهذا اليوم → سجّل RUNNING (يسمح بالتشغيل)
     * - سجل FAILED → أعد فتحه RUNNING (إعادة محاولة مسموحة)
     * - غير ذلك (قيد التشغيل/مكتمل/فائت) → امنع التشغيل
     */
    suspend fun beginExecution(eventId: Long, dateKey: String): Boolean {
        val existing = dao.executionOf(eventId, dateKey)
        return when (existing?.status) {
            null -> markRunning(eventId, dateKey)
            ExecutionStatus.FAILED -> {
                dao.updateExecutionStatus(eventId, dateKey, ExecutionStatus.RUNNING, "إعادة محاولة")
                true
            }
            else -> false
        }
    }

    suspend fun markStatus(eventId: Long, dateKey: String, status: String, note: String? = null) =
        dao.updateExecutionStatus(eventId, dateKey, status, note)

    /** هل شُغّل هذا الحدث في يوم معين؟ */
    suspend fun executedOn(eventId: Long, dateKey: String): Boolean =
        dao.executionOf(eventId, dateKey)?.let { it.status != ExecutionStatus.FAILED } ?: false

    /**
     * معالجة الفقرات الفائتة: كل حدث كان موعده اليوم قبل الآن ولم يُسجل تنفيذه → MISSED.
     * تُستدعى بعد إعادة التشغيل أو تغيّر الوقت (BootReceiver).
     *
     * v0.13.0 — [graceMinutes]: نافذة السماح. الفقرة التي بدأت خلال آخر graceMinutes
     * لا تُعلَّم فائتة — خدمة الأتمتة ستطلقها خلال نبضها القادم (يُمرر AutomationLogic.GRACE_MINUTES
     * من الخدمة/الإقلاع؛ الاستدعاءات القديمة تبقى بسلوكها الصارم 0).
     */
    suspend fun markMissed(nowMillis: Long, graceMinutes: Int = 0): Int {
        val dateKey = ScheduleLogic.dateKey(nowMillis)
        val cutoff = ScheduleLogic.minutesOfDay(nowMillis) - graceMinutes
        var missed = 0
        for (event in dao.eventsForDayBit(ScheduleLogic.dayBitOf(nowMillis))) {
            if (event.startMinutes < cutoff && !executedOn(event.id, dateKey)) {
                dao.insertExecution(
                    ScheduleExecutionEntity(
                        eventId = event.id, dateKey = dateKey,
                        status = ExecutionStatus.MISSED,
                        firedAt = nowMillis,
                        note = "فُوّت موعده (${ScheduleLogic.formatMinutes(event.startMinutes)}) قبل الفحص"
                    )
                )
                missed++
            }
        }
        return missed
    }

    /** تنظيف سجلات الفائتة القديمة (يُستدعى دورياً من الواجهة) */
    suspend fun clearMissedLog() = dao.deleteExecutionsByStatus(ExecutionStatus.MISSED)
}
