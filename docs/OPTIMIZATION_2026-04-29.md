# 功能优化实施记录

**日期**：2026-04-29  
**执行阶段**：阶段 1（技术债清理）+ 阶段 2（架构重构）

---

## 已完成的优化

### ✅ 阶段 1：技术债清理

#### 1.1 清理废弃资源文件
**问题**：4 个 `fb_*.ogg` 提示音文件在 SoundFeedback 移除后已不被引用，占用 ~10KB 空间。

**实施**：
- 删除 `app/src/main/res/raw/fb_next.ogg`
- 删除 `app/src/main/res/raw/fb_pause.ogg`
- 删除 `app/src/main/res/raw/fb_prev.ogg`
- 删除 `app/src/main/res/raw/fb_unmatched.ogg`

**收益**：减少 APK 体积，代码更清晰。

---

#### 1.2 长时间无命令自动停止
**问题**：用户忘记停止监听会导致持续耗电。

**实施**：
- 在 `VoiceForegroundService` 中添加 `lastCommandTime` 时间戳记录
- 新增 `runIdleCheck()` 协程，每分钟检查一次空闲时间
- 从 `AppConfig` 读取超时配置（默认 30 分钟）
- 超时后自动 `stopSelf()` 并发送通知

**关键代码**：
```kotlin
private val lastCommandTime = AtomicLong(System.currentTimeMillis())
private var idleCheckJob: Job? = null

private suspend fun runIdleCheck() {
    val appConfig = AppConfig(applicationContext)
    val timeoutMinutes = appConfig.idleTimeoutMinutes.first()
    val timeoutMs = timeoutMinutes * 60 * 1000L

    while (scope.isActive) {
        delay(60_000) // 每分钟检查一次
        val idleTime = System.currentTimeMillis() - lastCommandTime.get()
        if (idleTime >= timeoutMs) {
            Log.i(TAG, "No command for ${idleTime / 60_000} minutes, auto-stopping")
            sendAutoStopNotification(timeoutMinutes)
            stopSelf()
            break
        }
    }
}
```

**收益**：防止忘记关闭导致持续耗电，用户体验更友好。

---

### ✅ 阶段 2：架构重构

#### 2.1 引入 DataStore 配置持久化
**问题**：手势坐标、超时时长等参数硬编码，无法动态调整。

**实施**：
- 创建 `AppConfig` 类（`app/src/main/java/com/shower/voicectrl/config/AppConfig.kt`）
- 使用 DataStore Preferences 存储配置
- 支持的配置项：
  - 手势坐标（`centerXPct`, `swipeTopYPct`, `swipeBottomYPct`, `tapYPct`）
  - 超时时长（`idleTimeoutMinutes`）
  - 启用的目标 App 列表（`enabledAppPackages`）

**关键 API**：
```kotlin
class AppConfig(private val context: Context) {
    data class GestureCoordinates(
        val centerXPct: Float = 0.5f,
        val swipeTopYPct: Float = 0.25f,
        val swipeBottomYPct: Float = 0.75f,
        val tapYPct: Float = 0.5f
    )

    val gestureCoordinates: Flow<GestureCoordinates>
    val idleTimeoutMinutes: Flow<Long>
    val enabledAppPackages: Flow<Set<String>>

    suspend fun updateGestureCoordinates(coordinates: GestureCoordinates)
    suspend fun updateIdleTimeout(minutes: Long)
    suspend fun updateEnabledAppPackages(packages: Set<String>)
}
```

**集成点**：
- `GestureConfig.from(coordinates)` 支持从 `AppConfig.GestureCoordinates` 创建实例
- `VoiceForegroundService.runIdleCheck()` 从 `AppConfig` 读取超时配置

**收益**：为后续调试面板和用户自定义功能打好基础。

---

#### 2.2 重构多 App 支持架构
**问题**：`SUPPORTED_PACKAGES` 硬编码为 `Set<String>`，扩展性差，无法为不同 App 配置专属手势。

