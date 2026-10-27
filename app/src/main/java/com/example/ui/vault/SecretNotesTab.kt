package com.example.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.SecretNoteEntity
import com.example.ui.theme.AmethystNote
import com.example.ui.theme.CalcAmber
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.RubyDanger
import com.example.ui.theme.VaultCyan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SecretNotesTab(
    notes: List<SecretNoteEntity>,
    decryptContent: (SecretNoteEntity) -> String,
    onSaveNote: (id: Long, title: String, content: String, category: String, colorHex: String, isPinned: Boolean) -> Unit,
    onDeleteNote: (Long) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var editingNote by remember { mutableStateOf<SecretNoteEntity?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    val categories = listOf("ALL", "Personal", "Finance", "Ideas", "Journal")

    val filteredNotes = remember(notes, selectedCategory, searchQuery) {
        notes.filter { note ->
            val plain = decryptContent(note)
            val catMatch = selectedCategory == "ALL" || note.category.equals(selectedCategory, ignoreCase = true)
            val queryMatch = searchQuery.isBlank() ||
                note.title.contains(searchQuery, ignoreCase = true) ||
                plain.contains(searchQuery, ignoreCase = true)
            catMatch && queryMatch
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("secret_notes_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search encrypted notes & diary...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = AmethystNote) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(if (cat == "ALL") "All Notes (${notes.size})" else cat) }
                        )
                    }
                }
            }

            if (filteredNotes.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        color = ObsidianSurface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.EditNote,
                                contentDescription = null,
                                tint = AmethystNote,
                                modifier = Modifier.size(42.dp)
                            )
                            Text(
                                text = "No Secret Notes Yet",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                            Text(
                                text = "Write private memos, recovery seeds, or diary entries. Every note is AES-256 encrypted on disk.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF94A3B8),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Button(
                                onClick = { isCreatingNew = true },
                                colors = ButtonDefaults.buttonColors(containerColor = AmethystNote)
                            ) {
                                Text("Write First Secret Note", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                items(filteredNotes, key = { it.id }) { note ->
                    val plainContent = remember(note.id, note.updatedAt) { decryptContent(note) }
                    val accent = parseHexColor(note.colorHex)
                    val dateStr = remember(note.updatedAt) {
                        SimpleDateFormat("MMM dd • HH:mm", Locale.US).format(Date(note.updatedAt))
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { editingNote = note }
                            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                            .testTag("secret_note_card_${note.id}"),
                        shape = RoundedCornerShape(18.dp),
                        color = ObsidianSurface
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(accent)
                                    )
                                    Text(
                                        text = note.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row {
                                    IconButton(
                                        onClick = {
                                            onSaveNote(
                                                note.id,
                                                note.title,
                                                plainContent,
                                                note.category,
                                                note.colorHex,
                                                !note.isPinned
                                            )
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                            contentDescription = "Pin note",
                                            tint = if (note.isPinned) CalcAmber else Color(0xFF64748B),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteNote(note.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Delete note",
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = plainContent,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFCBD5E1),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${note.category.uppercase()} • AES-256",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent
                                )
                                Text(
                                    text = dateStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { isCreatingNew = true },
            containerColor = AmethystNote,
            contentColor = Color.White,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("New Note", fontWeight = FontWeight.Bold) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_secret_note_fab")
        )
    }

    if (isCreatingNew || editingNote != null) {
        val current = editingNote
        val initialContent = remember(current) { current?.let { decryptContent(it) }.orEmpty() }
        SecretNoteEditorDialog(
            initialId = current?.id ?: 0L,
            initialTitle = current?.title.orEmpty(),
            initialContent = initialContent,
            initialCategory = current?.category ?: "Personal",
            initialColorHex = current?.colorHex ?: "#00E5FF",
            initialPinned = current?.isPinned ?: false,
            onDismiss = {
                isCreatingNew = false
                editingNote = null
            },
            onSave = { id, title, content, cat, hex, pinned ->
                onSaveNote(id, title, content, cat, hex, pinned)
                isCreatingNew = false
                editingNote = null
            }
        )
    }
}

@Composable
private fun SecretNoteEditorDialog(
    initialId: Long,
    initialTitle: String,
    initialContent: String,
    initialCategory: String,
    initialColorHex: String,
    initialPinned: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long, String, String, String, String, Boolean) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var content by remember { mutableStateOf(initialContent) }
    var category by remember { mutableStateOf(initialCategory) }
    var colorHex by remember { mutableStateOf(initialColorHex) }
    var isPinned by remember { mutableStateOf(initialPinned) }

    val categories = listOf("Personal", "Finance", "Ideas", "Journal")
    val colors = listOf("#00E5FF", "#FF6B2C", "#10B981", "#8B5CF6", "#F59E0B")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = VaultCyan, modifier = Modifier.size(20.dp))
                Text(if (initialId == 0L) "New Encrypted Note" else "Edit Encrypted Note", color = Color.White)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Note Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_title_input")
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Confidential Note Content") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_content_input")
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        colors.forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(hex))
                                    .border(
                                        width = if (colorHex == hex) 2.5.dp else 0.dp,
                                        color = Color.White,
                                        shape = CircleShape
                                    )
                                    .clickable { colorHex = hex }
                            )
                        }
                    }
                    FilterChip(
                        selected = isPinned,
                        onClick = { isPinned = !isPinned },
                        label = { Text("Pin") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(initialId, title, content, category, colorHex, isPinned)
                },
                colors = ButtonDefaults.buttonColors(containerColor = VaultCyan),
                modifier = Modifier.testTag("save_note_button")
            ) {
                Text("Encrypt & Save", color = ObsidianBackground, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
