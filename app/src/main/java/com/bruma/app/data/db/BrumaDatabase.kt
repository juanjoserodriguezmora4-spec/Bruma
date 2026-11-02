package com.bruma.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DownloadEntity::class, TrackEntity::class, PlaylistEntity::class, PlaylistTrackRef::class],
    version = 2,
    exportSchema = false,
)
abstract class BrumaDatabase : RoomDatabase() {
    abstract fun downloads(): DownloadDao

    abstract fun tracks(): TrackDao

    abstract fun playlists(): PlaylistDao

    companion object {
        /**
         * Versión 1 → 2: agrega a cada canción el enlace de donde vino.
         * Se rellena con las descargas que todavía están en la lista, para no perder nada
         * de tu biblioteca al actualizar.
         */
        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE tracks ADD COLUMN sourceUrl TEXT")
                    db.execSQL(
                        "UPDATE tracks SET sourceUrl = " +
                            "(SELECT url FROM downloads WHERE downloads.trackId = tracks.id LIMIT 1)",
                    )
                }
            }

        fun build(context: Context): BrumaDatabase =
            Room
                .databaseBuilder(context, BrumaDatabase::class.java, "bruma.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration(true)
                .build()
    }
}
