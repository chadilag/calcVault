package com.example.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.vaultDataStore: DataStore<Preferences> by preferencesDataStore(name = "hideu_vault_prefs")

/**
 * In production builds (!BuildConfig.DEBUG), screenshot & recents preview protection (FLAG_SECURE)
 * is enabled by default on sensitive vault screens. In debug builds (such as the AI Studio streaming
 * emulator), a development override allows screen streaming to render unless toggled on in settings.
 */
val DEFAULT_PRODUCTION_BLOCK_SCREENSHOTS: Boolean = !BuildConfig.DEBUG

data class VaultSecurityState(
    val isPinConfigured: Boolean = false,
    val pinLength: Int = 6,
    val hasDecoyPin: Boolean = false,
    val decoyVaultEnabled: Boolean = false,
    val biometricUnlockEnabled: Boolean = false,
    val panicTriggerMode: String = "FLIP", // FLIP, SHAKE, FLOATING
    val breakInAlertsEnabled: Boolean = true,
    val autoLockTiming: String = "Instantly",
    val disguiseIconMode: String = "CALCULATOR", // CALCULATOR, NOTES, CLOCK, WEATHER
    val disguiseAppName: String = "Calculator",
    val hideFromRecentApps: Boolean = true,
    val blockScreenshots: Boolean = DEFAULT_PRODUCTION_BLOCK_SCREENSHOTS,
    val deleteOriginalsAfterImport: Boolean = false,
    val securityQuestion: String = "What was your childhood nickname?",
    val hasSecurityAnswer: Boolean = false,
    val shakeToLockEnabled: Boolean = true,
    val autoLockOnBackground: Boolean = true,
    val disguiseTheme: String = "OBSIDIAN",
    val calculationHistory: List<String> = emptyList(),
    val failedAttemptsCount: Int = 0,
    val lockoutUntilEpochMs: Long = 0L
)

enum class PinVerifyOutcome {
    MASTER_UNLOCKED,
    DECOY_UNLOCKED,
    INVALID_PIN,
    RATE_LIMITED
}

