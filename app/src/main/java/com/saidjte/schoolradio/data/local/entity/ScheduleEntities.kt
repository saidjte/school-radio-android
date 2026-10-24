package com.saidjte.schoolradio.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * جدول إذاعي مسمّى (مثل «طابور الصباح» و«الاستراحة»).
 * كل جدول يحتوي أحداثاً مجدولة (ScheduledEvent) تُطلق في أوقاتها.
 */
@Entity(tableName = "radio_schedules")
data class RadioScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** تفعيل الجدول — الجدول المعطّل لا تُضبط منبهاته */
    val enabled: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/**
 * فقرة مجدولة داخل جدول: تُشغّل قائمة تشغيل أو مقطعاً من المكتبة أو **تسجيلاً من الأرشيف**
 * في وقت محدد في أيام أسبوع محددة (بتشفير bitmask: البت i يعني اليوم (i+1)، الاثنين=1 ... الأحد=64).
 *
 * v0.5.0: أضيفت حقول العرض البصري (note_text + image_path) لتظهر على شاشة «على الهواء».
 */
@Entity(
    tableName = "scheduled_events",
    foreignKeys = [
        ForeignKey(
            entity = RadioScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["schedule_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recording_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("schedule_id"), Index("playlist_id"), Index("start_minutes"), Index("recording_id")]
)
data class ScheduledEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "schedule_id") val scheduleId: Long,
    /** دقائق من منتصف الليل — 09:45 = 585 (تجنب مشاكل المناطق الزمنية بالتخزين النسبي) */
    @ColumnInfo(name = "start_minutes") val startMinutes: Int,
    /** أيام التشغيل: bitmask — الاثنين=1، الثلاثاء=2، ... الأحد=64 */
    @ColumnInfo(name = "week_days_mask") val weekDaysMask: Int,
    val title: String,
    /** قائمة التشغيل المرتبطة (أو null إن أُسند مقطع/تسجيل واحد) */
    @ColumnInfo(name = "playlist_id") val playlistId: Long? = null,
    /** مقطع واحد من المكتبة عند عدم وجود قائمة */
    @ColumnInfo(name = "single_track_id") val singleTrackId: Long? = null,
    /** v0.5.0: تسجيل واحد من أرشيف التسجيلات (يُفضّل على القائمة عند فقرة مُسجّلة كاملة) */
    @ColumnInfo(name = "recording_id") val recordingId: Long? = null,
    /** v0.5.0: نص إرشادي/إعلاني يُعرض على شاشة «على الهواء» أثناء تشغيل الفقرة */
    @ColumnInfo(name = "note_text") val noteText: String? = null,
    /** v0.5.0: مسار مطلق لصورة الفقرة داخل ملفات التطبيق (filesDir/event_images) */
    @ColumnInfo(name = "image_path") val imagePath: String? = null
) {
    /** هل الحدث مجدول في اليوم المحدد؟ (dayOfWeek بمعيار ISO: الاثنين=1..الأحد=7) */
    fun runsOn(dayOfWeekIso: Int): Boolean {
        // ISO: الاثنين=1..الأحد=7  →  البت: الاثنين=1<<0
        val bit = 1 shl (dayOfWeekIso - 1)
        return (weekDaysMask and bit) != 0
    }

    companion object {
        fun maskOf(vararg isoDays: Int): Int = isoDays.fold(0) { acc, d -> acc or (1 shl (d - 1)) }
    }
}

/**
 * سجل تنفيذ الفقرة — يمنع التكرار ويوثق النتيجة.
 * مفتاح التفرد المنطقي: (event_id + date_key) حيث date_key = 2026-10-23
 */
@Entity(
    tableName = "schedule_executions",
    foreignKeys = [
        ForeignKey(
            entity = ScheduledEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("event_id"),
        Index(value = ["event_id", "date_key"], unique = true)
    ]
)
data class ScheduleExecutionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "event_id") val eventId: Long,
    /** مفتاح اليوم بصيغة yyyy-MM-dd — يمنع إعادة التشغيل في اليوم نفسه */
    @ColumnInfo(name = "date_key") val dateKey: String,
    /** حالة التنفيذ: SCHEDULED / RUNNING / COMPLETED / MISSED / FAILED */
    val status: String,
    /** وقت الإطلاق الفعلي بالميلي ثانية */
    @ColumnInfo(name = "fired_at") val firedAt: Long = System.currentTimeMillis(),
    val note: String? = null
)

/** حالات تنفيذ الفقرة — أسماء ثابتة تُخزن نصياً في قاعدة البيانات */
object ExecutionStatus {
    const val SCHEDULED = "SCHEDULED"
    const val RUNNING = "RUNNING"
    const val COMPLETED = "COMPLETED"
    const val MISSED = "MISSED"
    const val FAILED = "FAILED"

    /** التسمية العربية للعرض في الواجهة */
    fun labelAr(status: String): String = when (status) {
        SCHEDULED -> "مجدولة"
        RUNNING -> "قيد التشغيل"
        COMPLETED -> "مكتملة"
        MISSED -> "فائتة"
        FAILED -> "فشلت"
        else -> status
    }
}
