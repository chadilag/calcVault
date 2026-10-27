# CalcVault V2 — Revised Master Implementation Plan

This revised execution plan incorporates all 10 mandatory architectural, security, compatibility, scope, and testing corrections. No further source code modifications will be made until this revised plan is approved.

---

## 1. Mandatory Corrections Incorporated

### 1.1 `FLAG_SECURE` — Production Default vs. Development Emulator Override
- **Production Default (`blockScreenshots = true`)**:
  - In production builds (`!BuildConfig.DEBUG`), screenshot and recent-app preview protection (`WindowManager.LayoutParams.FLAG_SECURE`) is **enabled by default** (`blockScreenshots = true`) whenever the user unlocks and views any sensitive vault screen (`AppMode.VAULT_UNLOCKED`).
  - While in `AppMode.CALCULATOR_DISGUISE`, `FLAG_SECURE` remains cleared so the public calculator behaves identically to a standard utility calculator.
- **Controlled Development / AI Studio Emulator Override**:
  - Because the AI Studio browser streaming emulator captures the virtual display surface and renders a black screen when `FLAG_SECURE` is active, we provide an explicit development-only override (`BuildConfig.DEBUG` default override + a clearly labeled *"Block screenshots & recents preview"* toggle in **Security → App Disguise**).
  - `MainActivity` dynamically synchronizes `window.setFlags(FLAG_SECURE)` / `window.clearFlags(FLAG_SECURE)` based on `(appMode == AppMode.VAULT_UNLOCKED && securityState.blockScreenshots)`.

### 1.2 Existing Encrypted Data & Keystore Compatibility (`v1` & `v2`)
- **Strict Backward Compatibility for Existing Encrypted Files**:
  - `CryptoManager.decryptFileToStream(...)` inspects the first 6 bytes of every encrypted file using a `BufferedInputStream` with `mark(16)` / `reset()`:
    - **Format v2 (`CVLT2\x02`)**: Decrypts chunked 64 KB AES-256-GCM frames with per-chunk random 12-byte IVs and authenticated chunk-index AAD (`chunk:0`, `chunk:1`, …).
    - **Legacy Format v1 (no header)**: Resets the stream and decrypts the legacy `[12-byte IV][Ciphertext + 128-bit GCM Tag]` payload so all existing genuine user files remain 100% readable without re-encryption loss.
- **Fail-Closed Android Keystore Handling**:
  - `CryptoManager` **never** silently regenerates or overwrites the master key if the Keystore entry already exists or encounters a read error. If the hardware key cannot be loaded, `CryptoManager` throws a typed `GeneralSecurityException` (failing closed) rather than generating a new key that would permanently orphan existing encrypted files.

### 1.3 Safe, Non-Destructive Database Migration (`MIGRATION_1_2`)
- **Targeted Synthetic Row Removal Only**:
  - `MIGRATION_1_2` deletes **only** positively identified synthetic demo records from v1 (`DELETE FROM vault_files WHERE encryptedFilePath LIKE '%/seed_IMG_%' AND (originalName LIKE 'user_IMG_20%' OR originalName LIKE 'IMG_20%')`).
- **No Filename Prefix Stripping**:
  - `MIGRATION_1_2` **will not** strip `user_` prefixes from `originalName` via `SUBSTR(...)`, ensuring legitimate user files whose real names start with `user_` (e.g., `user_manual.pdf`, `user_avatar.png`) are never altered or damaged.
  - New imports in `VaultRepository` store the exact original display name directly without prepending `user_`.
- **No `.fallbackToDestructiveMigration()`**:
  - Room is configured strictly with `.addMigrations(MIGRATION_1_2)` so schema upgrades never wipe user data.

### 1.4 PIN Security (6–8 Digits, PBKDF2-HMAC-SHA256, Throttling & Forgotten PIN Handling)
- **6–8 Digit PIN Policy**:
  - All new PIN setups and PIN changes require **6 to 8 digits** (replacing the weaker 4-digit minimum).
  - For backward compatibility on existing installations, if a user previously configured a 4–5 digit PIN hash prior to V2, `verifyPinInput` still allows verifying that existing hash until the user updates their PIN.
- **Zero Hardcoded Master PINs or Backdoors**:
  - No default PIN (`1234`, `0000`) and no magic calculator recovery code (`11223344=`) are active unless explicitly configured by the user.
  - All PIN and recovery hashes use `PBKDF2WithHmacSHA256` (120,000 iterations, 256-bit derived key, random 16-byte cryptographic salt per secret) verified via constant-time `MessageDigest.isEqual`.
- **Brute-Force Throttling (Exponential Backoff)**:
  - Failed unlock attempts are persisted in DataStore (`failedAttemptsCount`, `lockoutUntilEpochMs`).
  - After 5 failed attempts, PIN verification enforces escalating cooldowns (`30s` → `60s` → `5m` → `15m`). During cooldown, pressing `=` evaluates standard calculator math without attempting PIN verification.
