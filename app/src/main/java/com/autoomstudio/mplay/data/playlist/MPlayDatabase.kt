package com.autoomstudio.mplay.data.playlist

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.autoomstudio.mplay.data.duplicates.DuplicateDao
import com.autoomstudio.mplay.data.duplicates.DuplicateOverrideEntity
import com.autoomstudio.mplay.data.duplicates.SongFingerprintEntity
import com.autoomstudio.mplay.data.stems.SeparationJobEntity
import com.autoomstudio.mplay.data.stems.StemDao
import com.autoomstudio.mplay.data.stems.StemSetEntity
import com.autoomstudio.mplay.data.tempo.SongTempoEntity
import com.autoomstudio.mplay.data.tempo.TempoDao

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        SongFingerprintEntity::class,
        DuplicateOverrideEntity::class,
        StemSetEntity::class,
        SeparationJobEntity::class,
        SongTempoEntity::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
    ],
)
abstract class MPlayDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao

    abstract fun duplicateDao(): DuplicateDao

    abstract fun stemDao(): StemDao

    abstract fun tempoDao(): TempoDao

    companion object {
        fun create(context: Context): MPlayDatabase =
            Room.databaseBuilder(context.applicationContext, MPlayDatabase::class.java, "mplay.db").build()
    }
}
