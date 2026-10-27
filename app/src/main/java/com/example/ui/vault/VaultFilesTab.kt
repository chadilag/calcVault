package com.example.ui.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VaultFileEntity
import com.example.data.VaultFolderEntity
import com.example.security.VaultSecurityState
import com.example.ui.theme.CalcBlueAccentText
import com.example.ui.theme.CalcBluePrimary
import com.example.ui.theme.CalcBlueSegmentLight
import com.example.ui.theme.CalcSlateSegment
import com.example.ui.theme.CalcTrackDark
import com.example.ui.theme.CalcVaultBg
import com.example.ui.theme.CalcVaultBorder
import com.example.ui.theme.CalcVaultCard
import com.example.ui.theme.DeleteCoralRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

/**
 * "Vault home" (My Vault) — 100% dynamic data from Room DB, zero hardcoded fake counts
 */
@Composable
fun MyVaultHomeScreen(
    files: List<VaultFileEntity>,
    folders: List<VaultFolderEntity>,
    notesCount: Int = 0,
    bookmarksCount: Int = 0,
    securityState: VaultSecurityState,
    loadThumbnail: (suspend (VaultFileEntity) -> ImageBitmap?)? = null,
    decryptFileBytes: suspend (VaultFileEntity) -> ByteArray,
    onOpenPhotosSelectMode: (String) -> Unit,
    onOpenPhotoViewer: (Int) -> Unit,
    onOpenNotes: () -> Unit = {},
    onOpenBrowser: () -> Unit = {},
    onOpenAlbums: () -> Unit,
    onOpenSecurity: () -> Unit = {},
    onOpenPanicAndDecoy: () -> Unit,
    onOpenAddSheet: () -> Unit = {}
) {
    val activeFiles = remember(files) { files.filter { !it.isTrashed } }
    val trashedFiles = remember(files) { files.filter { it.isTrashed } }

    val photoCount = remember(activeFiles) { activeFiles.count { it.category == "PHOTO" } }
    val videoCount = remember(activeFiles) { activeFiles.count { it.category == "VIDEO" } }
    val docCount = remember(activeFiles) { activeFiles.count { it.category == "DOCUMENT" || it.category == "AUDIO" } }
    val totalItems = activeFiles.size
    val totalBytesUsed = remember(activeFiles) { activeFiles.sumOf { it.encryptedSizeBytes } }

    val photoBytes = remember(activeFiles) { activeFiles.filter { it.category == "PHOTO" }.sumOf { it.encryptedSizeBytes } }
    val videoBytes = remember(activeFiles) { activeFiles.filter { it.category == "VIDEO" }.sumOf { it.encryptedSizeBytes } }
    val docBytes = remember(activeFiles) {
        activeFiles.filter { it.category == "DOCUMENT" || it.category == "AUDIO" }.sumOf { it.encryptedSizeBytes }
    }

    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val searchResults = remember(activeFiles, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) emptyList()
        else activeFiles.filter {
            it.originalName.contains(q, ignoreCase = true) ||
                it.category.contains(q, ignoreCase = true) ||
                it.notes.contains(q, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CalcVaultBg)
            .testTag("vault_files_list"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header: "My Vault" + Search button, and "AES-256 encrypted · locks on exit"
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "My Vault",
                            style = MaterialTheme.typography.headlineLarge,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "AES-256 encrypted · locks on exit",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                        }
                    }

                    CircularHeaderButton(
                        icon = if (isSearchExpanded) Icons.Filled.Close else Icons.Outlined.Search,
                        contentDescription = "Search vault",
                        onClick = {
                            isSearchExpanded = !isSearchExpanded
                            if (!isSearchExpanded) searchQuery = ""
                        },
                        modifier = Modifier.testTag("vault_search_button")
                    )
                }

                AnimatedVisibility(visible = isSearchExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search encrypted photos, videos, files...", color = TextMuted) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Outlined.Search, contentDescription = null, tint = CalcBlueAccentText)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = TextMuted)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vault_search_input")
                        )

                        if (searchQuery.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = CalcVaultCard,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, CalcVaultBorder, RoundedCornerShape(16.dp))
                            ) {
                                if (searchResults.isEmpty()) {
                                    Text(
                                        text = "No matching encrypted files for \"$searchQuery\"",
                                        color = TextMuted,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                } else {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "${searchResults.size} result(s)",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = CalcBlueAccentText
                                        )
                                        searchResults.take(5).forEach { resultFile ->
                                            val globalIdx = activeFiles.indexOfFirst { it.id == resultFile.id }.coerceAtLeast(0)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable { onOpenPhotoViewer(globalIdx) }
                                                    .padding(8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = resultFile.originalName,
                                                        color = TextWhite,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "${resultFile.category} · ${formatFileSize(resultFile.originalSizeBytes)}",
                                                        color = TextMuted,
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = null,
                                                    tint = TextMuted
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Real Storage Summary Card (computed dynamically from Room DB)
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CalcVaultBorder, RoundedCornerShape(22.dp))
                    .clickable { onOpenPhotosSelectMode("All") }
                    .testTag("vault_storage_summary_card"),
                shape = RoundedCornerShape(22.dp),
                color = CalcVaultCard
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "$totalItems ${if (totalItems == 1) "item" else "items"}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            modifier = Modifier.testTag("vault_total_items_text")
                        )
                        Text(
                            text = "${formatFileSize(totalBytesUsed)} used",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            modifier = Modifier.testTag("vault_total_size_text")
                        )
                    }

                    // Dynamic Segmented Progress Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (totalItems == 0) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CalcTrackDark)
                            )
                        } else {
                            val totalWeight = (totalBytesUsed.coerceAtLeast(1L)).toFloat()
                            val pWeight = (photoBytes / totalWeight).coerceIn(0.08f, 0.75f)
                            val vWeight = if (videoCount > 0) (videoBytes / totalWeight).coerceIn(0.08f, 0.65f) else 0f
                            val dWeight = if (docCount > 0) (docBytes / totalWeight).coerceIn(0.08f, 0.55f) else 0f
                            val freeWeight = (1f - (pWeight + vWeight + dWeight)).coerceAtLeast(0.12f)

                            if (photoCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight(pWeight)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(CalcBluePrimary)
                                )
                            }
                            if (videoCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight(vWeight)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(CalcBlueSegmentLight)
                                )
                            }
                            if (docCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight(dWeight)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(CalcSlateSegment)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(freeWeight)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CalcTrackDark)
                            )
                        }
                    }

                    Text(
                        text = "Photos $photoCount · Videos $videoCount · Files $docCount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        modifier = Modifier.testTag("vault_breakdown_text")
                    )
                }
            }
        }

        // 3. 3x2 Category Grid (Photos, Videos, Files / Notes, Browser, Albums) — 100% real counts
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VaultCategoryTile(
                        icon = Icons.Outlined.Image,
                        title = "Photos",
                        subtitle = "$photoCount",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("category_tile_photos"),
                        onClick = { onOpenPhotosSelectMode("Photos") }
                    )
                    VaultCategoryTile(
                        icon = Icons.Outlined.Videocam,
                        title = "Videos",
                        subtitle = "$videoCount",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("category_tile_videos"),
                        onClick = { onOpenPhotosSelectMode("Videos") }
                    )
                    VaultCategoryTile(
                        icon = Icons.Outlined.Description,
                        title = "Files",
                        subtitle = "$docCount",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("category_tile_files"),
                        onClick = { onOpenPhotosSelectMode("Files") }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VaultCategoryTile(
                        icon = Icons.Outlined.Folder,
                        title = "Albums",
                        subtitle = "${folders.size}",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("category_tile_albums"),
                        onClick = onOpenAlbums
                    )
                    VaultCategoryTile(
                        icon = Icons.Outlined.DeleteOutline,
                        title = "Trash",
                        subtitle = "${trashedFiles.size}",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("category_tile_trash"),
                        onClick = { onOpenPhotosSelectMode("Trash") }
                    )
                    VaultCategoryTile(
                        icon = Icons.Outlined.Shield,
                        title = "Security",
                        subtitle = "AES-256",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("category_tile_security"),
                        onClick = onOpenSecurity
                    )
                }
            }
        }

        // 4. "Vault protected" Card ("Decoy vault on · Break-in alerts on") + Optional Trash Banner
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onOpenPanicAndDecoy)
                        .border(1.dp, CalcVaultBorder, RoundedCornerShape(20.dp))
                        .testTag("vault_protected_card"),
                    shape = RoundedCornerShape(20.dp),
                    color = CalcVaultCard
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF17253E)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = null,
                                tint = CalcBlueAccentText,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Vault protected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            val decoyStatus = if (securityState.decoyVaultEnabled) "Decoy vault on" else "Decoy vault off"
                            val breakInStatus = if (securityState.breakInAlertsEnabled) "Break-in alerts on" else "Break-in alerts off"
                            Text(
                                text = "$decoyStatus · $breakInStatus",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = TextMuted
                        )
                    }
                }

                if (trashedFiles.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onOpenPhotosSelectMode("Trash") }
                            .border(1.dp, CalcVaultBorder, RoundedCornerShape(16.dp))
                            .testTag("vault_trash_banner"),
                        shape = RoundedCornerShape(16.dp),
                        color = CalcVaultCard
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = null,
                                    tint = DeleteCoralRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Trash (${trashedFiles.size} ${if (trashedFiles.size == 1) "item" else "items"})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextWhite
                                )
                            }
                            Text(
                                text = "Manage",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = CalcBluePrimary
                            )
                        }
                    }
                }
            }
        }

        // 5. "Recently added" Section — Real user files or clean Empty State CTA
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recently added",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Text(
                        text = "See all",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = CalcBluePrimary,
                        modifier = Modifier
                            .clickable { onOpenPhotosSelectMode("All") }
                            .testTag("recently_added_see_all")
                    )
                }

                if (activeFiles.isEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CalcVaultBorder, RoundedCornerShape(20.dp))
                            .clickable(onClick = onOpenAddSheet)
                            .testTag("empty_vault_cta_card"),
                        shape = RoundedCornerShape(20.dp),
                        color = CalcVaultCard
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF17253E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Add files",
                                    tint = CalcBlueAccentText,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                text = "Your encrypted vault is empty",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Tap + to import photos, videos, or documents with AES-256-GCM encryption.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    val recentFiles = activeFiles.take(4)
                    val recentStyleIndices = listOf(0, 3, 4, 2)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        recentFiles.forEachIndexed { idx, matchingFile ->
                            val paletteIdx = recentStyleIndices[idx % recentStyleIndices.size]
                            MountainArtworkThumbnail(
                                styleIndex = paletteIdx,
                                cornerRadius = 16.dp,
                                file = matchingFile,
                                loadThumbnail = loadThumbnail,
                                decryptFileBytes = decryptFileBytes,
                                onClick = { onOpenPhotoViewer(idx) },
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .testTag("recent_photo_thumb_$idx")
                            )
                        }
                        repeat((4 - recentFiles.size).coerceAtLeast(0)) {
                            Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultCategoryTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .border(1.dp, CalcVaultBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = CalcVaultCard
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF17253E)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = CalcBlueAccentText,
                    modifier = Modifier.size(20.dp)
                )
            }

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

