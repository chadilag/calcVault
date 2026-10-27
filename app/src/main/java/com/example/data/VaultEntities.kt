package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class VaultFileCategory(val label: String) {
    PHOTO("Photos"),
    VIDEO("Videos"),
    AUDIO("Audio"),
    DOCUMENT("Documents")
}

@Entity(tableName = "vault_folders")
data class VaultFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String, // PHOTO, VIDEO, AUDIO, DOCUMENT, CUSTOM
    val accentHex: String = "#00E5FF",
    val isDecoy: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "vault_files")
data class VaultFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    val originalName: String,
    val encryptedFilePath: String,
    val mimeType: String,
    val category: String, // PHOTO, VIDEO, AUDIO, DOCUMENT
    val originalSizeBytes: Long,
    val encryptedSizeBytes: Long,
    val ivHex: String,
    val sha256Checksum: String,
    val notes: String = "",
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
    val isDecoy: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "secret_notes")
data class SecretNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val encryptedContent: String,
    val category: String = "Personal", // Personal, Finance, Ideas, Journal, Checklist
    val colorHex: String = "#00E5FF",
    val isPinned: Boolean = false,
    val isDecoy: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "password_entries")
data class PasswordEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val accountOrUsername: String,
    val encryptedSecret: String,
    val websiteOrIssuer: String = "",
    val category: String = "LOGIN", // LOGIN, CARD, WIFI, CRYPTO
    val notes: String = "",
    val isFavorite: Boolean = false,
    val isDecoy: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "private_bookmarks")
data class PrivateBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val category: String = "Private",
    val accentHex: String = "#00E5FF",
    val isDecoy: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "intruder_logs")
data class IntruderLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val enteredExpression: String,
    val eventType: String, // FAILED_UNLOCK, DECOY_TRIGGERED, RECOVERY_OPENED, PANIC_LOCK
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)
