package com.bruma.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DownloadEntity::class, TrackEntity::class, PlaylistEntity::class, PlaylistTrackRef::class],
    version = 1,
    exportSchema = false,
)
abstract class BrumaDatabase : RoomDatabase() {
    abstract fun downloads(): DownloadDao

    abstract fun tracks(): TrackDao

    abstract fun playlists(): PlaylistDao

    companion object {
        fun build(context: Context): BrumaDatabase =
            Room
                .databaseBuilder(context, BrumaDatabase::class.java, "bruma.db")
                .fallbackToDestructiveMigration(true)
                .build()
    }
}
