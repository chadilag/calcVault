package com.example.ui.vault

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.VaultFileEntity
import com.example.data.VaultFolderEntity
import com.example.ui.theme.CalcBlueAccentText
import com.example.ui.theme.CalcBluePrimary
import com.example.ui.theme.CalcTrackDark
import com.example.ui.theme.CalcVaultBg
import com.example.ui.theme.CalcVaultBorder
import com.example.ui.theme.CalcVaultCard
import com.example.ui.theme.CalcVaultCardElevated
import com.example.ui.theme.CalcVaultDivider
import com.example.ui.theme.DeleteCoralRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.viewmodel.ImportProgressState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class VaultSortOrder(val label: String) {
    NEWEST("Newest"),
    NAME("Name"),
    SIZE("Size")
}

/**
 * Dynamic Gallery & Multi-Select Screen (replaces static 12-item mock grid)
 */
@Composable
fun PhotosSelectModeScreen(
    initialFilter: String,
    files: List<VaultFileEntity>,
    folders: List<VaultFolderEntity> = emptyList(),
    loadThumbnail: (suspend (VaultFileEntity) -> ImageBitmap?)? = null,
    decryptFileBytes: suspend (VaultFileEntity) -> ByteArray,
    onClose: () -> Unit,
    onOpenPhotoViewer: (Int) -> Unit,
    onOpenAddSheet: () -> Unit = {},
    onExportFile: (VaultFileEntity, Uri) -> Unit = { _, _ -> },
    onMoveSelectedToAlbum: (List<VaultFileEntity>, Long, String) -> Unit = { _, _, _ -> },
    onDeleteSelected: (List<VaultFileEntity>) -> Unit,
    onPermanentlyDeleteSelected: (List<VaultFileEntity>) -> Unit = {},
    onRestoreSelected: (List<VaultFileEntity>) -> Unit,
    onShowToast: (String) -> Unit
) {
    val activeFiles = remember(files) { files.filter { !it.isTrashed } }
    val trashedFiles = remember(files) { files.filter { it.isTrashed } }

    var selectedFilter by remember(initialFilter) {
        mutableStateOf(
            when {
                initialFilter.equals("Docs", ignoreCase = true) -> "Files"
                initialFilter.startsWith("All") -> "All"
                else -> initialFilter
            }
        )
    }

    var sortOrder by remember { mutableStateOf(VaultSortOrder.NEWEST) }
    val selectedIds = remember { mutableStateListOf<Long>() }
    var showMoveAlbumDialog by remember { mutableStateOf(false) }
    var pendingExportFile by remember { mutableStateOf<VaultFileEntity?>(null) }

    val filteredFiles = remember(activeFiles, trashedFiles, folders, selectedFilter, sortOrder) {
        val base = when {
            selectedFilter.equals("Trash", ignoreCase = true) -> trashedFiles
            selectedFilter.equals("All", ignoreCase = true) -> activeFiles
            selectedFilter.equals("Photos", ignoreCase = true) -> activeFiles.filter { it.category == "PHOTO" }
            selectedFilter.equals("Videos", ignoreCase = true) -> activeFiles.filter { it.category == "VIDEO" }
            selectedFilter.equals("Files", ignoreCase = true) -> activeFiles.filter {
                it.category == "DOCUMENT" || it.category == "AUDIO"
            }
            else -> {
                val matchingFolderIds = folders
                    .filter { it.name.equals(selectedFilter, ignoreCase = true) }
                    .map { it.id }
                    .toSet()
                activeFiles.filter {
                    it.folderId in matchingFolderIds || it.notes.contains(selectedFilter, ignoreCase = true)
                }
            }
        }
        when (sortOrder) {
            VaultSortOrder.NEWEST -> base.sortedByDescending { it.createdAt }
            VaultSortOrder.NAME -> base.sortedBy { it.originalName.lowercase(Locale.ROOT) }
            VaultSortOrder.SIZE -> base.sortedByDescending { it.originalSizeBytes }
        }
    }

    // Keep selection clean if filtered list changes
    LaunchedEffect(selectedFilter) {
        selectedIds.clear()
    }

    val filterChips = remember(activeFiles, trashedFiles, folders) {
        buildList {
            add("All" to "All (${activeFiles.size})")
            add("Photos" to "Photos (${activeFiles.count { it.category == "PHOTO" }})")
            add("Videos" to "Videos (${activeFiles.count { it.category == "VIDEO" }})")
            add("Files" to "Files (${activeFiles.count { it.category == "DOCUMENT" || it.category == "AUDIO" }})")
            folders.forEach { folder ->
                val count = activeFiles.count { it.folderId == folder.id }
                add(folder.name to "${folder.name} ($count)")
            }
            if (trashedFiles.isNotEmpty() || selectedFilter.equals("Trash", ignoreCase = true)) {
                add("Trash" to "Trash (${trashedFiles.size})")
            }
        }
    }

    val selectedEntities = remember(filteredFiles, selectedIds.toList()) {
        filteredFiles.filter { it.id in selectedIds }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val target = pendingExportFile
        pendingExportFile = null
        if (uri != null && target != null) {
            onExportFile(target, uri)
            selectedIds.remove(target.id)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CalcVaultBg)
            .testTag("photos_select_mode_screen")
    ) {
        // 1. Top Selection Bar: Circular 'X', count / title, Sort + Select all
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularHeaderButton(
                    icon = Icons.Filled.Close,
                    contentDescription = "Close gallery",
                    onClick = onClose,
                    modifier = Modifier.testTag("photos_select_close_button")
                )
                Column {
                    Text(
                        text = if (selectedIds.isEmpty()) {
                            "$selectedFilter (${filteredFiles.size})"
                        } else {
                            "${selectedIds.size} selected"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Text(
                        text = "Tap photo to view · Tap circle to select",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = CalcVaultCard,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            sortOrder = when (sortOrder) {
                                VaultSortOrder.NEWEST -> VaultSortOrder.NAME
                                VaultSortOrder.NAME -> VaultSortOrder.SIZE
                                VaultSortOrder.SIZE -> VaultSortOrder.NEWEST
                            }
                        }
                        .border(1.dp, CalcVaultBorder, RoundedCornerShape(50))
                        .testTag("photos_sort_button")
                ) {
                    Text(
                        text = "Sort: ${sortOrder.label}",
                        color = CalcBlueAccentText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                if (filteredFiles.isNotEmpty()) {
                    val allSelected = selectedIds.size == filteredFiles.size
                    Text(
                        text = if (allSelected) "Deselect all" else "Select all",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CalcBluePrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable {
                                if (allSelected) {
                                    selectedIds.clear()
                                } else {
                                    selectedIds.clear()
                                    selectedIds.addAll(filteredFiles.map { it.id })
                                }
                            }
                            .testTag("photos_select_all_button")
                    )
                }
            }
        }

        // 2. Dynamic Filter Pills Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            filterChips.forEach { (filterKey, chipLabel) ->
                val isSelected = selectedFilter.equals(filterKey, ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isSelected) CalcBluePrimary else CalcVaultCard,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { selectedFilter = filterKey }
                        .border(
                            width = 1.dp,
                            color = if (isSelected) CalcBluePrimary else CalcVaultBorder,
                            shape = RoundedCornerShape(50)
                        )
                ) {
                    Text(
                        text = chipLabel,
                        color = if (isSelected) CalcVaultBg else TextWhite,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3. Dynamic 3-Column Media Grid or Empty State
        if (filteredFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = CalcVaultCard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CalcVaultBorder, RoundedCornerShape(22.dp))
                        .testTag("photos_empty_state_card")
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (selectedFilter == "Trash") Icons.Outlined.DeleteOutline else Icons.Outlined.Image,
                            contentDescription = null,
                            tint = CalcBlueAccentText,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = if (selectedFilter == "Trash") {
                                "Trash is empty"
                            } else {
                                "No encrypted items in $selectedFilter"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (selectedFilter == "Trash") {
                                "Deleted items appear here before permanent removal."
                            } else {
                                "Import photos, videos, or documents into your vault using AES-256-GCM encryption."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                        if (selectedFilter != "Trash") {
                            Button(
                                onClick = onOpenAddSheet,
                                colors = ButtonDefaults.buttonColors(containerColor = CalcBluePrimary),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.testTag("empty_gallery_add_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = CalcVaultBg,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text(
                                    text = "Add to Vault",
                                    color = CalcVaultBg,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("photos_dynamic_grid"),
                contentPadding = PaddingValues(horizontal = 3.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                itemsIndexed(filteredFiles, key = { _, item -> item.id }) { idx, file ->
                    val isSelected = file.id in selectedIds
                    val globalActiveIndex = activeFiles.indexOfFirst { it.id == file.id }.coerceAtLeast(0)
                    MountainArtworkThumbnail(
                        styleIndex = idx % 6,
                        cornerRadius = 4.dp,
                        isSelected = isSelected,
                        showSelectionCircle = true,
                        onToggleSelect = {
                            if (isSelected) selectedIds.remove(file.id)
                            else selectedIds.add(file.id)
                        },
                        file = file,
                        loadThumbnail = loadThumbnail,
                        decryptFileBytes = decryptFileBytes,
                        onClick = {
                            if (selectedIds.isNotEmpty() || selectedFilter == "Trash") {
                                if (isSelected) selectedIds.remove(file.id)
                                else selectedIds.add(file.id)
                            } else {
                                onOpenPhotoViewer(globalActiveIndex)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .testTag("select_grid_item_$idx")
                    )
                }
            }
        }

        // 4. Bottom Action Bar: Export/Restore, Move, Details, Delete
        HorizontalDivider(color = CalcVaultDivider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CalcVaultBg)
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedFilter.equals("Trash", ignoreCase = true)) {
                BottomActionItem(
                    icon = Icons.Outlined.FileDownload,
                    label = "Restore",
                    tint = TextWhite,
                    onClick = {
                        if (selectedEntities.isEmpty()) {
                            onShowToast("Select at least 1 trashed item to restore")
                        } else {
                            onRestoreSelected(selectedEntities)
                            selectedIds.clear()
                        }
                    }
                )
            } else {
                BottomActionItem(
                    icon = Icons.Outlined.FileUpload,
                    label = "Export",
                    tint = TextWhite,
                    onClick = {
                        val target = selectedEntities.firstOrNull() ?: filteredFiles.firstOrNull()
                        if (target == null) {
                            onShowToast("No encrypted item available to export")
                        } else {
                            pendingExportFile = target
                            exportLauncher.launch(target.originalName)
                        }
                    }
                )
                BottomActionItem(
                    icon = Icons.Outlined.Folder,
                    label = "Move",
                    tint = TextWhite,
                    onClick = {
                        if (selectedEntities.isEmpty()) {
                            onShowToast("Select at least 1 item to move to an album")
                        } else {
                            showMoveAlbumDialog = true
                        }
                    }
                )
                BottomActionItem(
                    icon = Icons.Outlined.Info,
                    label = "Details",
                    tint = TextWhite,
                    onClick = {
                        val firstSelected = selectedEntities.firstOrNull() ?: filteredFiles.firstOrNull()
                        if (firstSelected != null) {
                            val activeIdx = activeFiles.indexOfFirst { it.id == firstSelected.id }.coerceAtLeast(0)
                            onOpenPhotoViewer(activeIdx)
                        } else {
                            onShowToast("No file available to inspect")
                        }
                    }
                )
            }
            BottomActionItem(
                icon = Icons.Outlined.DeleteOutline,
                label = if (selectedFilter.equals("Trash", ignoreCase = true)) "Wipe Forever" else "Trash",
                tint = DeleteCoralRed,
                onClick = {
                    if (selectedEntities.isEmpty()) {
                        onShowToast("Select at least 1 item to delete")
                    } else if (selectedFilter.equals("Trash", ignoreCase = true)) {
                        onPermanentlyDeleteSelected(selectedEntities)
                        selectedIds.clear()
                    } else {
                        onDeleteSelected(selectedEntities)
                        selectedIds.clear()
                    }
                }
            )
        }
    }

    if (showMoveAlbumDialog) {
        AlertDialog(
            onDismissRequest = { showMoveAlbumDialog = false },
            containerColor = CalcVaultCard,
            title = {
                Text(
                    text = "Move ${selectedEntities.size} item(s) to Album",
                    color = TextWhite
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    folders.forEach { folder ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = CalcVaultCardElevated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onMoveSelectedToAlbum(selectedEntities, folder.id, folder.name)
                                    selectedIds.clear()
                                    showMoveAlbumDialog = false
                                }
                                .border(1.dp, CalcVaultBorder, RoundedCornerShape(14.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Folder,
                                        contentDescription = null,
                                        tint = CalcBlueAccentText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = folder.name,
                                        color = TextWhite,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = "Move here",
                                    color = CalcBluePrimary,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMoveAlbumDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

/**
 * Full-Resolution Encrypted Photo Viewer (with Pinch-Zoom & Double-Tap Zoom) + Encrypted Video Player
 */
@Composable
fun PhotoViewerScreen(
    initialIndex: Int,
    files: List<VaultFileEntity>,
    folders: List<VaultFolderEntity> = emptyList(),
    loadThumbnail: (suspend (VaultFileEntity) -> ImageBitmap?)? = null,
    decryptFileBytes: suspend (VaultFileEntity) -> ByteArray,
    prepareTempVideoFile: (suspend (VaultFileEntity) -> File?)? = null,
    releaseTempVideoFile: ((File?) -> Unit)? = null,
    onBack: () -> Unit,
    onExportFile: (VaultFileEntity, Uri) -> Unit,
    onDeleteFile: (VaultFileEntity) -> Unit,
    onMoveFileToAlbum: (List<VaultFileEntity>, Long, String) -> Unit = { _, _, _ -> },
    onShowToast: (String) -> Unit
) {
    val activeFiles = remember(files) { files.filter { !it.isTrashed } }
    var currentIndex by remember(activeFiles.size, initialIndex) {
        mutableIntStateOf(
            if (activeFiles.isEmpty()) 0 else initialIndex.coerceIn(0, activeFiles.lastIndex)
        )
    }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }

    val currentFile = activeFiles.getOrNull(currentIndex)
    val folderName = remember(currentFile, folders) {
        folders.firstOrNull { it.id == currentFile?.folderId }?.name ?: "Vault"
    }

    val exportMimeType = currentFile?.mimeType ?: "application/octet-stream"
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(exportMimeType)
    ) { uri ->
        if (uri != null && currentFile != null) {
            onExportFile(currentFile, uri)
        }
    }

    // Pinch-to-zoom and pan state for full-resolution photos
    var zoomScale by remember(currentFile?.id) { mutableFloatStateOf(1f) }
    var panOffset by remember(currentFile?.id) { mutableStateOf(Offset.Zero) }
    var fullResBitmap by remember(currentFile?.id) { mutableStateOf<ImageBitmap?>(null) }
    var isLoadingMedia by remember(currentFile?.id) { mutableStateOf(false) }
    var tempVideoFile by remember(currentFile?.id) { mutableStateOf<File?>(null) }

    LaunchedEffect(currentFile?.id) {
        val file = currentFile ?: return@LaunchedEffect
        isLoadingMedia = true
        if (file.category == "PHOTO") {
            val decoded = withContext(Dispatchers.IO) {
                val bytes = decryptFileBytes(file)
                if (bytes.isNotEmpty()) {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    var sample = 1
                    while (opts.outWidth / sample > 2048 || opts.outHeight / sample > 2048) {
                        sample *= 2
                    }
                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)?.asImageBitmap()
                } else {
                    null
                }
            }
            fullResBitmap = decoded
        } else if (file.category == "VIDEO" && prepareTempVideoFile != null) {
            tempVideoFile = prepareTempVideoFile(file)
        }
        isLoadingMedia = false
    }

    DisposableEffect(currentFile?.id) {
        onDispose {
            val fileToWipe = tempVideoFile
            if (fileToWipe != null && releaseTempVideoFile != null) {
                releaseTempVideoFile(fileToWipe)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CalcVaultBg)
            .testTag("photo_viewer_screen")
    ) {
        // 1. Top Bar: '<', real "X / Y" counter, '⋮'
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("photo_viewer_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = TextWhite,
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = if (activeFiles.isEmpty()) "0 / 0" else "${currentIndex + 1} / ${activeFiles.size}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextWhite,
                modifier = Modifier.testTag("photo_viewer_counter_text")
            )

            IconButton(onClick = { showInfoDialog = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "File details",
                    tint = TextWhite
                )
            }
        }

        // 2. Main Media Viewport (Zoomable Photo, Video Player, or Empty State)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(0.dp))
                .background(Color(0xFF070B14)),
            contentAlignment = Alignment.Center
        ) {
            if (currentFile == null) {
                Text(
                    text = "No encrypted file selected",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyLarge
                )
            } else if (isLoadingMedia) {
                CircularProgressIndicator(color = CalcBluePrimary)
            } else if (currentFile.category == "VIDEO" && tempVideoFile != null) {
                val videoFile = tempVideoFile!!
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            val mediaController = MediaController(ctx)
                            mediaController.setAnchorView(this)
                            setMediaController(mediaController)
                            setVideoPath(videoFile.absolutePath)
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                start()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("vault_video_player")
                )
            } else if (fullResBitmap != null) {
                Image(
                    bitmap = fullResBitmap!!,
                    contentDescription = currentFile.originalName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(currentFile.id) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (zoomScale > 1.05f) {
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                    } else {
                                        zoomScale = 2.5f
                                    }
                                }
                            )
                        }
                        .pointerInput(currentFile.id) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (zoomScale * zoom).coerceIn(1f, 4f)
                                zoomScale = newScale
                                if (newScale > 1f) {
                                    panOffset += pan
                                } else {
                                    panOffset = Offset.Zero
                                }
                            }
                        }
                        .graphicsLayer(
                            scaleX = zoomScale,
                            scaleY = zoomScale,
                            translationX = panOffset.x,
                            translationY = panOffset.y
                        )
                        .testTag("zoomable_photo_image")
                )
            } else {
                MountainArtworkThumbnail(
                    styleIndex = currentIndex % 6,
                    cornerRadius = 0.dp,
                    file = currentFile,
                    loadThumbnail = loadThumbnail,
                    decryptFileBytes = decryptFileBytes,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 3. Real Metadata & Dynamic Filmstrip Scrubber
        if (currentFile != null) {
            val formattedDate = remember(currentFile.createdAt) {
                SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(currentFile.createdAt))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = currentFile.originalName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${formatFileSize(currentFile.originalSizeBytes)} · Added $formattedDate · $folderName",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = CalcBlueAccentText,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "AES-256",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CalcBlueAccentText,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Dynamic Filmstrip Scrubber of actual vault files
                if (activeFiles.size > 1) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(activeFiles, key = { _, item -> item.id }) { idx, item ->
                            val isCurrent = currentIndex == idx
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .then(
                                        if (isCurrent) {
                                            Modifier
                                                .border(2.dp, CalcBluePrimary, RoundedCornerShape(12.dp))
                                                .padding(2.dp)
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                MountainArtworkThumbnail(
                                    styleIndex = idx % 6,
                                    cornerRadius = 10.dp,
                                    file = item,
                                    loadThumbnail = loadThumbnail,
                                    onClick = { currentIndex = idx },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Bottom Action Bar: Export/Unhide, Move Album, Info, Delete
        HorizontalDivider(color = CalcVaultDivider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomActionItem(
                icon = Icons.Outlined.FileUpload,
                label = "Export",
                tint = TextWhite,
                onClick = {
                    if (currentFile != null) {
                        exportLauncher.launch(currentFile.originalName)
                    } else {
                        onShowToast("No file to export")
                    }
                }
            )
            BottomActionItem(
                icon = Icons.Outlined.Folder,
                label = "Album",
                tint = TextWhite,
                onClick = {
                    if (currentFile != null) {
                        showMoveDialog = true
                    }
                }
            )
            BottomActionItem(
                icon = Icons.Outlined.Info,
                label = "Info",
                tint = TextWhite,
                onClick = { showInfoDialog = true }
            )
            BottomActionItem(
                icon = Icons.Outlined.DeleteOutline,
                label = "Delete",
                tint = DeleteCoralRed,
                onClick = {
                    if (currentFile != null) {
                        onDeleteFile(currentFile)
                        if (activeFiles.size <= 1) {
                            onBack()
                        }
                    }
                }
            )
        }
    }

    if (showInfoDialog && currentFile != null) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = CalcVaultCard,
            title = { Text("Cryptographic File Info", color = TextWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Filename: ${currentFile.originalName}", color = TextWhite, fontWeight = FontWeight.SemiBold)
                    Text("Cipher: AES-256-GCM (Chunked CVLT2 + 128-bit auth tag)", color = CalcBlueAccentText)
                    Text("Original Size: ${formatFileSize(currentFile.originalSizeBytes)} (${currentFile.originalSizeBytes} bytes)", color = TextMuted)
                    Text("Encrypted Size: ${formatFileSize(currentFile.encryptedSizeBytes)}", color = TextMuted)
                    Text("MIME Type: ${currentFile.mimeType}", color = TextMuted)
                    Text("Album: $folderName", color = TextMuted)
                    Text("IV: ${currentFile.ivHex}", color = TextMuted)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showInfoDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = CalcBluePrimary)
                ) {
                    Text("Close", color = CalcVaultBg, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showMoveDialog && currentFile != null) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            containerColor = CalcVaultCard,
            title = { Text("Move to Album", color = TextWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    folders.forEach { folder ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CalcVaultCardElevated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onMoveFileToAlbum(listOf(currentFile), folder.id, folder.name)
                                    showMoveDialog = false
                                }
                                .border(1.dp, CalcVaultBorder, RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(folder.name, color = TextWhite, fontWeight = FontWeight.SemiBold)
                                if (folder.id == currentFile.folderId) {
                                    Text("Current", color = CalcBlueAccentText, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMoveDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

@Composable
private fun BottomActionItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
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
            style = MaterialTheme.typography.bodySmall,
            color = tint,
            fontSize = 12.sp
        )
    }
}

/**
 * "Add to vault" Modal Bottom Sheet — Shows encryption progress card ONLY when an active import job is running
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToVaultBottomSheet(
    deleteOriginalsAfterImport: Boolean,
    importProgress: ImportProgressState? = null,
    onToggleDeleteOriginals: (Boolean) -> Unit,
    onPickGallery: () -> Unit,
    onCaptureCamera: () -> Unit,
    onPickFiles: () -> Unit,
    onCreateNewNote: () -> Unit,
    onCancelImport: () -> Unit = {},
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111827),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("add_to_vault_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Add to vault",
                style = MaterialTheme.typography.headlineMedium,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )

            // 2x2 Action Tiles Grid: Gallery, Camera, Files, New note
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AddVaultActionCard(
                        icon = Icons.Outlined.Image,
                        title = "Gallery",
                        subtitle = "Photos & videos",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sheet_import_photos"),
                        onClick = onPickGallery
                    )
                    AddVaultActionCard(
                        icon = Icons.Outlined.CameraAlt,
                        title = "Camera",
                        subtitle = "Capture to vault",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sheet_create_snapshot"),
                        onClick = onCaptureCamera
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AddVaultActionCard(
                        icon = Icons.Outlined.Description,
                        title = "Files",
                        subtitle = "PDF, docs, any file",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sheet_import_docs"),
                        onClick = onPickFiles
                    )
                    AddVaultActionCard(
                        icon = Icons.Outlined.Folder,
                        title = "Albums",
                        subtitle = "Organize folders",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sheet_open_albums"),
                        onClick = onCreateNewNote
                    )
                }
            }

            // Toggle row: "Delete originals after import" / "Only once encryption finishes"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Delete originals after import",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextWhite
                    )
                    Text(
                        text = "Only once encryption finishes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }

                CalcVaultSwitch(
                    checked = deleteOriginalsAfterImport,
                    onCheckedChange = onToggleDeleteOriginals
                )
            }

            // Real Encryption Progress Card (shown ONLY during an active import job)
            if (importProgress != null) {
                val fraction = if (importProgress.total > 0) {
                    (importProgress.current.toFloat() / importProgress.total.toFloat()).coerceIn(0.05f, 1f)
                } else {
                    0.1f
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CalcVaultBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CalcVaultBorder, RoundedCornerShape(18.dp))
                        .testTag("active_encryption_progress_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Encrypting... ${importProgress.current} of ${importProgress.total}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = TextWhite
                                )
                                if (importProgress.currentLabel.isNotBlank()) {
                                    Text(
                                        text = importProgress.currentLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "AES-256",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted
                                )
                                Text(
                                    text = "Cancel",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = DeleteCoralRed,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable(onClick = onCancelImport)
                                        .testTag("cancel_import_button")
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(CalcTrackDark)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(CalcBluePrimary)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddVaultActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .border(1.dp, CalcVaultBorder, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = CalcVaultCardElevated
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = CalcBlueAccentText,
                modifier = Modifier.size(24.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        }
    }
}
