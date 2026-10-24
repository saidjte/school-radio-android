package com.saidjte.schoolradio.data.repository

import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.provider.OpenableColumns
import com.saidjte.schoolradio.data.local.AppDatabase
import com.saidjte.schoolradio.data.local.entity.AudioTrackEntity
import com.saidjte.schoolradio.data.local.entity.CategoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * مستودع المكتبة الصوتية: الاستيراد عبر SAF، التصنيف، البحث، واكتشاف الملفات غير المتاحة.
 *
 * لماذا SAF وليس صلاحيات التخزين الشامل؟
 * - لا يحتاج أي صلاحية READ/WRITE_EXTERNAL_STORAGE.
 * - المستخدم يختار الملفات نفسها (أو مجلداً كاملاً بـ ACTION_OPEN_DOCUMENT_TREE).
 * - takePersistableUriPermission يحفظ الوصول بعد إعادة التشغيل.
 */
class LibraryRepository(
    private val context: Context,
    private val db: AppDatabase
) {
    private val trackDao = db.audioTrackDao()
    private val categoryDao = db.categoryDao()

    val tracks: Flow<List<AudioTrackEntity>> = trackDao.observeAll()
    val categories: Flow<List<CategoryEntity>> = categoryDao.observeAll()

    fun search(query: String): Flow<List<AudioTrackEntity>> =
        if (query.isBlank()) trackDao.observeAll() else trackDao.search(query.trim())

    /** زرع التصنيفات الافتراضية السبعة عند أول تشغيل فقط */
    suspend fun ensureDefaultCategories() {
        if (categoryDao.count() == 0) {
            DEFAULT_CATEGORIES.forEachIndexed { i, name ->
                categoryDao.insert(CategoryEntity(name = name, sortOrder = i, isBuiltIn = true))
            }
        }
    }

    // ================== v0.10.0: إدارة كاملة للتصنيفات ==================
    // سابقاً كانت التصنيفات الافتراضية السبعة تُعرض للقراءة فقط — لا إضافة
    // ولا تسمية ولا حذف، فتكدّست المقاطع في «فقرات متنوعة» عن قهر.

    val categoriesWithCounts: Flow<List<com.saidjte.schoolradio.data.local.dao.CategoryWithCount>> =
        categoryDao.observeAllWithCounts()

    /**
     * إضافة تصنيف جديد مخصص.
     * @return null عند النجاح، أو رسالة عربية (اسم فارغ/مكرر) لعرضها للمستخدم
     */
    suspend fun addCategory(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "اكتب اسم التصنيف أولاً"
        if (categoryDao.insert(CategoryEntity(name = trimmed, sortOrder = 999, isBuiltIn = false)) == -1L) {
            return "تصنيف بهذا الاسم موجود بالفعل"
        }
        return null
    }

    /** إعادة تسمية أي تصنيف (افتراضي أو مخصص) — @return رسالة خطأ أو null */
    suspend fun renameCategory(category: CategoryEntity, newName: String): String? {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return "الاسم لا يمكن أن يكون فارغاً"
        if (trimmed == category.name) return null
        // فحص التكرار يدوياً (القيد UNIQUE يُتجاهل بصمت مع IGNORE فقط عند الإدراج)
        if (categories.first().any { it.id != category.id && it.name == trimmed }) {
            return "تصنيف بهذا الاسم موجود بالفعل"
        }
        categoryDao.update(category.copy(name = trimmed))
        return null
    }

    /**
     * حذف تصنيف مخصص — المقاطع التابعة له تعود إلى «بدون تصنيف» تلقائياً
     * (قيد FK onDelete = SET_NULL يتكفل بذلك على مستوى قاعدة البيانات).
     * التصنيفات الافتراضية (isBuiltIn) لا تُحذف حفاظاً على بنية البرنامج المدرسي.
     * @return رسالة خطأ أو null عند النجاح
     */
    suspend fun deleteCategory(category: CategoryEntity): String? {
        if (category.isBuiltIn) return "التصنيفات الافتراضية لا تُحذف — يمكنك إعادة تسميتها فقط"
        categoryDao.deleteIfCustom(category.id)
        return null
    }

    /**
     * استيراد عدة ملفات في عملية واحدة.
     * تستخرج الاسم والمدة والحجم، وتطلب صلاحية وصول دائمة لكل مستند.
     * @return قائمة العناوين المستوردة (لرسالة التأكيد)
     */
    suspend fun importUris(uris: List<Uri>, categoryId: Long?): Int =
        withContext(Dispatchers.IO) {
            val cr = context.contentResolver
            var imported = 0
            for (uri in uris) {
                try {
                    // صلاحية دائمة حتى يبقى الملف قابلاً للتشغيل بعد إعادة التشغيل
                    cr.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // بعض الموفرات لا تدعم الدوام — يعمل الملف داخل الجلسة وقد يُطلب إعادة ربطه لاحقاً
                }
                val name = queryDisplayName(cr, uri) ?: "مقطع ${imported + 1}"
                val duration = queryDuration(cr, uri)
                val size = querySize(cr, uri)
                trackDao.insert(
                    AudioTrackEntity(
                        title = name.substringBeforeLast('.').ifBlank { name },
                        uri = uri.toString(),
                        documentId = try { uri.lastPathSegment } catch (_: Exception) { null },
                        categoryId = categoryId,
                        durationMs = duration,
                        sizeBytes = size,
                        isAvailable = true
                    )
                )
                imported++
            }
            imported
        }

    suspend fun rename(track: AudioTrackEntity, newTitle: String) {
        trackDao.update(track.copy(title = newTitle))
    }

    suspend fun moveToCategory(track: AudioTrackEntity, categoryId: Long?) {
        trackDao.update(track.copy(categoryId = categoryId))
    }

    /** حذف المقطع من المكتبة فقط — لا يمس الملف الأصلي على الإطلاق */
    suspend fun removeFromLibrary(track: AudioTrackEntity) = trackDao.delete(track)

    /** فحص توفر الملفات (يُستدعى عند الإقلاع أو يدوياً): من يفشل وصوله يُعلَّم غير متاح */
    suspend fun refreshAvailability(): List<AudioTrackEntity> = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val all = trackDao.unavailable().ifEmpty { return@withContext emptyList() }
        val relinked = mutableListOf<AudioTrackEntity>()
        for (t in all) {
            val ok = try {
                cr.openInputStream(Uri.parse(t.uri))?.use { it.read() >= 0 } ?: false
            } catch (_: Exception) { false }
            if (ok) { trackDao.setAvailability(t.id, true); relinked += t.copy(isAvailable = true) }
        }
        relinked
    }

    /** إعادة ربط مقطع فُقدت صلاحيته بملف جديد يختاره المستخدم */
    suspend fun relink(track: AudioTrackEntity, newUri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                newUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) { /* كما سبق */ }
        trackDao.update(track.copy(uri = newUri.toString(), isAvailable = true))
    }

    suspend fun trackById(id: Long): AudioTrackEntity? = trackDao.byId(id)
    suspend fun tracksByIds(ids: List<Long>): List<AudioTrackEntity> = trackDao.byIds(ids)
    suspend fun categoryById(id: Long): CategoryEntity? = categoryDao.byId(id)

    private fun queryDisplayName(cr: android.content.ContentResolver, uri: Uri): String? =
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private fun querySize(cr: android.content.ContentResolver, uri: Uri): Long =
        cr.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0
        } ?: 0

    private fun queryDuration(cr: android.content.ContentResolver, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        } catch (_: Exception) { 0 } finally {
            runCatching { retriever.release() }
        }
    }

    companion object {
        val DEFAULT_CATEGORIES = listOf(
            "القرآن الكريم",
            "الحديث النبوي",
            "النشيد الوطني",
            "الأناشيد المدرسية",
            "المؤثرات الصوتية",
            "الفقرات الثقافية",
            "فقرات متنوعة"
        )
    }
}
