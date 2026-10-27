package com.example.calculator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.VaultSecurityState
import com.example.ui.theme.CalcBlueAccentText
import com.example.ui.theme.CalcBluePrimary
import com.example.ui.theme.CalcNumButtonBg
import com.example.ui.theme.CalcOpButtonBg
import com.example.ui.theme.CalcVaultBg
import com.example.ui.theme.CalcVaultBorder
import com.example.ui.theme.CalcVaultCard
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.GoldFolder
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.RubyDanger
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.viewmodel.CalculatorUiState
import com.example.viewmodel.PasswordVerificationState
import com.example.viewmodel.PinSetupStage

@Composable
fun CalculatorScreen(
    calcState: CalculatorUiState,
    securityState: VaultSecurityState,
    onKeyPress: (String) -> Unit,
    onEqualsPress: () -> Unit,
    onToggleScientific: () -> Unit,
    onToggleHistory: () -> Unit,
    onClearHistory: () -> Unit,
    onToggleTheme: () -> Unit,
    onResetPinSetup: () -> Unit = {},
    onOpenRecoveryDialog: () -> Unit = {},
    onQuickConfigureDefaultPin: (String) -> Unit = {},
    onQuickOpenVault: () -> Unit = {},
    onCompleteInitialSetup: (question: String, answer: String, seedDemoPack: Boolean) -> Unit = { _, _, _ -> },
    onAttemptRecoveryReset: (answer: String, newPin: String, onResult: (Boolean) -> Unit) -> Unit,
    onDismissRecovery: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var showOverflowMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CalcVaultBg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("calculator_screen"),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Minimal Top Bar (Clock icon left, 3-dots menu right — 100% legitimate calculator options)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleHistory,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("toggle_history_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = "Calculation history",
                    tint = Color(0xFFCBD5E1),
                    modifier = Modifier.size(24.dp)
                )
            }

            Box {
                IconButton(
                    onClick = { showOverflowMenu = true },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("calculator_more_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More options",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(24.dp)
                    )
                }

                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false },
                    modifier = Modifier.background(CalcVaultCard)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (calcState.isScientificExpanded) "Masquer touches scientifiques" else "Touches scientifiques",
                                color = TextWhite
                            )
                        },
                        onClick = {
                            showOverflowMenu = false
                            onToggleScientific()
                        },
                        modifier = Modifier.testTag("toggle_scientific_button")
                    )
                    DropdownMenuItem(
                        text = { Text("Inverser le signe (+/−)", color = TextWhite) },
                        onClick = {
                            showOverflowMenu = false
                            onKeyPress("+/-")
                        },
                        modifier = Modifier.testTag("toggle_sign_button")
                    )
                    DropdownMenuItem(
                        text = { Text("Effacer l'historique", color = TextMuted) },
                        onClick = {
                            showOverflowMenu = false
                            onClearHistory()
                        }
                    )
                }
            }
        }

        // First-Launch PIN Setup Banner (ONLY shown before master PIN is configured)
        AnimatedVisibility(
            visible = !securityState.isPinConfigured,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            FirstLaunchPinSetupBanner(
                setupStage = calcState.setupStage,
                lastEquationLabel = calcState.lastEquationLabel,
                onResetPinSetup = onResetPinSetup
            )
        }

        // Expandable Calculation History Tape (when clock icon is tapped)
        AnimatedVisibility(
            visible = calcState.isHistoryVisible,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .padding(vertical = 4.dp)
                    .border(1.dp, CalcVaultBorder, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                color = CalcVaultCard
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    if (securityState.calculationHistory.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Aucun calcul récent",
                                color = TextMuted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(securityState.calculationHistory) { entry ->
                                Text(text = entry, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (securityState.hasSecurityAnswer && securityState.failedAttemptsCount >= 3) {
                        Text(
                            text = "Récupération du code configurée",
                            color = CalcBlueAccentText,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .align(Alignment.End)
                                .clickable(onClick = onOpenRecoveryDialog)
                                .testTag("open_pin_recovery_button")
                        )
                    }
                }
            }
        }

        // Spacious Display Area (Right-aligned Equation + Large White Result, starting cleanly at "0")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End
        ) {
            // Invisible semantic verification node when PIN is configured (100% stealth visually)
            PasswordVerificationStatusStrip(
                verificationState = calcState.verificationState,
                setupStage = calcState.setupStage,
                isPinConfigured = securityState.isPinConfigured
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Secondary Expression Line (empty on initial launch, shows ongoing/previous equation only when calculated)
            val topEquationText = when {
                calcState.liveResult.isNotEmpty() -> calcState.expression
                calcState.lastEquationLabel.isNotEmpty() -> calcState.lastEquationLabel
                else -> ""
            }
            Text(
                text = topEquationText,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 22.sp,
                color = TextMuted,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("calc_display_result")
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Primary Big Readout Line (defaults to "0" with no pre-filled calculation)
            val mainDisplayValue = when {
                calcState.verificationState is PasswordVerificationState.PinCandidateProgress -> calcState.expression
                calcState.expression.isNotEmpty() -> formatCalculatorDisplay(calcState.expression)
                else -> "0"
            }
            Text(
                text = mainDisplayValue,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = when {
                    mainDisplayValue.length > 14 -> 34.sp
                    mainDisplayValue.length > 9 -> 44.sp
                    else -> 60.sp
                },
                color = TextWhite,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("calc_display_expression")
            )
        }

        // Optional Expandable Scientific Drawer
        AnimatedVisibility(
            visible = calcState.isScientificExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            val sciKeys = listOf("sin(", "cos(", "tan(", "√(", "^", "(", ")")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                sciKeys.forEach { token ->
                    val label = if (token.length > 1 && token.endsWith("(")) token.removeSuffix("(") else token
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(CalcOpButtonBg)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onKeyPress(token)
                            }
                            .testTag("calc_sci_$label"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = CalcBlueAccentText,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // 5-Row Calculator Keypad
        val topFourRows = listOf(
            listOf("AC", "DEL", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "-"),
            listOf("1", "2", "3", "+")
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.55f, fill = true),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            topFourRows.forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { key ->
                        val isTopOpRow = rowIndex == 0
                        val isRightOpCol = key in listOf("÷", "×", "-", "+")
                        val isActionStyle = isTopOpRow || isRightOpCol

                        val buttonBg = if (isActionStyle) CalcOpButtonBg else CalcNumButtonBg
                        val contentColor = if (isActionStyle) CalcBlueAccentText else TextWhite

                        val tag = when (key) {
                            "AC" -> "calc_key_clear"
                            "DEL" -> "calc_key_backspace"
                            "%" -> "calc_key_percent"
                            "÷" -> "calc_key_divide"
                            "×" -> "calc_key_multiply"
                            "-" -> "calc_key_minus"
                            "+" -> "calc_key_plus"
                            else -> "calc_key_$key"
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp))
                                .background(buttonBg)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onKeyPress(key)
                                }
                                .testTag(tag),
                            contentAlignment = Alignment.Center
                        ) {
                            if (key == "DEL") {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Backspace,
                                    contentDescription = "Backspace",
                                    tint = contentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                val displaySymbol = if (key == "-") "−" else key
                                Text(
                                    text = displaySymbol,
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = if (isRightOpCol) 30.sp else 26.sp,
                                    color = contentColor
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Row 5: Wide '0' (spans 2 columns), '.', and Electric Blue '='
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(2.1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                        .background(CalcNumButtonBg)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onKeyPress("0")
                        }
                        .testTag("calc_key_0"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "0",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 26.sp,
                        color = TextWhite
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                        .background(CalcNumButtonBg)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onKeyPress(".")
                        }
                        .testTag("calc_key_dot"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ".",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        color = TextWhite
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                        .background(CalcBluePrimary)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onEqualsPress()
                        }
                        .testTag("calc_key_equals"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "=",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 30.sp,
                        color = CalcVaultBg
                    )
                }
            }
        }
    }

    // Forgot PIN Recovery Dialog (Triggered via 11223344=)
    if (calcState.showRecoveryDialog) {
        PinRecoveryDialog(
            securityQuestion = securityState.securityQuestion,
            onAttemptReset = onAttemptRecoveryReset,
            onDismiss = onDismissRecovery
        )
    }
}

@Composable
private fun FirstLaunchPinSetupBanner(
    setupStage: PinSetupStage,
    lastEquationLabel: String,
    onResetPinSetup: () -> Unit
) {
    val isConfirmStep = setupStage == PinSetupStage.CONFIRM_PIN
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .border(1.dp, CalcBluePrimary.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .testTag("first_launch_pin_setup_card"),
        shape = RoundedCornerShape(18.dp),
        color = CalcVaultCard
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = CalcBlueAccentText,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isConfirmStep) {
                            "Étape 2/2 · Confirmez votre code secret"
                        } else {
                            "Étape 1/2 · Créez votre code secret (6–8 chiffres)"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = CalcBlueAccentText
                    )
                }
                if (isConfirmStep) {
                    Text(
                        text = "Recommencer",
                        style = MaterialTheme.typography.labelMedium,
                        color = CalcBluePrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable(onClick = onResetPinSetup)
                            .testTag("reset_pin_setup_button")
                    )
                }
            }
            Text(
                text = if (isConfirmStep) {
                    "Retapez le même code de 6 à 8 chiffres puis appuyez sur '='. La calculatrice sera ensuite 100% discrète."
                } else {
                    "Saisissez un code de 6 à 8 chiffres puis appuyez sur '=' pour activer votre coffre."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            if (lastEquationLabel.isNotBlank()) {
                Text(
                    text = lastEquationLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldFolder,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PasswordVerificationStatusStrip(
    verificationState: PasswordVerificationState,
    setupStage: PinSetupStage,
    isPinConfigured: Boolean
) {
    val statusLabel = when (verificationState) {
        is PasswordVerificationState.Idle -> {
            if (!isPinConfigured) {
                if (setupStage == PinSetupStage.CONFIRM_PIN) {
                    "CONFIRM PIN + PRESS '='"
                } else {
                    "ENTER 4–8 DIGIT PIN + PRESS '='"
                }
            } else {
                ""
            }
        }
        is PasswordVerificationState.PinCandidateProgress -> {
            val ready = verificationState.enteredDigits >= 4
            if (ready) {
                "PIN CANDIDATE (${verificationState.enteredDigits} DIGITS) • PRESS '=' TO VERIFY"
            } else {
                "PIN INPUT (${verificationState.enteredDigits}/${verificationState.targetDigits} DIGITS)"
            }
        }
        is PasswordVerificationState.Verifying -> {
            "VERIFYING PASSWORD VIA '${verificationState.triggerOperation}'..."
        }
        is PasswordVerificationState.VerifiedMaster -> {
            "PASSWORD VERIFIED • VAULT UNLOCKED"
        }
        is PasswordVerificationState.VerifiedDecoy -> {
            "DECOY PIN VERIFIED • DECOY VAULT"
        }
        is PasswordVerificationState.VerificationFailed -> {
            "VERIFICATION FAILED (ATTEMPT #${verificationState.attemptCount})"
        }
        is PasswordVerificationState.RecoveryCodeTriggered -> {
            "RECOVERY CODE 11223344 VERIFIED"
        }
    }

    // Once PIN is configured, keep verification semantics completely invisible (Color.Transparent, 1.sp)
    // so the calculator never leaks that it is a vault while keeping semantic tags accessible for tests.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .testTag("password_verification_banner"),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = statusLabel,
            fontSize = 1.sp,
            color = Color.Transparent,
            maxLines = 1,
            modifier = Modifier.testTag("password_verification_state_text")
        )

        if (verificationState is PasswordVerificationState.PinCandidateProgress) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.testTag("pin_candidate_dots")
            ) {
                val totalDots = verificationState.targetDigits.coerceIn(4, 8)
                for (i in 1..totalDots) {
                    Box(
                        modifier = Modifier
                            .size(1.dp)
                            .background(Color.Transparent)
                    )
                }
            }
        }
    }
}

