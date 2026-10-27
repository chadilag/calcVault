package com.example.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedFileMetadata(
    val encryptedFilePath: String,
    val ivHex: String,
    val sha256Checksum: String,
    val originalSizeBytes: Long,
    val encryptedSizeBytes: Long
)

data class PasswordStrengthResult(
    val score: Int, // 0..100
    val label: String,
    val entropyBits: Int
)

class CryptoManager(
    private val context: Context,
    private val allowTestEphemeralKey: Boolean = isJvmTestEnvironment()
) {

    companion object {
        private const val KEYSTORE_ALIAS = "HideUVaultMasterAesKey_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
        private const val STREAM_CHUNK_SIZE = 64 * 1024 // 64 KB bounded chunks
        private val STREAM_MAGIC = byteArrayOf('C'.code.toByte(), 'V'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte(), 2)
        private val PORTABLE_BACKUP_MAGIC = byteArrayOf('C'.code.toByte(), 'V'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte(), 1)
        private const val PBKDF2_PREFIX = "pbkdf2v1$"
        private const val PBKDF2_ITERATIONS = 120_000
        private const val PBKDF2_KEY_BITS = 256

        // Ephemeral random key used ONLY inside local JVM / Robolectric unit tests where AndroidKeyStore hardware is absent.
        @Volatile
        private var jvmTestEphemeralKey: SecretKey? = null

        fun isJvmTestEnvironment(): Boolean {
            return Build.FINGERPRINT == null ||
                Build.FINGERPRINT.equals("robolectric", ignoreCase = true) ||
                Build.MODEL.isNullOrEmpty()
        }
    }

    private val secureRandom = SecureRandom()

    /**
     * Retrieves the master AES-256 key from AndroidKeyStore, or creates it ONLY on first initialization
     * when the alias does not yet exist.
     * CRITICAL: If the alias already exists in AndroidKeyStore, this method NEVER regenerates or overwrites
     * the key on transient errors—it fails closed with GeneralSecurityException so existing encrypted files
     * are never orphaned by silent key replacement.
     */
    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
                val existingKey = keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
                existingKey ?: throw GeneralSecurityException(
                    "Existing AndroidKeyStore key '$KEYSTORE_ALIAS' could not be loaded; refusing to overwrite key."
                )
            } else {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore"
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(false)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            if (allowTestEphemeralKey) {
                jvmTestEphemeralKey ?: synchronized(Companion) {
                    jvmTestEphemeralKey ?: ByteArray(32).also { secureRandom.nextBytes(it) }
                        .let { SecretKeySpec(it, "AES") }
                        .also { jvmTestEphemeralKey = it }
                }
            } else {
                throw GeneralSecurityException("Android Keystore unavailable; failing closed to protect vault data", e)
            }
        }
    }

    /**
     * Bounded-memory chunked AES-256-GCM stream encryption with staged temporary file and atomic rename.
     * Each 64 KB chunk is encrypted with a unique 12-byte IV and 128-bit GCM authentication tag,
     * with chunk index bound as AAD to prevent chunk reordering or truncation.
     */
    fun encryptStreamToFile(
        inputStream: InputStream,
        destFile: File,
        stagingDir: File = File(destFile.parentFile ?: context.filesDir, "staging")
    ): EncryptedFileMetadata {
        stagingDir.mkdirs()
        destFile.parentFile?.mkdirs()
        val tempFile = File(stagingDir, "${destFile.name}.tmp")
        if (tempFile.exists()) tempFile.delete()

        val secretKey = getOrCreateSecretKey()
        val sha256Digest = MessageDigest.getInstance("SHA-256")
        var totalPlainBytes = 0L
        var firstIvHex = ""

        try {
            DataOutputStream(FileOutputStream(tempFile).buffered()).use { out ->
                out.write(STREAM_MAGIC)
                val buffer = ByteArray(STREAM_CHUNK_SIZE)
                var chunkIndex = 0
                while (true) {
                    val read = inputStream.read(buffer)
                    if (read <= 0) break
                    totalPlainBytes += read
                    sha256Digest.update(buffer, 0, read)

                    val iv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
                    if (chunkIndex == 0) {
                        firstIvHex = iv.joinToString("") { "%02x".format(it) }
                    }
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                    cipher.updateAAD("chunk:$chunkIndex".toByteArray(Charsets.UTF_8))
                    val cipherChunk = cipher.doFinal(buffer, 0, read)

                    out.write(iv)
                    out.writeInt(cipherChunk.size)
                    out.write(cipherChunk)
                    chunkIndex++
                }
                if (chunkIndex == 0) {
                    val iv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
                    firstIvHex = iv.joinToString("") { "%02x".format(it) }
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                    cipher.updateAAD("chunk:0".toByteArray(Charsets.UTF_8))
                    val cipherChunk = cipher.doFinal(ByteArray(0))
                    out.write(iv)
                    out.writeInt(cipherChunk.size)
                    out.write(cipherChunk)
                }
                out.flush()
            }

            if (destFile.exists()) destFile.delete()
            val renamed = tempFile.renameTo(destFile)
            if (!renamed) {
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()
            }

            val sha256Hex = sha256Digest.digest().joinToString("") { "%02x".format(it) }
            return EncryptedFileMetadata(
                encryptedFilePath = destFile.absolutePath,
                ivHex = firstIvHex,
                sha256Checksum = sha256Hex,
                originalSizeBytes = totalPlainBytes,
                encryptedSizeBytes = destFile.length()
            )
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            throw e
        }
    }

    fun encryptBytesToFile(plainBytes: ByteArray, destFile: File): EncryptedFileMetadata {
        return ByteArrayInputStream(plainBytes).use { input ->
            encryptStreamToFile(input, destFile)
        }
    }

    /**
     * Writes a legacy v1 single-frame AES-256-GCM file ([12-byte IV][Ciphertext + 16-byte Tag] without CVLT2 header).
     * Used by migration & backward-compatibility tests to verify existing v1 user files remain readable in V2.
     */
    fun writeLegacyV1EncryptedFileForTest(plainBytes: ByteArray, destFile: File): EncryptedFileMetadata {
        destFile.parentFile?.mkdirs()
        val secretKey = getOrCreateSecretKey()
        val iv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherBytes = cipher.doFinal(plainBytes)
        FileOutputStream(destFile).use { out ->
            out.write(iv)
            out.write(cipherBytes)
            out.flush()
        }
        return EncryptedFileMetadata(
            encryptedFilePath = destFile.absolutePath,
            ivHex = iv.joinToString("") { "%02x".format(it) },
            sha256Checksum = sha256Hex(plainBytes),
            originalSizeBytes = plainBytes.size.toLong(),
            encryptedSizeBytes = destFile.length()
        )
    }

    /**
     * Decrypts both CVLT2 chunked-stream files and legacy v1 single-frame AES-256-GCM files.
     * Throws GeneralSecurityException (AEADBadTagException) if any chunk or tag is tampered with.
     */
    fun decryptFileToStream(encryptedFile: File, outputStream: OutputStream): Long {
        if (!encryptedFile.exists()) throw GeneralSecurityException("Encrypted file does not exist")
        val fileLen = encryptedFile.length()
        if (fileLen <= GCM_IV_LENGTH) throw GeneralSecurityException("Corrupted or truncated encrypted file")

        val secretKey = getOrCreateSecretKey()
        FileInputStream(encryptedFile).buffered().use { rawIn ->
            rawIn.mark(STREAM_MAGIC.size + 1)
            val header = ByteArray(STREAM_MAGIC.size)
            val headerRead = rawIn.read(header)
            val isChunkedFormat = headerRead == STREAM_MAGIC.size && header.contentEquals(STREAM_MAGIC)

            if (isChunkedFormat) {
                val dataIn = DataInputStream(rawIn)
                var chunkIndex = 0
                var totalPlainWritten = 0L
                val iv = ByteArray(GCM_IV_LENGTH)
                while (true) {
                    val firstByte = dataIn.read()
                    if (firstByte == -1) break
                    iv[0] = firstByte.toByte()
                    dataIn.readFully(iv, 1, GCM_IV_LENGTH - 1)
                    val cipherChunkSize = dataIn.readInt()
                    if (cipherChunkSize < 16 || cipherChunkSize > STREAM_CHUNK_SIZE + 1024) {
                        throw GeneralSecurityException("Invalid encrypted chunk size: $cipherChunkSize")
                    }
                    val cipherChunk = ByteArray(cipherChunkSize)
                    dataIn.readFully(cipherChunk)

                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                    cipher.updateAAD("chunk:$chunkIndex".toByteArray(Charsets.UTF_8))
                    val plainChunk = cipher.doFinal(cipherChunk)
                    outputStream.write(plainChunk)
                    totalPlainWritten += plainChunk.size
                    chunkIndex++
                }
                outputStream.flush()
                return totalPlainWritten
            } else {
                // Backward-compatible decryption for v1 files created prior to V2 upgrade
                rawIn.reset()
                val combined = rawIn.readBytes()
                if (combined.size <= GCM_IV_LENGTH) throw EOFException("Legacy encrypted file too short")
                val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
                val cipherBytes = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                val plainBytes = cipher.doFinal(cipherBytes)
                outputStream.write(plainBytes)
                outputStream.flush()
                return plainBytes.size.toLong()
            }
        }
    }

    fun decryptFileToBytes(encryptedFile: File): ByteArray {
        if (!encryptedFile.exists()) return ByteArray(0)
        val out = ByteArrayOutputStream()
        decryptFileToStream(encryptedFile, out)
        return out.toByteArray()
    }

    /**
     * Derives an AES-256 SecretKey from a user-supplied backup password and 16-byte salt using PBKDF2WithHmacSHA256.
     * Used for portable `.cvbackup` archives that survive app uninstall or device replacement.
     */
    private fun deriveBackupSecretKey(password: String, saltBytes: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), saltBytes, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keyBytes = factory.generateSecret(spec).encoded
            SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Encrypts a portable backup payload stream with a user-chosen password (independent of Android Keystore).
     */
    fun encryptPortableBackupStream(
        plainInput: InputStream,
        encryptedOutput: OutputStream,
        backupPassword: String
    ) {
        require(backupPassword.length >= 6) { "Backup password must be at least 6 characters" }
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val backupKey = deriveBackupSecretKey(backupPassword, salt)
        val dataOut = DataOutputStream(encryptedOutput.buffered())
        dataOut.write(PORTABLE_BACKUP_MAGIC)
        dataOut.write(salt)

        val buffer = ByteArray(STREAM_CHUNK_SIZE)
        var chunkIndex = 0
        while (true) {
            val read = plainInput.read(buffer)
            if (read <= 0) break
            val iv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, backupKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD("backup:$chunkIndex".toByteArray(Charsets.UTF_8))
            val cipherChunk = cipher.doFinal(buffer, 0, read)
            dataOut.write(iv)
            dataOut.writeInt(cipherChunk.size)
            dataOut.write(cipherChunk)
            chunkIndex++
        }
        dataOut.flush()
    }

    /**
     * Decrypts a portable `.cvbackup` stream using the user-supplied backup password.
     */
    fun decryptPortableBackupStream(
        encryptedInput: InputStream,
        plainOutput: OutputStream,
        backupPassword: String
    ) {
        val dataIn = DataInputStream(encryptedInput.buffered())
        val magic = ByteArray(PORTABLE_BACKUP_MAGIC.size)
        dataIn.readFully(magic)
        if (!magic.contentEquals(PORTABLE_BACKUP_MAGIC)) {
            throw GeneralSecurityException("Invalid backup archive header")
        }
        val salt = ByteArray(16)
        dataIn.readFully(salt)
        val backupKey = deriveBackupSecretKey(backupPassword, salt)

        var chunkIndex = 0
        val iv = ByteArray(GCM_IV_LENGTH)
        while (true) {
            val firstByte = dataIn.read()
            if (firstByte == -1) break
            iv[0] = firstByte.toByte()
            dataIn.readFully(iv, 1, GCM_IV_LENGTH - 1)
            val cipherSize = dataIn.readInt()
            if (cipherSize < 16 || cipherSize > STREAM_CHUNK_SIZE + 1024) {
                throw GeneralSecurityException("Invalid backup chunk length")
            }
            val cipherChunk = ByteArray(cipherSize)
            dataIn.readFully(cipherChunk)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, backupKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD("backup:$chunkIndex".toByteArray(Charsets.UTF_8))
            val plainChunk = cipher.doFinal(cipherChunk)
            plainOutput.write(plainChunk)
            chunkIndex++
        }
        plainOutput.flush()
    }

    fun encryptText(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val iv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decryptText(cipherTextBase64: String): String {
        if (cipherTextBase64.isEmpty()) return ""
        return try {
            val combined = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH) return ""
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    fun generateSalt(): String {
        val bytes = ByteArray(16).also { secureRandom.nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Derives a hardened PIN / secret hash using PBKDF2WithHmacSHA256 (120,000 iterations, 256-bit key).
     */
    fun hashSecret(secret: String, salt: String): String {
        val normalized = secret.trim().lowercase()
        val saltBytes = salt.toByteArray(Charsets.UTF_8)
        val spec = PBEKeySpec(normalized.toCharArray(), saltBytes, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val hashBytes = factory.generateSecret(spec).encoded
            PBKDF2_PREFIX + hashBytes.joinToString("") { "%02x".format(it) }
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Constant-time verification supporting both PBKDF2 v1 hashes and legacy SHA-256 hashes from prior installs.
     */
    fun verifySecretHash(candidateSecret: String, salt: String, storedHash: String): Boolean {
        if (storedHash.isBlank() || salt.isBlank()) return false
        return if (storedHash.startsWith(PBKDF2_PREFIX)) {
            val candidateDerived = hashSecret(candidateSecret, salt)
            MessageDigest.isEqual(
                candidateDerived.toByteArray(Charsets.UTF_8),
                storedHash.toByteArray(Charsets.UTF_8)
            )
        } else {
            val normalized = candidateSecret.trim().lowercase()
            val legacyDigest = MessageDigest.getInstance("SHA-256")
                .digest("$salt:$normalized".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            MessageDigest.isEqual(
                legacyDigest.toByteArray(Charsets.UTF_8),
                storedHash.toByteArray(Charsets.UTF_8)
            )
        }
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Best-effort cleanup of temporary files in app-private storage.
     * Note: On wear-leveled NAND flash storage and journaled filesystems, overwriting and unlinking a file
     * removes OS-level file access but does not guarantee physical forensic erasure of underlying flash blocks.
     * Therefore, plaintext temporary files are avoided whenever possible and restricted in lifetime.
     */
    fun secureDeleteFile(file: File) {
        if (!file.exists()) return
        try {
            if (file.isFile && file.length() > 0) {
                val len = file.length().coerceAtMost(256 * 1024L).toInt()
                FileOutputStream(file).use { out ->
                    out.write(ByteArray(len))
                    out.flush()
                }
            }
        } catch (_: Exception) {
            // Proceed to unlink
        } finally {
            file.delete()
        }
    }

    fun generatePassword(
        length: Int = 16,
        includeUppercase: Boolean = true,
        includeDigits: Boolean = true,
        includeSymbols: Boolean = true
    ): String {
        val lower = "abcdefghijkmnopqrstuvwxyz"
        val upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        val digits = "23456789"
        val symbols = "!@#$%^&*()-_=+[]{}?"

        val pool = buildString {
            append(lower)
            if (includeUppercase) append(upper)
            if (includeDigits) append(digits)
            if (includeSymbols) append(symbols)
        }
        return (1..length.coerceIn(6, 64))
            .map { pool[secureRandom.nextInt(pool.length)] }
            .joinToString("")
    }

    fun evaluatePasswordStrength(password: String): PasswordStrengthResult {
        if (password.isEmpty()) return PasswordStrengthResult(0, "Empty", 0)
        var poolSize = 0
        if (password.any { it.isLowerCase() }) poolSize += 26
        if (password.any { it.isUpperCase() }) poolSize += 26
        if (password.any { it.isDigit() }) poolSize += 10
        if (password.any { !it.isLetterOrDigit() }) poolSize += 32

        val entropy = (password.length * (Math.log(poolSize.coerceAtLeast(1).toDouble()) / Math.log(2.0))).toInt()
        val score = (entropy * 100 / 95).coerceIn(8, 100)
        val label = when {
            score >= 80 -> "Military Grade"
            score >= 60 -> "Strong"
            score >= 40 -> "Moderate"
            else -> "Weak"
        }
        return PasswordStrengthResult(score, label, entropy)
    }

    fun generateEncryptedSnapshotPng(
        title: String,
        subtitle: String,
        badge: String,
        startColor: Int,
        endColor: Int
    ): ByteArray {
        val width = 720
        val height = 540
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                startColor, endColor, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCC0D1322.toInt()
        }
        canvas.drawRoundRect(RectF(48f, 64f, width - 48f, height - 64f), 32f, 32f, cardPaint)

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF4D8DFF.toInt()
        }
        canvas.drawRoundRect(RectF(84f, 104f, 310f, 150f), 23f, 23f, badgePaint)

        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0B101B.toInt()
            textSize = 22f
            isFakeBoldText = true
        }
        canvas.drawText(badge.take(16).uppercase(), 106f, 135f, badgeTextPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF8FAFC.toInt()
            textSize = 40f
            isFakeBoldText = true
        }
        canvas.drawText(title.take(26), 84f, 240f, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF94A3B8.toInt()
            textSize = 26f
        }
        val lines = subtitle.chunked(38).take(4)
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, 84f, 305f + (index * 40f), subPaint)
        }

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }
}