class VaultPreferencesRepository(
    private val context: Context,
    private val cryptoManager: CryptoManager
) {
    companion object {
        const val MIN_NEW_PIN_DIGITS = 6
        const val MAX_NEW_PIN_DIGITS = 8
    }

    private object Keys {
        val IS_PIN_CONFIGURED = booleanPreferencesKey("is_pin_configured_v2")
        val MASTER_PIN_HASH = stringPreferencesKey("master_pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PIN_LENGTH = intPreferencesKey("pin_length")
        val DECOY_PIN_HASH = stringPreferencesKey("decoy_pin_hash")
        val DECOY_ENABLED = booleanPreferencesKey("decoy_enabled")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val PANIC_TRIGGER_MODE = stringPreferencesKey("panic_trigger_mode")
        val BREAK_IN_ALERTS = booleanPreferencesKey("break_in_alerts")
        val AUTO_LOCK_TIMING = stringPreferencesKey("auto_lock_timing")
        val DISGUISE_ICON_MODE = stringPreferencesKey("disguise_icon_mode")
        val DISGUISE_APP_NAME = stringPreferencesKey("disguise_app_name")
        val HIDE_FROM_RECENTS = booleanPreferencesKey("hide_from_recents")
        val BLOCK_SCREENSHOTS = booleanPreferencesKey("block_screenshots_v2")
        val DELETE_ORIGINALS = booleanPreferencesKey("delete_originals_v2")
        val SECURITY_QUESTION = stringPreferencesKey("security_question")
        val SECURITY_ANSWER_HASH = stringPreferencesKey("security_answer_hash")
        val SHAKE_TO_LOCK = booleanPreferencesKey("shake_to_lock")
        val AUTO_LOCK_BG = booleanPreferencesKey("auto_lock_bg")
        val DISGUISE_THEME = stringPreferencesKey("disguise_theme")
        val CALC_HISTORY = stringPreferencesKey("calc_history")
        val FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")
        val LOCKOUT_UNTIL_MS = longPreferencesKey("lockout_until_ms")
    }

    val securityStateFlow: Flow<VaultSecurityState> = context.vaultDataStore.data.map { prefs ->
        val rawHistory = prefs[Keys.CALC_HISTORY].orEmpty()
        val historyList = if (rawHistory.isBlank()) {
            emptyList()
        } else {
            rawHistory.split("|||").filter { it.isNotBlank() }
        }
        val rawPanic = prefs[Keys.PANIC_TRIGGER_MODE] ?: "FLIP"
        val normalizedPanic = if (rawPanic == "FLIP_PHONE") "FLIP" else rawPanic
        val rawIconMode = prefs[Keys.DISGUISE_ICON_MODE] ?: "CALCULATOR"
        val normalizedIconMode = if (rawIconMode.equals("Calculator", ignoreCase = true)) "CALCULATOR" else rawIconMode.uppercase()

        val hasMasterHash = !prefs[Keys.MASTER_PIN_HASH].isNullOrBlank() && !prefs[Keys.PIN_SALT].isNullOrBlank()
        val hasDecoyHash = !prefs[Keys.DECOY_PIN_HASH].isNullOrBlank()

        VaultSecurityState(
            isPinConfigured = hasMasterHash,
            pinLength = prefs[Keys.PIN_LENGTH] ?: MIN_NEW_PIN_DIGITS,
            hasDecoyPin = hasDecoyHash,
            decoyVaultEnabled = (prefs[Keys.DECOY_ENABLED] ?: false) && hasDecoyHash,
            biometricUnlockEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
            panicTriggerMode = normalizedPanic,
            breakInAlertsEnabled = prefs[Keys.BREAK_IN_ALERTS] ?: true,
            autoLockTiming = prefs[Keys.AUTO_LOCK_TIMING] ?: "Instantly",
            disguiseIconMode = normalizedIconMode,
            disguiseAppName = prefs[Keys.DISGUISE_APP_NAME] ?: "Calculator",
            hideFromRecentApps = prefs[Keys.HIDE_FROM_RECENTS] ?: true,
            blockScreenshots = prefs[Keys.BLOCK_SCREENSHOTS] ?: DEFAULT_PRODUCTION_BLOCK_SCREENSHOTS,
            deleteOriginalsAfterImport = prefs[Keys.DELETE_ORIGINALS] ?: false,
            securityQuestion = prefs[Keys.SECURITY_QUESTION] ?: "What was your childhood nickname?",
            hasSecurityAnswer = !prefs[Keys.SECURITY_ANSWER_HASH].isNullOrBlank(),
            shakeToLockEnabled = prefs[Keys.SHAKE_TO_LOCK] ?: true,
            autoLockOnBackground = prefs[Keys.AUTO_LOCK_BG] ?: true,
            disguiseTheme = prefs[Keys.DISGUISE_THEME] ?: "OBSIDIAN",
            calculationHistory = historyList,
            failedAttemptsCount = prefs[Keys.FAILED_ATTEMPTS] ?: 0,
            lockoutUntilEpochMs = prefs[Keys.LOCKOUT_UNTIL_MS] ?: 0L
        )
    }

    suspend fun setupMasterPin(
        pin: String,
        question: String = "",
        answer: String = ""
    ) {
        val trimmedPin = pin.trim()
        require(trimmedPin.length in MIN_NEW_PIN_DIGITS..MAX_NEW_PIN_DIGITS && trimmedPin.all { it.isDigit() }) {
            "Master PIN must be $MIN_NEW_PIN_DIGITS to $MAX_NEW_PIN_DIGITS digits"
        }
        val salt = cryptoManager.generateSalt()
        val pinHash = cryptoManager.hashSecret(trimmedPin, salt)
        val answerHash = if (answer.isNotBlank()) cryptoManager.hashSecret(answer.trim(), salt) else ""

        context.vaultDataStore.edit { prefs ->
            prefs[Keys.PIN_SALT] = salt
            prefs[Keys.MASTER_PIN_HASH] = pinHash
            prefs[Keys.PIN_LENGTH] = trimmedPin.length
            prefs[Keys.IS_PIN_CONFIGURED] = true
            if (question.isNotBlank()) {
                prefs[Keys.SECURITY_QUESTION] = question.trim()
            }
            if (answerHash.isNotEmpty()) {
                prefs[Keys.SECURITY_ANSWER_HASH] = answerHash
            }
            prefs[Keys.FAILED_ATTEMPTS] = 0
            prefs[Keys.LOCKOUT_UNTIL_MS] = 0L
        }
    }

    suspend fun changeMasterPin(newPin: String) {
        val trimmedPin = newPin.trim()
        require(trimmedPin.length in MIN_NEW_PIN_DIGITS..MAX_NEW_PIN_DIGITS && trimmedPin.all { it.isDigit() }) {
            "New PIN must be $MIN_NEW_PIN_DIGITS to $MAX_NEW_PIN_DIGITS digits"
        }
        context.vaultDataStore.edit { prefs ->
            val salt = prefs[Keys.PIN_SALT] ?: cryptoManager.generateSalt().also { prefs[Keys.PIN_SALT] = it }
            prefs[Keys.MASTER_PIN_HASH] = cryptoManager.hashSecret(trimmedPin, salt)
            prefs[Keys.PIN_LENGTH] = trimmedPin.length
            prefs[Keys.IS_PIN_CONFIGURED] = true
            prefs[Keys.FAILED_ATTEMPTS] = 0
            prefs[Keys.LOCKOUT_UNTIL_MS] = 0L
        }
    }

    suspend fun setDecoyPin(decoyPin: String?) {
        context.vaultDataStore.edit { prefs ->
            if (decoyPin.isNullOrBlank()) {
                prefs[Keys.DECOY_ENABLED] = false
                prefs.remove(Keys.DECOY_PIN_HASH)
            } else {
                val trimmed = decoyPin.trim()
                require(trimmed.length in MIN_NEW_PIN_DIGITS..MAX_NEW_PIN_DIGITS && trimmed.all { it.isDigit() }) {
                    "Decoy PIN must be $MIN_NEW_PIN_DIGITS to $MAX_NEW_PIN_DIGITS digits"
                }
                val salt = prefs[Keys.PIN_SALT] ?: cryptoManager.generateSalt().also { prefs[Keys.PIN_SALT] = it }
                prefs[Keys.DECOY_PIN_HASH] = cryptoManager.hashSecret(trimmed, salt)
                prefs[Keys.DECOY_ENABLED] = true
            }
        }
    }

    suspend fun setDecoyVaultEnabled(enabled: Boolean) {
        context.vaultDataStore.edit { prefs ->
            prefs[Keys.DECOY_ENABLED] = enabled
        }
    }

    suspend fun setBiometricUnlock(enabled: Boolean) {
        context.vaultDataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_ENABLED] = enabled
        }
    }

    suspend fun setPanicTriggerMode(mode: String) {
        context.vaultDataStore.edit { prefs ->
            prefs[Keys.PANIC_TRIGGER_MODE] = mode
        }
    }

    suspend fun setBreakInAlerts(enabled: Boolean) {
        context.vaultDataStore.edit { prefs ->
            prefs[Keys.BREAK_IN_ALERTS] = enabled
        }
    }

    suspend fun setAutoLockTiming(timing: String) {
        context.vaultDataStore.edit { prefs ->
            prefs[Keys.AUTO_LOCK_TIMING] = timing
        }
    }

    suspend fun setDisguiseAppConfig(iconMode: String, appName: String) {
        context.vaultDataStore.edit { prefs ->
            prefs[Keys.DISGUISE_ICON_MODE] = iconMode
            prefs[Keys.DISGUISE_APP_NAME] = appName.ifBlank { iconMode }
        }
    }

    suspend fun setHideFromRecentApps(enabled: Boolean) {
        context.vaultDataStore.edit { prefs ->
            prefs[Keys.HIDE_FROM_RECENTS] = enabled
        }
    }

    suspend fun setBlockScreenshots(enabled: Boolean) {
        context.vaultDataStore.edit { prefs ->
            prefs[Keys.BLOCK_SCREENSHOTS] = enabled
        }
    }

    suspend fun setDeleteOriginalsAfterImport(enabled: Boolean) {
        context.vaultDataStore.edit { prefs ->
            prefs[Keys.DELETE_ORIGINALS] = enabled
        }
    }

    suspend fun updateSecurityQuestion(question: String, answer: String) {
        context.vaultDataStore.edit { prefs ->
            val salt = prefs[Keys.PIN_SALT] ?: cryptoManager.generateSalt().also { prefs[Keys.PIN_SALT] = it }
            prefs[Keys.SECURITY_QUESTION] = question.trim()
            if (answer.isNotBlank()) {
                prefs[Keys.SECURITY_ANSWER_HASH] = cryptoManager.hashSecret(answer.trim(), salt)
            } else {
                prefs.remove(Keys.SECURITY_ANSWER_HASH)
            }
        }
    }

    /**
     * Verifies candidate PIN using PBKDF2-HMAC-SHA256 (with legacy SHA-256 upgrade support)
     * and enforces exponential backoff after 5 consecutive failed attempts.
     * Never accepts hardcoded backdoor PINs.
     */
    suspend fun verifyPinInput(candidatePin: String, nowEpochMs: Long = System.currentTimeMillis()): PinVerifyOutcome {
        var outcome = PinVerifyOutcome.INVALID_PIN
        context.vaultDataStore.edit { prefs ->
            val lockoutUntil = prefs[Keys.LOCKOUT_UNTIL_MS] ?: 0L
            if (nowEpochMs < lockoutUntil) {
                outcome = PinVerifyOutcome.RATE_LIMITED
                return@edit
            }

            val salt = prefs[Keys.PIN_SALT]
            val masterHash = prefs[Keys.MASTER_PIN_HASH]
            val decoyHash = prefs[Keys.DECOY_PIN_HASH]
            val decoyEnabled = prefs[Keys.DECOY_ENABLED] ?: false

            if (salt.isNullOrBlank() || masterHash.isNullOrBlank()) {
                outcome = PinVerifyOutcome.INVALID_PIN
                return@edit
            }

            val isMasterMatch = cryptoManager.verifySecretHash(candidatePin, salt, masterHash)
            val isDecoyMatch = decoyEnabled && !decoyHash.isNullOrBlank() &&
                cryptoManager.verifySecretHash(candidatePin, salt, decoyHash)

            when {
                isMasterMatch -> {
                    if (!masterHash.startsWith("pbkdf2v1$")) {
                        prefs[Keys.MASTER_PIN_HASH] = cryptoManager.hashSecret(candidatePin, salt)
                    }
                    prefs[Keys.FAILED_ATTEMPTS] = 0
                    prefs[Keys.LOCKOUT_UNTIL_MS] = 0L
                    outcome = PinVerifyOutcome.MASTER_UNLOCKED
                }
                isDecoyMatch -> {
                    if (!decoyHash!!.startsWith("pbkdf2v1$")) {
                        prefs[Keys.DECOY_PIN_HASH] = cryptoManager.hashSecret(candidatePin, salt)
                    }
                    prefs[Keys.FAILED_ATTEMPTS] = 0
                    prefs[Keys.LOCKOUT_UNTIL_MS] = 0L
                    outcome = PinVerifyOutcome.DECOY_UNLOCKED
                }
                else -> {
                    if (candidatePin.length in 4..8 && candidatePin.all { it.isDigit() }) {
                        val fails = (prefs[Keys.FAILED_ATTEMPTS] ?: 0) + 1
                        prefs[Keys.FAILED_ATTEMPTS] = fails
                        if (fails >= 5) {
                            val backoffSeconds = when {
                                fails == 5 -> 30L
                                fails == 6 -> 60L
                                fails == 7 -> 300L
                                else -> 900L
                            }
                            prefs[Keys.LOCKOUT_UNTIL_MS] = nowEpochMs + (backoffSeconds * 1000L)
                        }
                    }
                    outcome = PinVerifyOutcome.INVALID_PIN
                }
            }
        }
        return outcome
    }

    suspend fun verifySecurityAnswerAndResetPin(answer: String, newPin: String): Boolean {
        val trimmedPin = newPin.trim()
        if (trimmedPin.length !in MIN_NEW_PIN_DIGITS..MAX_NEW_PIN_DIGITS || !trimmedPin.all { it.isDigit() }) {
            return false
        }
        var success = false
        context.vaultDataStore.edit { prefs ->
            val salt = prefs[Keys.PIN_SALT] ?: return@edit
            val storedAnswerHash = prefs[Keys.SECURITY_ANSWER_HASH] ?: return@edit
            if (storedAnswerHash.isNotBlank() && cryptoManager.verifySecretHash(answer, salt, storedAnswerHash)) {
                prefs[Keys.MASTER_PIN_HASH] = cryptoManager.hashSecret(trimmedPin, salt)
                prefs[Keys.PIN_LENGTH] = trimmedPin.length
                prefs[Keys.FAILED_ATTEMPTS] = 0
                prefs[Keys.LOCKOUT_UNTIL_MS] = 0L
                success = true
            }
        }
        return success
    }

    suspend fun setShakeToLock(enabled: Boolean) {
        context.vaultDataStore.edit { prefs ->
            prefs[Keys.SHAKE_TO_LOCK] = enabled
        }
    }

    suspend fun setAutoLockOnBackground(enabled: Boolean) {
        context.vaultDataStore.edit { prefs ->
            prefs[Keys.AUTO_LOCK_BG] = enabled
        }
    }

    suspend fun setDisguiseTheme(theme: String) {
        context.vaultDataStore.edit { prefs ->
            prefs[Keys.DISGUISE_THEME] = theme
        }
    }

    suspend fun appendCalculationHistory(entry: String) {
        context.vaultDataStore.edit { prefs ->
            val raw = prefs[Keys.CALC_HISTORY].orEmpty()
            val list = if (raw.isBlank()) mutableListOf() else raw.split("|||").toMutableList()
            list.add(0, entry)
            val trimmed = list.take(25)
            prefs[Keys.CALC_HISTORY] = trimmed.joinToString("|||")
        }
    }

    suspend fun clearCalculationHistory() {
        context.vaultDataStore.edit { prefs ->
            prefs.remove(Keys.CALC_HISTORY)
        }
    }
}
