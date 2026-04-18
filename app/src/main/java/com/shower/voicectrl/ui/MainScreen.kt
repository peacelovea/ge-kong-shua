package com.shower.voicectrl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class MainUiState(
    val micGranted: Boolean,
    val accessibilityEnabled: Boolean,
    val listening: Boolean,
)

@Composable
fun MainScreen(
    state: MainUiState,
    onRequestMic: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onToggleListening: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("浴室语音遥控（抖音）", style = MaterialTheme.typography.headlineSmall)

            StatusRow("麦克风权限", state.micGranted)
            StatusRow("无障碍服务", state.accessibilityEnabled)

            if (!state.micGranted) {
                Button(onClick = onRequestMic, modifier = Modifier.fillMaxWidth()) {
                    Text("授予麦克风权限")
                }
            }
            if (!state.accessibilityEnabled) {
                Button(onClick = onOpenAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
                    Text("去开启无障碍服务")
                }
            }

            Spacer(Modifier.weight(1f))

            val canStart = state.micGranted && state.accessibilityEnabled
            Button(
                onClick = onToggleListening,
                enabled = canStart,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.listening) "停止监听" else "开始监听")
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(if (ok) "✓ 就绪" else "× 未就绪")
    }
}
