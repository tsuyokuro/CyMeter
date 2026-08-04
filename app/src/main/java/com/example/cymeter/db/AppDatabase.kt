package com.example.cymeter.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

@Database(entities = [LocationPoint::class, Session::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbName = "cymeter_database"
                val externalFilesDirs = context.getExternalFilesDirs(null)
                
                // If a second directory is available (usually SD card), use it
                val dbFile = if (externalFilesDirs != null && externalFilesDirs.size > 1 && externalFilesDirs[1] != null) {
                    File(externalFilesDirs[1], dbName).absolutePath
                } else {
                    dbName
                }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    dbFile,
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
