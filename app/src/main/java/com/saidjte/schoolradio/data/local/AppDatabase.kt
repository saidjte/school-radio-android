package com.saidjte.schoolradio.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.saidjte.schoolradio.data.local.dao.AudioTrackDao
import com.saidjte.schoolradio.data.local.dao.CategoryDao
import com.saidjte.schoolradio.data.local.dao.PlaylistDao
import com.saidjte.schoolradio.data.local.dao.RecordingDao
import com.saidjte.schoolradio.data.local.dao.ScheduleDao
import com.saidjte.schoolradio.data.local.dao.UserDao
import com.saidjte.schoolradio.data.local.entity.AudioTrackEntity
import com.saidjte.schoolradio.data.local.entity.CategoryEntity
import com.saidjte.schoolradio.data.local.entity.PlaylistEntity
import com.saidjte.schoolradio.data.local.entity.PlaylistItemEntity
import com.saidjte.schoolradio.data.local.entity.RadioScheduleEntity
import com.saidjte.schoolradio.data.local.entity.RecordingEntity
import com.saidjte.schoolradio.data.local.entity.ScheduleExecutionEntity
import com.saidjte.schoolradio.data.local.entity.ScheduledEventEntity
import com.saidjte.schoolradio.data.local.entity.UserEntity

/**
 * قاعدة البيانات المحلية الوحيدة للتطبيق (تعمل دون إنترنت تماماً).
 * الإصدار 2 = v0.3.0: أُضيف جدولا «users» (الأدوار) و«recordings» (الأرشيف الصوتي).
 * عند أي تعديل مستقبلي: زد الإصدار واكتب Migration() صريحاً (لا fallback في الإنتاج)
 * — بيانات المدارس الحقيقية (مكتبة + جداول + أرشيف) لا يجوز فقدانها أبداً.
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
        UserEntity::class,
        RecordingEntity::class,
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun audioTrackDao(): AudioTrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun userDao(): UserDao
    abstract fun recordingDao(): RecordingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * ترحيل 1 → 2 (v0.2.0 → v0.3.0): إنشاء جدولين جديدن فقط —
         * لا مساس بالجداول الموجودة فيمتا مكتبات المدارس وبرامجها محفوظة بالكامل.
         * SQL هنا يجب أن يطابق حرفياً ما يولده Room من الكيانات (يُفحص مخطط app/schemas/2.json).
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // المستخدمون (الأدوار) — الاسم فريد
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `users` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL)""".trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_users_name` ON `users` (`name`)"
                )
                // أرشيف التسجيلات — بلا مفاتيح خارجية (يبقى سليماً بعد حذف مستخدم)
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `recordings` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `file_path` TEXT NOT NULL,
                        `duration_ms` INTEGER NOT NULL,
                        `size_bytes` INTEGER NOT NULL,
                        `mime_type` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `recorded_by_name` TEXT,
                        `recorded_by_role` TEXT,
                        `created_at` INTEGER NOT NULL)""".trimIndent()
                )
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "school-radio.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
    }
}
