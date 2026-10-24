package com.saidjte.schoolradio.data.scripts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * سكربت إذاعي (إعلان/تقديم/نص فقرة) — نص يقرأه المذيع من شاشة الهاتف.
 */
data class ScriptItem(
    val id: Long,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    /** عدد كلمات تقريبي — يظهر في القائمة لمساعدة المذيع على تقدير مدة القراءة */
    val wordCount: Int get() = body.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
}

/**
 * مستودع السكربتات الإذاعية (v0.9.0) — تخزين محلي 100% في ملف JSON
 * داخل مسار التطبيق الخاص (filesDir/scripts.json) — لا شبكة إطلاقاً.
 *
 * - الكتابة ذرّية عملياً: تُكتب نسخة مؤقتة ثم تُبدّل بالملف الأصلي
 *   فلا يُتلف الملف لو انقطعت العملية أثناء الحفظ.
 * - القائمة منسّزة دائماً بالأحدث تعديلاً.
 */
class ScriptRepository(
    private val dir: File,
    private val scope: CoroutineScope
) {

    private val file = File(dir, "scripts.json")
    private val lock = Any()

    private val _scripts = MutableStateFlow<List<ScriptItem>>(emptyList())
    val scripts: StateFlow<List<ScriptItem>> = _scripts.asStateFlow()

    private var nextId = 1L
    private var loaded = false

    init {
        scope.launch(Dispatchers.IO) { load() }
    }

    private fun load() {
        synchronized(lock) {
            if (loaded) return
            loaded = true
            if (!file.exists()) return
            runCatching {
                val arr = JSONArray(file.readText())
                val list = mutableListOf<ScriptItem>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list += ScriptItem(
                        id = o.getLong("id"),
                        title = o.optString("title", "بدون عنوان"),
                        body = o.optString("body", ""),
                        createdAt = o.getLong("createdAt"),
                        updatedAt = o.getLong("updatedAt")
                    )
                }
                nextId = (list.maxOfOrNull { it.id } ?: 0L) + 1
                _scripts.value = list.sortedByDescending { it.updatedAt }
            }
        }
    }

    /** إضافة سكربت جديد — تُرجع العنصر المُنشأ */
    fun add(title: String, body: String): ScriptItem = synchronized(lock) {
        val now = System.currentTimeMillis()
        val item = ScriptItem(
            id = nextId,
            title = title.trim().ifBlank { "سكربت $nextId" },
            body = body.trim(),
            createdAt = now,
            updatedAt = now
        )
        nextId += 1
        _scripts.value = (listOf(item) + _scripts.value).sortedByDescending { it.updatedAt }
        persistAsync()
        item
    }

    /** تعديل سكربت موجود */
    fun update(item: ScriptItem) = synchronized(lock) {
        val updated = item.copy(
            title = item.title.trim(),
            body = item.body,
            updatedAt = System.currentTimeMillis()
        )
        _scripts.value = _scripts.value.map { if (it.id == updated.id) updated else it }
            .sortedByDescending { it.updatedAt }
        persistAsync()
    }

    /** حذف نهائي */
    fun delete(id: Long) = synchronized(lock) {
        _scripts.value = _scripts.value.filter { it.id != id }
        persistAsync()
    }

    /** كتابة غير حاجبة على خيط IO بنسخة لحظية من القائمة */
    private fun persistAsync() {
        val snapshot = _scripts.value
        scope.launch(Dispatchers.IO) {
            synchronized(lock) {
                runCatching {
                    val arr = JSONArray()
                    snapshot.forEach { s ->
                        arr.put(
                            JSONObject()
                                .put("id", s.id)
                                .put("title", s.title)
                                .put("body", s.body)
                                .put("createdAt", s.createdAt)
                                .put("updatedAt", s.updatedAt)
                        )
                    }
                    val tmp = File(dir, "scripts.json.tmp")
                    tmp.writeText(arr.toString())
                    // استبدال ذري عملياً — إما القديم أو الجديد كاملاً
                    if (file.exists()) file.delete()
                    tmp.renameTo(file)
                }
            }
        }
    }
}
