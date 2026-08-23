package io.github.tsuyokuro.cymeter.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

@Database(entities = [LocationPoint::class, Session::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun sessionDao(): SessionDao

    fun checkpoint() {
        this.openHelper.writableDatabase.execSQL("PRAGMA checkpoint(FULL)")
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Table: sessions
                if (!columnExists(db, "sessions", "avgSpeed")) {
                    db.execSQL("ALTER TABLE sessions ADD COLUMN avgSpeed REAL NOT NULL DEFAULT 0.0")
                }
                if (!columnExists(db, "sessions", "totalDistance")) {
                    db.execSQL("ALTER TABLE sessions ADD COLUMN totalDistance REAL NOT NULL DEFAULT 0.0")
                }
                if (!columnExists(db, "sessions", "maxSpeed")) {
                    db.execSQL("ALTER TABLE sessions ADD COLUMN maxSpeed REAL NOT NULL DEFAULT 0.0")
                }
                if (!columnExists(db, "sessions", "totalMovingTime")) {
                    db.execSQL("ALTER TABLE sessions ADD COLUMN totalMovingTime INTEGER NOT NULL DEFAULT 0")
                }

                // Table: location_points
                if (!columnExists(db, "location_points", "avgSpeed")) {
                    db.execSQL("ALTER TABLE location_points ADD COLUMN avgSpeed REAL NOT NULL DEFAULT 0.0")
                }
                if (!columnExists(db, "location_points", "totalDistanceMeters")) {
                    db.execSQL("ALTER TABLE location_points ADD COLUMN totalDistanceMeters REAL NOT NULL DEFAULT 0.0")
                }
                if (!columnExists(db, "location_points", "movingTimeMillis")) {
                    db.execSQL("ALTER TABLE location_points ADD COLUMN movingTimeMillis INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                if (!columnExists(db, "sessions", "totalMovingTime")) {
                    db.execSQL("ALTER TABLE sessions ADD COLUMN totalMovingTime INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        private fun columnExists(
            db: androidx.sqlite.db.SupportSQLiteDatabase,
            tableName: String,
            columnName: String
        ): Boolean {
            db.query("PRAGMA table_info($tableName)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex == -1) return false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex).trim()
                            .equals(columnName.trim(), ignoreCase = true)
                    ) return true
                }
            }
            return false
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbName = "cymeter_database"
                val externalFilesDirs = context.getExternalFilesDirs(null)

                // If a second directory is available (usually SD card), use it
                val dbFile =
                    if (externalFilesDirs != null && externalFilesDirs.size > 1 && externalFilesDirs[1] != null) {
                        File(externalFilesDirs[1], dbName).absolutePath
                    } else {
                        dbName
                    }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    dbFile,
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun getDatabasePath(context: Context): String {
            val dbName = "cymeter_database"
            val externalFilesDirs = context.getExternalFilesDirs(null)
            return if (externalFilesDirs != null && externalFilesDirs.size > 1 && externalFilesDirs[1] != null) {
                File(externalFilesDirs[1], dbName).absolutePath
            } else {
                context.getDatabasePath(dbName).absolutePath
            }
        }

        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
