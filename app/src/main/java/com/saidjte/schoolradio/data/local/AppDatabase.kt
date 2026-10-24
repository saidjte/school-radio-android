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
 * الإصدار 3 = v0.5.0: حقول الفقرة الموسعة (recording_id + note_text + image_path)
 *   لتشغيل التسجيلات ضمن الفقرات ودعم صور/نصوص شاشة العرض.
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
    version = 3,
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

        /**
         * ترحيل 2 → 3 (v0.4.0 → v0.5.0): ثلاثة أعمدة على scheduled_events + فهرس.
         * ADD COLUMN مع REFERENCES يبقى آمناً هنا لأن القيمة الافتراضية NULL
         * ولا تُكتب صفوف أثناء الترحيل — لا مساس بأي بيانات قائمة.
         * SQL هنا يجب أن يطابق حرفياً ما يولّده Room من الكيانات (يُفحص مخطط app/schemas/3.json).
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // تسجيل من أرشيف التسجيلات كمحتوى للفقرة (يبقى سليماً إذا حُذف التسجيل)
                db.execSQL(
                    "ALTER TABLE `scheduled_events` ADD COLUMN `recording_id` INTEGER " +
                        "REFERENCES `recordings`(`id`) ON DELETE SET NULL"
                )
                // نص الفقرة المعروض على شاشة العرض
                db.execSQL("ALTER TABLE `scheduled_events` ADD COLUMN `note_text` TEXT")
                // صورة الفقرة المعروضة على شاشة العرض (مسار مطلق داخل ملفات التطبيق)
                db.execSQL("ALTER TABLE `scheduled_events` ADD COLUMN `image_path` TEXT")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_scheduled_events_recording_id` " +
                        "ON `scheduled_events` (`recording_id`)"
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { INSTANCE = it }
            }
    }
}
