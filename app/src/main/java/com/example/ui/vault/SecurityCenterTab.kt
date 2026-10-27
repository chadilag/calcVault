package com.example.ui.vault

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.IntruderLogEntity
import com.example.security.VaultSecurityState
import com.example.ui.theme.CalcBlueAccentText
import com.example.ui.theme.CalcBluePrimary
import com.example.ui.theme.CalcVaultBg
import com.example.ui.theme.CalcVaultBorder
import com.example.ui.theme.CalcVaultCard
import com.example.ui.theme.CalcVaultCardElevated
import com.example.ui.theme.CalcVaultDivider
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSubtle
import com.example.ui.theme.TextWhite

/**
 * Screenshot 7: "Security" Screen
 */
@Composable
fun SecurityCenterTab(
    securityState: VaultSecurityState,
    intruderLogs: List<IntruderLogEntity>,
    totalItemsCount: Int = 0,
    totalBytesUsed: Long = 0L,
    onChangeMasterPin: (String) -> Unit,
    onUpdateSecurityRecovery: (String, String) -> Unit = { _, _ -> },
    onToggleDecoyVault: (Boolean) -> Unit,
    onToggleBiometricUnlock: (Boolean) -> Unit,
    onOpenPanicAndDecoy: () -> Unit,
    onToggleBreakInAlerts: (Boolean) -> Unit,
    onChangeAutoLockTiming: (String) -> Unit,
    onOpenAppDisguise: () -> Unit,
    onToggleHideFromRecents: (Boolean) -> Unit,
    onClearIntruderLogs: () -> Unit,
    onExportPortableBackup: (Uri, String) -> Unit = { _, _ -> },
    onRestorePortableBackup: (Uri, String) -> Unit = { _, _ -> },
    onShowToast: (String) -> Unit
) {
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var showAutoLockDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val pw = backupPassword
        if (uri != null && pw.length >= 6) {
            onExportPortableBackup(uri, pw)
            showBackupDialog = false
            backupPassword = ""
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val pw = backupPassword
        if (uri != null && pw.length >= 6) {
            onRestorePortableBackup(uri, pw)
            showBackupDialog = false
            backupPassword = ""
        }
    }

    val panicModeShortLabel = when (securityState.panicTriggerMode) {
        "SHAKE" -> "Shake phone"
        "FLOATING" -> "Floating button"
        else -> "Flip phone"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CalcVaultBg)
            .testTag("security_center_list"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Title: "Security"
        item {
            Text(
                text = "Security",
                style = MaterialTheme.typography.headlineLarge,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )
        }

        // Section 1: Access
        item {
            SecuritySectionGroup(title = "Access") {
                SecurityChevronRow(
                    title = "Secret code",
                    subtitle = "Enter 6–8 digits on calculator + =",
                    valueText = "••••••",
                    onClick = { showChangePinDialog = true },
                    modifier = Modifier.testTag("security_secret_code_row")
                )
                HorizontalDivider(color = CalcVaultDivider)
                SecurityChevronRow(
                    title = "Recovery phrase",
                    subtitle = "Reset secret code if forgotten",
                    valueText = if (securityState.hasSecurityAnswer) "Configured" else "Not set",
                    onClick = { showRecoveryDialog = true },
                    modifier = Modifier.testTag("security_recovery_row")
                )
                HorizontalDivider(color = CalcVaultDivider)
                SecuritySwitchRow(
                    title = "Decoy vault",
                    subtitle = "Second code opens a fake vault",
                    checked = securityState.decoyVaultEnabled,
                    onCheckedChange = onToggleDecoyVault,
                    modifier = Modifier.testTag("security_decoy_vault_switch")
                )
                HorizontalDivider(color = CalcVaultDivider)
                SecuritySwitchRow(
                    title = "Biometric unlock",
                    subtitle = "Fingerprint after secret code",
                    checked = securityState.biometricUnlockEnabled,
                    onCheckedChange = onToggleBiometricUnlock,
                    modifier = Modifier.testTag("security_biometric_switch")
                )
            }
        }

        // Section 2: Protection
        item {
            SecuritySectionGroup(title = "Protection") {
                SecurityChevronRow(
                    title = "Panic mode",
                    subtitle = "Instant lock, back to calculator",
                    valueText = panicModeShortLabel,
                    onClick = onOpenPanicAndDecoy,
                    modifier = Modifier.testTag("security_panic_mode_row")
                )
                HorizontalDivider(color = CalcVaultDivider)
                SecuritySwitchRow(
                    title = "Break-in alerts",
                    subtitle = "Photo after 3 wrong codes",
                    checked = securityState.breakInAlertsEnabled,
                    onCheckedChange = onToggleBreakInAlerts,
                    modifier = Modifier.testTag("security_break_in_switch")
                )
                HorizontalDivider(color = CalcVaultDivider)
                SecurityChevronRow(
                    title = "Auto-lock",
                    subtitle = "When leaving the app",
                    valueText = securityState.autoLockTiming,
                    onClick = { showAutoLockDialog = true },
                    modifier = Modifier.testTag("security_auto_lock_row")
                )
            }
        }

        // Section 3: Disguise & backup
        item {
            SecuritySectionGroup(title = "Disguise & backup") {
                SecurityChevronRow(
                    title = "App name & icon",
                    subtitle = null,
                    valueText = securityState.disguiseAppName,
                    onClick = onOpenAppDisguise,
                    modifier = Modifier.testTag("security_app_disguise_row")
                )
                HorizontalDivider(color = CalcVaultDivider)
                SecuritySwitchRow(
                    title = "Hide from recent apps",
                    subtitle = null,
                    checked = securityState.hideFromRecentApps,
                    onCheckedChange = onToggleHideFromRecents,
                    modifier = Modifier.testTag("security_hide_recents_switch")
                )
                HorizontalDivider(color = CalcVaultDivider)
                SecurityChevronRow(
                    title = "Encrypted backup (.cvbackup)",
                    subtitle = "Export or restore password-encrypted archive",
                    valueText = "",
                    onClick = { showBackupDialog = true },
                    modifier = Modifier.testTag("security_encrypted_backup_row")
                )
            }
        }
    }

    if (showChangePinDialog) {
        var newPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showChangePinDialog = false },
            containerColor = CalcVaultCard,
            title = { Text("Change Secret Code", color = TextWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a 6 to 8 digit secret code. Unlock your vault by typing it on the calculator and pressing '='.",
                        color = TextMuted
                    )
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 8) newPin = it },
                        label = { Text("Secret Code (6–8 digits)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("change_pin_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPin.length in 6..8) {
                            onChangeMasterPin(newPin)
                            showChangePinDialog = false
                        } else {
                            onShowToast("Secret code must be 6 to 8 digits")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CalcBluePrimary)
                ) {
                    Text("Save Code", color = CalcVaultBg, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePinDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    if (showRecoveryDialog) {
        var question by remember { mutableStateOf(securityState.securityQuestion) }
        var answer by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRecoveryDialog = false },
            containerColor = CalcVaultCard,
            title = { Text("Configure PIN Recovery", color = TextWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Set a security question and recovery answer (stored using PBKDF2-HMAC-SHA256). Without this or a portable .cvbackup, a forgotten PIN cannot be bypassed.",
                        color = TextMuted
                    )
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        label = { Text("Security Question") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = answer,
                        onValueChange = { answer = it },
                        label = { Text("Recovery Answer") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (question.isNotBlank() && answer.trim().length >= 3) {
                            onUpdateSecurityRecovery(question.trim(), answer.trim())
                            showRecoveryDialog = false
                        } else {
                            onShowToast("Enter a question and at least 3 characters for the answer")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CalcBluePrimary)
                ) {
                    Text("Save Recovery", color = CalcVaultBg, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecoveryDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    if (showAutoLockDialog) {
        val options = listOf("Instantly", "After 30s", "After 1m", "After 5m")
        AlertDialog(
            onDismissRequest = { showAutoLockDialog = false },
            containerColor = CalcVaultCard,
            title = { Text("Auto-lock timing", color = TextWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onChangeAutoLockTiming(option)
                                    showAutoLockDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(option, color = TextWhite, fontWeight = FontWeight.Medium)
                            CalcVaultRadioIndicator(selected = securityState.autoLockTiming == option)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAutoLockDialog = false }) {
                    Text("Done", color = CalcBlueAccentText)
                }
            }
        )
    }

    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            containerColor = CalcVaultCard,
            title = { Text("Portable Encrypted Backup (.cvbackup)", color = TextWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Your vault ($totalItemsCount ${if (totalItemsCount == 1) "item" else "items"} · ${formatFileSize(totalBytesUsed)}) is encrypted with an Android Keystore hardware-backed key. Uninstalling the app or losing your device permanently destroys the Keystore key. Export a password-encrypted .cvbackup file to protect your files.",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it },
                        label = { Text("Backup Password (min 6 chars)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("portable_backup_password_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (backupPassword.length < 6) {
                                    onShowToast("Enter a backup password of at least 6 characters")
                                } else {
                                    exportBackupLauncher.launch("calcvault_backup.cvbackup")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CalcBluePrimary),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_cvbackup_button")
                        ) {
                            Text("Export", color = CalcVaultBg, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                if (backupPassword.length < 6) {
                                    onShowToast("Enter the backup password (min 6 chars) first")
                                } else {
                                    restoreBackupLauncher.launch(arrayOf("*/*"))
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CalcVaultCardElevated),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("restore_cvbackup_button")
                        ) {
                            Text("Restore", color = TextWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (intruderLogs.isNotEmpty()) {
                        Text(
                            text = "Break-in alerts (${intruderLogs.size}): ${intruderLogs.first().description}",
                            color = CalcBlueAccentText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                if (intruderLogs.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            onClearIntruderLogs()
                            showBackupDialog = false
                        }
                    ) {
                        Text("Clear Alerts", color = CalcBlueAccentText)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupDialog = false }) {
                    Text("Close", color = TextMuted)
                }
            }
        )
    }
}

@Composable
private fun SecuritySectionGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = TextMuted,
            fontWeight = FontWeight.Medium
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CalcVaultCard,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CalcVaultBorder, RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun SecurityChevronRow(
    title: String,
    subtitle: String?,
    valueText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextWhite
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (valueText.isNotEmpty()) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SecuritySwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextWhite
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        }

        CalcVaultSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * Screenshot 8: "Panic & Decoy" Sub-Screen
 */
@Composable
fun PanicAndDecoyScreen(
    securityState: VaultSecurityState,
    onBack: () -> Unit,
    onSelectPanicMode: (String) -> Unit,
    onUpdateDecoyPin: (String?) -> Unit,
    onOpenDecoyVaultPreview: () -> Unit
) {
    var showEditDecoyDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CalcVaultBg)
            .testTag("panic_and_decoy_screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Header: Circular '<' button + "Panic & Decoy"
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularHeaderButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    onClick = onBack,
                    modifier = Modifier.testTag("panic_decoy_back_button")
                )
                Text(
                    text = "Panic & Decoy",
                    style = MaterialTheme.typography.headlineMedium,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
            }
        }

        // 2. "Panic trigger" Radio Group Card
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Panic trigger",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium
                )

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CalcVaultCard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CalcVaultBorder, RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        PanicRadioRow(
                            title = "Flip phone face-down",
                            subtitle = "Locks instantly",
                            selected = securityState.panicTriggerMode == "FLIP",
                            onClick = { onSelectPanicMode("FLIP") },
                            modifier = Modifier.testTag("panic_radio_flip")
                        )
                        HorizontalDivider(color = CalcVaultDivider)
                        PanicRadioRow(
                            title = "Shake phone twice",
                            subtitle = "Locks and clears recents",
                            selected = securityState.panicTriggerMode == "SHAKE",
                            onClick = { onSelectPanicMode("SHAKE") },
                            modifier = Modifier.testTag("panic_radio_shake")
                        )
                        HorizontalDivider(color = CalcVaultDivider)
                        PanicRadioRow(
                            title = "Floating panic button",
                            subtitle = "Tap anywhere to exit",
                            selected = securityState.panicTriggerMode == "FLOATING",
                            onClick = { onSelectPanicMode("FLOATING") },
                            modifier = Modifier.testTag("panic_radio_floating")
                        )
                    }
                }
            }
        }

        // 3. "Decoy vault" Card with 4 Mountain Thumbnails & "Edit decoy content" button
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Decoy vault",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium
                )

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CalcVaultCard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CalcVaultBorder, RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 4 Mountain Thumbnails Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val decoyPalettes = listOf(0, 2, 1, 3)
                            decoyPalettes.forEach { paletteIdx ->
                                MountainArtworkThumbnail(
                                    styleIndex = paletteIdx,
                                    cornerRadius = 12.dp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Opens with your second code",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Text(
                                text = "Shows harmless photos so no one suspects a second vault.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                        }

                        // "Edit decoy content" full-width outlined dark button
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showEditDecoyDialog = true }
                                .border(1.dp, Color(0xFF26354F), RoundedCornerShape(14.dp))
                                .testTag("edit_decoy_content_button"),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF151F33)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 13.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Edit decoy content",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextWhite
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDecoyDialog) {
        var decoyPin by remember { mutableStateOf("000000") }
        AlertDialog(
            onDismissRequest = { showEditDecoyDialog = false },
            containerColor = CalcVaultCard,
            title = { Text("Edit Decoy Vault", color = TextWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Set your second 6 to 8 digit calculator code (e.g. 000000) or switch into your Decoy Vault to add harmless photos.",
                        color = TextMuted
                    )
                    OutlinedTextField(
                        value = decoyPin,
                        onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 8) decoyPin = it },
                        label = { Text("Second Code (6–8 digits)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (decoyPin.length in 6..8) {
                            onUpdateDecoyPin(decoyPin)
                            showEditDecoyDialog = false
                            onOpenDecoyVaultPreview()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CalcBluePrimary)
                ) {
                    Text("Open Decoy Vault", color = CalcVaultBg, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDecoyDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

@Composable
private fun PanicRadioRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextWhite
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }

        CalcVaultRadioIndicator(selected = selected)
    }
}

@Composable
private fun CalcVaultRadioIndicator(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .border(
                width = 2.dp,
                color = if (selected) CalcBluePrimary else Color(0xFF475569),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(CalcBluePrimary)
            )
        }
    }
}

