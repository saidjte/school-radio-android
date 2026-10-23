package com.saidjte.schoolradio.data.repository

import com.saidjte.schoolradio.data.local.AppDatabase
import com.saidjte.schoolradio.data.local.entity.PlaylistEntity
import com.saidjte.schoolradio.data.local.entity.PlaylistItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * مستودع قوائم التشغيل: إنشاء، تسمية، إضافة/حذف مقاطع، إعادة ترتيب، تكرار.
 * كل البيانات في Room — لا شبكة إطلاقاً.
 */
class PlaylistRepository(private val db: AppDatabase) {
    private val dao = db.playlistDao()

    val playlists: Flow<List<PlaylistEntity>> = dao.observeAll()

    fun observeItems(playlistId: Long): Flow<List<PlaylistItemEntity>> = dao.observeItems(playlistId)

    suspend fun create(name: String): Long = dao.insert(PlaylistEntity(name = name))

    suspend fun rename(playlist: PlaylistEntity, newName: String) =
        dao.update(playlist.copy(name = newName))

    suspend fun setRepeat(playlist: PlaylistEntity, repeat: Boolean) =
        dao.update(playlist.copy(repeat = repeat))

    suspend fun delete(playlistId: Long) = dao.deleteById(playlistId)

    /** إضافة مقطع إلى نهاية القائمة */
    suspend fun addTrack(playlistId: Long, trackId: Long) {
        val position = dao.items(playlistId).size
        dao.insertItem(PlaylistItemEntity(playlistId = playlistId, trackId = trackId, position = position))
    }

    suspend fun removeItem(itemId: Long) = dao.deleteItemById(itemId)

    /** إعادة ترتيب القائمة بعد تحريك عنصر (من index إلى index آخر) */
    suspend fun move(playlistId: Long, from: Int, to: Int) {
        val items = dao.items(playlistId).toMutableList()
        if (from !in items.indices || to !in items.indices) return
        val moved = items.removeAt(from)
        items.add(to, moved)
        dao.reorder(items)
    }

    /** عناصر القائمة مرتبة مع بيانات المقاطع — تستثني غير المتاح */
    suspend fun resolvedTracks(playlistId: Long): List<com.saidjte.schoolradio.data.local.entity.AudioTrackEntity> {
        val items = dao.items(playlistId)
        val tracks = db.audioTrackDao().byIds(items.map { it.trackId })
        val byId = tracks.associateBy { it.id }
        return items.mapNotNull { byId[it.trackId] }.filter { it.isAvailable }
    }

    suspend fun playlistById(id: Long): PlaylistEntity? = dao.byId(id)
}
