package com.shower.voicectrl.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class MainUiState(
    val micGranted: Boolean,
    val accessibilityEnabled: Boolean,
    val listening: Boolean,
)

// iOS 系统色，语义化命名。跨浅色 / 深色模式都能直接用。
private val IosBlue = Color(0xFF0A84FF)
private val IosGreen = Color(0xFF34C759)
private val IosOrange = Color(0xFFFF9F0A)
private val IosRed = Color(0xFFFF3B30)
private val IosGray = Color(0xFF8E8E93)

@Composable
fun MainScreen(
    state: MainUiState,
    onRequestMic: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onToggleListening: () -> Unit,
    onEnterDebug: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 40.dp),
        ) {
            Header(onLongPress = onEnterDebug)
            Spacer(Modifier.height(28.dp))
            StatusCard(
                state = state,
                onRequestMic = onRequestMic,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
            )
            Spacer(Modifier.weight(1f))
            PrimaryAction(
                state = state,
                onToggleListening = onToggleListening,
            )
            Spacer(Modifier.height(12.dp))
            HintFooter()
        }
    }
}

@Composable
private fun Header(onLongPress: () -> Unit) {
    Column(
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(
                onLongPress = { onLongPress() }
            )
        }
    ) {
        Text(
            text = "隔空刷",
            fontWeight = FontWeight.Bold,
            fontSize = 36.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "说一声，视频就过了",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun StatusCard(
    state: MainUiState,
    onRequestMic: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        StatusRow(
            label = "麦克风权限",
            granted = state.micGranted,
            actionLabel = "授予",
            onAction = onRequestMic,
            padding = PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(start = 16.dp),
        )
        StatusRow(
            label = "无障碍服务",
            granted = state.accessibilityEnabled,
            actionLabel = "去设置",
            onAction = onOpenAccessibilitySettings,
            padding = PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun StatusRow(
    label: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    padding: PaddingValues,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(granted = granted)
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (granted) {
            Text(
                text = "已就绪",
                fontSize = 14.sp,
                color = IosGreen,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(end = 12.dp),
            )
        } else {
            TextButton(onClick = onAction) {
                Text(
                    text = actionLabel,
                    fontSize = 14.sp,
                    color = IosBlue,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun StatusDot(granted: Boolean) {
    val color = if (granted) IosGreen else IosGray
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun PrimaryAction(
    state: MainUiState,
    onToggleListening: () -> Unit,
) {
    val canStart = state.micGranted && state.accessibilityEnabled
    val bgColor by animateColorAsState(
        targetValue = when {
            !canStart -> IosGray.copy(alpha = 0.25f)
            state.listening -> IosRed
            else -> IosBlue
        },
        animationSpec = tween(220),
        label = "btnColor",
    )

    Button(
        onClick = onToggleListening,
        enabled = canStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = bgColor,
            contentColor = Color.White,
            disabledContainerColor = bgColor,
            disabledContentColor = Color.White.copy(alpha = 0.6f),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.listening) PulseDot()
            Text(
                text = when {
                    !canStart -> "请先完成上方授权"
                    state.listening -> "正在监听 · 点击停止"
                    else -> "开始监听"
                },
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PulseDot() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )
    val scale by animateFloatAsState(targetValue = 1f, label = "pulseScale")
    Box(
        modifier = Modifier
            .padding(end = 10.dp)
            .size(10.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = alpha * scale)),
    )
}

@Composable
private fun HintFooter() {
    Text(
        text = "开启后切到抖音，说 \u201C下一条\u201D / \u201C上一条\u201D / \u201C暂停\u201D",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    )
}
