package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    // Folders
    @Query("SELECT * FROM vault_folders WHERE isDecoy = :isDecoy ORDER BY createdAt ASC")
    fun observeFolders(isDecoy: Boolean): Flow<List<VaultFolderEntity>>

    @Query("SELECT * FROM vault_folders WHERE isDecoy = :isDecoy ORDER BY createdAt ASC")
    suspend fun getFoldersSync(isDecoy: Boolean): List<VaultFolderEntity>

    @Query("SELECT COUNT(*) FROM vault_folders WHERE isDecoy = :isDecoy")
    suspend fun countFolders(isDecoy: Boolean): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: VaultFolderEntity): Long

    @Query("UPDATE vault_folders SET name = :newName WHERE id = :folderId")
    suspend fun renameFolder(folderId: Long, newName: String)

    @Query("DELETE FROM vault_folders WHERE id = :folderId")
    suspend fun deleteFolderById(folderId: Long)

    // Vault Files
    @Query("SELECT * FROM vault_files WHERE isDecoy = :isDecoy ORDER BY isFavorite DESC, createdAt DESC")
    fun observeAllFiles(isDecoy: Boolean): Flow<List<VaultFileEntity>>

    @Query("SELECT * FROM vault_files WHERE isDecoy = :isDecoy AND isTrashed = 0 ORDER BY createdAt DESC")
    suspend fun getActiveFilesSync(isDecoy: Boolean): List<VaultFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: VaultFileEntity): Long

    @Update
    suspend fun updateFile(file: VaultFileEntity)

    @Query("UPDATE vault_files SET folderId = :targetFolderId WHERE folderId = :oldFolderId")
    suspend fun reassignFolderFiles(oldFolderId: Long, targetFolderId: Long)

    @Query("DELETE FROM vault_files WHERE id = :fileId")
    suspend fun deleteFileById(fileId: Long)

    @Query("SELECT * FROM vault_files WHERE isDecoy = :isDecoy AND isTrashed = 1")
    suspend fun getTrashedFiles(isDecoy: Boolean): List<VaultFileEntity>

    /**
     * Deletes ONLY positively identified synthetic seed records from v1 (matching exact synthetic path,
     * static IV, static SHA-256, and synthetic IMG_2039..IMG_2050 names).
     * Never modifies or strips prefixes from legitimate user filenames.
     */
    @Query(
        "DELETE FROM vault_files WHERE encryptedFilePath LIKE '%/seed_IMG_%' " +
            "AND ivHex = 'a84f92c17e3b05d1994e2a11' " +
            "AND sha256Checksum = '9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08'"
    )
    suspend fun deleteSyntheticSeedFiles()

    // Secret Notes
    @Query("SELECT * FROM secret_notes WHERE isDecoy = :isDecoy ORDER BY isPinned DESC, updatedAt DESC")
    fun observeNotes(isDecoy: Boolean): Flow<List<SecretNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: SecretNoteEntity): Long

    @Update
    suspend fun updateNote(note: SecretNoteEntity)

    @Query("DELETE FROM secret_notes WHERE id = :noteId")
    suspend fun deleteNoteById(noteId: Long)

    // Passwords & Cards
    @Query("SELECT * FROM password_entries WHERE isDecoy = :isDecoy ORDER BY isFavorite DESC, updatedAt DESC")
    fun observePasswords(isDecoy: Boolean): Flow<List<PasswordEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPassword(entry: PasswordEntryEntity): Long

    @Update
    suspend fun updatePassword(entry: PasswordEntryEntity)

    @Query("DELETE FROM password_entries WHERE id = :entryId")
    suspend fun deletePasswordById(entryId: Long)

    // Private Bookmarks
    @Query("SELECT * FROM private_bookmarks WHERE isDecoy = :isDecoy ORDER BY createdAt ASC")
    fun observeBookmarks(isDecoy: Boolean): Flow<List<PrivateBookmarkEntity>>

    @Query("SELECT COUNT(*) FROM private_bookmarks WHERE isDecoy = :isDecoy")
    suspend fun countBookmarks(isDecoy: Boolean): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: PrivateBookmarkEntity): Long

    @Query("DELETE FROM private_bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmarkById(bookmarkId: Long)

    // Intruder / Security Audit Logs
    @Query("SELECT * FROM intruder_logs ORDER BY timestamp DESC LIMIT 50")
    fun observeIntruderLogs(): Flow<List<IntruderLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntruderLog(log: IntruderLogEntity): Long

    @Query("DELETE FROM intruder_logs")
    suspend fun clearIntruderLogs()
}
