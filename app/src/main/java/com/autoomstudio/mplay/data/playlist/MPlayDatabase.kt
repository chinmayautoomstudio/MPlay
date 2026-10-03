package com.autoomstudio.mplay.data.playlist

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.autoomstudio.mplay.data.duplicates.DuplicateDao
import com.autoomstudio.mplay.data.duplicates.DuplicateOverrideEntity
import com.autoomstudio.mplay.data.duplicates.SongFingerprintEntity

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        SongFingerprintEntity::class,
        DuplicateOverrideEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class MPlayDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao

    abstract fun duplicateDao(): DuplicateDao

    companion object {
        fun create(context: Context): MPlayDatabase =
            Room.databaseBuilder(context.applicationContext, MPlayDatabase::class.java, "mplay.db").build()
    }
}
