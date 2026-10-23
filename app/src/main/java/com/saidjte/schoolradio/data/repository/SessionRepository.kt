package com.saidjte.schoolradio.data.repository

import com.saidjte.schoolradio.data.local.dao.UserDao
import com.saidjte.schoolradio.data.local.entity.UserEntity
import com.saidjte.schoolradio.data.local.entity.UserRole
import com.saidjte.schoolradio.data.settings.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * جلسة المستخدم الحالي وأدواره.
 *
 * المستخدم الحالي = توليفة (معرف من DataStore) + (جدول users من Room) —
 * فلا نسخة مكررة من الحالة: إذا حُذف المستخدم الحالي يقع الاختيار تلقائياً
 * على أول مستخدم، وإذا كان الجدول فارغاً (قبل الزرع) تكون الصلاحيات كاملة
 * كي لا يُقفل التطبيق على نفسه عند أول تشغيل.
 */
class SessionRepository(
    private val settings: SettingsStore,
    private val userDao: UserDao
) {
    /** المستخدم الحالي (أو null قبل الزرع الأول) */
    val currentUser: Flow<UserEntity?> = combine(settings.currentUserId, userDao.observeAll()) { id, users ->
        users.firstOrNull { it.id == id } ?: users.firstOrNull()
    }

    /** صلاحية إدارة المحتوى (الجدولة/حذف التسجيلات) — معلم فأعلى */
    val canManage: Flow<Boolean> = currentUser.map { it == null || UserRole.canManage(it.roleEnum()) }

    /** صلاحية إدارة المستخدمين — مشرف فقط */
    val canManageUsers: Flow<Boolean> = currentUser.map { it == null || UserRole.canManageUsers(it.roleEnum()) }

    fun observeUsers(): Flow<List<UserEntity>> = userDao.observeAll()

    /** زرع المشرف الافتراضي عند أول تشغيل بعد تثبيت نظيف (ولا شيء بعد الترحيل) */
    suspend fun ensureDefaultUser() {
        if (userDao.count() == 0) {
            val id = userDao.insert(UserEntity(name = DEFAULT_ADMIN_NAME, role = UserRole.ADMIN.name))
            if (id != -1L) settings.setCurrentUserId(id)
        }
    }

    suspend fun switchUser(id: Long) = settings.setCurrentUserId(id)

    /** إضافة مستخدم — يفشل بأمان عند تكرار الاسم (قيد التفرد في القاعدة) */
    suspend fun addUser(name: String, role: UserRole): Result<Long> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("الاسم فارغ"))
        val id = userDao.insert(UserEntity(name = trimmed, role = role.name))
        return if (id != -1L) Result.success(id)
        else Result.failure(IllegalStateException("يوجد مستخدم بنفس الاسم"))
    }

    /** حذف مستخدم غير الحالي — التسجيلات السابقة تبقى سليمة بلقطة اسمها المطبَّعة */
    suspend fun deleteUser(user: UserEntity): Result<Unit> {
        val activeId = settings.currentUserId.first()
        if (user.id == activeId) {
            return Result.failure(IllegalStateException("لا يمكن حذف المستخدم الحالي"))
        }
        userDao.deleteById(user.id)
        return Result.success(Unit)
    }

    companion object {
        const val DEFAULT_ADMIN_NAME = "مشرف الإذاعة"
    }
}
