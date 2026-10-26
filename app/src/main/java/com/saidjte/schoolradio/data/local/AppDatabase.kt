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
 * الإصدار 4 = v0.12.0: أعمدة الفواصل المجدولة على radio_schedules (جينغل آلي بين الفقرات).
 * الإصدار 5 = v0.13.0: ربط سكربت المذيع بالفقرة المجدولة (script_id على scheduled_events)
 *   + أساس الأتمتة الموثوقة (خدمة حارسة + منبهات دقيقة مضمونة).
 * الإصدار 6 = v0.15.0: مهلة الانتقال القابلة للضبط للفواصل + نمط «الجينل مع نهاية كل مقطع»
 *   (break_transition_sec + break_every_track على radio_schedules).
 * الإصدار 7 = v0.18.0: قص الفراغ الأول (trim_start_ms + silence_analyzed على audio_tracks).
 * الإصدار 8 = v0.19.0: القص اليدوي (trim_end_ms + trim_manual على audio_tracks) —
 *   نقطة نهاية يحددها المذيع من المحرر + وسم سلطة يمنع التحليل الآلي من المساس بالقص.
 * الإصدار 9 = v0.20.0: القص اليدوي لتسجيلات الأرشيف (trim_start_ms + trim_end_ms +
 *   trim_manual على recordings) + معرض شكل موجي داخل محرر القص.
 * الإصدار 10 = v0.23.0: استيراد من الإنترنت (is_remote + source_url على audio_tracks)
 *   — مقاطع بث من يوتوب/ساوندكلاود/Drive مع تنزيل محلي يحولها لملفات دائمة.
 * الإصدار 11 = v0.27.0: تاريخ الموعد الواحد (specific_date على scheduled_events)
 *   — فقرات مناسبات تُطلق مرة واحدة ثم تنقرض منبهاتها.
 * الإصدار 12 = v0.28.0: تنبيه إشعار قبل الفقرة المؤرخة (reminder_before_minutes
 *   على scheduled_events) — دقائق قبل البدء، يُضبط عبر AlarmManager مستقل.
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
    version = 12,
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

        /**
         * ترحيل 3 → 4 (v0.11.0 → v0.12.0): ثلاثة أعمدة للفواصل المجدولة على radio_schedules.
         * ADD COLUMN مع REFERENCES آمن كما في 2→3: القيم الافتراضية NULL/0 ولا تُكتب صفوف
         * أثناء الترحيل — لا مساس ببيانات الجداول والفقرات القائمة.
         * SQL هنا يجب أن يطابق حرفياً ما يولّده Room من الكيانات (يُفحص مخطط app/schemas/4.json).
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // تفعيل الفواصل لهذا الجدول (معطل افتراضياً لكل الجداول القائمة)
                db.execSQL(
                    "ALTER TABLE `radio_schedules` ADD COLUMN `break_enabled` INTEGER NOT NULL DEFAULT 0"
                )
                // جينغل محدد من المكتبة (يبقى سليماً إذا حُذف المقطع)
                db.execSQL(
                    "ALTER TABLE `radio_schedules` ADD COLUMN `break_track_id` INTEGER " +
                        "REFERENCES `audio_tracks`(`id`) ON DELETE SET NULL"
                )
                // أو جينغلات عشوائية من تصنيف كامل (يبقى سليماً إذا حُذف التصنيف)
                db.execSQL(
                    "ALTER TABLE `radio_schedules` ADD COLUMN `break_category_id` INTEGER " +
                        "REFERENCES `categories`(`id`) ON DELETE SET NULL"
                )
                // فهرسة أعمدة المفاتيح الخارجية — مطابقة لإعلان الكيان (index_radio_schedules_*)
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_radio_schedules_break_track_id` " +
                        "ON `radio_schedules` (`break_track_id`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_radio_schedules_break_category_id` " +
                        "ON `radio_schedules` (`break_category_id`)"
                )
            }
        }

        /**
         * ترحيل 4 → 5 (v0.12.0 → v0.13.0): عمود واحد رابط سكربت المذيع.
         * ADD COLUMN قيمته NULL افتراضياً ولا تُكتب صفوف أثناء الترحيل — لا مساس بالبيانات.
         * لا FK عمداً: السكربتات تخزن في JSON خارج Room — يُتحقق من الوجود لحظة الإطلاق.
         * SQL هنا يجب أن يطابق حرفياً ما يولّده Room من الكيانات (يُفحص مخطط app/schemas/5.json).
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `scheduled_events` ADD COLUMN `script_id` INTEGER"
                )
            }
        }

        /**
         * ترحيل 5 → 6 (v0.14.0 → v0.15.0): عمودان للفواصل المطورة على radio_schedules —
         * مهلة الانتقال (ثواني الصمت بعد الفقرة قبل الجينل) + نمط «الجينل مع نهاية كل مقطع».
         * ADD COLUMN بقيم افتراضية ولا تُكتب صفوف أثناء الترحيل — لا مساس بالبيانات.
         * SQL هنا يجب أن يطابق حرفياً ما يولّده Room من الكيانات (يُفحص مخطط app/schemas/6.json).
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `radio_schedules` ADD COLUMN `break_transition_sec` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `radio_schedules` ADD COLUMN `break_every_track` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * ترحيل 6 → 7 (v0.17.0 → v0.18.0): عمودان لقص الفراغ الأول على audio_tracks —
         * مدة الصمت المقيسة في بداية الملف (trim_start_ms) + وسم المرور على التحليل
         * (silence_analyzed) لفحص خلفي تدريجي للمكتبة القائمة دون تعطيل الإقلاع.
         * ADD COLUMN بقيم افتراضية ولا تُكتب صفوف أثناء الترحيل — لا مساس بالبيانات.
         * SQL هنا يجب أن يطابق حرفياً ما يولّده Room من الكيانات (يُفحص مخطط app/schemas/7.json).
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `audio_tracks` ADD COLUMN `trim_start_ms` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `audio_tracks` ADD COLUMN `silence_analyzed` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * ترحيل 7 → 8 (v0.18.0 → v0.19.0): عمودان للقص اليدوي على audio_tracks —
         * نقطة النهاية التي يحددها المذيع (trim_end_ms، صفر = تشغيل حتى نهاية الملف)
         * + وسم السلطة (trim_manual) الذي يجعل قص المقطع يدوياً محسوماً: التحليل الآلي
         * لا يمسه، ومفتاح «قص الفراغ الأول» العام لا يلغيه.
         * ADD COLUMN بقيم افتراضية ولا تُكتب صفوف أثناء الترحيل — لا مساس بالبيانات.
         * SQL هنا يجب أن يطابق حرفياً ما يولّده Room من الكيانات (يُفحص مخطط app/schemas/8.json).
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `audio_tracks` ADD COLUMN `trim_end_ms` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `audio_tracks` ADD COLUMN `trim_manual` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * ترحيل 8 → 9 (v0.19.0 → v0.20.0): ثلاثة أعمدة للقص اليدوي على recordings —
         * محرر القص (الذي كان حصرياً لمقاطع المكتبة) يصل إلى أرشيف التسجيلات:
         * بداية ونهاية يحددهما المذيع فوق الشكل الموجي، ويُطبّقان في كل مسارات
         * التشغيل (المعاينة من الأرشيف والفقرة المجدولة).
         * ADD COLUMN بقيم افتراضية ولا تُكتب صفوف أثناء الترحيل — لا مساس بالبيانات.
         * SQL هنا يجب أن يطابق حرفياً ما يولّده Room من الكيانات (يُفحص مخطط app/schemas/9.json).
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `recordings` ADD COLUMN `trim_start_ms` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `recordings` ADD COLUMN `trim_end_ms` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `recordings` ADD COLUMN `trim_manual` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * ترحيل 9 → 10 (v0.22.0 → v0.23.0): عمودان للاستيراد من الإنترنت على audio_tracks —
         * راية «مقطع بث» (is_remote) والرابط الأصلي للمنصة (source_url) الذي تتم منه
         * إعادة استخراج التدفقات المنتهية (روابط يوتوب/ساوندكلاود تعيش ساعات فقط).
         * ADD COLUMN بقيم افتراضية ولا تُكتب صفوف أثناء الترحيل — لا مساس بالبيانات.
         * SQL هنا يجب أن يطابق حرفياً ما يولّده Room من الكيانات (يُفحص مخطط app/schemas/10.json).
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `audio_tracks` ADD COLUMN `is_remote` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `audio_tracks` ADD COLUMN `source_url` TEXT"
                )
            }
        }

        // v0.27.0: تاريخ واحد للفقرات غير المتكررة (تخرّج، احتفال…)
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `scheduled_events` ADD COLUMN `specific_date` TEXT"
                )
            }
        }

        /**
         * ترحيل 11 → 12 (v0.27.0 → v0.28.0): عمود تنبيه الفقرة المؤرخة —
         * دقائق قبل انطلاق الفقرة يُرسل عندها إشعار تذكير (60 = ساعة، 1440 = يوم).
         * ADD COLUMN قيمته NULL افتراضياً ولا تُكتب صفوف أثناء الترحيل —
         * كل الفقرات القائمة تبقى «بدون تنبيه» كما هي، لا مساس بأي بيانات.
         * SQL هنا يجب أن يطابق حرفياً ما يولّده Room من الكيانات (يُفحص مخطط app/schemas/12.json).
         */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `scheduled_events` ADD COLUMN `reminder_before_minutes` INTEGER"
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
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                        MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12
                    )
                    .build().also { INSTANCE = it }
            }
    }
}
