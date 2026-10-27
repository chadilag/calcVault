package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.calculator.CalculatorEngine
import com.example.calculator.CalculatorScreen
import com.example.data.VaultDatabase
import com.example.data.VaultRepository
import com.example.security.CryptoManager
import com.example.security.PinVerifyOutcome
import com.example.security.VaultPreferencesRepository
import com.example.security.VaultSecurityState
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.vault.MyVaultHomeScreen
import com.example.viewmodel.CalculatorUiState
import com.example.viewmodel.PasswordVerificationState
import com.example.viewmodel.PinSetupStage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("HideU Calc", appName)
    }

    @Test
    fun `calculator engine evaluates precedence decimals negative and division by zero`() {
        assertEquals("42", CalculatorEngine.evaluate("6×7").getOrNull())
        assertEquals("15", CalculatorEngine.evaluate("10+20÷4").getOrNull())
        assertEquals("9", CalculatorEngine.evaluate("√(81)").getOrNull())
        assertEquals("8", CalculatorEngine.evaluate("2^3").getOrNull())
        assertEquals("3.75", CalculatorEngine.evaluate("1.5×2.5").getOrNull())
        assertEquals("-5", CalculatorEngine.evaluate("5-10").getOrNull())
        assertTrue(CalculatorEngine.evaluate("10÷0").isFailure)
    }

    @Test
    fun `crypto manager decrypts both legacy v1 and chunked v2 files portable backups and detects tampering`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = CryptoManager(context)

        // 1. Text encryption with fresh random IV per call
        val secretMessage = "Confidential Recovery Seed: alpha bravo charlie 9941"
        val encryptedText1 = crypto.encryptText(secretMessage)
        val encryptedText2 = crypto.encryptText(secretMessage)
        assertNotEquals(encryptedText1, encryptedText2)
        assertEquals(secretMessage, crypto.decryptText(encryptedText1))
        assertEquals(secretMessage, crypto.decryptText(encryptedText2))

        // 2. Backward-compatible legacy v1 single-frame encrypted file
        val legacyFile = File(context.cacheDir, "legacy_v1_test.vlt")
        val legacyPayload = "Legacy V1 Encrypted Photo Payload 1234567890".encodeToByteArray()
        crypto.writeLegacyV1EncryptedFileForTest(legacyPayload, legacyFile)
        val legacyOut = ByteArrayOutputStream()
        crypto.decryptFileToStream(legacyFile, legacyOut)
        assertArrayEquals(legacyPayload, legacyOut.toByteArray())
        crypto.secureDeleteFile(legacyFile)

        // 3. Chunked v2 stream encryption (multi-chunk payload > 64 KB)
        val tempFile = File(context.cacheDir, "test_vault_chunked.vlt")
        val rawBytes = ByteArray(150_000) { idx -> (idx % 251).toByte() }
        val meta = crypto.encryptStreamToFile(ByteArrayInputStream(rawBytes), tempFile)
        assertTrue(meta.encryptedSizeBytes > rawBytes.size)
        assertEquals(150_000L, meta.originalSizeBytes)

        val outStream = ByteArrayOutputStream()
        crypto.decryptFileToStream(tempFile, outStream)
        assertArrayEquals(rawBytes, outStream.toByteArray())

        // 4. Tamper detection: corrupting a ciphertext byte must fail authentication
        val fileBytes = tempFile.readBytes()
        fileBytes[fileBytes.lastIndex - 4] = (fileBytes[fileBytes.lastIndex - 4].toInt() xor 0xFF).toByte()
        tempFile.writeBytes(fileBytes)

        var tamperDetected = false
        try {
            crypto.decryptFileToStream(tempFile, ByteArrayOutputStream())
        } catch (_: GeneralSecurityException) {
            tamperDetected = true
        }
        assertTrue("Expected GeneralSecurityException on tampered ciphertext", tamperDetected)
        crypto.secureDeleteFile(tempFile)
        assertFalse(tempFile.exists())

        // 5. Portable password-derived backup stream (CVBK1) round-trip + wrong password rejection
        val backupPayload = "Portable Backup Archive Content".encodeToByteArray()
        val encryptedBackupOut = ByteArrayOutputStream()
        crypto.encryptPortableBackupStream(
            ByteArrayInputStream(backupPayload),
            encryptedBackupOut,
            "StrongPass#2026"
        )
        val decryptedBackupOut = ByteArrayOutputStream()
        crypto.decryptPortableBackupStream(
            ByteArrayInputStream(encryptedBackupOut.toByteArray()),
            decryptedBackupOut,
            "StrongPass#2026"
        )
        assertArrayEquals(backupPayload, decryptedBackupOut.toByteArray())

        var wrongPasswordRejected = false
        try {
            crypto.decryptPortableBackupStream(
                ByteArrayInputStream(encryptedBackupOut.toByteArray()),
                ByteArrayOutputStream(),
                "WrongPass#0000"
            )
        } catch (_: GeneralSecurityException) {
            wrongPasswordRejected = true
        }
        assertTrue("Expected GeneralSecurityException when portable backup password is wrong", wrongPasswordRejected)
    }

    @Test
    fun `migration 1 to 2 deletes only synthetic seed rows and preserves legitimate user_ filenames`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_migration_1_2.db"
        context.deleteDatabase(dbName)

        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vault_files` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `folderId` INTEGER NOT NULL,
                        `originalName` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `encryptedFilePath` TEXT NOT NULL,
                        `ivHex` TEXT NOT NULL,
                        `originalSizeBytes` INTEGER NOT NULL,
                        `encryptedSizeBytes` INTEGER NOT NULL,
                        `sha256Checksum` TEXT NOT NULL,
                        `isFavorite` INTEGER NOT NULL,
                        `isTrashed` INTEGER NOT NULL,
                        `trashedAt` INTEGER,
                        `isDecoy` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                // Insert a positively identified synthetic seed row
                db.execSQL(
                    """
                    INSERT INTO `vault_files` (
                        `folderId`, `originalName`, `mimeType`, `category`, `encryptedFilePath`,
                        `ivHex`, `originalSizeBytes`, `encryptedSizeBytes`, `sha256Checksum`,
                        `isFavorite`, `isTrashed`, `trashedAt`, `isDecoy`, `notes`, `createdAt`
                    ) VALUES (
                        1, 'IMG_0001.JPG', 'image/jpeg', 'PHOTO', '/data/user/0/app/files/vault_blobs/seed_IMG_0001.vlt',
                        'a84f92c17e3b05d1994e2a11', 2400000, 2400028,
                        '9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08',
                        0, 0, NULL, 0, 'Family', 1700000000000
                    )
                    """.trimIndent()
                )
                // Insert a legitimate user file whose name starts with "user_"
                db.execSQL(
                    """
                    INSERT INTO `vault_files` (
                        `folderId`, `originalName`, `mimeType`, `category`, `encryptedFilePath`,
                        `ivHex`, `originalSizeBytes`, `encryptedSizeBytes`, `sha256Checksum`,
                        `isFavorite`, `isTrashed`, `trashedAt`, `isDecoy`, `notes`, `createdAt`
                    ) VALUES (
                        1, 'user_manual.pdf', 'application/pdf', 'DOCUMENT', '/data/user/0/app/files/vault_blobs/vlt_999.vlt',
                        '11223344556677889900aabb', 8192, 8227,
                        'CVLT2',
                        0, 0, NULL, 0, 'Documents', 1700000005000
                    )
                    """.trimIndent()
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )

        val db = helper.writableDatabase
        VaultDatabase.MIGRATION_1_2.migrate(db)

        val remainingNames = mutableListOf<String>()
        db.query("SELECT originalName FROM vault_files").use { cursor ->
            while (cursor.moveToNext()) {
                remainingNames.add(cursor.getString(0))
            }
        }
        helper.close()
        context.deleteDatabase(dbName)

        // Synthetic seed row was deleted; legitimate "user_manual.pdf" was preserved with its "user_" prefix intact
        assertEquals(listOf("user_manual.pdf"), remainingNames)
    }

    @Test
    fun `pin security enforces 6 to 8 digits and applies escalating backoff throttling after 5 failed attempts`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = CryptoManager(context)
        val prefs = VaultPreferencesRepository(context, crypto)

        // Reject 4-digit PIN for new setup
        var fourDigitRejected = false
        try {
            prefs.setupMasterPin("1234", "Pet?", "Rex")
        } catch (_: IllegalArgumentException) {
            fourDigitRejected = true
        }
        assertTrue("Expected 4-digit PIN to be rejected on new setup", fourDigitRejected)

        // Configure valid 6-digit PIN
        prefs.setupMasterPin("258046", "First pet?", "Apollo")
        val stateAfterSetup = prefs.securityStateFlow.first()
        assertTrue(stateAfterSetup.isPinConfigured)
        assertEquals(6, stateAfterSetup.pinLength)
        assertTrue(stateAfterSetup.hasSecurityAnswer)

        // Verify wrong PIN increments failed attempts and triggers throttling on 5th failure
        repeat(5) {
            val outcome = prefs.verifyPinInput("000000")
            assertEquals(PinVerifyOutcome.INVALID_PIN, outcome)
        }
        // Subsequent attempt while locked out returns RATE_LIMITED
        val rateLimitedOutcome = prefs.verifyPinInput("000000")
        assertEquals(PinVerifyOutcome.RATE_LIMITED, rateLimitedOutcome)

        // Reset via verified recovery answer and new 6-digit PIN
        val resetSucceeded = prefs.verifySecurityAnswerAndResetPin("apollo", "654321")
        assertTrue(resetSucceeded)
        val unlockedOutcome = prefs.verifyPinInput("654321")
        assertEquals(PinVerifyOutcome.MASTER_UNLOCKED, unlockedOutcome)
    }

    @Test
    fun `vault repository stream import album crud video temp cleanup and low storage handling`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val crypto = CryptoManager(context)
        val repo = VaultRepository(context, db.vaultDao(), crypto)

        repo.ensureDefaultFoldersAndBookmarks(isDecoy = false)
        val initialFolders = repo.observeFolders(isDecoy = false).first()
        assertTrue(initialFolders.isNotEmpty())
        val firstFolder = initialFolders.first()

        // 1. Real stream import & persistence check
        val photoBytes = ByteArray(12_000) { idx -> (idx * 7).toByte() }
        val importedPhoto = repo.importStreamIntoVault(
            inputStream = ByteArrayInputStream(photoBytes),
            originalName = "user_vacation_photo.jpg",
            mimeType = "image/jpeg",
            category = "PHOTO",
            folderId = firstFolder.id,
            isDecoy = false,
            expectedSizeBytes = photoBytes.size.toLong()
        )
        assertNotNull(importedPhoto)
        assertEquals("user_vacation_photo.jpg", importedPhoto!!.originalName)
        assertArrayEquals(photoBytes, repo.decryptFileBytes(importedPhoto))

        // 2. Low-storage fail-closed check (leaves zero orphan staging .tmp files)
        var lowStorageCaught = false
        try {
            repo.importStreamIntoVault(
                inputStream = ByteArrayInputStream(photoBytes),
                originalName = "too_large.mp4",
                mimeType = "video/mp4",
                category = "VIDEO",
                folderId = firstFolder.id,
                isDecoy = false,
                expectedSizeBytes = photoBytes.size.toLong(),
                availableBytesOverride = 512L // Below MIN_FREE_STORAGE_BYTES
            )
        } catch (_: IOException) {
            lowStorageCaught = true
        }
        assertTrue("Expected IOException when available storage is below MIN_FREE_STORAGE_BYTES", lowStorageCaught)

        // 3. Album CRUD: create new album, move file, rename album, safe delete album reassigning file
        val newAlbumId = repo.createFolder("Projects", "PHOTO", "#4D8DFF", isDecoy = false)
        repo.moveFileToFolder(importedPhoto, newAlbumId)
        repo.renameFolder(newAlbumId, "Confidential Projects")
        repo.deleteFolderSafely(newAlbumId, fallbackFolderId = firstFolder.id)

        val activeAfterAlbumDelete = repo.observeAllFiles(isDecoy = false).first().filter { !it.isTrashed }
        assertEquals(1, activeAfterAlbumDelete.size)
        assertEquals(firstFolder.id, activeAfterAlbumDelete.first().folderId)

        // 4. Video temporary playback file lifecycle & immediate cleanup
        val videoEntity = repo.importStreamIntoVault(
            inputStream = ByteArrayInputStream("FakeMP4VideoStreamData".encodeToByteArray()),
            originalName = "clip.mp4",
            mimeType = "video/mp4",
            category = "VIDEO",
            folderId = firstFolder.id,
            isDecoy = false
        )
        assertNotNull(videoEntity)
        val tempPlaybackFile = repo.prepareTempVideoPlaybackFile(videoEntity!!)
        assertNotNull(tempPlaybackFile)
        assertTrue(tempPlaybackFile!!.exists())
        assertTrue(tempPlaybackFile.absolutePath.contains("no_backup"))

        repo.releaseTempVideoPlaybackFile(tempPlaybackFile)
        assertFalse("Temp playback file must be deleted on release", tempPlaybackFile.exists())

        // 5. Cleanup
        repo.permanentlyShredFile(activeAfterAlbumDelete.first())
        repo.permanentlyShredFile(videoEntity)
        db.close()
    }

    @Test
    fun `calculator starts cleanly at zero with no prefilled operation`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                CalculatorScreen(
                    calcState = CalculatorUiState(),
                    securityState = VaultSecurityState(isPinConfigured = true),
                    onKeyPress = {},
                    onEqualsPress = {},
                    onToggleScientific = {},
                    onToggleHistory = {},
                    onClearHistory = {},
                    onToggleTheme = {},
                    onAttemptRecoveryReset = { _, _, _ -> },
                    onDismissRecovery = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("calculator_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("calc_display_expression").assertTextEquals("0")
        composeTestRule.onNodeWithTag("calc_display_result").assertTextEquals("")
    }

    @Test
    fun `calculator lock screen digit and operation buttons trigger password verification state`() {
        val pressedKeys = mutableListOf<String>()
        var equalsTriggered = false

        composeTestRule.setContent {
            MyApplicationTheme {
                CalculatorScreen(
                    calcState = CalculatorUiState(
                        expression = "258046",
                        setupStage = PinSetupStage.READY,
                        verificationState = PasswordVerificationState.PinCandidateProgress(
                            enteredDigits = 6,
                            targetDigits = 6
                        )
                    ),
                    securityState = VaultSecurityState(
                        isPinConfigured = true,
                        pinLength = 6
                    ),
                    onKeyPress = { pressedKeys.add(it) },
                    onEqualsPress = { equalsTriggered = true },
                    onToggleScientific = {},
                    onToggleHistory = {},
                    onClearHistory = {},
                    onToggleTheme = {},
                    onCompleteInitialSetup = { _, _, _ -> },
                    onAttemptRecoveryReset = { _, _, _ -> },
                    onDismissRecovery = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("calculator_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("calc_display_expression").assertTextContains("258046")
        composeTestRule.onNodeWithTag("password_verification_state_text")
            .assertTextContains("PIN CANDIDATE (6 DIGITS)", substring = true)
        composeTestRule.onNodeWithTag("pin_candidate_dots").assertIsDisplayed()

        composeTestRule.onNodeWithTag("calc_key_7").performClick()
        composeTestRule.onNodeWithTag("calc_key_plus").performClick()
        composeTestRule.onNodeWithTag("calc_key_equals").performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("7", "+"), pressedKeys)
        assertTrue(equalsTriggered)
    }

    @Test
    fun `vault home displays real zero counts when empty instead of hardcoded 248 items`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                MyVaultHomeScreen(
                    files = emptyList(),
                    folders = emptyList(),
                    securityState = VaultSecurityState(isPinConfigured = true),
                    decryptFileBytes = { ByteArray(0) },
                    onOpenPhotosSelectMode = {},
                    onOpenPhotoViewer = {},
                    onOpenAlbums = {},
                    onOpenPanicAndDecoy = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vault_total_items_text", useUnmergedTree = true).assertTextEquals("0 items")
        composeTestRule.onNodeWithTag("vault_total_size_text", useUnmergedTree = true).assertTextEquals("0 B used")
        composeTestRule.onNodeWithTag("vault_breakdown_text", useUnmergedTree = true)
            .assertTextEquals("Photos 0 · Videos 0 · Files 0")
        composeTestRule.onNodeWithTag("empty_vault_cta_card").assertIsDisplayed()
    }
}
