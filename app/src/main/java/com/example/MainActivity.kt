package com.example

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calculator.CalculatorScreen
import com.example.ui.theme.CalcVaultBg
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.vault.VaultMainScreen
import com.example.viewmodel.AppMode
import com.example.viewmodel.HideUViewModel
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val hideUViewModel: HideUViewModel = viewModel(
                factory = HideUViewModel.Factory(context)
            )
            HideUAppRoot(viewModel = hideUViewModel)
        }
    }
}

@Composable
fun HideUAppRoot(viewModel: HideUViewModel) {
    val appMode by viewModel.appMode.collectAsStateWithLifecycle()
    val calcState by viewModel.calcState.collectAsStateWithLifecycle()
    val securityState by viewModel.securityState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Synchronize WindowManager.LayoutParams.FLAG_SECURE on sensitive vault screens:
    // - In production (!BuildConfig.DEBUG), blockScreenshots defaults to TRUE so sensitive vault screens
    //   and recent-app previews are protected by FLAG_SECURE.
    // - In debug builds (AI Studio streaming emulator), the development override keeps screen streaming
    //   functional unless the user toggles "Block screenshots" on in App Disguise / Security settings.
    // - On the public CALCULATOR_DISGUISE screen, FLAG_SECURE is always cleared so the calculator behaves normally.
    DisposableEffect(context, appMode, securityState.blockScreenshots) {
        val window = (context as? Activity)?.window
        val shouldApplyFlagSecure = appMode == AppMode.VAULT_UNLOCKED && securityState.blockScreenshots
        if (shouldApplyFlagSecure) {
            window?.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose { }
    }

    // Auto-Lock Vault when app goes to background (ON_STOP)
    DisposableEffect(lifecycleOwner, securityState.autoLockOnBackground) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && securityState.autoLockOnBackground) {
                viewModel.lockVaultImmediately("Background Auto-Lock")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Real Accelerometer Panic Lock Listener (supports Flip Phone Face-Down & Shake Phone Twice)
    DisposableEffect(appMode, securityState.shakeToLockEnabled, securityState.panicTriggerMode) {
        if (appMode != AppMode.VAULT_UNLOCKED || !securityState.shakeToLockEnabled) {
            return@DisposableEffect onDispose { }
        }
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var lastTriggerTimestamp = 0L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val values = event?.values ?: return
                if (values.size < 3) return
                val gX = values[0] / SensorManager.GRAVITY_EARTH
                val gY = values[1] / SensorManager.GRAVITY_EARTH
                val gZ = values[2] / SensorManager.GRAVITY_EARTH
                val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

                val isShake = gForce > 2.7f
                val isFlipFaceDown = securityState.panicTriggerMode == "FLIP" && values[2] < -9.2f

                if (isShake || isFlipFaceDown) {
                    val now = System.currentTimeMillis()
                    if (now - lastTriggerTimestamp > 1000L) {
                        lastTriggerTimestamp = now
                        viewModel.lockVaultImmediately("Shake Panic Lock")
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (accelerometer != null) {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    val useLightCalculator = appMode == AppMode.CALCULATOR_DISGUISE && securityState.disguiseTheme == "LIGHT"

    MyApplicationTheme(useLightDisguise = useLightCalculator) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = CalcVaultBg
        ) {
            Crossfade(targetState = appMode, label = "VaultDisguiseTransition") { mode ->
                when (mode) {
                    AppMode.CALCULATOR_DISGUISE -> {
                        CalculatorScreen(
                            calcState = calcState,
                            securityState = securityState,
                            onKeyPress = viewModel::onCalculatorInput,
                            onEqualsPress = viewModel::onEqualsPressed,
                            onToggleScientific = viewModel::toggleScientificPad,
                            onToggleHistory = viewModel::toggleHistoryDrawer,
                            onClearHistory = viewModel::clearCalcHistory,
                            onToggleTheme = {
                                val next = if (securityState.disguiseTheme == "LIGHT") "OBSIDIAN" else "LIGHT"
                                viewModel.setDisguiseTheme(next)
                            },
                            onResetPinSetup = viewModel::resetPinSetupDraft,
                            onOpenRecoveryDialog = viewModel::openRecoveryDialog,
                            onQuickConfigureDefaultPin = viewModel::quickConfigureDefaultPin,
                            onCompleteInitialSetup = viewModel::completeInitialPinSetup,
                            onAttemptRecoveryReset = viewModel::attemptSecurityAnswerReset,
                            onDismissRecovery = viewModel::dismissRecoveryDialog,
                            onQuickOpenVault = { viewModel.openVaultDirectly(false) }
                        )
                    }
                    AppMode.VAULT_UNLOCKED -> {
                        VaultMainScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
