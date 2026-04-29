package com.shower.voicectrl.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shower.voicectrl.config.AppConfig

private val IosBlue = Color(0xFF0A84FF)
private val IosGreen = Color(0xFF34C759)
private val IosGray = Color(0xFF8E8E93)

@Composable
fun DebugScreen(
    initialConfig: AppConfig.GestureCoordinates,
    onSave: (AppConfig.GestureCoordinates) -> Unit,
    onTest: (AppConfig.GestureCoordinates) -> Unit,
    onBack: () -> Unit,
) {
    var centerXPct by remember { mutableFloatStateOf(initialConfig.centerXPct) }
    var swipeTopYPct by remember { mutableFloatStateOf(initialConfig.swipeTopYPct) }
    var swipeBottomYPct by remember { mutableFloatStateOf(initialConfig.swipeBottomYPct) }
    var tapYPct by remember { mutableFloatStateOf(initialConfig.tapYPct) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 40.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text("← 返回", color = IosBlue, fontSize = 16.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "手势坐标调试",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(60.dp)) // 平衡左侧按钮
            }

            Spacer(Modifier.height(32.dp))

            // 配置卡片
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    CoordinateSlider(
                        label = "横向位置 (centerXPct)",
                        value = centerXPct,
                        onValueChange = { centerXPct = it },
                    )
                    Spacer(Modifier.height(24.dp))
                    CoordinateSlider(
                        label = "上滑起点 (swipeTopYPct)",
                        value = swipeTopYPct,
                        onValueChange = { swipeTopYPct = it },
                    )
                    Spacer(Modifier.height(24.dp))
                    CoordinateSlider(
                        label = "下滑起点 (swipeBottomYPct)",
                        value = swipeBottomYPct,
                        onValueChange = { swipeBottomYPct = it },
                    )
                    Spacer(Modifier.height(24.dp))
                    CoordinateSlider(
                        label = "暂停位置 (tapYPct)",
                        value = tapYPct,
                        onValueChange = { tapYPct = it },
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // 操作按钮
            val currentConfig = AppConfig.GestureCoordinates(
                centerXPct = centerXPct,
                swipeTopYPct = swipeTopYPct,
                swipeBottomYPct = swipeBottomYPct,
                tapYPct = tapYPct,
            )

            Button(
                onClick = { onTest(currentConfig) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = IosGray.copy(alpha = 0.2f),
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ),
            ) {
                Text("测试手势", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        centerXPct = AppConfig.DEFAULT_CENTER_X_PCT
                        swipeTopYPct = AppConfig.DEFAULT_SWIPE_TOP_Y_PCT
                        swipeBottomYPct = AppConfig.DEFAULT_SWIPE_BOTTOM_Y_PCT
                        tapYPct = AppConfig.DEFAULT_TAP_Y_PCT
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IosGray.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                ) {
                    Text("重置", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(Modifier.width(12.dp))

                Button(
                    onClick = { onSave(currentConfig) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IosGreen,
                        contentColor = Color.White,
                    ),
                ) {
                    Text("保存", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun CoordinateSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(value * 100).toInt()}%",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = IosBlue,
            )
        }
        Spacer(Modifier.height(8.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = IosBlue,
                activeTrackColor = IosBlue,
                inactiveTrackColor = IosGray.copy(alpha = 0.3f),
            ),
        )
    }
}
