package com.shower.voicectrl

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.shower.voicectrl.accessibility.ShowerAccessibilityService
import com.shower.voicectrl.config.AppConfig
import com.shower.voicectrl.ui.DebugScreen
import com.shower.voicectrl.ui.MainScreen
import com.shower.voicectrl.ui.MainUiState
import com.shower.voicectrl.ui.theme.ShowervoicectrlTheme
import com.shower.voicectrl.voice.VoiceForegroundService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestMic = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* state refreshed by the polling LaunchedEffect */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShowervoicectrlTheme {
                var micGranted by remember { mutableStateOf(isMicGranted()) }
                var accessibilityEnabled by remember { mutableStateOf(isAccessibilityEnabled()) }
                var listening by remember { mutableStateOf(VoiceForegroundService.isRunning()) }
                var showDebugScreen by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                val appConfig = remember { AppConfig(applicationContext) }

                LaunchedEffect(Unit) {
                    while (true) {
                        micGranted = isMicGranted()
                        accessibilityEnabled = isAccessibilityEnabled()
                        listening = VoiceForegroundService.isRunning()
                        delay(1000)
                    }
                }

                if (showDebugScreen) {
                    var initialConfig by remember { mutableStateOf<AppConfig.GestureCoordinates?>(null) }

                    LaunchedEffect(Unit) {
                        initialConfig = appConfig.gestureCoordinates.first()
                    }

                    initialConfig?.let { config ->
                        DebugScreen(
                            initialConfig = config,
                            onSave = { newConfig ->
                                scope.launch {
                                    appConfig.updateGestureCoordinates(newConfig)
                                    Toast.makeText(
                                        this@MainActivity,
                                        "配置已保存",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    showDebugScreen = false
                                }
                            },
                            onTest = { config ->
                                Toast.makeText(
                                    this@MainActivity,
                                    "测试功能开发中",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onBack = { showDebugScreen = false }
                        )
                    }
                } else {
                    MainScreen(
                        state = MainUiState(micGranted, accessibilityEnabled, listening),
                        onRequestMic = { requestMic.launch(Manifest.permission.RECORD_AUDIO) },
                        onOpenAccessibilitySettings = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onToggleListening = {
                            if (listening) {
                                VoiceForegroundService.stop(this)
                            } else {
                                VoiceForegroundService.start(this)
                            }
                            listening = !listening // optimistic; polling reconciles service state
                        },
                        onEnterDebug = { showDebugScreen = true }
                    )
                }
            }
        }
    }

    private fun isMicGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(AccessibilityManager::class.java) ?: return false
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            .any { it.id.contains(ShowerAccessibilityService::class.java.simpleName) }
    }
}
