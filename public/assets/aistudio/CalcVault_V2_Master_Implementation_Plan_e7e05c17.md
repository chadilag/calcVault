# CalcVault V2 — Master Implementation Plan

## Mission and operating rules
You are the lead native Android engineer, application-security reviewer, QA engineer, and product designer responsible for improving the EXISTING CalcVault Android project. This is not a greenfield rewrite. The supplied project is Kotlin + Jetpack Compose with Gradle, Room, calculator logic, vault repository, encryption manager, and a ViewModel. Audit the actual code before making changes. Do not assume any issue is fixed until verified.

**Primary outcome:** a polished, reliable, offline-first Android calculator disguise that securely stores and manages REAL photos and videos. Match the functional quality of mature Android vault apps without copying proprietary assets, branding, or exact UI.

**Non-negotiable:** preserve legitimate existing user files, keys, metadata, and settings. Never delete data merely to make counters display zero. Never replace a failed feature with mock content. Never claim tests passed unless actually run. Work phase-by-phase and report evidence.

## 0. Initial repository audit (mandatory)
1. Inspect `app/build.gradle.kts`, AndroidManifest, min/target SDK, dependencies, Kotlin/Compose versions, navigation, storage model, permissions, Room schema and migrations, and tests. Check compatibility before changing versions.
2. Inspect at minimum `MainActivity.kt`, `security/CryptoManager.kt`, `data/VaultDatabase.kt`, `data/VaultRepository.kt`, `calculator/CalculatorEngine.kt`, `viewmodel/HideUViewModel.kt`, and all relevant screens. Trace calculator -> unlock -> vault -> import -> display -> export.
3. Identify all hardcoded UI values, demo media, placeholder handlers, unsafe storage paths, key fallback behavior, backup configuration, and incorrect lifecycle behavior. Determine whether sample records are distinguishable from genuine user records.
4. Create a matrix: feature, current implementation, evidence, defect, severity (P0-P3), affected files, recommended fix, and test plan.
5. Produce a data-preservation strategy, including an upgrade test from an existing installation. If a destructive operation is unavoidable, stop and ask for explicit approval.
6. Establish baseline build/test results; record commands and failures. If no Android SDK/emulator is available, report that limitation, do not invent test results.

**Known concerns from prior review; independently verify against source:** seeded `IMG_2039`-style demo records; hardcoded `248` / `1.2 GB`; Keystore failure fallback; destructive Room migration; `FLAG_SECURE` clearing. Do not blindly trust the prior review—inspect actual code.

## 1. Product scope and priorities
### P0 — security, preservation, correctness
- No plaintext private media persisted outside controlled app-private temporary or encrypted storage.
- No predictable encryption keys or insecure silent fallbacks.
- No destructive migration of legitimate data.
- No fake imported items or misleading progress.
- Safe interrupted-import recovery, lifecycle lock, screenshot/recent-app protection for sensitive screens.

### P1 — complete core product
- Real standard calculator and optional scientific mode.
- First-run PIN setup and confirmation; PIN followed by `=` unlocks the vault with no visible shortcut after setup.
- Real photo/video selection, import, encryption, gallery, photo viewer, video playback, export/restore, delete, and album organization.
- Accurate counts, sizes, metadata, thumbnails, and empty states.

### P2 — premium quality
- Smooth scrolling and thumbnail generation, refined design system, accessibility, dark mode, orientation/system-bar behavior, clear feedback, robust edge cases, and performance optimization.

### Later / out of MVP
- Password manager, private browser, advanced security center, secret notes, decoy vault, cloud sync, and complex extra utilities. Preserve existing modules safely if removing them could break data or navigation; do not invest in them until core features pass acceptance tests.

## 2. Calculator and discreet access
- After PIN setup, launch into a clean calculator showing `0`, no vault indicators, no prior expression, no "Enter Vault" action. During first-run setup, provide a clear but brief setup and confirmation experience; after setup it must disappear.
- Standard operations: digits, decimal, sign, addition, subtraction, multiplication, division, percent, clear, delete, equals; sensible precedence, negative values, divide-by-zero and overflow handling. Scientific mode only if implemented correctly.
- Separate calculation parsing from secret-PIN detection. Only an exact eligible PIN entry followed by `=` unlocks. Incorrect entries should not leak PIN state or reveal the vault; ordinary expressions must calculate normally. Specify how ambiguity with numeric calculations is handled and test it.
- Secure first-run PIN setup; do not store PIN in plaintext, logs, or ordinary preferences. Support rate limiting / backoff. Define safe behavior for lost PIN without promising impossible recovery.
- On lock/background timeout, remove decrypted sensitive UI state and return to calculator. Define behavior for screen off, app switch, process death, and device restart. Optional biometrics only if correctly tied to platform authentication and key access.

