package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        UserProfileEntity::class,
        PrivateUserEntity::class,
        OutboxMessageEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class UniversDatabase : RoomDatabase() {
    abstract fun universDao(): UniversDao

    companion object {
        @Volatile
        private var instance: UniversDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE private_users ADD COLUMN notificationPreviewEnabled INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "ALTER TABLE private_users ADD COLUMN fcmTokensJson TEXT NOT NULL DEFAULT '{}'"
                )
            }
        }

        fun getInstance(context: Context): UniversDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    UniversDatabase::class.java,
                    "univers_chat_local.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
