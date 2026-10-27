package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.calculator.CalculatorEngine
import com.example.data.IntruderLogEntity
import com.example.data.PasswordEntryEntity
import com.example.data.PrivateBookmarkEntity
import com.example.data.SecretNoteEntity
import com.example.data.VaultDatabase
import com.example.data.VaultFileEntity
import com.example.data.VaultFolderEntity
import com.example.data.VaultRepository
import com.example.security.CryptoManager
import com.example.security.PinVerifyOutcome
import com.example.security.VaultPreferencesRepository
import com.example.security.VaultSecurityState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PinSetupStage {
    ENTER_FIRST_PIN,
    CONFIRM_PIN,
    SET_SECURITY_QUESTION,
    READY
}

sealed interface PasswordVerificationState {
    data object Idle : PasswordVerificationState
    data class PinCandidateProgress(val enteredDigits: Int, val targetDigits: Int) : PasswordVerificationState
    data class Verifying(val triggerOperation: String) : PasswordVerificationState
    data class VerifiedMaster(val triggerOperation: String) : PasswordVerificationState
    data class VerifiedDecoy(val triggerOperation: String) : PasswordVerificationState
    data class VerificationFailed(val attemptCount: Int, val evaluatedMathResult: String?) : PasswordVerificationState
    data object RecoveryCodeTriggered : PasswordVerificationState
}

enum class AppMode {
    CALCULATOR_DISGUISE,
    VAULT_UNLOCKED
}

enum class VaultTab(val label: String) {
    FILES("Vault"),
    ALBUMS("Albums"),
    TRASH("Trash"),
    SECURITY("Security")
}

sealed interface VaultSubScreen {
    data object None : VaultSubScreen
    data class PhotosSelectMode(val initialCategoryOrAlbum: String = "All") : VaultSubScreen
    data class PhotoViewer(val selectedIndex: Int = 0, val filterContext: String = "All") : VaultSubScreen
    data object PanicAndDecoy : VaultSubScreen
    data object AppDisguise : VaultSubScreen
    data object NotesScreen : VaultSubScreen
    data object PasswordsScreen : VaultSubScreen
}

data class ImportProgressState(
    val current: Int,
    val total: Int,
    val currentLabel: String = ""
)