- **Clear Forgotten-PIN Handling**:
  - During PIN setup or in Security settings, users can optionally configure a **Recovery Phrase / Question**.
  - If a user forgets their PIN and has configured a recovery phrase, they can access the recovery verification dialog after 3 failed PIN attempts (or via a subtle option in the calculator history sheet when locked out) to verify their PBKDF2-hashed recovery answer and set a new 6–8 digit PIN.
  - If no recovery phrase is configured, the UI clearly informs the user that the PIN cannot be bypassed or recovered by anyone.

### 1.5 Video Playback Privacy & Ephemeral File Lifecycle
- **Minimized Plaintext Exposure**:
  - Encrypted videos are stored at rest in `CVLT2` AES-256-GCM chunks inside `filesDir/vault_blobs`.
  - When playing a video in `PhotoViewerScreen`, playback prepares an isolated temporary file inside `context.noBackupFilesDir/temp_playback` with owner-only permissions (`setReadable(false, false); setReadable(true, true)`).
- **Strict Cleanup Lifecycle**:
  - The temporary playback file is deleted immediately on:
    1. `PhotoViewerScreen` exit or switching to another media item (`DisposableEffect.onDispose`),
    2. App backgrounding / auto-lock (`Lifecycle.Event.ON_STOP` -> `lockVaultImmediately()`),
    3. Coroutine cancellation during video preparation,
    4. App startup / crash recovery (`VaultRepository.init` sweeps and deletes all files in `temp_playback/` and any `.tmp` staging files in `vault_blobs/`).
- **Honest Security Disclosure**:
  - Code comments and UI copy do **not** claim that deleting or overwriting a temporary file guarantees physical forensic erasure on wear-leveled NAND flash storage.

### 1.6 Backup, Export & Keystore Loss Protection
- **Clear Risk Disclosure & Real Encrypted Archive Export**:
  - Because Android Keystore hardware-backed keys are bound to the app installation and device hardware, uninstalling the app or losing the device destroys the Keystore key.
  - Every item in the vault can be exported back to user-selected device storage via Android SAF (`ActivityResultContracts.CreateDocument` / `Export`).
  - In **Security → Encrypted Backup**, we replace the simulated toast dialog with a **real Portable Encrypted Backup (`.cvbackup`) Export & Restore** workflow using Android SAF (`CreateDocument("application/octet-stream")` / `OpenDocument`) where the backup archive is encrypted with a user-entered backup password via `PBKDF2WithHmacSHA256` (120,000 iterations) + `AES-256-GCM`, independent of the device-bound Keystore key, alongside a clear explanation that users must export a backup before uninstalling the app or switching phones.

### 1.7 Core Product Scope Focus
- **Focused Navigation & Features**:
  - We focus strictly on the core product:
    1. **Discreet Calculator** (starting at `0`, 2-step 6–8 digit PIN setup on first launch, 100% stealth thereafter),
    2. **My Vault Home** (real dynamic storage metrics, real category counts for *Photos*, *Videos*, *Files*, *Albums*, *Trash*, real-time search, and *Recently added* real thumbnails or empty state),
    3. **Dynamic Media Gallery & Multi-Select** (filter by *All*, *Photos*, *Videos*, *Files*, *Trash*, or specific *Album*; sort by *Newest*, *Name*, *Size*; batch *Move to Album*, *Export/Restore*, *Move to Trash*, and *Permanent Delete*),
    4. **Full-Screen Photo Viewer & Video Player** (pinch-to-zoom, double-tap zoom, real EXIF/file metadata, encrypted video playback, SAF file export),
    5. **Albums Grid** (6 empty default albums starting at `0 items`, create custom album, rename album, safe delete album),
    6. **Essential Security & Disguise Settings** (change 6–8 digit secret code, configure recovery phrase, decoy vault, panic lock via flip/shake/floating button, auto-lock timing, screenshot protection toggle, and portable `.cvbackup` export/restore).
  - Non-core distractions (*Private Browser*, *Password Manager*, *Secret Notes*) are removed from the bottom navigation bar and home category grid so the bottom bar is dedicated to **`Vault` · `Albums` · `+` · `Trash` · `Security`** and every visible control is 100% real and thoroughly tested.

---

## 2. Phased Execution & Verification Gates

### Phase 1: Security, Keystore Compatibility, Non-Destructive Migration & Portable Backup (`CryptoManager`, `VaultDatabase`, `VaultPreferencesRepository`, `VaultRepository`)
- **Files to Modify**:
  - `/app/src/main/java/com/example/security/CryptoManager.kt`
  - `/app/src/main/java/com/example/data/VaultDatabase.kt`
  - `/app/src/main/java/com/example/security/VaultPreferencesRepository.kt`
  - `/app/src/main/java/com/example/data/VaultRepository.kt`
