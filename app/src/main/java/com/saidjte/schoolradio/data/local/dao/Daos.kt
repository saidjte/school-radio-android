package com.saidjte.schoolradio.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.saidjte.schoolradio.data.local.entity.AudioTrackEntity
import com.saidjte.schoolradio.data.local.entity.CategoryEntity
import com.saidjte.schoolradio.data.local.entity.PlaylistEntity
import com.saidjte.schoolradio.data.local.entity.PlaylistItemEntity
import com.saidjte.schoolradio.data.local.entity.RadioScheduleEntity
import com.saidjte.schoolradio.data.local.entity.ScheduleExecutionEntity
import com.saidjte.schoolradio.data.local.entity.ScheduledEventEntity
import kotlinx.coroutines.flow.Flow

// =====================================================================
// المكتبة الصوتية
// =====================================================================

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CategoryEntity): Long

    @Query("SELECT * FROM categories ORDER BY sort_order, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun byId(id: Long): CategoryEntity?

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id AND is_built_in = 0")
    suspend fun deleteIfCustom(id: Long)

    /** عدد المقاطع في كل تصنيف — لإدارة التصنيفات (v0.10.0) */
    @Query(
        """
        SELECT c.*, (SELECT COUNT(*) FROM audio_tracks t WHERE t.category_id = c.id)
            AS track_count
        FROM categories c ORDER BY c.sort_order, c.name
        """
    )
    fun observeAllWithCounts(): Flow<List<CategoryWithCount>>
}

/**
 * v0.10.0: تصنيف مع عدد مقاطعه — لعرض حوار إدارة التصنيفات بشكل مفهوم
 * (الاسم، عدد المقاطع، وهل هو افتراضي لا يُحذف).
 * data class عادي (ليس كيان Room) — Room يصفّ الأعمدة عليه في الاستعلام السابق.
 */
data class CategoryWithCount(
    val id: Long,
    val name: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "is_built_in") val isBuiltIn: Boolean = false,
    @ColumnInfo(name = "track_count") val trackCount: Int = 0
)

@Dao
interface AudioTrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(track: AudioTrackEntity): Long

    @Update
    suspend fun update(track: AudioTrackEntity)

    @Query("SELECT * FROM audio_tracks ORDER BY added_at DESC")
    fun observeAll(): Flow<List<AudioTrackEntity>>

    @Query("SELECT * FROM audio_tracks WHERE title LIKE '%' || :query || '%' ORDER BY title")
    fun search(query: String): Flow<List<AudioTrackEntity>>

    @Query("SELECT * FROM audio_tracks WHERE id = :id")
    suspend fun byId(id: Long): AudioTrackEntity?

    @Query("SELECT * FROM audio_tracks WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<AudioTrackEntity>

    @Query("UPDATE audio_tracks SET is_available = :available WHERE id = :id")
    suspend fun setAvailability(id: Long, available: Boolean)

    @Query("SELECT * FROM audio_tracks WHERE is_available = 0")
    suspend fun unavailable(): List<AudioTrackEntity>

    @Delete
    suspend fun delete(track: AudioTrackEntity)

    @Query("SELECT COUNT(*) FROM audio_tracks")
    suspend fun count(): Int
}

// =====================================================================
// قوائم التشغيل
// =====================================================================

@Dao
interface PlaylistDao {
    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists ORDER BY name")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun byId(id: Long): PlaylistEntity?

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: PlaylistItemEntity): Long

    @Query("SELECT * FROM playlist_items WHERE playlist_id = :playlistId ORDER BY position")
    suspend fun items(playlistId: Long): List<PlaylistItemEntity>

    @Query("SELECT * FROM playlist_items WHERE playlist_id = :playlistId ORDER BY position")
    fun observeItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Update
    suspend fun updateItem(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlist_id = :playlistId")
    suspend fun clearItems(playlistId: Long)

    @Query("DELETE FROM playlist_items WHERE id = :itemId")
    suspend fun deleteItemById(itemId: Long)

    /** إعادة ترتيب دفعة واحدة داخل معاملة تمنع انتهاك قيد التفرد المؤقت */
    @Transaction
    suspend fun reorder(items: List<PlaylistItemEntity>) {
        // حيّد المواقع مؤقتاً بقيم سالبة ثم ثبّت الترتيب الجديد
        items.forEachIndexed { i, it -> updateItem(it.copy(position = -(i + 1))) }
        items.forEachIndexed { i, it -> updateItem(it.copy(position = i)) }
    }
}

// =====================================================================
// الجدولة وسجل التنفيذ
// =====================================================================

@Dao
interface ScheduleDao {
    @Insert
    suspend fun insert(schedule: RadioScheduleEntity): Long

    @Update
    suspend fun update(schedule: RadioScheduleEntity)

    @Query("SELECT * FROM radio_schedules ORDER BY created_at")
    fun observeAll(): Flow<List<RadioScheduleEntity>>

    @Query("SELECT * FROM radio_schedules WHERE enabled = 1")
    suspend fun enabledSchedules(): List<RadioScheduleEntity>

    @Query("DELETE FROM radio_schedules WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ----- الأحداث -----

    @Insert
    suspend fun insertEvent(event: ScheduledEventEntity): Long

    @Update
    suspend fun updateEvent(event: ScheduledEventEntity)

    @Query("SELECT * FROM scheduled_events ORDER BY start_minutes")
    fun observeEvents(): Flow<List<ScheduledEventEntity>>

    @Query("SELECT * FROM scheduled_events")
    suspend fun allEvents(): List<ScheduledEventEntity>

    @Query("SELECT * FROM scheduled_events WHERE id = :id")
    suspend fun eventById(id: Long): ScheduledEventEntity?

    @Query("DELETE FROM scheduled_events WHERE id = :id")
    suspend fun deleteEventById(id: Long)

    /** أحداث هذا اليوم (ISO 1..7) مرشّحة ببت الأيام + الجداول المفعلة فقط */
    @Query(
        """
        SELECT e.* FROM scheduled_events e
        JOIN radio_schedules s ON s.id = e.schedule_id
        WHERE s.enabled = 1 AND (e.week_days_mask & :dayBit) != 0
        ORDER BY e.start_minutes
        """
    )
    suspend fun eventsForDayBit(dayBit: Int): List<ScheduledEventEntity>

    // ----- سجل التنفيذ -----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExecution(execution: ScheduleExecutionEntity): Long

    @Update
    suspend fun updateExecution(execution: ScheduleExecutionEntity)

    @Query("SELECT * FROM schedule_executions WHERE event_id = :eventId AND date_key = :dateKey")
    suspend fun executionOf(eventId: Long, dateKey: String): ScheduleExecutionEntity?

    @Query("SELECT * FROM schedule_executions ORDER BY fired_at DESC LIMIT :limit")
    fun observeRecentExecutions(limit: Int = 50): Flow<List<ScheduleExecutionEntity>>

    @Query(
        """
        UPDATE schedule_executions SET status = :status, note = :note
        WHERE event_id = :eventId AND date_key = :dateKey
        """
    )
    suspend fun updateExecutionStatus(eventId: Long, dateKey: String, status: String, note: String?)

    @Query("DELETE FROM schedule_executions WHERE status = :status")
    suspend fun deleteExecutionsByStatus(status: String)
}
