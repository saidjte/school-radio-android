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

    @Delete
    suspend fun delete(recording: RecordingEntity)

    @Query("SELECT COUNT(*) FROM recordings")
    suspend fun count(): Int
}
