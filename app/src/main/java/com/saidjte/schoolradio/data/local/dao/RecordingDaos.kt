package com.saidjte.schoolradio.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.saidjte.schoolradio.data.local.entity.RecordingEntity
import com.saidjte.schoolradio.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

// =====================================================================
// المستخدمون والأدوار
// =====================================================================

@Dao
interface UserDao {
    /** IGNORE: الاسم فريد — الإدراج المكرر يعيد -1 بدل الانهيار */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(user: UserEntity): Long

    @Query("SELECT * FROM users ORDER BY created_at")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun byId(id: Long): UserEntity?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteById(id: Long)
}

// =====================================================================
// أرشيف التسجيلات
// =====================================================================

@Dao
interface RecordingDao {
    @Insert
    suspend fun insert(recording: RecordingEntity): Long

    @Query("SELECT * FROM recordings ORDER BY created_at DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun byId(id: Long): RecordingEntity?

    @Query("UPDATE recordings SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    /** v0.20.0: حفظ قص يدوي (بداية + نهاية) مع وسم السلطة اليدوية */
    @Query(
        "UPDATE recordings SET trim_start_ms = :startMs, trim_end_ms = :endMs, " +
            "trim_manual = 1 WHERE id = :id"
    )
    suspend fun setManualTrim(id: Long, startMs: Long, endMs: Long)

    /** v0.20.0: إزالة كل القص — يعود التسجيل للتشغيل الكامل */
    @Query(
        "UPDATE recordings SET trim_start_ms = 0, trim_end_ms = 0, trim_manual = 0 WHERE id = :id"
    )
    suspend fun clearTrim(id: Long)

    @Delete
    suspend fun delete(recording: RecordingEntity)

    @Query("SELECT COUNT(*) FROM recordings")
    suspend fun count(): Int
}