/**
 * "Albums" 2-Column Grid Screen — Real dynamic counts & CRUD (Create, Rename, Safe Delete)
 */
@Composable
fun AlbumsGridScreen(
    folders: List<VaultFolderEntity>,
    files: List<VaultFileEntity> = emptyList(),
    loadThumbnail: (suspend (VaultFileEntity) -> ImageBitmap?)? = null,
    onCreateAlbum: (name: String, category: String, accentHex: String) -> Unit,
    onRenameAlbum: (VaultFolderEntity, String) -> Unit = { _, _ -> },
    onDeleteAlbum: (VaultFolderEntity) -> Unit = {},
    onSelectAlbum: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<VaultFolderEntity?>(null) }
    var folderToDelete by remember { mutableStateOf<VaultFolderEntity?>(null) }

    val activeFiles = remember(files) { files.filter { !it.isTrashed } }

    data class AlbumDisplaySpec(
        val folder: VaultFolderEntity,
        val name: String,
        val subtitle: String,
        val paletteIndex: Int,
        val coverFile: VaultFileEntity?,
        val isLocked: Boolean = false
    )

    val paletteForName = mapOf(
        "Family" to 1,
        "Documents" to 4,
        "Travel" to 0,
        "Screenshots" to 2,
        "Receipts" to 5,
        "Private" to 3
    )

    val displayAlbums = remember(folders, activeFiles) {
        folders.mapIndexed { index, folder ->
            val albumFiles = activeFiles.filter { it.folderId == folder.id }
            val count = albumFiles.size
            val isPrivate = folder.name.equals("Private", ignoreCase = true)
            val countLabel = "$count ${if (count == 1) "item" else "items"}" + if (isPrivate) " · locked" else ""
            val paletteIdx = paletteForName[folder.name] ?: ((index + 1) % 6)
            AlbumDisplaySpec(
                folder = folder,
                name = folder.name,
                subtitle = countLabel,
                paletteIndex = paletteIdx,
                coverFile = albumFiles.firstOrNull(),
                isLocked = isPrivate
            )
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .background(CalcVaultBg)
            .testTag("albums_grid_screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 104.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header Row spanning 2 columns: "Albums" + "+ New" button
        item(span = { GridItemSpan(2) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Albums",
                    style = MaterialTheme.typography.headlineLarge,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )

                Button(
                    onClick = { showCreateDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CalcBluePrimary),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("new_album_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = CalcVaultBg,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "New",
                        color = CalcVaultBg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        itemsIndexed(displayAlbums, key = { _, item -> item.folder.id }) { _, album ->
            var showAlbumMenu by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectAlbum(album.name) }
                    .testTag("album_card_${album.name.lowercase()}"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box {
                    MountainArtworkThumbnail(
                        styleIndex = album.paletteIndex,
                        cornerRadius = 20.dp,
                        isLockedBadge = album.isLocked,
                        file = album.coverFile,
                        loadThumbnail = loadThumbnail,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.22f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = album.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = album.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showAlbumMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Album options",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showAlbumMenu,
                            onDismissRequest = { showAlbumMenu = false },
                            modifier = Modifier.background(CalcVaultCard)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename Album", color = TextWhite) },
                                onClick = {
                                    showAlbumMenu = false
                                    folderToRename = album.folder
                                }
                            )
                            if (folders.size > 1) {
                                DropdownMenuItem(
                                    text = { Text("Delete Album", color = DeleteCoralRed) },
                                    onClick = {
                                        showAlbumMenu = false
                                        folderToDelete = album.folder
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = CalcVaultCard,
            title = { Text("New Album", color = TextWhite) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Album Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onCreateAlbum(newName.trim(), "PHOTO", "#4D8DFF")
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CalcBluePrimary)
                ) {
                    Text("Create", color = CalcVaultBg, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    if (folderToRename != null) {
        val target = folderToRename!!
        var updatedName by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { folderToRename = null },
            containerColor = CalcVaultCard,
            title = { Text("Rename Album", color = TextWhite) },
            text = {
                OutlinedTextField(
                    value = updatedName,
                    onValueChange = { updatedName = it },
                    label = { Text("Album Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (updatedName.isNotBlank()) {
                            onRenameAlbum(target, updatedName.trim())
                            folderToRename = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CalcBluePrimary)
                ) {
                    Text("Save", color = CalcVaultBg, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToRename = null }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    if (folderToDelete != null) {
        val target = folderToDelete!!
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            containerColor = CalcVaultCard,
            title = { Text("Delete \"${target.name}\"?", color = TextWhite) },
            text = {
                Text(
                    text = "Any files inside this album will be safely moved to your default album so no encrypted files are lost.",
                    color = TextMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAlbum(target)
                        folderToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeleteCoralRed)
                ) {
                    Text("Delete Album", color = TextWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(java.util.Locale.US, "%.2f GB", gb)
}

fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        CalcBluePrimary
    }
}