## 3. Cryptography and key lifecycle
- Use authenticated encryption such as AES-256-GCM with fresh unique 96-bit random nonces per encryption under a given key, authenticated metadata where appropriate, and explicit versioned file format. Never reuse nonce/key pairs.
- Generate/store keys securely using Android Keystore where feasible. No package-name-derived, fixed, hardcoded, or predictable fallback keys. If secure key access fails, fail closed and explain recovery options.
- Distinguish PIN verification from file-encryption key management. A salted, memory-hard KDF (e.g. Argon2id if safely supported) or platform-appropriate vetted alternative is required for PIN-derived secrets; simple SHA-256 of a 4–8 digit PIN is not acceptable. Keystore keys are not automatically protected against all unlocked-device attacks: document threat model and tradeoffs.
- Encrypt bytes using streaming/chunked authenticated design for large videos, or another reviewed bounded-memory approach. Do not load multi-GB videos fully into RAM. Authentication failure must never produce accepted partial plaintext.
- Protect metadata and thumbnails where feasible. Avoid plaintext thumbnail caches on disk; bound decrypted in-memory cache and clear on lock. Ensure Android backup rules do not back up unusable ciphertext without key strategy or expose sensitive material.
- Document key loss, reinstall, device transfer, and backup/recovery limitations. Provide a tested encrypted export/backup approach before representing the app as suitable for irreplaceable files.
- Security review must include `FLAG_SECURE` on sensitive screens, task previews, clipboard/share intents, temporary URI grants, logging, crash traces, and cleanup of decrypted temporary media.

## 4. Storage and database integrity
- Preserve Room schema and all real user records through explicit migrations; remove any destructive fallback in production. Never assume demo-looking filenames imply safe deletion. Delete only provably synthetic records after audit and with an explicit migration strategy.
- Use app-private storage for encrypted payloads and a Room metadata index with stable IDs, media type, original display name, original byte length, encrypted byte length, timestamps, album ID, encryption format/version, and import status.
- Keep database and filesystem consistent using staged writes, verification, atomic rename/commit where supported, and orphan cleanup. Handle cancellation, app crash, disk full, permission revoked, duplicate names, corrupt media, and missing files.
- Counters and storage totals must be computed from persisted actual data, not hardcoded demo values. Distinguish original size, encrypted size, and available device storage in labels.
- Do not automatically remove source media after import. Any source deletion requires a separate explicit confirmation and Android-compliant user-mediated deletion.

## 5. Photo and video import
- Use Android Photo Picker (`PickMultipleVisualMedia`) for images/videos where available, with compatible fallback; use SAF document picker only for supported documents if retained in scope.
- Import one or many files; display actual progress and file-specific errors. Support cancellation without orphaned records. Perform IO/encryption off main thread and avoid excessive concurrency/memory use.
- Read file metadata safely; do not trust display name, MIME type, or extension alone. Preserve original file and metadata where possible.
- Camera capture is optional for MVP. If included, use full-resolution `TakePicture` with a controlled content URI, not `TakePicturePreview` as a substitute for full-quality photos; encrypt and clean up temporary files reliably.
- Generate encrypted/protected thumbnails from real media; no seeded fake images. Video thumbnails must correspond to actual imported videos.

## 6. Gallery, photo viewer, video player
- Gallery: actual images and videos in a responsive grid, consistent aspect ratios, real thumbnail placeholders, loading/error states, smooth scrolling, and filters by type/album.
- Photo viewer: full-resolution image with pinch zoom, double-tap zoom, pan, next/previous navigation, metadata, and no sensitive screenshots.
- Video player: real playback with play/pause, seek, duration, orientation, audio, lifecycle pause, and cleanup. Use a maintained Android player (e.g. Media3) after verifying project compatibility. Never hand a private encrypted path directly to a player that expects plaintext; implement safe authenticated streaming or controlled temporary decrypted storage with strict cleanup and exposure controls. Document any residual risk.
- Operations: multi-select, move, export/restore through user-chosen destination, trash, recover, permanent delete with confirmation, and predictable navigation.
- Export must verify integrity and provide explicit user feedback; failed exports must not alter the vault copy.

## 7. Albums, search, and trash
- Preserve or create empty default albums: Family, Documents, Travel, Screenshots, Receipts, Private; zero counts at a genuinely empty vault. Support user-created albums, rename, delete, move, and meaningful empty states.
- Deleting an album must not silently destroy its media: offer moving contents or explicit separate deletion with confirmation.
- Album counts and covers update after import, move, trash, restore, and permanent deletion.
- Search and sorting use real indexed metadata; specify name/type/date sorting. Avoid exposing sensitive search strings to logs.
- Trash must distinguish soft-delete from permanent deletion, allow recovery, and define retention policy clearly.

