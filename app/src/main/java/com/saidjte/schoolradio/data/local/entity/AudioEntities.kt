package com.saidjte.schoolradio.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * تصنيف المكتبة الصوتية.
 * تُزرع التصنيفات الافتراضية عند أول تشغيل (القرآن الكريم، الحديث النبوي…) ويمكن إضافة تصنيفات مخصصة.
 */
@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** الترتيب الافتراضي للعرض */
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    /** تصنيف نظام افتراضي لا يُحذف من الواجهة */
    @ColumnInfo(name = "is_built_in") val isBuiltIn: Boolean = false
)

/**
 * المقطع الصوتي في المكتبة.
 *
 * ملاحظة تصميمية مهمة: لا نحتفظ بالملف نفسه داخل قاعدة البيانات (ثقيل جداً) بل
 * نخزّن URI الخاص به عبر Storage Access Framework مع صلاحية وصول دائمة
 * (takePersistableUriPermission) حتى يبقى الوصول ممكناً بعد إعادة تشغيل التطبيق.
 */
@Entity(
    tableName = "audio_tracks",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("category_id"), Index("title"), Index("added_at")]
)
data class AudioTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** العنوان المعروض (قابل للتعديل، افتراضياً اسم الملف) */
    val title: String,
    /** URI المستند (content://) للملف الصوتي */
    val uri: String,
    /** معرّف المستند لدعم إعادة الربط عند فقدان الصلاحية */
    @ColumnInfo(name = "document_id") val documentId: String? = null,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    /** المدة بالميلي ثانية — تُستخرج عند الاستيراد إن توفرت */
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0,
    /** حجم الملف بالبايت — للمعلومات فقط */
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long = 0,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis(),
    /** هل ما زال الملف متاحاً؟ (يُحدَّث عند فشل الوصول) */
    @ColumnInfo(name = "is_available") val isAvailable: Boolean = true
)