/**
 * Screenshot 9: "App disguise" Sub-Screen
 */
@Composable
fun AppDisguiseScreen(
    securityState: VaultSecurityState,
    onBack: () -> Unit,
    onSelectDisguise: (iconMode: String, appName: String) -> Unit,
    onToggleHideFromRecents: (Boolean) -> Unit,
    onToggleBlockScreenshots: (Boolean) -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CalcVaultBg)
            .testTag("app_disguise_screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Header: Circular '<' button + "App disguise"
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularHeaderButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    onClick = onBack,
                    modifier = Modifier.testTag("app_disguise_back_button")
                )
                Text(
                    text = "App disguise",
                    style = MaterialTheme.typography.headlineMedium,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
            }
        }

        // 2. Section label: "Icon & name on your home screen"
        item {
            Text(
                text = "Icon & name on your home screen",
                style = MaterialTheme.typography.titleSmall,
                color = TextMuted,
                fontWeight = FontWeight.Medium
            )
        }

        // 3. 2x2 Grid of Disguise Cards: Calculator, Notes, Clock, Weather
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DisguiseOptionCard(
                        label = "Calculator",
                        icon = Icons.Outlined.Calculate,
                        selected = securityState.disguiseIconMode == "CALCULATOR",
                        onClick = { onSelectDisguise("CALCULATOR", "Calculator") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("disguise_option_calculator")
                    )
                    DisguiseOptionCard(
                        label = "Notes",
                        icon = Icons.Outlined.Description,
                        selected = securityState.disguiseIconMode == "NOTES",
                        onClick = { onSelectDisguise("NOTES", "Notes") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("disguise_option_notes")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DisguiseOptionCard(
                        label = "Clock",
                        icon = Icons.Outlined.AccessTime,
                        selected = securityState.disguiseIconMode == "CLOCK",
                        onClick = { onSelectDisguise("CLOCK", "Clock") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("disguise_option_clock")
                    )
                    DisguiseOptionCard(
                        label = "Weather",
                        icon = Icons.Outlined.WbSunny,
                        selected = securityState.disguiseIconMode == "WEATHER",
                        onClick = { onSelectDisguise("WEATHER", "Weather") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("disguise_option_weather")
                    )
                }
            }
        }

        // 4. Home Screen Preview Strip (3 generic "App" icons + selected blue "Calculator" icon)
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CalcVaultCard,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CalcVaultBorder, RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp, horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(Color(0xFF1A253A))
                            )
                            Text(
                                text = "App",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSubtle,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Active Disguise Icon in Home Preview
                    val activeIcon = when (securityState.disguiseIconMode) {
                        "NOTES" -> Icons.Outlined.Description
                        "CLOCK" -> Icons.Outlined.AccessTime
                        "WEATHER" -> Icons.Outlined.WbSunny
                        else -> Icons.Outlined.Calculate
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(CalcBluePrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = activeIcon,
                                contentDescription = securityState.disguiseAppName,
                                tint = CalcVaultBg,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = securityState.disguiseAppName,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextWhite,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // 5. "App name" Field
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "App name",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CalcVaultCard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showRenameDialog = true }
                        .border(1.dp, CalcVaultBorder, RoundedCornerShape(16.dp))
                        .testTag("disguise_app_name_box")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = securityState.disguiseAppName,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextWhite,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 6. Toggles: "Hide from recent apps" & "Block screenshots"
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CalcVaultCard,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CalcVaultBorder, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SecuritySwitchRow(
                        title = "Hide from recent apps",
                        subtitle = null,
                        checked = securityState.hideFromRecentApps,
                        onCheckedChange = onToggleHideFromRecents,
                        modifier = Modifier.testTag("disguise_hide_recents_switch")
                    )
                    HorizontalDivider(color = CalcVaultDivider)
                    SecuritySwitchRow(
                        title = "Block screenshots",
                        subtitle = null,
                        checked = securityState.blockScreenshots,
                        onCheckedChange = onToggleBlockScreenshots,
                        modifier = Modifier.testTag("disguise_block_screenshots_switch")
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        var customName by remember { mutableStateOf(securityState.disguiseAppName) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = CalcVaultCard,
            title = { Text("Custom App Name", color = TextWhite) },
            text = {
                OutlinedTextField(
                    value = customName,
                    onValueChange = { if (it.length <= 24) customName = it },
                    label = { Text("Launcher Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customName.isNotBlank()) {
                            onSelectDisguise(securityState.disguiseIconMode, customName.trim())
                            showRenameDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CalcBluePrimary)
                ) {
                    Text("Save", color = CalcVaultBg, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

@Composable
private fun DisguiseOptionCard(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) CalcBluePrimary else CalcVaultBorder,
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        color = CalcVaultCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (selected) CalcBluePrimary else CalcVaultCardElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) CalcVaultBg else CalcBlueAccentText,
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextWhite
            )
        }
    }
}
