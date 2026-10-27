package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        VaultFolderEntity::class,
        VaultFileEntity::class,
        SecretNoteEntity::class,
        PasswordEntryEntity::class,
        PrivateBookmarkEntity::class,
        IntruderLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    companion object {
        @Volatile
        private var INSTANCE: VaultDatabase? = null

        /**
         * Explicit non-destructive migration from version 1 to version 2.
         * Deletes ONLY positively identified synthetic seed records (`%/seed_IMG_%` with hardcoded seed IV & checksum).
         * Does NOT strip `user_` prefixes from `originalName`, preserving all legitimate user filenames intact.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "DELETE FROM vault_files WHERE encryptedFilePath LIKE '%/seed_IMG_%' " +
                        "AND ivHex = 'a84f92c17e3b05d1994e2a11' " +
                        "AND sha256Checksum = '9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08'"
                )
            }
        }

        fun getInstance(context: Context): VaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VaultDatabase::class.java,
                    "hideu_encrypted_vault.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
