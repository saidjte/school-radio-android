package com.saidjte.schoolradio.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * أدوار المستخدمين في استوديو الإذاعة المدرسي.
 *
 * مصفوفة الصلاحيات (قرار تصميمي موثق في README):
 *  - طالب (STUDENT)   : بث مباشر، تسجيل، تشغيل — التبويبات التحضيرية (مكتبة/قوائم) مفتوحة
 *                       لأن الطلاب هم فريق الإذاعة الفعلي ويحضرون فقراتهم.
 *  - معلم (TEACHER)   : كل ما سبق + إدارة الجدولة + حذف/تسمية التسجيلات (رقابة المحتوى).
 *  - مشرف (ADMIN)     : كل ما سبق + إدارة المستخدمين أنفسهم (إضافة/حذف).
 */
enum class UserRole(val labelAr: String) {
    ADMIN("مشرف"),
    TEACHER("معلم"),
    STUDENT("طالب");

    companion object {
        /** تحويل آمن لاسم الدور المخزن نصياً — أي قيمة غير معروفة تُعامل كطالب (الأقل صلاحية) */
        fun from(name: String?): UserRole =
            entries.firstOrNull { it.name == name } ?: STUDENT

        /** الصلاحية الإدارية على المحتوى (جدولة/تسجيلات) = معلم فأعلى */
        fun canManage(role: UserRole): Boolean = role == ADMIN || role == TEACHER

        /** إدارة المستخدمين أنفسهم = المشرف فقط */
        fun canManageUsers(role: UserRole): Boolean = role == ADMIN
    }
}

/**
 * مستخدم التطبيق: طالب/معلم/مشرف.
 * الاسم فريد (فهرس uniqueness) حتى لا يحدث التباس عند التوقيع على التسجيلات.
 * لا يوجد نظام كلمات مرور عمداً — الجهاز جهاز الاستوديو المحلي، والدور للتنظيم لا للحماية.
 */
@Entity(tableName = "users", indices = [Index(value = ["name"], unique = true)])
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val role: String = UserRole.STUDENT.name,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
) {
    fun roleEnum(): UserRole = UserRole.from(role)
}

/** مصدر التسجيل: بث حي مسجَّل أثناء PTT أو فقرة تحضيرية مسجَّلة منفردة */
enum class RecordingSource(val labelAr: String) {
    PTT("بث مباشر"),
    MEMO("فقرة تحضيرية")
}

/**
 * سجل في أرشيف التسجيلات المحلية.
 *
 * قرارات تصميمية:
 *  - الملف نفسه داخل مسار التطبيق الخاص (getExternalFilesDir/Music/recordings)
 *    فلا حاجة لأي صلاحية تخزين، ويُحذف تلقائياً مع التطبيق (خصوصية).
 *  - بلا مفاتيح خارجية عمداً: يجب أن يبقى الأرشيف سليماً حتى بعد حذف المستخدم،
 *    لذلك تُحفظ لقطة (اسم + دور) المسجِّل مُطبَّعة (denormalized) عند لحظة التسجيل.
 *  - الصيغة AAC-LC داخل حاوية MP4 — أعلى جودة مقبولة بأقل حجم (128kbps مونو
 *    ≈ 57MB/ساعة) ويشغّلها أي مشغل بما فيها ExoPlayer في التطبيق نفسه.
 */
@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long = 0,
    @ColumnInfo(name = "mime_type") val mimeType: String = "audio/mp4",
    /** PTT = أُسجل أثناء البث الحي، MEMO = فقرة تحضيرية */
    val source: String = RecordingSource.PTT.name,
    @ColumnInfo(name = "recorded_by_name") val recordedByName: String? = null,
    @ColumnInfo(name = "recorded_by_role") val recordedByRole: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
) {
    fun sourceEnum(): RecordingSource =
        RecordingSource.entries.firstOrNull { it.name == source } ?: RecordingSource.PTT
}
