package com.autoomstudio.mplay.data.playlist

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PlaylistEntity::class, PlaylistSongEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MPlayDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao

    companion object {
        fun create(context: Context): MPlayDatabase =
            Room.databaseBuilder(context.applicationContext, MPlayDatabase::class.java, "mplay.db").build()
    }
}
