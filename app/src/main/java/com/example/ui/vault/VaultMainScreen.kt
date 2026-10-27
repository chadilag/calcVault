package com.example.ui.vault

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.CalcBlueAccentText
import com.example.ui.theme.CalcBluePrimary
import com.example.ui.theme.CalcVaultBg
import com.example.ui.theme.CalcVaultDivider
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.viewmodel.HideUViewModel
import com.example.viewmodel.VaultSubScreen
import com.example.viewmodel.VaultTab

@Composable
fun VaultMainScreen(
    viewModel: HideUViewModel
) {
    val selectedTab by viewModel.selectedVaultTab.collectAsStateWithLifecycle()
    val activeSubScreen by viewModel.activeSubScreen.collectAsStateWithLifecycle()
    val showAddToVaultSheet by viewModel.showAddToVaultSheet.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val files by viewModel.files.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val passwords by viewModel.passwords.collectAsStateWithLifecycle()
    val intruderLogs by viewModel.intruderLogs.collectAsStateWithLifecycle()
    val securityState by viewModel.securityState.collectAsStateWithLifecycle()
    val toastMessage by viewModel.statusToast.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(toastMessage) {
        val msg = toastMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    // Back handler navigates back from sub-screen, tab, or locks back to calculator disguise
    BackHandler {
        viewModel.navigateBackInVault()
    }

    val defaultFolderId = folders.firstOrNull()?.id ?: 1L

    // Zero-permission Android Photo & Video Picker (up to 25 items per batch)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 25)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importUris(uris, defaultFolderId, "PHOTO")
        }
    }

    // SAF Document Picker
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importUris(uris, defaultFolderId, "DOCUMENT")
        }
    }

    // Camera Snapshot Capture Launcher
    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            viewModel.importCapturedCameraBitmap(bitmap, defaultFolderId)
        }
    }

    val showBottomBar = activeSubScreen !is VaultSubScreen.PhotosSelectMode &&
        activeSubScreen !is VaultSubScreen.PhotoViewer

    val activeFilesCount = remember(files) { files.count { !it.isTrashed } }
    val activeBytesUsed = remember(files) { files.filter { !it.isTrashed }.sumOf { it.encryptedSizeBytes } }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(CalcVaultBg)
            .testTag("vault_main_screen"),
        containerColor = CalcVaultBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                CalcVaultBottomBar(
                    selectedTab = selectedTab,
                    onSelectTab = { tab -> viewModel.selectVaultTab(tab) },
                    onCenterAddClick = { viewModel.setShowAddToVaultSheet(true) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CalcVaultBg)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            when (val sub = activeSubScreen) {
                is VaultSubScreen.PhotosSelectMode -> {
                    PhotosSelectModeScreen(
                        initialFilter = sub.initialCategoryOrAlbum,
                        files = files,
                        folders = folders,
                        loadThumbnail = { file -> viewModel.repository.loadThumbnailBitmap(file) },
                        decryptFileBytes = { file -> viewModel.repository.decryptFileBytes(file) },
                        onClose = { viewModel.navigateToSubScreen(VaultSubScreen.None) },
                        onOpenPhotoViewer = { index ->
                            viewModel.navigateToSubScreen(VaultSubScreen.PhotoViewer(index))
                        },
                        onOpenAddSheet = {
                            viewModel.setShowAddToVaultSheet(true)
                        },
                        onExportFile = viewModel::exportUnhideFile,
                        onMoveSelectedToAlbum = { selectedFiles, targetFolderId, targetFolderName ->
                            viewModel.moveFilesToFolder(selectedFiles, targetFolderId, targetFolderName)
                        },
                        onDeleteSelected = { toDelete ->
                            toDelete.forEach { viewModel.moveFileToTrash(it) }
                        },
                        onPermanentlyDeleteSelected = { toWipe ->
                            toWipe.forEach { viewModel.shredFilePermanently(it) }
                        },
                        onRestoreSelected = { toRestore ->
                            toRestore.forEach { file ->
                                if (file.isTrashed) {
                                    viewModel.restoreFileFromTrash(file)
                                }
                            }
                            viewModel.showToast("Restored ${toRestore.size} encrypted item(s)")
                        },
                        onShowToast = viewModel::showToast
                    )
                }

                is VaultSubScreen.PhotoViewer -> {
                    PhotoViewerScreen(
                        initialIndex = sub.selectedIndex,
                        files = files,
                        folders = folders,
                        loadThumbnail = { file -> viewModel.repository.loadThumbnailBitmap(file) },
                        decryptFileBytes = { file -> viewModel.repository.decryptFileBytes(file) },
                        prepareTempVideoFile = { file -> viewModel.repository.prepareTempVideoPlaybackFile(file) },
                        releaseTempVideoFile = { tempFile -> viewModel.repository.releaseTempVideoPlaybackFile(tempFile) },
                        onBack = { viewModel.navigateToSubScreen(VaultSubScreen.None) },
                        onExportFile = viewModel::exportUnhideFile,
                        onDeleteFile = viewModel::moveFileToTrash,
                        onMoveFileToAlbum = { selectedFiles, targetFolderId, targetFolderName ->
                            viewModel.moveFilesToFolder(selectedFiles, targetFolderId, targetFolderName)
                        },
                        onShowToast = viewModel::showToast
                    )
                }

                VaultSubScreen.PanicAndDecoy -> {
                    PanicAndDecoyScreen(
                        securityState = securityState,
                        onBack = { viewModel.navigateToSubScreen(VaultSubScreen.None) },
                        onSelectPanicMode = viewModel::setPanicTriggerMode,
                        onUpdateDecoyPin = viewModel::updateDecoyPin,
                        onOpenDecoyVaultPreview = {
                            viewModel.openVaultDirectly(isDecoy = true)
                            viewModel.showToast("Viewing Decoy Vault content")
                        }
                    )
                }

                VaultSubScreen.AppDisguise -> {
                    AppDisguiseScreen(
                        securityState = securityState,
                        onBack = { viewModel.navigateToSubScreen(VaultSubScreen.None) },
                        onSelectDisguise = viewModel::setDisguiseAppConfig,
                        onToggleHideFromRecents = viewModel::setHideFromRecentApps,
                        onToggleBlockScreenshots = viewModel::setBlockScreenshots
                    )
                }

                VaultSubScreen.NotesScreen -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularHeaderButton(
                                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Back",
                                onClick = { viewModel.navigateToSubScreen(VaultSubScreen.None) }
                            )
                            Text(
                                text = "Encrypted Notes",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                        }
                        SecretNotesTab(
                            notes = notes,
                            decryptContent = { note -> viewModel.repository.decryptNoteContent(note) },
                            onSaveNote = viewModel::saveNote,
                            onDeleteNote = viewModel::deleteNote
                        )
                    }
                }

                VaultSubScreen.PasswordsScreen -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularHeaderButton(
                                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Back",
                                onClick = { viewModel.navigateToSubScreen(VaultSubScreen.None) }
                            )
                            Text(
                                text = "Passwords & Keys",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                        }
                        PasswordsTab(
                            entries = passwords,
                            cryptoManager = viewModel.repository.cryptoManager,
                            decryptSecret = { entry -> viewModel.repository.decryptPasswordSecret(entry) },
                            onSavePassword = viewModel::savePassword,
                            onDeletePassword = viewModel::deletePassword,
                            onCopiedToast = viewModel::showToast
                        )
                    }
                }

                VaultSubScreen.None -> {
                    when (selectedTab) {
                        VaultTab.FILES -> {
                            MyVaultHomeScreen(
                                files = files,
                                folders = folders,
                                securityState = securityState,
                                loadThumbnail = { file -> viewModel.repository.loadThumbnailBitmap(file) },
                                decryptFileBytes = { file -> viewModel.repository.decryptFileBytes(file) },
                                onOpenPhotosSelectMode = { filter ->
                                    viewModel.navigateToSubScreen(VaultSubScreen.PhotosSelectMode(filter))
                                },
                                onOpenPhotoViewer = { idx ->
                                    viewModel.navigateToSubScreen(VaultSubScreen.PhotoViewer(idx))
                                },
                                onOpenAlbums = {
                                    viewModel.selectVaultTab(VaultTab.ALBUMS)
                                },
                                onOpenSecurity = {
                                    viewModel.selectVaultTab(VaultTab.SECURITY)
                                },
                                onOpenPanicAndDecoy = {
                                    viewModel.selectVaultTab(VaultTab.SECURITY)
                                    viewModel.navigateToSubScreen(VaultSubScreen.PanicAndDecoy)
                                },
                                onOpenAddSheet = {
                                    viewModel.setShowAddToVaultSheet(true)
                                }
                            )
                        }

                        VaultTab.ALBUMS -> {
                            AlbumsGridScreen(
                                folders = folders,
                                files = files,
                                loadThumbnail = { file -> viewModel.repository.loadThumbnailBitmap(file) },
                                onCreateAlbum = viewModel::createCustomFolder,
                                onRenameAlbum = { folder, newName -> viewModel.renameFolder(folder.id, newName) },
                                onDeleteAlbum = { folder -> viewModel.deleteFolderSafely(folder.id) },
                                onSelectAlbum = { albumName ->
                                    viewModel.navigateToSubScreen(VaultSubScreen.PhotosSelectMode(albumName))
                                }
                            )
                        }

                        VaultTab.TRASH -> {
                            PhotosSelectModeScreen(
                                initialFilter = "Trash",
                                files = files,
                                folders = folders,
                                loadThumbnail = { file -> viewModel.repository.loadThumbnailBitmap(file) },
                                decryptFileBytes = { file -> viewModel.repository.decryptFileBytes(file) },
                                onClose = { viewModel.selectVaultTab(VaultTab.FILES) },
                                onOpenPhotoViewer = { index ->
                                    viewModel.navigateToSubScreen(VaultSubScreen.PhotoViewer(index))
                                },
                                onOpenAddSheet = {
                                    viewModel.setShowAddToVaultSheet(true)
                                },
                                onExportFile = viewModel::exportUnhideFile,
                                onMoveSelectedToAlbum = { selectedFiles, targetFolderId, targetFolderName ->
                                    viewModel.moveFilesToFolder(selectedFiles, targetFolderId, targetFolderName)
                                },
                                onDeleteSelected = { toDelete ->
                                    toDelete.forEach { viewModel.moveFileToTrash(it) }
                                },
                                onPermanentlyDeleteSelected = { toWipe ->
                                    toWipe.forEach { viewModel.shredFilePermanently(it) }
                                },
                                onRestoreSelected = { toRestore ->
                                    toRestore.forEach { file ->
                                        if (file.isTrashed) {
                                            viewModel.restoreFileFromTrash(file)
                                        }
                                    }
                                    viewModel.showToast("Restored ${toRestore.size} encrypted item(s)")
                                },
                                onShowToast = viewModel::showToast
                            )
                        }

                        VaultTab.SECURITY -> {
                            SecurityCenterTab(
                                securityState = securityState,
                                intruderLogs = intruderLogs,
                                totalItemsCount = activeFilesCount,
                                totalBytesUsed = activeBytesUsed,
                                onChangeMasterPin = viewModel::updateMasterPin,
                                onUpdateSecurityRecovery = viewModel::updateSecurityRecovery,
                                onToggleDecoyVault = viewModel::setDecoyVaultEnabled,
                                onToggleBiometricUnlock = viewModel::setBiometricUnlock,
                                onOpenPanicAndDecoy = {
                                    viewModel.navigateToSubScreen(VaultSubScreen.PanicAndDecoy)
                                },
                                onToggleBreakInAlerts = viewModel::setBreakInAlerts,
                                onChangeAutoLockTiming = viewModel::setAutoLockTiming,
                                onOpenAppDisguise = {
                                    viewModel.navigateToSubScreen(VaultSubScreen.AppDisguise)
                                },
                                onToggleHideFromRecents = viewModel::setHideFromRecentApps,
                                onClearIntruderLogs = viewModel::clearIntruderLogs,
                                onExportPortableBackup = viewModel::exportPortableBackup,
                                onRestorePortableBackup = viewModel::restorePortableBackup,
                                onShowToast = viewModel::showToast
                            )
                        }
                    }
                }
            }

            // Optional Floating Panic Button when selected in Panic & Decoy
            if (securityState.panicTriggerMode == "FLOATING") {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = CalcBluePrimary.copy(alpha = 0.2f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 72.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable { viewModel.lockVaultImmediately("Floating Panic Button") }
                        .testTag("quick_lock_vault_button")
                ) {
                    Text(
                        text = "Lock",
                        style = MaterialTheme.typography.labelMedium,
                        color = CalcBlueAccentText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    // "Add to vault" Modal Bottom Sheet
    if (showAddToVaultSheet || importProgress != null) {
        AddToVaultBottomSheet(
            deleteOriginalsAfterImport = securityState.deleteOriginalsAfterImport,
            importProgress = importProgress,
            onToggleDeleteOriginals = viewModel::setDeleteOriginalsAfterImport,
            onPickGallery = {
                viewModel.setShowAddToVaultSheet(false)
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
            },
            onCaptureCamera = {
                viewModel.setShowAddToVaultSheet(false)
                try {
                    cameraCaptureLauncher.launch(null)
                } catch (_: Exception) {
                    viewModel.showToast("Camera unavailable on this device")
                }
            },
            onPickFiles = {
                viewModel.setShowAddToVaultSheet(false)
                documentPickerLauncher.launch(arrayOf("*/*"))
            },
            onCreateNewNote = {
                viewModel.setShowAddToVaultSheet(false)
                viewModel.selectVaultTab(VaultTab.ALBUMS)
            },
            onCancelImport = viewModel::cancelActiveImport,
            onDismiss = { viewModel.setShowAddToVaultSheet(false) }
        )
    }
}

/**
 * Custom Bottom Navigation Bar:
 * [Vault]  [Albums]   (+)   [Trash]  [Security]
 */
@Composable
private fun CalcVaultBottomBar(
    selectedTab: VaultTab,
    onSelectTab: (VaultTab) -> Unit,
    onCenterAddClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CalcVaultBg)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        HorizontalDivider(color = CalcVaultDivider, thickness = 1.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            BottomNavTabItem(
                icon = Icons.Outlined.Home,
                label = "Vault",
                selected = selectedTab == VaultTab.FILES,
                onClick = { onSelectTab(VaultTab.FILES) },
                modifier = Modifier.testTag("vault_nav_files")
            )

            BottomNavTabItem(
                icon = Icons.Outlined.Folder,
                label = "Albums",
                selected = selectedTab == VaultTab.ALBUMS,
                onClick = { onSelectTab(VaultTab.ALBUMS) },
                modifier = Modifier.testTag("vault_nav_albums")
            )

            // Center Floating Electric Blue '+' Button
            Box(
                modifier = Modifier
                    .offset(y = (-6).dp)
                    .size(56.dp)
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .background(CalcBluePrimary)
                    .clickable(onClick = onCenterAddClick)
                    .testTag("vault_fab_add"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add to vault",
                    tint = CalcVaultBg,
                    modifier = Modifier.size(28.dp)
                )
            }

            BottomNavTabItem(
                icon = Icons.Outlined.DeleteOutline,
                label = "Trash",
                selected = selectedTab == VaultTab.TRASH,
                onClick = { onSelectTab(VaultTab.TRASH) },
                modifier = Modifier.testTag("vault_nav_trash")
            )

            BottomNavTabItem(
                icon = Icons.Outlined.Shield,
                label = "Security",
                selected = selectedTab == VaultTab.SECURITY,
                onClick = { onSelectTab(VaultTab.SECURITY) },
                modifier = Modifier.testTag("vault_nav_security")
            )
        }
    }
}

@Composable
private fun BottomNavTabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (selected) CalcBluePrimary else TextMuted
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 11.sp
        )
    }
}
