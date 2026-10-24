package com.saidjte.schoolradio.data.repository

import com.saidjte.schoolradio.data.local.entity.RecordingEntity
import kotlinx.coroutines.flow.Flow

/**
 * مستودع أرشيف التسجيلات — يلف RecordingDao ويضيف إدارة الملفات الفعلية:
 * الحذف من القاعدة يحذف الملف من القرص أيضاً (لا ملفات يتيمة).
 */
class RecordingRepository(private val dao: com.saidjte.schoolradio.data.local.dao.RecordingDao) {

    fun observeAll(): Flow<List<RecordingEntity>> = dao.observeAll()

    /** جلب تسجيل واحد بالمعرّف — يستخدمه المجدول عند إطلاق فقرة مرتبطة بتسجيل (v0.5.0) */
    suspend fun byId(id: Long): RecordingEntity? = dao.byId(id)

    suspend fun insert(recording: RecordingEntity): Long = dao.insert(recording)

    suspend fun rename(id: Long, title: String) = dao.rename(id, title)

    /** حذف السجل + ملف الصوت من القرص — يعيد true إن نجح الحذف الكلي */
    suspend fun delete(recording: RecordingEntity): Boolean {
        val fileDeleted = try {
            java.io.File(recording.filePath).delete()
        } catch (_: Exception) {
            false
        }
        dao.delete(recording)
        return fileDeleted
    }

    suspend fun count(): Int = dao.count()
}