- **Changes**:
  - Verify fail-closed Keystore retrieval and dual v1/v2 stream decryption in `CryptoManager`, plus password-based `PBKDF2WithHmacSHA256` + `AES-256-GCM` portable `.cvbackup` stream encryption/decryption.
  - Remove the `SUBSTR(originalName, 6)` query from `MIGRATION_1_2` in `VaultDatabase.kt` so `user_` prefixes on legitimate filenames are never stripped, and restrict deletion strictly to synthetic `seed_IMG_*` rows.
  - Enforce 6–8 digit PIN creation/update in `VaultPreferencesRepository.kt`, remove any unconfigured recovery backdoors, and set `blockScreenshots` default to `!BuildConfig.DEBUG` (true in production, overridable in debug/emulator).
  - Add low-storage / `IOException` safe cleanup and portable `.cvbackup` archive export/import in `VaultRepository.kt`.
- **Gate 1 Verification**: Run `compile_applet` and unit tests for v1/v2 compatibility, `MIGRATION_1_2`, and 6–8 digit PIN throttling.

### Phase 2: Discreet Calculator & 6–8 Digit PIN Setup Flow (`HideUViewModel`, `CalculatorScreen`, `MainActivity`)
- **Files to Modify**:
  - `/app/src/main/java/com/example/viewmodel/HideUViewModel.kt`
  - `/app/src/main/java/com/example/calculator/CalculatorScreen.kt`
  - `/app/src/main/java/com/example/MainActivity.kt`
- **Changes**:
  - Enforce 6–8 digit PIN validation during first-launch setup (`PinSetupStage.ENTER_FIRST_PIN` -> `CONFIRM_PIN` -> `READY`).
  - Provide an optional "Forgot PIN?" recovery button on the calculator **only** when the user has configured a recovery answer and has triggered 3+ failed PIN attempts.
  - Synchronize `WindowManager.LayoutParams.FLAG_SECURE` in `MainActivity.kt` whenever `appMode == AppMode.VAULT_UNLOCKED && securityState.blockScreenshots`.
- **Gate 2 Verification**: Run `compile_applet` and Robolectric calculator UI tests.

### Phase 3: Core Vault UI Scope & Zero Placebo Actions (`VaultMainScreen`, `VaultFilesTab`, `VaultMediaScreens`, `SecurityCenterTab`)
- **Files to Modify**:
  - `/app/src/main/java/com/example/ui/vault/VaultMainScreen.kt`
  - `/app/src/main/java/com/example/ui/vault/VaultFilesTab.kt`
  - `/app/src/main/java/com/example/ui/vault/VaultMediaScreens.kt`
  - `/app/src/main/java/com/example/ui/vault/SecurityCenterTab.kt`
- **Changes**:
  - Streamline the bottom navigation bar and home category tiles to the core product: **Photos, Videos, Files, Albums, Trash, and Security** (removing non-core Browser/Notes/Passwords tabs).
  - Ensure every action button in `PhotosSelectModeScreen` and `PhotoViewerScreen` performs a real database/file/SAF operation (*Export to Device*, *Move to Album*, *Restore from Trash*, *Move to Trash*, *Delete Permanently*).
  - Wire the **Encrypted Backup (`.cvbackup`)** row in `SecurityCenterTab` to real SAF `CreateDocument` / `OpenDocument` launchers with a user-defined backup password and clear Keystore/uninstall warnings.
- **Gate 3 Verification**: Run `compile_applet` and verify zero unused/dead-end UI affordances.

### Phase 4: Comprehensive Automated & Emulator/Device Test Suite (`ExampleRobolectricTest.kt`)
- **Files to Modify**:
  - `/app/src/test/java/com/example/ExampleRobolectricTest.kt`
- **Automated JVM / Robolectric Tests (`gradle :app:testDebugUnitTest`)**:
  1. **Calculator & 6–8 Digit PIN Flow**: Starts at `0`, evaluates precedence/decimals/negatives/divide-by-zero, enforces 6–8 digit PIN creation, unlocks on exact PIN + `=`, and throttles after 5 wrong PIN attempts.
  2. **v1 -> v2 Encryption Compatibility & Tamper Detection**: Verifies legacy v1 ciphertexts and new v2 chunked ciphertexts both decrypt accurately, and tampered ciphertexts throw `GeneralSecurityException`.
  3. **Database `MIGRATION_1_2` Safety**: Verifies synthetic `seed_IMG_*` rows are removed while a legitimate `user_manual.pdf` row and its `user_` prefix are preserved intact.
  4. **Real Media Import, Export, Interrupted Import & Low Storage**: Verifies stream import, SAF export round-trip, `.tmp` cleanup on cancelled/IOException import, and persistence across repository re-instantiation (simulated app restart).
  5. **Album CRUD & Trash Lifecycle**: Verifies creating, renaming, moving files between albums, safe album deletion (reassigning files to default album), moving to Trash, restoring, and permanent deletion.
  6. **Video Playback Ephemeral File Cleanup**: Verifies temporary playback file creation in `noBackupFilesDir/temp_playback` and immediate deletion on release, vault lock, and startup crash-recovery sweep.
- **Streaming Emulator & Physical Device Manual Verification Checklist**:
  - Clearly documented separately from automated JVM tests (pinch-to-zoom gesture feel, `VideoView` hardware codec rendering, `FLAG_SECURE` screenshot blocking on physical Android device vs. debug streaming emulator override, and accelerometer flip/shake panic lock).