data class CalculatorUiState(
    val expression: String = "0",
    val lastEquationLabel: String = "",
    val liveResult: String = "",
    val lastAnswer: String = "0",
    val memoryValue: Double = 0.0,
    val isInitialDisplay: Boolean = true,
    val isScientificExpanded: Boolean = false,
    val isHistoryVisible: Boolean = false,
    val setupStage: PinSetupStage = PinSetupStage.ENTER_FIRST_PIN,
    val verificationState: PasswordVerificationState = PasswordVerificationState.Idle,
    val draftFirstPin: String = "",
    val statusBannerMessage: String? = null,
    val isStatusError: Boolean = false,
    val showRecoveryDialog: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class HideUViewModel(
    val repository: VaultRepository,
    private val prefsRepository: VaultPreferencesRepository
) : ViewModel() {

    val securityState: StateFlow<VaultSecurityState> = prefsRepository.securityStateFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = VaultSecurityState()
        )

    private val _appMode = MutableStateFlow(AppMode.CALCULATOR_DISGUISE)
    val appMode: StateFlow<AppMode> = _appMode.asStateFlow()

    private val _isDecoySession = MutableStateFlow(false)
    val isDecoySession: StateFlow<Boolean> = _isDecoySession.asStateFlow()

    private val _selectedVaultTab = MutableStateFlow(VaultTab.FILES)
    val selectedVaultTab: StateFlow<VaultTab> = _selectedVaultTab.asStateFlow()

    private val _activeSubScreen = MutableStateFlow<VaultSubScreen>(VaultSubScreen.None)
    val activeSubScreen: StateFlow<VaultSubScreen> = _activeSubScreen.asStateFlow()

    private val _showAddToVaultSheet = MutableStateFlow(false)
    val showAddToVaultSheet: StateFlow<Boolean> = _showAddToVaultSheet.asStateFlow()

    private val _importProgress = MutableStateFlow<ImportProgressState?>(null)
    val importProgress: StateFlow<ImportProgressState?> = _importProgress.asStateFlow()

    private var activeImportJob: Job? = null

    private val _calcState = MutableStateFlow(CalculatorUiState())
    val calcState: StateFlow<CalculatorUiState> = _calcState.asStateFlow()

    private val _statusToast = MutableStateFlow<String?>(null)
    val statusToast: StateFlow<String?> = _statusToast.asStateFlow()

    val folders: StateFlow<List<VaultFolderEntity>> = _isDecoySession
        .flatMapLatest { isDecoy -> repository.observeFolders(isDecoy) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val files: StateFlow<List<VaultFileEntity>> = _isDecoySession
        .flatMapLatest { isDecoy -> repository.observeAllFiles(isDecoy) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<SecretNoteEntity>> = _isDecoySession
        .flatMapLatest { isDecoy -> repository.observeNotes(isDecoy) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val passwords: StateFlow<List<PasswordEntryEntity>> = _isDecoySession
        .flatMapLatest { isDecoy -> repository.observePasswords(isDecoy) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarks: StateFlow<List<PrivateBookmarkEntity>> = _isDecoySession
        .flatMapLatest { isDecoy -> repository.observeBookmarks(isDecoy) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val intruderLogs: StateFlow<List<IntruderLogEntity>> = repository.observeIntruderLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.ensureDefaultFoldersAndBookmarks(isDecoy = false)
        }
        viewModelScope.launch {
            securityState.collect { sec ->
                if (sec.isPinConfigured && _calcState.value.setupStage != PinSetupStage.READY) {
                    _calcState.update { it.copy(setupStage = PinSetupStage.READY, statusBannerMessage = null) }
                } else if (!sec.isPinConfigured && _calcState.value.setupStage == PinSetupStage.READY) {
                    _calcState.update { it.copy(setupStage = PinSetupStage.ENTER_FIRST_PIN) }
                }
            }
        }
    }

    fun showToast(message: String) {
        _statusToast.value = message
    }

    fun clearToast() {
        _statusToast.value = null
    }

    fun selectVaultTab(tab: VaultTab) {
        _activeSubScreen.value = VaultSubScreen.None
        _selectedVaultTab.value = tab
    }

    fun navigateToSubScreen(subScreen: VaultSubScreen) {
        _activeSubScreen.value = subScreen
    }

    fun navigateBackInVault(): Boolean {
        if (_showAddToVaultSheet.value) {
            _showAddToVaultSheet.value = false
            return true
        }
        if (_activeSubScreen.value != VaultSubScreen.None) {
            _activeSubScreen.value = VaultSubScreen.None
            return true
        }
        if (_selectedVaultTab.value != VaultTab.FILES) {
            _selectedVaultTab.value = VaultTab.FILES
            return true
        }
        lockVaultImmediately("Back Press")
        return true
    }

    fun setShowAddToVaultSheet(visible: Boolean) {
        _showAddToVaultSheet.value = visible
    }

    fun openVaultDirectly(isDecoy: Boolean = false) {
        viewModelScope.launch {
            _isDecoySession.value = isDecoy
            repository.ensureDefaultFoldersAndBookmarks(isDecoy = isDecoy)
            _activeSubScreen.value = VaultSubScreen.None
            _selectedVaultTab.value = VaultTab.FILES
            _appMode.value = AppMode.VAULT_UNLOCKED
        }
    }

    /**
     * Immediately locks the vault, evicts decrypted bitmaps from memory, wipes any temporary
     * video playback files on disk, and returns to a clean calculator displaying "0".
     */
    fun lockVaultImmediately(reason: String = "Quick Lock") {
        repository.clearSensitiveMemoryAndTempFiles()
        if (_appMode.value == AppMode.VAULT_UNLOCKED) {
            _showAddToVaultSheet.value = false
            _activeSubScreen.value = VaultSubScreen.None
            _appMode.value = AppMode.CALCULATOR_DISGUISE
            _calcState.update {
                it.copy(
                    expression = "0",
                    lastEquationLabel = "",
                    liveResult = "",
                    isInitialDisplay = true,
                    verificationState = PasswordVerificationState.Idle,
                    statusBannerMessage = null,
                    isStatusError = false
                )
            }
            if (reason == "Shake Panic Lock") {
                viewModelScope.launch {
                    repository.logSecurityEvent("SENSOR_SHAKE", "PANIC_LOCK", "Emergency panic lock triggered")
                }
            }
        }
    }

    fun resetPinSetupDraft() {
        _calcState.update {
            it.copy(
                expression = "0",
                lastEquationLabel = "",
                liveResult = "",
                isInitialDisplay = true,
                setupStage = PinSetupStage.ENTER_FIRST_PIN,
                draftFirstPin = "",
                statusBannerMessage = null,
                isStatusError = false
            )
        }
    }

    fun onCalculatorInput(token: String) {
        _calcState.update { state ->
            val rawBase = state.expression.replace(",", "")
            val baseExpr = if ((state.isInitialDisplay || rawBase == "0") && token !in listOf("+", "-", "×", "÷", "%", ".")) {
                ""
            } else {
                rawBase
            }

            val updatedExpr = when (token) {
                "AC" -> ""
                "DEL" -> if (baseExpr.isNotEmpty()) baseExpr.dropLast(1) else ""
                "+/-" -> {
                    if (baseExpr.isEmpty() || baseExpr == "0") "-"
                    else if (baseExpr.startsWith("-")) baseExpr.drop(1)
                    else "-$baseExpr"
                }
                "." -> {
                    // Prevent multiple decimals in the current numeric segment
                    val lastNumberSegment = baseExpr.split('+', '-', '×', '÷', '^', '(', ')').lastOrNull().orEmpty()
                    if (lastNumberSegment.contains('.')) {
                        baseExpr
                    } else if (baseExpr.isEmpty() || !baseExpr.last().isDigit()) {
                        "${baseExpr}0."
                    } else {
                        "$baseExpr."
                    }
                }
                else -> {
                    val operators = setOf('+', '-', '×', '÷', '^')
                    if (token.length == 1 && token[0] in operators) {
                        if (baseExpr.isEmpty()) {
                            if (token == "-") "-" else "0$token"
                        } else if (baseExpr.last() in operators) {
                            baseExpr.dropLast(1) + token
                        } else {
                            baseExpr + token
                        }
                    } else if (baseExpr.length >= 32) {
                        baseExpr
                    } else {
                        baseExpr + token
                    }
                }
            }

            val finalExpr = updatedExpr.ifEmpty { "0" }
            state.copy(
                expression = finalExpr,
                lastEquationLabel = if (token == "AC") "" else state.lastEquationLabel,
                liveResult = computeLivePreview(finalExpr),
                isInitialDisplay = updatedExpr.isEmpty(),
                verificationState = computeCandidateVerificationState(finalExpr),
                statusBannerMessage = null,
                isStatusError = false
            )
        }
    }

    private fun computeLivePreview(expr: String): String {
        if (expr.isBlank() || expr == "0" || expr == "-") return ""
        val hasOperatorOrFunc = expr.drop(if (expr.startsWith("-")) 1 else 0).any { !it.isDigit() && it != '.' }
        if (!hasOperatorOrFunc) return ""
        return CalculatorEngine.evaluate(expr).getOrNull().orEmpty()
    }

    private fun computeCandidateVerificationState(expr: String): PasswordVerificationState {
        val trimmed = expr.replace(",", "").trim()
        if (trimmed.isEmpty() || trimmed == "0") return PasswordVerificationState.Idle
        val targetLen = securityState.value.pinLength.coerceIn(4, 8)
        return if (trimmed.all { it.isDigit() } && trimmed.length <= 8) {
            PasswordVerificationState.PinCandidateProgress(
                enteredDigits = trimmed.length,
                targetDigits = targetLen
            )
        } else {
            PasswordVerificationState.Idle
        }
    }

    fun toggleScientificPad() {
        _calcState.update { it.copy(isScientificExpanded = !it.isScientificExpanded) }
    }

    fun toggleHistoryDrawer() {
        _calcState.update { it.copy(isHistoryVisible = !it.isHistoryVisible) }
    }

    fun dismissRecoveryDialog() {
        _calcState.update {
            it.copy(
                showRecoveryDialog = false,
                verificationState = PasswordVerificationState.Idle
            )
        }
    }

    fun openRecoveryDialog() {
        if (securityState.value.hasSecurityAnswer) {
            _calcState.update {
                it.copy(
                    showRecoveryDialog = true,
                    verificationState = PasswordVerificationState.RecoveryCodeTriggered
                )
            }
        }
    }

    fun quickConfigureDefaultPin(pin: String = "123456") {
        viewModelScope.launch {
            prefsRepository.setupMasterPin(
                pin = pin,
                question = "What was your childhood nickname?",
                answer = "hideu"
            )
            repository.ensureDefaultFoldersAndBookmarks(isDecoy = false)
            _calcState.update {
                it.copy(
                    draftFirstPin = "",
                    expression = "0",
                    lastEquationLabel = "",
                    isInitialDisplay = true,
                    setupStage = PinSetupStage.READY,
                    verificationState = PasswordVerificationState.Idle,
                    statusBannerMessage = null,
                    isStatusError = false
                )
            }
        }
    }

    fun onEqualsPressed() {
        triggerPasswordVerification(triggerOp = "=")
    }

    fun triggerPasswordVerification(triggerOp: String = "=") {
        val current = _calcState.value
        val expr = current.expression.replace(",", "").trim()

        if (expr.isEmpty()) return

        // 1. Handle First-Run PIN Setup Flow when PIN is not yet configured
        if (!securityState.value.isPinConfigured || current.setupStage != PinSetupStage.READY) {
            handleFirstRunPinSetupStep(expr)
            return
        }

        _calcState.update {
            it.copy(verificationState = PasswordVerificationState.Verifying(triggerOp))
        }

        // 2. Only check PIN if input is strictly a 4..8 digit number (6..8 for new PINs, 4..8 for legacy upgraded PINs)
        val isEligiblePinCandidate = expr.length in 4..8 && expr.all { it.isDigit() }
        if (!isEligiblePinCandidate) {
            evaluateStandardMath(expr, isFailedPinCandidate = false)
            return
        }

        viewModelScope.launch {
            val outcome = prefsRepository.verifyPinInput(expr)
            when (outcome) {
                PinVerifyOutcome.MASTER_UNLOCKED -> {
                    _isDecoySession.value = false
                    repository.ensureDefaultFoldersAndBookmarks(isDecoy = false)
                    _calcState.update {
                        it.copy(
                            expression = "0",
                            lastEquationLabel = "",
                            liveResult = "",
                            isInitialDisplay = true,
                            verificationState = PasswordVerificationState.VerifiedMaster(triggerOp),
                            statusBannerMessage = null,
                            isStatusError = false
                        )
                    }
                    _activeSubScreen.value = VaultSubScreen.None
                    _selectedVaultTab.value = VaultTab.FILES
                    _appMode.value = AppMode.VAULT_UNLOCKED
                }
                PinVerifyOutcome.DECOY_UNLOCKED -> {
                    _isDecoySession.value = true
                    repository.ensureDefaultFoldersAndBookmarks(isDecoy = true)
                    repository.logSecurityEvent("••••$triggerOp", "DECOY_TRIGGERED", "Decoy vault opened with secondary PIN")
                    _calcState.update {
                        it.copy(
                            expression = "0",
                            lastEquationLabel = "",
                            liveResult = "",
                            isInitialDisplay = true,
                            verificationState = PasswordVerificationState.VerifiedDecoy(triggerOp),
                            statusBannerMessage = null,
                            isStatusError = false
                        )
                    }
                    _activeSubScreen.value = VaultSubScreen.None
                    _selectedVaultTab.value = VaultTab.FILES
                    _appMode.value = AppMode.VAULT_UNLOCKED
                }
                PinVerifyOutcome.RATE_LIMITED,
                PinVerifyOutcome.INVALID_PIN -> {
                    if (securityState.value.breakInAlertsEnabled) {
                        val masked = expr.take(1) + "•".repeat((expr.length - 2).coerceAtLeast(1)) + expr.takeLast(1)
                        repository.logSecurityEvent(
                            enteredExpression = "$masked$triggerOp",
                            eventType = "FAILED_UNLOCK",
                            description = "Unrecognized ${expr.length}-digit code submitted"
                        )
                    }
                    // Discreetly evaluate as standard math so an observer sees normal calculator behavior
                    evaluateStandardMath(expr, isFailedPinCandidate = true)
                }
            }
        }
    }

    private fun handleFirstRunPinSetupStep(expr: String) {
        val current = _calcState.value
        val isValidPinFormat = expr.length in 6..8 && expr.all { it.isDigit() }

        when (current.setupStage) {
            PinSetupStage.ENTER_FIRST_PIN -> {
                if (!isValidPinFormat) {
                    _calcState.update {
                        it.copy(
                            lastEquationLabel = "Entrez un code de 6 à 8 chiffres puis appuyez sur =",
                            statusBannerMessage = "Entrez un code de 6 à 8 chiffres puis appuyez sur =",
                            isStatusError = true
                        )
                    }
                    return
                }
                _calcState.update {
                    it.copy(
                        draftFirstPin = expr,
                        expression = "0",
                        lastEquationLabel = "",
                        liveResult = "",
                        isInitialDisplay = true,
                        setupStage = PinSetupStage.CONFIRM_PIN,
                        statusBannerMessage = null,
                        isStatusError = false
                    )
                }
            }
            PinSetupStage.CONFIRM_PIN,
            PinSetupStage.SET_SECURITY_QUESTION -> {
                if (expr != current.draftFirstPin) {
                    _calcState.update {
                        it.copy(
                            draftFirstPin = "",
                            expression = "0",
                            lastEquationLabel = "Les codes ne correspondent pas. Recommencez (6–8 chiffres).",
                            liveResult = "",
                            isInitialDisplay = true,
                            setupStage = PinSetupStage.ENTER_FIRST_PIN,
                            statusBannerMessage = "Les codes ne correspondent pas. Recommencez.",
                            isStatusError = true
                        )
                    }
                    return
                }
                viewModelScope.launch {
                    prefsRepository.setupMasterPin(pin = expr)
                    _isDecoySession.value = false
                    repository.ensureDefaultFoldersAndBookmarks(isDecoy = false)
                    _calcState.update {
                        it.copy(
                            draftFirstPin = "",
                            expression = "0",
                            lastEquationLabel = "",
                            liveResult = "",
                            isInitialDisplay = true,
                            setupStage = PinSetupStage.READY,
                            verificationState = PasswordVerificationState.VerifiedMaster("="),
                            statusBannerMessage = null,
                            isStatusError = false
                        )
                    }
                    _activeSubScreen.value = VaultSubScreen.None
                    _selectedVaultTab.value = VaultTab.FILES
                    _appMode.value = AppMode.VAULT_UNLOCKED
                }
            }
            PinSetupStage.READY -> Unit
        }
    }

    private fun evaluateStandardMath(expr: String, isFailedPinCandidate: Boolean = false) {
        val evalResult = CalculatorEngine.evaluate(expr)
        evalResult.onSuccess { res ->
            if (expr != res) {
                viewModelScope.launch {
                    prefsRepository.appendCalculationHistory("$expr = $res")
                }
            }
            val fails = securityState.value.failedAttemptsCount + (if (isFailedPinCandidate) 1 else 0)
            _calcState.update {
                it.copy(
                    expression = res,
                    lastEquationLabel = if (expr != res) "$expr =" else "",
                    liveResult = "",
                    lastAnswer = res,
                    isInitialDisplay = true,
                    verificationState = if (isFailedPinCandidate) {
                        PasswordVerificationState.VerificationFailed(
                            attemptCount = fails.coerceAtLeast(1),
                            evaluatedMathResult = res
                        )
                    } else {
                        PasswordVerificationState.Idle
                    },
                    isStatusError = false
                )
            }
        }.onFailure {
            _calcState.update {
                it.copy(
                    expression = "0",
                    lastEquationLabel = expr,
                    liveResult = "Erreur",
                    isInitialDisplay = true,
                    verificationState = PasswordVerificationState.Idle,
                    isStatusError = true
                )
            }
        }
    }

    fun completeInitialPinSetup(
        securityQuestion: String,
        securityAnswer: String,
        loadDemoVaultPack: Boolean
    ) {
        val pin = _calcState.value.draftFirstPin.ifBlank { "1234" }
        viewModelScope.launch {
            prefsRepository.setupMasterPin(pin, securityQuestion, securityAnswer)
            _isDecoySession.value = false
            repository.ensureDefaultFoldersAndBookmarks(isDecoy = false)
            _calcState.update {
                it.copy(
                    draftFirstPin = "",
                    expression = "0",
                    lastEquationLabel = "",
                    isInitialDisplay = true,
                    setupStage = PinSetupStage.READY,
                    verificationState = PasswordVerificationState.VerifiedMaster("=")
                )
            }
            _appMode.value = AppMode.VAULT_UNLOCKED
        }
    }

    fun attemptSecurityAnswerReset(answer: String, newPin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = prefsRepository.verifySecurityAnswerAndResetPin(answer, newPin)
            if (ok) {
                _calcState.update {
                    it.copy(
                        showRecoveryDialog = false,
                        expression = "0",
                        lastEquationLabel = "",
                        liveResult = "",
                        verificationState = PasswordVerificationState.Idle
                    )
                }
                showToast("Code secret mis à jour")
            }
            onResult(ok)
        }
    }

    // Vault File Operations with Real Progress & Cancellation
    fun importUris(uris: List<Uri>, folderId: Long, fallbackCategory: String) {
        if (uris.isEmpty()) return
        activeImportJob?.cancel()
        activeImportJob = viewModelScope.launch {
            val total = uris.size
            var importedCount = 0
            var failedCount = 0
            val isDecoy = _isDecoySession.value
            try {
                uris.forEachIndexed { idx, uri ->
                    if (!isActive) throw CancellationException()
                    _importProgress.value = ImportProgressState(
                        current = idx + 1,
                        total = total,
                        currentLabel = "AES-256"
                    )
                    val saved = repository.importUriIntoVault(uri, folderId, fallbackCategory, isDecoy)
                    if (saved != null) importedCount++ else failedCount++
                }
                if (importedCount > 0) {
                    val suffix = if (failedCount > 0) " ($failedCount ignoré(s))" else ""
                    showToast("$importedCount fichier(s) chiffré(s) avec AES-256$suffix")
                } else {
                    showToast("Impossible d'importer le fichier sélectionné")
                }
            } catch (_: CancellationException) {
                repository.cleanupOrphanStagingAndPlaybackFiles()
                showToast("Importation annulée ($importedCount chiffré(s))")
            } finally {
                _importProgress.value = null
                _showAddToVaultSheet.value = false
            }
        }
    }

    fun cancelActiveImport() {
        activeImportJob?.cancel()
        activeImportJob = null
        _importProgress.value = null
    }

    fun importCapturedCameraBitmap(bitmap: Bitmap?, folderId: Long) {
        if (bitmap == null) return
        viewModelScope.launch {
            _importProgress.value = ImportProgressState(1, 1, "Camera AES-256")
            try {
                val saved = repository.importCameraBitmapIntoVault(
                    bitmap = bitmap,
                    folderId = folderId,
                    isDecoy = _isDecoySession.value
                )
                if (saved != null) {
                    showToast("Photo capturée et chiffrée dans le coffre")
                } else {
                    showToast("Erreur lors du chiffrement de la photo")
                }
            } finally {
                _importProgress.value = null
                _showAddToVaultSheet.value = false
            }
        }
    }

    fun createCustomFolder(name: String, category: String, accentHex: String) {
        viewModelScope.launch {
            repository.createFolder(name, category, accentHex, _isDecoySession.value)
            showToast("Album '$name' créé")
        }
    }

    fun renameFolder(folderId: Long, newName: String) {
        viewModelScope.launch {
            repository.renameFolder(folderId, newName)
            showToast("Album renommé en '$newName'")
        }
    }

    fun deleteFolderSafely(folderId: Long) {
        viewModelScope.launch {
            val fallbackId = folders.value.firstOrNull { it.id != folderId }?.id ?: 1L
            repository.deleteFolderSafely(folderId, fallbackId)
            showToast("Album supprimé (fichiers conservés)")
        }
    }

    fun toggleFavoriteFile(file: VaultFileEntity) {
        viewModelScope.launch {
            repository.toggleFileFavorite(file)
        }
    }

    fun moveFileToTrash(file: VaultFileEntity) {
        viewModelScope.launch {
            repository.moveFileToTrash(file)
            showToast("'${file.originalName}' déplacé vers la corbeille")
        }
    }

    fun restoreFileFromTrash(file: VaultFileEntity) {
        viewModelScope.launch {
            repository.restoreFileFromTrash(file)
            showToast("'${file.originalName}' restauré dans le coffre")
        }
    }

    fun moveFilesToFolder(filesToMove: List<VaultFileEntity>, folderId: Long, folderName: String) {
        if (filesToMove.isEmpty()) return
        viewModelScope.launch {
            filesToMove.forEach { file ->
                repository.moveFileToFolder(file, folderId)
            }
            showToast("${filesToMove.size} élément(s) déplacé(s) vers $folderName")
        }
    }

    fun shredFilePermanently(file: VaultFileEntity) {
        viewModelScope.launch {
            repository.permanentlyShredFile(file)
            showToast("'${file.originalName}' supprimé définitivement")
        }
    }

    fun emptyTrashBin() {
        viewModelScope.launch {
            repository.emptyTrash(_isDecoySession.value)
            showToast("Corbeille vidée définitivement")
        }
    }

    fun exportUnhideFile(file: VaultFileEntity, targetUri: Uri) {
        viewModelScope.launch {
            val ok = repository.exportUnhideFileToUri(file, targetUri)
            if (ok) {
                showToast("'${file.originalName}' déchiffré et exporté avec succès")
            } else {
                showToast("Échec de l'exportation : vérification d'intégrité refusée")
            }
        }
    }

    fun exportPortableBackup(targetUri: Uri, backupPassword: String) {
        viewModelScope.launch {
            _importProgress.value = ImportProgressState(1, 1, "Chiffrement de la sauvegarde (.cvbackup)...")
            try {
                val res = repository.exportPortableBackupToUri(targetUri, backupPassword, _isDecoySession.value)
                res.onSuccess { count ->
                    showToast("Sauvegarde chiffrée exportée ($count élément(s))")
                }.onFailure { err ->
                    showToast("Échec de la sauvegarde : ${err.message ?: "erreur E/S"}")
                }
            } finally {
                _importProgress.value = null
            }
        }
    }

    fun restorePortableBackup(sourceUri: Uri, backupPassword: String) {
        viewModelScope.launch {
            _importProgress.value = ImportProgressState(1, 1, "Restauration de la sauvegarde (.cvbackup)...")
            try {
                val res = repository.restorePortableBackupFromUri(sourceUri, backupPassword, _isDecoySession.value)
                res.onSuccess { count ->
                    showToast("$count élément(s) restauré(s) dans le coffre")
                }.onFailure {
                    showToast("Mot de passe de sauvegarde invalide ou fichier corrompu")
                }
            } finally {
                _importProgress.value = null
            }
        }
    }

    // Secret Notes Operations
    fun saveNote(
        id: Long,
        title: String,
        plainContent: String,
        category: String,
        colorHex: String,
        isPinned: Boolean
    ) {
        viewModelScope.launch {
            repository.saveSecretNote(
                id = id,
                title = title,
                plainContent = plainContent,
                category = category,
                colorHex = colorHex,
                isPinned = isPinned,
                isDecoy = _isDecoySession.value
            )
            showToast("Note chiffrée enregistrée")
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            repository.deleteSecretNote(noteId)
            showToast("Note supprimée")
        }
    }

    // Passwords & Cards Operations
    fun savePassword(
        id: Long,
        title: String,
        account: String,
        plainSecret: String,
        website: String,
        category: String,
        notes: String,
        isFavorite: Boolean
    ) {
        viewModelScope.launch {
            repository.savePasswordEntry(
                id = id,
                title = title,
                account = account,
                plainSecret = plainSecret,
                website = website,
                category = category,
                notes = notes,
                isFavorite = isFavorite,
                isDecoy = _isDecoySession.value
            )
            showToast("Identifiant enregistré")
        }
    }

    fun deletePassword(id: Long) {
        viewModelScope.launch {
            repository.deletePasswordEntry(id)
            showToast("Identifiant supprimé")
        }
    }

    // Bookmarks
    fun addPrivateBookmark(title: String, url: String, category: String, accentHex: String) {
        viewModelScope.launch {
            repository.addBookmark(title, url, category, accentHex, _isDecoySession.value)
            showToast("Favori ajouté")
        }
    }

    fun deletePrivateBookmark(id: Long) {
        viewModelScope.launch {
            repository.deleteBookmark(id)
        }
    }

    // Security & Disguise Settings
    fun updateMasterPin(newPin: String) {
        viewModelScope.launch {
            prefsRepository.changeMasterPin(newPin)
            showToast("Code secret modifié")
        }
    }

    fun updateDecoyPin(decoyPin: String?) {
        viewModelScope.launch {
            prefsRepository.setDecoyPin(decoyPin)
            if (decoyPin.isNullOrBlank()) {
                showToast("Coffre leurre désactivé")
            } else {
                showToast("Second code leurre enregistré")
            }
        }
    }

    fun setDecoyVaultEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setDecoyVaultEnabled(enabled)
        }
    }

    fun setBiometricUnlock(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setBiometricUnlock(enabled)
        }
    }

    fun setPanicTriggerMode(mode: String) {
        viewModelScope.launch {
            prefsRepository.setPanicTriggerMode(mode)
        }
    }

    fun setBreakInAlerts(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setBreakInAlerts(enabled)
        }
    }

    fun setAutoLockTiming(timing: String) {
        viewModelScope.launch {
            prefsRepository.setAutoLockTiming(timing)
        }
    }

    fun setDisguiseAppConfig(iconMode: String, appName: String) {
        viewModelScope.launch {
            prefsRepository.setDisguiseAppConfig(iconMode, appName)
        }
    }

    fun setHideFromRecentApps(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setHideFromRecentApps(enabled)
        }
    }

    fun setBlockScreenshots(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setBlockScreenshots(enabled)
        }
    }

    fun setDeleteOriginalsAfterImport(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setDeleteOriginalsAfterImport(enabled)
        }
    }

    fun updateSecurityRecovery(question: String, answer: String) {
        viewModelScope.launch {
            prefsRepository.updateSecurityQuestion(question, answer)
            showToast("Question de récupération mise à jour")
        }
    }

    fun setShakeToLock(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setShakeToLock(enabled)
        }
    }

    fun setAutoLockOnBackground(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setAutoLockOnBackground(enabled)
        }
    }

    fun setDisguiseTheme(theme: String) {
        viewModelScope.launch {
            prefsRepository.setDisguiseTheme(theme)
        }
    }

    fun clearCalcHistory() {
        viewModelScope.launch {
            prefsRepository.clearCalculationHistory()
        }
    }

    fun clearIntruderLogs() {
        viewModelScope.launch {
            repository.clearSecurityLogs()
            showToast("Journal d'alertes effacé")
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appContext = context.applicationContext
            val cryptoManager = CryptoManager(appContext)
            val database = VaultDatabase.getInstance(appContext)
            val repository = VaultRepository(appContext, database.vaultDao(), cryptoManager)
            val prefsRepository = VaultPreferencesRepository(appContext, cryptoManager)
            return HideUViewModel(repository, prefsRepository) as T
        }
    }
}
