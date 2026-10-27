package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class VaultRepository(
    private val context: Context,
    private val vaultDao: VaultDao,
    val cryptoManager: CryptoManager
) {
    companion object {
        const val MIN_FREE_STORAGE_BYTES = 2L * 1024L * 1024L // 2 MB minimum free space buffer
    }

    val vaultBlobsDir: File
        get() = File(context.filesDir, "vault_blobs").apply { mkdirs() }

    val stagingDir: File
        get() = File(vaultBlobsDir, "staging").apply { mkdirs() }

    /**
     * Isolated directory inside noBackupFilesDir for short-lived video playback files.
     * Swept immediately on VaultRepository initialization (crash recovery), vault lock, and playback exit.
     */
    val tempPlaybackDir: File
        get() = File(context.noBackupFilesDir, "temp_playback").apply {
            mkdirs()
            setReadable(false, false)
            setReadable(true, true)
            setWritable(false, false)
            setWritable(true, true)
        }

    // Bounded in-memory LRU cache for decrypted thumbnails (evicted immediately on vault lock)
    private val thumbnailCache = object : LruCache<Long, ImageBitmap>(48) {}

    init {
        // Crash-recovery & startup sweep: remove any leftover staging .tmp or temp_playback files
        cleanupOrphanStagingAndPlaybackFiles()
    }

    fun observeFolders(isDecoy: Boolean): Flow<List<VaultFolderEntity>> =
        vaultDao.observeFolders(isDecoy)

    fun observeAllFiles(isDecoy: Boolean): Flow<List<VaultFileEntity>> =
        vaultDao.observeAllFiles(isDecoy)

    fun observeNotes(isDecoy: Boolean): Flow<List<SecretNoteEntity>> =
        vaultDao.observeNotes(isDecoy)

    fun observePasswords(isDecoy: Boolean): Flow<List<PasswordEntryEntity>> =
        vaultDao.observePasswords(isDecoy)

    fun observeBookmarks(isDecoy: Boolean): Flow<List<PrivateBookmarkEntity>> =
        vaultDao.observeBookmarks(isDecoy)

    fun observeIntruderLogs(): Flow<List<IntruderLogEntity>> =
        vaultDao.observeIntruderLogs()

    /**
     * Ensures the 6 default empty albums exist without seeding any synthetic demo files.
     * Cleans up only positively identified synthetic rows (`seed_IMG_*`) without touching user filenames.
     */
    suspend fun ensureDefaultFoldersAndBookmarks(isDecoy: Boolean) = withContext(Dispatchers.IO) {
        cleanupOrphanStagingAndPlaybackFiles()
        vaultDao.deleteSyntheticSeedFiles()

        if (vaultDao.countFolders(isDecoy) == 0) {
            val defaults = listOf(
                VaultFolderEntity(name = "Family", category = "PHOTO", accentHex = "#1F515C", isDecoy = isDecoy),
                VaultFolderEntity(name = "Documents", category = "DOCUMENT", accentHex = "#2B465E", isDecoy = isDecoy),
                VaultFolderEntity(name = "Travel", category = "PHOTO", accentHex = "#2E5180", isDecoy = isDecoy),
                VaultFolderEntity(name = "Screenshots", category = "PHOTO", accentHex = "#412854", isDecoy = isDecoy),
                VaultFolderEntity(name = "Receipts", category = "DOCUMENT", accentHex = "#3A2D4C", isDecoy = isDecoy),
                VaultFolderEntity(name = "Private", category = "PHOTO", accentHex = "#3A3154", isDecoy = isDecoy)
            )
            defaults.forEach { vaultDao.insertFolder(it) }
        }
    }

    /**
     * Evicts decrypted thumbnails from memory and deletes any temporary playback or staging files.
     * Invoked on vault lock, app backgrounding, and startup crash recovery.
     */
    fun clearSensitiveMemoryAndTempFiles() {
        synchronized(thumbnailCache) {
            thumbnailCache.evictAll()
        }
        cleanupOrphanStagingAndPlaybackFiles()
    }

    fun cleanupOrphanStagingAndPlaybackFiles() {
        try {
            stagingDir.listFiles()?.forEach { cryptoManager.secureDeleteFile(it) }
            tempPlaybackDir.listFiles()?.forEach { cryptoManager.secureDeleteFile(it) }
            // Also clean legacy filesDir/temp_playback if present from earlier builds
            File(context.filesDir, "temp_playback").listFiles()?.forEach { cryptoManager.secureDeleteFile(it) }
        } catch (_: Exception) {
            // Best-effort cleanup
        }
    }

    fun getAvailableDeviceStorageBytes(): Long {
        return try {
            val usable = context.filesDir.usableSpace
            if (usable > 0L) {
                usable
            } else {
                val stat = StatFs(context.filesDir.absolutePath)
                val statBytes = stat.availableBytes
                if (statBytes > 0L) statBytes else Long.MAX_VALUE
            }
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }

    suspend fun createFolder(name: String, category: String, accentHex: String, isDecoy: Boolean): Long {
        return vaultDao.insertFolder(
            VaultFolderEntity(
                name = name.trim(),
                category = category,
                accentHex = accentHex,
                isDecoy = isDecoy
            )
        )
    }

    suspend fun renameFolder(folderId: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isNotEmpty()) {
            vaultDao.renameFolder(folderId, trimmed)
        }
    }

    /**
     * Safely deletes an album by reassigning any contained files to fallbackFolderId first,
     * ensuring user media is never orphaned or lost.
     */
    suspend fun deleteFolderSafely(folderId: Long, fallbackFolderId: Long) {
        if (folderId != fallbackFolderId) {
            vaultDao.reassignFolderFiles(folderId, fallbackFolderId)
        }
        vaultDao.deleteFolderById(folderId)
    }

    suspend fun deleteFolder(folderId: Long) {
        vaultDao.deleteFolderById(folderId)
    }

    /**
     * Streams an arbitrary InputStream into an AES-256-GCM encrypted vault file using staged atomic writes.
     * Verifies available storage before writing and cleans up any partial staging file on IOException or cancellation.
     */
    suspend fun importStreamIntoVault(
        inputStream: InputStream,
        originalName: String,
        mimeType: String,
        category: String,
        folderId: Long,
        isDecoy: Boolean,
        expectedSizeBytes: Long = 0L,
        availableBytesOverride: Long? = null
    ): VaultFileEntity? = withContext(Dispatchers.IO) {
        val availableBytes = availableBytesOverride ?: getAvailableDeviceStorageBytes()
        val requiredBytes = expectedSizeBytes.coerceAtLeast(0L) + MIN_FREE_STORAGE_BYTES
        if (availableBytes < requiredBytes) {
            throw IOException("Insufficient storage space to safely encrypt file")
        }

        val destFile = File(vaultBlobsDir, "enc_${UUID.randomUUID()}.vlt")
        try {
            val meta = cryptoManager.encryptStreamToFile(inputStream, destFile, stagingDir)
            if (meta.originalSizeBytes <= 0L) {
                cryptoManager.secureDeleteFile(destFile)
                return@withContext null
            }

            val entity = VaultFileEntity(
                folderId = folderId,
                originalName = originalName.trim().ifEmpty { "FILE_${System.currentTimeMillis() % 100000}" },
                encryptedFilePath = meta.encryptedFilePath,
                mimeType = mimeType,
                category = category,
                originalSizeBytes = meta.originalSizeBytes,
                encryptedSizeBytes = meta.encryptedSizeBytes,
                ivHex = meta.ivHex,
                sha256Checksum = meta.sha256Checksum,
                notes = "",
                isDecoy = isDecoy
            )
            val id = vaultDao.insertFile(entity)
            entity.copy(id = id)
        } catch (e: Exception) {
            cryptoManager.secureDeleteFile(destFile)
            throw e
        }
    }

    /**
     * Streams a photo, video, or document from a content Uri into an AES-256-GCM encrypted file
     * using staged atomic writes without loading the entire file into heap memory.
     */
    suspend fun importUriIntoVault(
        uri: Uri,
        folderId: Long,
        fallbackCategory: String,
        isDecoy: Boolean
    ): VaultFileEntity? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val (rawName, expectedSize) = resolveDisplayNameAndSize(uri)
            val cleanName = rawName?.trim()?.takeIf { it.isNotEmpty() }
                ?: "FILE_${System.currentTimeMillis() % 100000}"
            val mimeType = resolver.getType(uri) ?: inferMimeFromFilename(cleanName, fallbackCategory)

            val category = when {
                mimeType.startsWith("image/") -> "PHOTO"
                mimeType.startsWith("video/") -> "VIDEO"
                mimeType.startsWith("audio/") -> "AUDIO"
                else -> fallbackCategory
            }

            resolver.openInputStream(uri)?.use { inputStream ->
                importStreamIntoVault(
                    inputStream = inputStream,
                    originalName = cleanName,
                    mimeType = mimeType,
                    category = category,
                    folderId = folderId,
                    isDecoy = isDecoy,
                    expectedSizeBytes = expectedSize
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Encrypts a captured camera Bitmap directly into the vault without writing plaintext to public storage.
     */
    suspend fun importCameraBitmapIntoVault(
        bitmap: Bitmap,
        folderId: Long,
        isDecoy: Boolean
    ): VaultFileEntity? = withContext(Dispatchers.IO) {
        try {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            val jpegBytes = out.toByteArray()
            if (jpegBytes.isEmpty()) return@withContext null

            val fileName = "CAM_${System.currentTimeMillis()}.jpg"
            ByteArrayInputStream(jpegBytes).use { input ->
                importStreamIntoVault(
                    inputStream = input,
                    originalName = fileName,
                    mimeType = "image/jpeg",
                    category = "PHOTO",
                    folderId = folderId,
                    isDecoy = isDecoy,
                    expectedSizeBytes = jpegBytes.size.toLong()
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun decryptFileBytes(file: VaultFileEntity): ByteArray = withContext(Dispatchers.IO) {
        val diskFile = File(file.encryptedFilePath)
        if (!diskFile.exists()) return@withContext ByteArray(0)
        return@withContext try {
            cryptoManager.decryptFileToBytes(diskFile)
        } catch (_: Exception) {
            ByteArray(0)
        }
    }

    /**
     * Loads a downsampled decrypted thumbnail for a photo or extracts a frame for an encrypted video,
     * caching the result in the bounded in-memory LruCache.
     */
    suspend fun loadThumbnailBitmap(file: VaultFileEntity, maxDimensionPx: Int = 360): ImageBitmap? = withContext(Dispatchers.IO) {
        synchronized(thumbnailCache) {
            thumbnailCache.get(file.id)?.let { return@withContext it }
        }

        val diskFile = File(file.encryptedFilePath)
        if (!diskFile.exists()) return@withContext null

        val decodedBitmap: Bitmap? = try {
            if (file.category == "VIDEO" || file.mimeType.startsWith("video/")) {
                val tempVid = File(tempPlaybackDir, "thumb_${file.id}_${UUID.randomUUID()}.mp4")
                try {
                    restrictOwnerOnlyPermissions(tempVid)
                    FileOutputStream(tempVid).use { out ->
                        cryptoManager.decryptFileToStream(diskFile, out)
                    }
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(tempVid.absolutePath)
                        retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } finally {
                        retriever.release()
                    }
                } finally {
                    cryptoManager.secureDeleteFile(tempVid)
                }
            } else if (file.category == "PHOTO" || file.mimeType.startsWith("image/")) {
                val bytes = cryptoManager.decryptFileToBytes(diskFile)
                if (bytes.isEmpty()) null else decodeDownsampledBitmap(bytes, maxDimensionPx)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }

        val imageBitmap = decodedBitmap?.asImageBitmap()
        if (imageBitmap != null) {
            synchronized(thumbnailCache) {
                thumbnailCache.put(file.id, imageBitmap)
            }
        }
        imageBitmap
    }

    private fun decodeDownsampledBitmap(bytes: ByteArray, maxDimensionPx: Int): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

        var sampleSize = 1
        while (boundsOptions.outWidth / sampleSize > maxDimensionPx * 2 ||
            boundsOptions.outHeight / sampleSize > maxDimensionPx * 2
        ) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    }

    private fun restrictOwnerOnlyPermissions(file: File) {
        try {
            if (!file.exists()) file.createNewFile()
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
        } catch (_: Exception) {
            // Ignore on filesystems that do not support POSIX chmod
        }
    }

    /**
     * Prepares a strictly scoped temporary file inside context.noBackupFilesDir/temp_playback with 0600 permissions
     * for video playback. Cleaned up on release, vault lock, backgrounding, or next app startup.
     */
    suspend fun prepareTempVideoPlaybackFile(file: VaultFileEntity): File? = withContext(Dispatchers.IO) {
        val diskFile = File(file.encryptedFilePath)
        if (!diskFile.exists()) return@withContext null
        val ext = file.originalName.substringAfterLast('.', "mp4").take(5)
        val tempFile = File(tempPlaybackDir, "play_${file.id}_${System.currentTimeMillis()}.$ext")
        try {
            restrictOwnerOnlyPermissions(tempFile)
            FileOutputStream(tempFile).use { out ->
                val written = cryptoManager.decryptFileToStream(diskFile, out)
                if (written <= 0L) {
                    cryptoManager.secureDeleteFile(tempFile)
                    return@withContext null
                }
            }
            tempFile
        } catch (_: Exception) {
            cryptoManager.secureDeleteFile(tempFile)
            null
        }
    }

    fun releaseTempVideoPlaybackFile(tempFile: File?) {
        if (tempFile != null) {
            cryptoManager.secureDeleteFile(tempFile)
        }
    }

    /**
     * Fail-closed export: decrypts the vault file directly to targetUri and verifies byte length.
     */
    suspend fun exportUnhideFileToUri(file: VaultFileEntity, targetUri: Uri): Boolean = withContext(Dispatchers.IO) {
        val diskFile = File(file.encryptedFilePath)
        if (!diskFile.exists()) return@withContext false
        try {
            var writtenBytes = 0L
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                writtenBytes = cryptoManager.decryptFileToStream(diskFile, out)
                out.flush()
            } ?: return@withContext false
            writtenBytes > 0L
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Exports all active vault files and their album assignments into a password-protected portable `.cvbackup`
     * archive encrypted with PBKDF2-HMAC-SHA256 + AES-256-GCM so users can recover their vault even after
     * uninstalling the app, switching phones, or losing the hardware-bound Android Keystore key.
     */
    suspend fun exportPortableBackupToUri(
        targetUri: Uri,
        backupPassword: String,
        isDecoy: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val activeFiles = vaultDao.getActiveFilesSync(isDecoy)
            val folders = vaultDao.getFoldersSync(isDecoy).associateBy { it.id }

            // Serialize archive into a temporary staging file before encrypting to targetUri
            val rawStagingArchive = File(stagingDir, "backup_staging_${UUID.randomUUID()}.bin")
            try {
                DataOutputStream(FileOutputStream(rawStagingArchive).buffered()).use { out ->
                    out.writeInt(1) // Archive schema version 1
                    out.writeInt(activeFiles.size)
                    for (entity in activeFiles) {
                        val diskFile = File(entity.encryptedFilePath)
                        val plainBytes = if (diskFile.exists()) cryptoManager.decryptFileToBytes(diskFile) else ByteArray(0)
                        val albumName = folders[entity.folderId]?.name ?: "Family"
                        out.writeUTF(entity.originalName)
                        out.writeUTF(entity.mimeType)
                        out.writeUTF(entity.category)
                        out.writeUTF(albumName)
                        out.writeLong(entity.createdAt)
                        out.writeInt(plainBytes.size)
                        out.write(plainBytes)
                    }
                    out.flush()
                }

                context.contentResolver.openOutputStream(targetUri)?.use { destOut ->
                    rawStagingArchive.inputStream().use { plainIn ->
                        cryptoManager.encryptPortableBackupStream(plainIn, destOut, backupPassword)
                    }
                } ?: throw IOException("Unable to open backup destination URI")

                activeFiles.size
            } finally {
                cryptoManager.secureDeleteFile(rawStagingArchive)
            }
        }
    }

    /**
     * Restores a password-protected portable `.cvbackup` archive into the current vault, re-encrypting
     * each file with the current device's Android Keystore AES-256-GCM key.
     */
    suspend fun restorePortableBackupFromUri(
        sourceUri: Uri,
        backupPassword: String,
        isDecoy: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            ensureDefaultFoldersAndBookmarks(isDecoy)
            val decryptedStaging = File(stagingDir, "restore_staging_${UUID.randomUUID()}.bin")
            try {
                context.contentResolver.openInputStream(sourceUri)?.use { encryptedIn ->
                    FileOutputStream(decryptedStaging).use { plainOut ->
                        cryptoManager.decryptPortableBackupStream(encryptedIn, plainOut, backupPassword)
                    }
                } ?: throw IOException("Unable to open backup source URI")

                val existingFolders = vaultDao.getFoldersSync(isDecoy).associateBy { it.name.lowercase() }.toMutableMap()
                val fallbackFolderId = existingFolders.values.firstOrNull()?.id ?: 1L
                var restoredCount = 0

                DataInputStream(decryptedStaging.inputStream().buffered()).use { dataIn ->
                    val schemaVersion = dataIn.readInt()
                    if (schemaVersion != 1) throw IOException("Unsupported backup version: $schemaVersion")
                    val count = dataIn.readInt()
                    repeat(count) {
                        val originalName = dataIn.readUTF()
                        val mimeType = dataIn.readUTF()
                        val category = dataIn.readUTF()
                        val albumName = dataIn.readUTF()
                        val createdAt = dataIn.readLong()
                        val payloadSize = dataIn.readInt()
                        if (payloadSize < 0 || payloadSize > 100 * 1024 * 1024) {
                            throw IOException("Invalid backup entry size")
                        }
                        val payload = ByteArray(payloadSize)
                        dataIn.readFully(payload)

                        val targetFolderId = existingFolders[albumName.lowercase()]?.id ?: run {
                            val newId = createFolder(albumName, category, "#4D8DFF", isDecoy)
                            existingFolders[albumName.lowercase()] = VaultFolderEntity(
                                id = newId,
                                name = albumName,
                                category = category,
                                isDecoy = isDecoy
                            )
                            newId
                        }.takeIf { it > 0 } ?: fallbackFolderId

                        val destFile = File(vaultBlobsDir, "enc_${UUID.randomUUID()}.vlt")
                        val meta = cryptoManager.encryptBytesToFile(payload, destFile)
                        vaultDao.insertFile(
                            VaultFileEntity(
                                folderId = targetFolderId,
                                originalName = originalName,
                                encryptedFilePath = meta.encryptedFilePath,
                                mimeType = mimeType,
                                category = category,
                                originalSizeBytes = meta.originalSizeBytes,
                                encryptedSizeBytes = meta.encryptedSizeBytes,
                                ivHex = meta.ivHex,
                                sha256Checksum = meta.sha256Checksum,
                                isDecoy = isDecoy,
                                createdAt = createdAt
                            )
                        )
                        restoredCount++
                    }
                }
                restoredCount
            } finally {
                cryptoManager.secureDeleteFile(decryptedStaging)
            }
        }
    }

    suspend fun toggleFileFavorite(file: VaultFileEntity) {
        vaultDao.updateFile(file.copy(isFavorite = !file.isFavorite))
    }

    suspend fun moveFileToTrash(file: VaultFileEntity) {
        vaultDao.updateFile(file.copy(isTrashed = true))
    }

    suspend fun restoreFileFromTrash(file: VaultFileEntity) {
        vaultDao.updateFile(file.copy(isTrashed = false))
    }

    suspend fun moveFileToFolder(file: VaultFileEntity, targetFolderId: Long) {
        vaultDao.updateFile(file.copy(folderId = targetFolderId))
    }

    suspend fun permanentlyShredFile(file: VaultFileEntity) = withContext(Dispatchers.IO) {
        synchronized(thumbnailCache) {
            thumbnailCache.remove(file.id)
        }
        val diskFile = File(file.encryptedFilePath)
        cryptoManager.secureDeleteFile(diskFile)
        vaultDao.deleteFileById(file.id)
    }

    suspend fun emptyTrash(isDecoy: Boolean) = withContext(Dispatchers.IO) {
        val trashed = vaultDao.getTrashedFiles(isDecoy)
        trashed.forEach { permanentlyShredFile(it) }
    }

    // Secret Notes
    suspend fun saveSecretNote(
        id: Long,
        title: String,
        plainContent: String,
        category: String,
        colorHex: String,
        isPinned: Boolean,
        isDecoy: Boolean
    ) {
        val encrypted = cryptoManager.encryptText(plainContent)
        val note = SecretNoteEntity(
            id = id,
            title = title.trim().ifBlank { "Untitled Secret Note" },
            encryptedContent = encrypted,
            category = category,
            colorHex = colorHex,
            isPinned = isPinned,
            isDecoy = isDecoy,
            updatedAt = System.currentTimeMillis()
        )
        vaultDao.insertNote(note)
    }

    fun decryptNoteContent(note: SecretNoteEntity): String {
        return cryptoManager.decryptText(note.encryptedContent)
    }

    suspend fun deleteSecretNote(noteId: Long) {
        vaultDao.deleteNoteById(noteId)
    }

    // Passwords & Cards
    suspend fun savePasswordEntry(
        id: Long,
        title: String,
        account: String,
        plainSecret: String,
        website: String,
        category: String,
        notes: String,
        isFavorite: Boolean,
        isDecoy: Boolean
    ) {
        val encryptedSecret = cryptoManager.encryptText(plainSecret)
        val entry = PasswordEntryEntity(
            id = id,
            title = title.trim(),
            accountOrUsername = account.trim(),
            encryptedSecret = encryptedSecret,
            websiteOrIssuer = website.trim(),
            category = category,
            notes = notes.trim(),
            isFavorite = isFavorite,
            isDecoy = isDecoy,
            updatedAt = System.currentTimeMillis()
        )
        vaultDao.insertPassword(entry)
    }

    fun decryptPasswordSecret(entry: PasswordEntryEntity): String {
        return cryptoManager.decryptText(entry.encryptedSecret)
    }

    suspend fun deletePasswordEntry(entryId: Long) {
        vaultDao.deletePasswordById(entryId)
    }

    // Bookmarks
    suspend fun addBookmark(title: String, url: String, category: String, accentHex: String, isDecoy: Boolean) {
        val formattedUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        vaultDao.insertBookmark(
            PrivateBookmarkEntity(
                title = title.trim(),
                url = formattedUrl.trim(),
                category = category,
                accentHex = accentHex,
                isDecoy = isDecoy
            )
        )
    }

    suspend fun deleteBookmark(id: Long) {
        vaultDao.deleteBookmarkById(id)
    }

    // Intruder / Security Audit Log
    suspend fun logSecurityEvent(enteredExpression: String, eventType: String, description: String) {
        vaultDao.insertIntruderLog(
            IntruderLogEntity(
                enteredExpression = enteredExpression,
                eventType = eventType,
                description = description
            )
        )
    }

    suspend fun clearSecurityLogs() {
        vaultDao.clearIntruderLogs()
    }

    private fun resolveDisplayNameAndSize(uri: Uri): Pair<String?, Long> {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    val name = if (nameIndex != -1) cursor.getString(nameIndex) else null
                    val size = if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
                    name to size
                } else null to 0L
            } ?: (null to 0L)
        } catch (_: Exception) {
            null to 0L
        }
    }

    private fun inferMimeFromFilename(name: String, fallbackCategory: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".mkv") -> "video/mp4"
            lower.endsWith(".pdf") -> "application/pdf"
            fallbackCategory == "PHOTO" -> "image/jpeg"
            fallbackCategory == "VIDEO" -> "video/mp4"
            else -> "application/octet-stream"
        }
    }
}
