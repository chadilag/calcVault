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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.CurrencyBitcoin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.PasswordEntryEntity
import com.example.security.CryptoManager
import com.example.ui.theme.AmethystNote
import com.example.ui.theme.CalcAmber
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.GoldFolder
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.RubyDanger
import com.example.ui.theme.VaultCyan

@Composable
fun PasswordsTab(
    entries: List<PasswordEntryEntity>,
    cryptoManager: CryptoManager,
    decryptSecret: (PasswordEntryEntity) -> String,
    onSavePassword: (id: Long, title: String, account: String, secret: String, website: String, category: String, notes: String, isFavorite: Boolean) -> Unit,
    onDeletePassword: (Long) -> Unit,
    onCopiedToast: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var selectedCategory by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var showEditorDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<PasswordEntryEntity?>(null) }
    var showGeneratorModal by remember { mutableStateOf(false) }
    val revealedMap = remember { mutableStateMapOf<Long, Boolean>() }

    val filtered = remember(entries, selectedCategory, searchQuery) {
        entries.filter { item ->
            val catOk = selectedCategory == "ALL" || item.category == selectedCategory
            val qOk = searchQuery.isBlank() ||
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.accountOrUsername.contains(searchQuery, ignoreCase = true) ||
                item.websiteOrIssuer.contains(searchQuery, ignoreCase = true)
            catOk && qOk
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("passwords_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Password Generator Banner Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ObsidianSurfaceElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, EmeraldSafe.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CRYPTOGRAPHIC KEY GENERATOR",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSafe
                            )
                            Text(
                                text = "Create High-Entropy Passwords",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = "${entries.size} encrypted logins, cards & recovery keys",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Button(
                            onClick = { showGeneratorModal = true },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSafe),
                            modifier = Modifier.testTag("open_password_generator_button")
                        ) {
                            Icon(Icons.Filled.Key, contentDescription = null, tint = ObsidianBackground, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Generate", color = ObsidianBackground, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search logins, bank cards, Wi-Fi, seeds...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = EmeraldSafe) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                val cats = listOf(
                    "ALL" to "All (${entries.size})",
                    "LOGIN" to "Logins",
                    "CARD" to "Bank Cards",
                    "WIFI" to "Wi-Fi",
                    "CRYPTO" to "Crypto Seeds"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    cats.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedCategory == key,
                            onClick = { selectedCategory = key },
                            label = { Text(label) }
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = ObsidianSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.Password, contentDescription = null, tint = EmeraldSafe, modifier = Modifier.size(40.dp))
                            Text("No Saved Credentials", style = MaterialTheme.typography.titleLarge, color = Color.White)
                            Text(
                                text = "Store private account passwords, bank cards, PINs, and crypto seed phrases encrypted with AES-256.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF94A3B8),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filtered, key = { it.id }) { entry ->
                    val plainSecret = remember(entry.id, entry.updatedAt) { decryptSecret(entry) }
                    val isRevealed = revealedMap[entry.id] == true
                    val strength = remember(plainSecret) { cryptoManager.evaluatePasswordStrength(plainSecret) }

                    val catColor = when (entry.category) {
                        "CARD" -> GoldFolder
                        "WIFI" -> VaultCyan
                        "CRYPTO" -> CalcAmber
                        else -> EmeraldSafe
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                editingEntry = entry
                                showEditorDialog = true
                            }
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(18.dp))
                            .testTag("password_card_${entry.id}"),
                        shape = RoundedCornerShape(18.dp),
                        color = ObsidianSurface
                    ) {
                        Column(
                            modifier = Modifier.padding(15.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(catColor.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = when (entry.category) {
                                                "CARD" -> Icons.Filled.CreditCard
                                                "WIFI" -> Icons.Filled.Wifi
                                                "CRYPTO" -> Icons.Outlined.CurrencyBitcoin
                                                else -> Icons.Filled.Key
                                            },
                                            contentDescription = null,
                                            tint = catColor
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = entry.accountOrUsername,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF94A3B8),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                IconButton(onClick = { onDeletePassword(entry.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color(0xFF64748B))
                                }
                            }

                            // Masked / Revealed Secret Row
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ObsidianBackground,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (isRevealed) plainSecret else "•".repeat(plainSecret.length.coerceIn(8, 18)),
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = if (isRevealed) VaultCyan else Color(0xFF94A3B8),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row {
                                        IconButton(
                                            onClick = { revealedMap[entry.id] = !isRevealed },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isRevealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                contentDescription = "Toggle visibility",
                                                tint = Color(0xFF94A3B8),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                clipboard.setText(AnnotatedString(plainSecret))
                                                onCopiedToast("Copied '${entry.title}' secret to clipboard")
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ContentCopy,
                                                contentDescription = "Copy secret",
                                                tint = EmeraldSafe,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${entry.category} • ${strength.label} (${strength.entropyBits} bits)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = catColor
                                )
                                if (entry.websiteOrIssuer.isNotBlank()) {
                                    Text(
                                        text = entry.websiteOrIssuer,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = {
                editingEntry = null
                showEditorDialog = true
            },
            containerColor = EmeraldSafe,
            contentColor = ObsidianBackground,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Add Credential", fontWeight = FontWeight.Bold) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_password_fab")
        )
    }

    if (showEditorDialog) {
        val current = editingEntry
        val initialSecret = remember(current) { current?.let { decryptSecret(it) }.orEmpty() }
        PasswordEditorDialog(
            initialId = current?.id ?: 0L,
            initialTitle = current?.title.orEmpty(),
            initialAccount = current?.accountOrUsername.orEmpty(),
            initialSecret = initialSecret,
            initialWebsite = current?.websiteOrIssuer.orEmpty(),
            initialCategory = current?.category ?: "LOGIN",
            initialNotes = current?.notes.orEmpty(),
            cryptoManager = cryptoManager,
            onDismiss = {
                showEditorDialog = false
                editingEntry = null
            },
            onSave = { id, title, acc, sec, web, cat, notes ->
                onSavePassword(id, title, acc, sec, web, cat, notes, true)
                showEditorDialog = false
                editingEntry = null
            }
        )
    }

    if (showGeneratorModal) {
        PasswordGeneratorModal(
            cryptoManager = cryptoManager,
            onDismiss = { showGeneratorModal = false },
            onCopy = { generated ->
                clipboard.setText(AnnotatedString(generated))
                onCopiedToast("Generated password copied to clipboard")
            }
        )
    }
}

@Composable
private fun PasswordEditorDialog(
    initialId: Long,
    initialTitle: String,
    initialAccount: String,
    initialSecret: String,
    initialWebsite: String,
    initialCategory: String,
    initialNotes: String,
    cryptoManager: CryptoManager,
    onDismiss: () -> Unit,
    onSave: (Long, String, String, String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var account by remember { mutableStateOf(initialAccount) }
    var secret by remember { mutableStateOf(initialSecret) }
    var website by remember { mutableStateOf(initialWebsite) }
    var category by remember { mutableStateOf(initialCategory) }
    var notes by remember { mutableStateOf(initialNotes) }

    val strength = remember(secret) { cryptoManager.evaluatePasswordStrength(secret) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = { Text(if (initialId == 0L) "Add Encrypted Credential" else "Edit Credential", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("LOGIN", "CARD", "WIFI", "CRYPTO").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Service / Bank Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = account,
                    onValueChange = { account = it },
                    label = { Text("Username / Email / Card Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text("Password / PIN / Seed Phrase") },
                    trailingIcon = {
                        IconButton(
                            onClick = { secret = cryptoManager.generatePassword(16, true, true, true) }
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Generate random", tint = EmeraldSafe)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (secret.isNotEmpty()) {
                    LinearProgressIndicator(
                        progress = { strength.score / 100f },
                        color = if (strength.score >= 70) EmeraldSafe else CalcAmber,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Strength: ${strength.label} (${strength.entropyBits} bits entropy)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                }
                OutlinedTextField(
                    value = website,
                    onValueChange = { website = it },
                    label = { Text("Website URL / Issuer (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && secret.isNotBlank()) {
                        onSave(initialId, title, account, secret, website, category, notes)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSafe)
            ) {
                Text("Encrypt & Save", color = ObsidianBackground, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun PasswordGeneratorModal(
    cryptoManager: CryptoManager,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit
) {
    var length by remember { mutableFloatStateOf(18f) }
    var upper by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var generated by remember {
        mutableStateOf(cryptoManager.generatePassword(18, true, true, true))
    }

    val strength = remember(generated) { cryptoManager.evaluatePasswordStrength(generated) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = { Text("Cryptographic Key Generator", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianBackground,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = generated,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = VaultCyan,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                generated = cryptoManager.generatePassword(length.toInt(), upper, digits, symbols)
                            }
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Regenerate", tint = EmeraldSafe)
                        }
                    }
                }

                Text(
                    text = "Length: ${length.toInt()} chars • ${strength.label} (${strength.entropyBits} bits)",
                    style = MaterialTheme.typography.labelLarge,
                    color = EmeraldSafe
                )

                Slider(
                    value = length,
                    onValueChange = {
                        length = it
                        generated = cryptoManager.generatePassword(it.toInt(), upper, digits, symbols)
                    },
                    valueRange = 8f..32f
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = upper,
                        onCheckedChange = {
                            upper = it
                            generated = cryptoManager.generatePassword(length.toInt(), upper, digits, symbols)
                        }
                    )
                    Text("Uppercase (A-Z)", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(12.dp))
                    Checkbox(
                        checked = digits,
                        onCheckedChange = {
                            digits = it
                            generated = cryptoManager.generatePassword(length.toInt(), upper, digits, symbols)
                        }
                    )
                    Text("Digits (0-9)", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = symbols,
                        onCheckedChange = {
                            symbols = it
                            generated = cryptoManager.generatePassword(length.toInt(), upper, digits, symbols)
                        }
                    )
                    Text("Symbols (!@#\$%^&*)", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCopy(generated)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSafe)
            ) {
                Text("Copy Key", color = ObsidianBackground, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
