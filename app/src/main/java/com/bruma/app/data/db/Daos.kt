package com.bruma.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun all(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: Long): DownloadEntity?

    @Insert
    suspend fun insert(d: DownloadEntity): Long

    @Update
    suspend fun update(d: DownloadEntity)

    @Query("UPDATE downloads SET state = :state, message = :message, errorType = :errorType WHERE id = :id")
    suspend fun setState(
        id: Long,
        state: String,
        message: String?,
        errorType: String?,
    )

    @Query(
        "UPDATE downloads SET state = :state, progress = :progress, totalBytes = :total, speed = :speed, eta = :eta, message = :message, errorType = NULL WHERE id = :id",
    )
    suspend fun progress(
        id: Long,
        state: String,
        progress: Float,
        total: Long,
        speed: Long,
        eta: Long,
        message: String?,
    )

    @Query(
        "UPDATE downloads SET state = :state, message = :message, errorType = :errorType, attempts = :attempts, speed = 0 WHERE id = :id",
    )
    suspend fun problem(
        id: Long,
        state: String,
        message: String?,
        errorType: String?,
        attempts: Int,
    )

    @Query(
        "UPDATE downloads SET state = 'COMPLETED', progress = 1, trackId = :trackId, title = :title, totalBytes = :size, message = NULL, errorType = NULL, speed = 0 WHERE id = :id",
    )
    suspend fun complete(
        id: Long,
        trackId: Long,
        title: String,
        size: Long,
    )

    @Query("UPDATE downloads SET title = :title, author = :author WHERE id = :id")
    suspend fun setTitle(
        id: Long,
        title: String,
        author: String?,
    )

    @Query("UPDATE downloads SET state = 'QUEUED', message = NULL, errorType = NULL WHERE id = :id")
    suspend fun requeue(id: Long)

    @Query("UPDATE downloads SET state = 'QUEUED', message = NULL, errorType = NULL, attempts = 0, progress = 0 WHERE id = :id")
    suspend fun resetForRetry(id: Long)

    @Query("SELECT * FROM downloads WHERE state IN ('QUEUED','ANALYZING','DOWNLOADING','PROCESSING')")
    suspend fun running(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE state = 'PAUSED'")
    suspend fun paused(): List<DownloadEntity>

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM downloads WHERE state = 'COMPLETED'")
    suspend fun clearCompleted()
}

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY addedAt DESC")
    fun all(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun get(id: Long): TrackEntity?

    @Insert
    suspend fun insert(t: TrackEntity): Long

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE tracks SET lastPositionMs = :pos WHERE id = :id")
    suspend fun savePosition(
        id: Long,
        pos: Long,
    )

    @Query("UPDATE tracks SET playCount = playCount + 1 WHERE id = :id")
    suspend fun incPlay(id: Long)

    @Query("UPDATE tracks SET title = :title, artist = :artist WHERE id = :id")
    suspend fun updateTags(
        id: Long,
        title: String,
        artist: String,
    )
}

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT p.id AS id, p.name AS name, p.createdAt AS createdAt,
               (SELECT COUNT(*) FROM playlist_tracks x WHERE x.playlistId = p.id) AS itemCount,
               (SELECT t.coverPath FROM playlist_tracks y JOIN tracks t ON t.id = y.trackId
                 WHERE y.playlistId = p.id ORDER BY y.position LIMIT 1) AS cover
        FROM playlists p ORDER BY p.createdAt DESC
        """,
    )
    fun summaries(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observe(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT t.* FROM tracks t JOIN playlist_tracks pt ON pt.trackId = t.id WHERE pt.playlistId = :pid ORDER BY pt.position")
    fun tracksOf(pid: Long): Flow<List<TrackEntity>>

    @Insert
    suspend fun insertPlaylist(p: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(
        id: Long,
        name: String,
    )

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addRef(ref: PlaylistTrackRef)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :pid")
    suspend fun maxPos(pid: Long): Int

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :pid ORDER BY position")
    suspend fun refs(pid: Long): List<PlaylistTrackRef>

    @Update
    suspend fun updateRefs(refs: List<PlaylistTrackRef>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :pid AND trackId = :tid")
    suspend fun removeRef(
        pid: Long,
        tid: Long,
    )

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :pid")
    suspend fun clearPlaylist(pid: Long)

    @Query("DELETE FROM playlist_tracks WHERE trackId = :tid")
    suspend fun removeTrackEverywhere(tid: Long)
}