## 8. UI/UX quality bar
- Keep a cohesive original design language with consistent typography, spacing, contrast, icons, transitions, and Material 3 / Compose conventions. Do not reproduce copyrighted HideU graphics or branding.
- Essential screens: Calculator, First-run Setup, Vault Home, Photo/Video Gallery, Albums, Viewer/Player, Import Progress, Trash, and Settings.
- Design empty, loading, success, partial-success, error, permission-denied, and storage-full states for every core flow.
- All visible buttons must work or be removed/disabled with honest messaging. No fake progress, placeholder photos, fabricated statistics, or dead navigation.
- Support common small/large screens, display/font scaling, TalkBack semantics, touch target sizing, dark mode, and Android back navigation. Do not promise 60 FPS without measurements.

## 9. Test and verification matrix
### Automated
- Calculator: operator precedence, decimal, negative, percentage, divide-by-zero, backspace, clear, large numbers, PIN unlock collision handling.
- Security: incorrect PIN, retry backoff, key unavailability, AES-GCM tampering/authentication failure, fresh nonces, lock lifecycle, no fallback key.
- Persistence: migrations with real user fixtures, fresh install, process restart, import interrupted mid-write, orphan cleanup, duplicate filename, corrupted media, disk full, missing URI permission.
- Repository: counts, albums, move, trash, restore, permanent delete, metadata, and search.
- UI: setup, unlock, empty gallery, import progress, gallery, photo viewer, video player, error states, back navigation.
### Manual on emulator and physical Android device
- Import 1 photo, 1 video, a mixed batch of 20+ files, and a large video; verify decrypt and playback after app restart.
- Verify source files remain untouched, no plaintext appears in public gallery/caches, screenshots/recent-app previews hide sensitive screens, and lock works after background/screen off.
- Export and compare original/exported content hashes; test cancel and storage-full failure.
- Test Android versions supported by min/target SDK, different screen sizes, accessibility font scaling, and long media lists.
- Measure cold start, scroll jank, memory usage, and import duration. Report measured values, not assumptions.

## 10. Execution phases and gates
**Phase A — Audit and baseline:** architecture map, issue inventory, build/test baseline, migration/data-risk analysis. Gate: evidence-based plan accepted.

**Phase B — P0 safety:** fix key fallback and cryptographic issues, screenshot protection, safe migration, no fake records, atomic imports. Gate: security and data-preservation tests pass.

**Phase C — Calculator and PIN:** complete calculator behavior, first-run setup, discreet unlock, lifecycle lock. Gate: calculator and authentication tests pass.

**Phase D — Import and encrypted persistence:** Android picker, reliable batch imports, accurate metadata/counters, cancellation/restart safety. Gate: real imported media survives restart and remains encrypted at rest.

**Phase E — Gallery and playback:** real thumbnails, full-resolution viewer, reliable video player, export. Gate: import -> view/play -> export round-trip passes.

**Phase F — Albums and management:** album CRUD, move, search, trash/recovery, permanent deletion. Gate: counts, files, and UI stay consistent.

**Phase G — Polish and release readiness:** UI refinement, performance, accessibility, instrumentation, device testing, security review, release build, documented limitations. Gate: all core acceptance scenarios pass with evidence.

At the start of each phase: list exact files to change, risks, and tests. At the end: list modified files, implementation summary, commands run, actual results, remaining defects, and whether the phase gate passed. Never silently skip a gate. Stop and ask when a change could irreversibly destroy user data or when architectural constraints make a requirement impossible.

## 11. Definition of Done
CalcVault V2 is not complete until:
- A real calculator works and remains discreet after setup.
- Correct PIN unlocks and incorrect PIN never reveals vault state.
- A real photo and video can be imported, encrypted, displayed/played, and exported correctly after restarting the app.
- All visible item counts and storage numbers are derived from real records.
- Albums, move, trash, restore, and permanent delete work without data inconsistency.
- No known insecure encryption fallback, destructive migration, or fake media remains.
- Core tests pass; release build succeeds; at least one physical-device verification is documented (or explicitly flagged pending).
- Remaining limitations are clearly listed, especially backup/recovery and any unsupported platform behaviors.

## 12. First instruction to the coding agent
**Begin with Phase A only.** Read the entire existing repository and the earlier `implementation_plan.md`, produce an audit with file-level evidence, a data-preservation strategy, and a refined execution plan. Do not edit source code yet. Await my approval before Phase B. Once approved, proceed one gated phase at a time. Do not rebuild from scratch.