**实施**：
- 创建 `SupportedApp` 数据类（`app/src/main/java/com/shower/voicectrl/accessibility/SupportedApp.kt`）
- 每个 App 包含：
  - `packageName`: 应用包名
  - `displayName`: 显示名称
  - `gestureConfig`: 专属手势配置（可选）

**关键代码**：
```kotlin
data class SupportedApp(
    val packageName: String,
    val displayName: String,
    val gestureConfig: GestureConfig? = null
) {
    companion object {
        val DEFAULT_APPS = listOf(
            SupportedApp("com.ss.android.ugc.aweme", "抖音"),
            SupportedApp("com.ss.android.ugc.aweme.lite", "抖音极速版"),
            SupportedApp("com.smile.gifmaker", "快手"),
            SupportedApp("com.kuaishou.nebula", "快手极速版")
        )

        fun findByPackage(packageName: String): SupportedApp?
        fun isSupported(packageName: String): Boolean
        fun supportedPackages(): Set<String>
    }
}
```

**重构点**：
- `ShowerAccessibilityService.handleCommand()` 改用 `SupportedApp.isSupported()` 检查
- 支持为每个 App 配置专属 `GestureConfig`（当前使用默认配置）
- 移除硬编码的 `SUPPORTED_PACKAGES` 常量

**收益**：
- 为快手/视频号/小红书适配铺路
- 支持为不同 App 配置不同手势坐标
- 代码结构更清晰，扩展性更强

---

## 验证结果

### 编译验证
```bash
./gradlew :app:compileDebugKotlin
# BUILD SUCCESSFUL
```

### 单元测试
```bash
./gradlew :app:testDebugUnitTest
# BUILD SUCCESSFUL
# 24 actionable tasks: 8 executed, 16 up-to-date
```

### 代码变更
```
M  app/src/main/java/com/shower/voicectrl/accessibility/GestureConfig.kt
M  app/src/main/java/com/shower/voicectrl/accessibility/ShowerAccessibilityService.kt
M  app/src/main/java/com/shower/voicectrl/voice/VoiceForegroundService.kt
D  app/src/main/res/raw/fb_next.ogg
D  app/src/main/res/raw/fb_pause.ogg
D  app/src/main/res/raw/fb_prev.ogg
D  app/src/main/res/raw/fb_unmatched.ogg
A  app/src/main/java/com/shower/voicectrl/accessibility/SupportedApp.kt
A  app/src/main/java/com/shower/voicectrl/config/AppConfig.kt
```

---

## 下一步建议

### 立即可做（基于已完成的架构）
1. **手势坐标调试面板**（P2.3）
   - 长按标题 3 秒进入调试页
   - 4 个滑块调整坐标 → 调用 `AppConfig.updateGestureCoordinates()`
   - 预估工作量：半天

2. **App 选择界面**
   - 让用户选择启用哪些 App
   - 调用 `AppConfig.updateEnabledAppPackages()`
   - 预估工作量：2 小时

### 需要数据驱动（P1.1）
3. **浴室真实环境数据收集**
   - 收集识别率、误触率、后台存活时间
   - 决定后续优化方向（自启动引导 vs 识别优化 vs 唤醒词）

### 功能扩展
4. **适配新 App**（基于 `SupportedApp` 架构）
   - 视频号：`com.tencent.mm`（需验证包名和手势）
   - 小红书：`com.xingin.xhs`
   - 每个 App 预估半天（需实测坐标）

---

## 技术亮点

1. **配置持久化**：使用 DataStore 替代硬编码，支持运行时动态调整
2. **架构抽象**：`SupportedApp` 类为多 App 支持提供清晰的扩展点
3. **自动停止**：基于配置的超时机制，提升用户体验和省电
4. **向后兼容**：所有配置都有默认值，不影响现有用户

---

## 参考文档

- 路线图：[`docs/roadmap.md`](roadmap.md)
- 开发手册：[`docs/development.md`](development.md)
- 设计文档：[`docs/superpowers/specs/2026-04-17-shower-voice-ctrl-design.md`](superpowers/specs/2026-04-17-shower-voice-ctrl-design.md)