private fun formatCalculatorDisplay(raw: String): String {
    if (raw.isEmpty()) return "0"
    // Add thousands separators to pure integer numbers for display while keeping raw expression intact
    if (raw.all { it.isDigit() } && raw.length in 4..12) {
        return try {
            java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(raw.toLong())
        } catch (_: Exception) {
            raw
        }
    }
    return raw
}

@Composable
private fun PinRecoveryDialog(
    securityQuestion: String,
    onAttemptReset: (answer: String, newPin: String, onResult: (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var answer by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CalcVaultCard,
        title = {
            Text("PIN Recovery", style = MaterialTheme.typography.titleLarge, color = TextWhite)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Question: $securityQuestion",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CalcBlueAccentText,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = answer,
                    onValueChange = {
                        answer = it
                        errorMsg = null
                    },
                    label = { Text("Your Security Answer") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 8) {
                            newPin = input
                            errorMsg = null
                        }
                    },
                    label = { Text("New 6–8 Digit PIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = RubyDanger
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPin.length !in 6..8) {
                        errorMsg = "New PIN must be 6 to 8 digits"
                        return@Button
                    }
                    onAttemptReset(answer, newPin) { ok ->
                        if (!ok) {
                            errorMsg = "Security answer did not match"
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CalcBluePrimary)
            ) {
                Text("Reset PIN", color = CalcVaultBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}
