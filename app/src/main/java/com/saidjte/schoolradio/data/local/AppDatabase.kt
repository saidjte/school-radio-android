package com.saidjte.schoolradio.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.saidjte.schoolradio.data.local.dao.AudioTrackDao
import com.saidjte.schoolradio.data.local.dao.CategoryDao
import com.saidjte.schoolradio.data.local.dao.PlaylistDao
import com.saidjte.schoolradio.data.local.dao.ScheduleDao
import com.saidjte.schoolradio.data.local.entity.AudioTrackEntity
import com.saidjte.schoolradio.data.local.entity.CategoryEntity
import com.saidjte.schoolradio.data.local.entity.PlaylistEntity
import com.saidjte.schoolradio.data.local.entity.PlaylistItemEntity
import com.saidjte.schoolradio.data.local.entity.RadioScheduleEntity
import com.saidjte.schoolradio.data.local.entity.ScheduleExecutionEntity
import com.saidjte.schoolradio.data.local.entity.ScheduledEventEntity

/**
 * قاعدة البيانات المحلية الوحيدة للتطبيق (تعمل دون إنترنت تماماً).
 * الإصدار 1 = مخطط النسخة الاحترافية v0.2.0.
 * عند أي تعديل مستقبلي: زد الإصدار واكتب Migration() صريحاً (لا fallback في الإنتاج).
 */
@Database(
    entities = [
        CategoryEntity::class,
        AudioTrackEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        RadioScheduleEntity::class,
        ScheduledEventEntity::class,
        ScheduleExecutionEntity::class,
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun audioTrackDao(): AudioTrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "school-radio.db"
                ).build().also { INSTANCE = it }
            }
    }
}
