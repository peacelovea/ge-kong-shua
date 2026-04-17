# shower-voice-ctrl Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android 应用：洗澡时用语音"下一条 / 上一条 / 暂停"控制抖音，解决湿手无法跳过长视频的痛点。

**Architecture:** 前台服务持续录音 → Vosk 离线关键词识别 → `CommandBus` (SharedFlow) 分发 → `AccessibilityService` 注入滑动/点击手势 + `SoundPool` 播放反馈音。三条链路（采集、执行、反馈）通过单一事件总线解耦。

**Tech Stack:** Kotlin 2.1.0、Jetpack Compose（Material 3）、Vosk 0.3.47 离线 KWS、AccessibilityService + `dispatchGesture`、`AudioRecord`、`SoundPool`、DataStore、Coroutines/Flow。`minSdk 28` / `targetSdk 35`。

**Working dir：** `/Users/kaifa/workspace/my-project/shower-voice-ctrl/`（已 `git init`，当前主分支 `main`）。

**Design doc：** `docs/superpowers/specs/2026-04-17-shower-voice-ctrl-design.md`

**本计划阅读者预设：** 熟悉通用编程，**未写过 Android**。每一步给完整代码、完整命令、完整预期输出。

---

## 前置检查（开始前一次性完成）

- [ ] **P1: 确认开发环境**

安装好 Android Studio Ladybug（或更新版），Android SDK Platform 35 + Build-Tools 35 已下载，`$ANDROID_HOME` 已配置。验证：

```bash
adb --version
```

Expected: 输出包含 `Android Debug Bridge version 1.0.xx`。

- [ ] **P2: Vivo X200 Pro 开启调试模式**

设置 → 我的设备 → 版本信息 → 连续点 "软件版本号" 7 次 → 设置 → 系统管理 → 开发者选项 → 打开 "USB 调试" + "USB 安装"。用数据线连上 Mac，运行：

```bash
adb devices
```

Expected: 列出一台 `device` 状态的设备（非 `unauthorized` / `offline`）。第一次会在手机上弹"允许 USB 调试"，勾"始终允许"。

- [ ] **P3: 下载 Vosk 中文模型**

下载 `vosk-model-small-cn-0.22.zip`（~42MB）：

```bash
cd ~/Downloads && curl -L -O https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip && unzip -q vosk-model-small-cn-0.22.zip
ls vosk-model-small-cn-0.22
```

Expected: 输出包含 `am`、`conf`、`graph`、`ivector` 等子目录。模型先放在下载目录，Task 2 里会拷到项目。

---

## 文件结构总览

```
shower-voice-ctrl/
├── settings.gradle.kts
├── build.gradle.kts                       (root)
├── gradle.properties
├── gradle/
│   ├── wrapper/...
│   └── libs.versions.toml
├── app/
│   ├── build.gradle.kts
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/com/shower/voicectrl/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── bus/
│   │   │   │   │   ├── Command.kt
│   │   │   │   │   ├── CommandBus.kt
│   │   │   │   │   └── Debouncer.kt
│   │   │   │   ├── voice/
│   │   │   │   │   ├── VoiceForegroundService.kt
│   │   │   │   │   └── VoskRecognizer.kt
│   │   │   │   ├── accessibility/
│   │   │   │   │   ├── ShowerAccessibilityService.kt
│   │   │   │   │   └── GestureConfig.kt
│   │   │   │   ├── sound/
│   │   │   │   │   └── SoundFeedback.kt
│   │   │   │   └── ui/
│   │   │   │       └── MainScreen.kt
│   │   │   ├── assets/
│   │   │   │   └── vosk-model-small-cn-0.22/   (git-ignored, 脚本拷入)
│   │   │   └── res/
│   │   │       ├── raw/                          (ogg 提示音)
│   │   │       └── xml/
│   │   │           └── accessibility_service_config.xml
│   │   ├── test/
│   │   │   └── java/com/shower/voicectrl/
│   │   │       ├── bus/CommandBusTest.kt
│   │   │       ├── bus/DebouncerTest.kt
│   │   │       └── accessibility/GestureConfigTest.kt
│   │   └── androidTest/
│   │       └── java/com/shower/voicectrl/
│   │           └── voice/VoskRecognizerTest.kt
├── scripts/
│   └── install_vosk_model.sh
├── .gitignore
├── docs/superpowers/... (已存在)
└── README.md
```

**文件责任：**
- `bus/` — 纯 Kotlin，事件定义与分发，高度可单测
- `voice/` — 麦克风采集 + Vosk 识别（依赖 Android Context）
- `accessibility/` — 无障碍服务 + 手势坐标计算
- `sound/` — 提示音封装
- `ui/` — Compose 单屏
- `MainActivity.kt` — 承载 UI、权限流程、启动/停止前台服务

---

## Task 1：生成 Android 项目骨架

**Files:**
- Create：`settings.gradle.kts`、`build.gradle.kts`（root）、`gradle.properties`、`gradle/wrapper/*`、`gradle/libs.versions.toml`、`app/build.gradle.kts`、`app/src/main/AndroidManifest.xml`、`app/src/main/java/com/shower/voicectrl/MainActivity.kt`、`app/src/main/res/values/themes.xml`、`app/src/main/res/values/strings.xml`、`gradlew`、`gradlew.bat`

Android Studio 生成大量样板，不手写。

- [ ] **Step 1.1: 在 Android Studio 新建工程**

File → New → New Project → 选 **"Empty Activity"**（Compose 模板，确认右侧标注有 "Jetpack Compose"）→ Next，填：
- Name: `shower-voice-ctrl`
- Package name: `com.shower.voicectrl`
- Save location: **`/Users/kaifa/workspace/my-project/shower-voice-ctrl`**（注意：选中的是这个已存在的空目录本身，Android Studio 会要求同意目录非空；也可以先临时选其他路径生成后再把文件挪进来）
- Language: Kotlin
- Minimum SDK: **API 28: Android 9.0 (Pie)**
- Build configuration language: **Kotlin DSL (build.gradle.kts)**

Finish。首次同步可能要 5-15 分钟下 Gradle 和依赖。

- [ ] **Step 1.2: 合并生成文件到仓库根**

如果 Task 1.1 Save location 选到了别处，把生成的所有文件（含隐藏的 `.gradle`、`.idea` 除外）移动到 `/Users/kaifa/workspace/my-project/shower-voice-ctrl/`。已经 `docs/` 和 `.git/` 在里面，合并即可。

验证目录结构：

```bash
cd /Users/kaifa/workspace/my-project/shower-voice-ctrl
ls -la
```

Expected: 看到 `app/`、`gradle/`、`gradlew`、`settings.gradle.kts`、`build.gradle.kts`、`docs/`、`.git/`。

- [ ] **Step 1.3: 添加 `.gitignore`**

把下面内容写到仓库根的 `.gitignore`（如果 AS 生成了就覆盖）：

```gitignore
# Gradle
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar

# Android Studio / IntelliJ
.idea/
*.iml
local.properties
captures/
.cxx/

# Android build
*.apk
*.aab
*.ap_
*.dex

# Kotlin
.kotlin/

# macOS
.DS_Store

# Vosk 模型（体积大，通过脚本下载）
app/src/main/assets/vosk-model-small-cn-0.22/
```

- [ ] **Step 1.4: 首次构建确认可跑**

```bash
cd /Users/kaifa/workspace/my-project/shower-voice-ctrl
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` + 生成 `app/build/outputs/apk/debug/app-debug.apk`。第一次会慢（下依赖），后续秒级。

- [ ] **Step 1.5: 提交**

```bash
git add .gitignore settings.gradle.kts build.gradle.kts gradle.properties gradle/ gradlew gradlew.bat app/
git commit -m "chore: scaffold Android project with Compose template"
```

---

## Task 2：引入 Vosk + 项目依赖

**Files:**
- Modify：`gradle/libs.versions.toml`、`app/build.gradle.kts`
- Create：`scripts/install_vosk_model.sh`

- [ ] **Step 2.1: 编辑 `gradle/libs.versions.toml`**

完全覆盖为（Android Studio 生成的已有部分，用下面内容替换）：

```toml
[versions]
agp = "8.7.3"
kotlin = "2.1.0"
coreKtx = "1.15.0"
lifecycleRuntimeKtx = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2025.01.00"
coroutines = "1.9.0"
datastore = "1.1.1"
vosk = "0.3.47"
junit = "4.13.2"
mockk = "1.13.13"
androidxTestExt = "1.2.1"
androidxTestRunner = "1.6.2"
espressoCore = "3.6.1"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
vosk-android = { group = "com.alphacephei", name = "vosk-android", version.ref = "vosk" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxTestExt" }
androidx-test-runner = { group = "androidx.test", name = "runner", version.ref = "androidxTestRunner" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 2.2: 编辑 `app/build.gradle.kts`**

完全覆盖为：

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.shower.voicectrl"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shower.voicectrl"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // 不压缩 Vosk 模型中的二进制文件，否则运行时读取会出错
    androidResources {
        noCompress += listOf("mdl", "fst", "int", "bin")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.vosk.android)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
```

- [ ] **Step 2.3: 写 Vosk 模型安装脚本**

Create `scripts/install_vosk_model.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS_DIR="$ROOT_DIR/app/src/main/assets"
MODEL_NAME="vosk-model-small-cn-0.22"
MODEL_ZIP="$MODEL_NAME.zip"
MODEL_URL="https://alphacephei.com/vosk/models/$MODEL_ZIP"

mkdir -p "$ASSETS_DIR"

if [[ -d "$ASSETS_DIR/$MODEL_NAME" ]]; then
  echo "Model already installed at $ASSETS_DIR/$MODEL_NAME"
  exit 0
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

echo "Downloading $MODEL_URL ..."
curl -L -o "$TMP_DIR/$MODEL_ZIP" "$MODEL_URL"

echo "Unzipping to $ASSETS_DIR ..."
unzip -q "$TMP_DIR/$MODEL_ZIP" -d "$ASSETS_DIR"

echo "Done. Model installed at $ASSETS_DIR/$MODEL_NAME"
```

赋可执行权限并运行：

```bash
chmod +x scripts/install_vosk_model.sh
./scripts/install_vosk_model.sh
ls app/src/main/assets/vosk-model-small-cn-0.22
```

Expected: 列出 `am`、`conf`、`graph`、`ivector` 等目录。

- [ ] **Step 2.4: Sync + 构建**

在 Android Studio 里点 "Sync Now"，或命令行：

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`。APK 体积约 45MB（含模型）。

- [ ] **Step 2.5: 提交**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts scripts/install_vosk_model.sh
git commit -m "build: add Vosk, DataStore, MockK deps and model install script"
```

---

## Task 3：`Command` enum（TDD）

**Files:**
- Create：`app/src/main/java/com/shower/voicectrl/bus/Command.kt`

这个类只是个枚举，但写测试是为了后面扩展时有保护。

- [ ] **Step 3.1: 写失败测试**

Create `app/src/test/java/com/shower/voicectrl/bus/CommandTest.kt`:

```kotlin
package com.shower.voicectrl.bus

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandTest {
    @Test
    fun `Command enum has exactly the four expected values`() {
        val names = Command.entries.map { it.name }.toSet()
        assertEquals(setOf("NEXT", "PREV", "PAUSE", "UNMATCHED"), names)
    }

    @Test
    fun `fromKeyword maps known Chinese phrases`() {
        assertEquals(Command.NEXT, Command.fromKeyword("下一条"))
        assertEquals(Command.PREV, Command.fromKeyword("上一条"))
        assertEquals(Command.PAUSE, Command.fromKeyword("暂停"))
    }

    @Test
    fun `fromKeyword returns null for unknown`() {
        assertEquals(null, Command.fromKeyword("随便说一句"))
    }
}
```

- [ ] **Step 3.2: 跑测试确认失败**

```bash
./gradlew :app:testDebugUnitTest --tests "com.shower.voicectrl.bus.CommandTest"
```

Expected: 编译失败 `Unresolved reference: Command`。

- [ ] **Step 3.3: 实现**

Create `app/src/main/java/com/shower/voicectrl/bus/Command.kt`:

```kotlin
package com.shower.voicectrl.bus

enum class Command {
    NEXT,
    PREV,
    PAUSE,
    UNMATCHED;

    companion object {
        fun fromKeyword(text: String): Command? = when (text.trim()) {
            "下一条" -> NEXT
            "上一条" -> PREV
            "暂停" -> PAUSE
            else -> null
        }
    }
}
```

- [ ] **Step 3.4: 跑测试确认通过**

```bash
./gradlew :app:testDebugUnitTest --tests "com.shower.voicectrl.bus.CommandTest"
```

Expected: `BUILD SUCCESSFUL` + 3 tests passed.

- [ ] **Step 3.5: 提交**

```bash
git add app/src/main/java/com/shower/voicectrl/bus/Command.kt app/src/test/java/com/shower/voicectrl/bus/CommandTest.kt
git commit -m "feat(bus): add Command enum with keyword mapping"
```

---

## Task 4：`Debouncer`（TDD）

**Files:**
- Create：`app/src/main/java/com/shower/voicectrl/bus/Debouncer.kt`

"同一命令 800ms 内只触发一次"。纯函数 + 注入时钟，方便测。

- [ ] **Step 4.1: 写失败测试**

Create `app/src/test/java/com/shower/voicectrl/bus/DebouncerTest.kt`:

```kotlin
package com.shower.voicectrl.bus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebouncerTest {
    private var now = 0L
    private val clock: () -> Long = { now }

    @Test
    fun `first event of a key passes`() {
        val d = Debouncer(windowMs = 800, clock = clock)
        assertTrue(d.shouldEmit(Command.NEXT))
    }

    @Test
    fun `same key within window is dropped`() {
        val d = Debouncer(windowMs = 800, clock = clock)
        now = 0L; d.shouldEmit(Command.NEXT)
        now = 799L
        assertFalse(d.shouldEmit(Command.NEXT))
    }

    @Test
    fun `same key after window passes again`() {
        val d = Debouncer(windowMs = 800, clock = clock)
        now = 0L; d.shouldEmit(Command.NEXT)
        now = 801L
        assertTrue(d.shouldEmit(Command.NEXT))
    }

    @Test
    fun `different keys are independent`() {
        val d = Debouncer(windowMs = 800, clock = clock)
        now = 0L; assertTrue(d.shouldEmit(Command.NEXT))
        now = 10L; assertTrue(d.shouldEmit(Command.PREV))
        now = 20L; assertTrue(d.shouldEmit(Command.PAUSE))
    }
}
```

- [ ] **Step 4.2: 跑测试确认失败**

```bash
./gradlew :app:testDebugUnitTest --tests "com.shower.voicectrl.bus.DebouncerTest"
```

Expected: 编译失败 `Unresolved reference: Debouncer`。

- [ ] **Step 4.3: 实现**

Create `app/src/main/java/com/shower/voicectrl/bus/Debouncer.kt`:

```kotlin
package com.shower.voicectrl.bus

class Debouncer(
    private val windowMs: Long = 800,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val lastEmitAt = mutableMapOf<Command, Long>()

    @Synchronized
    fun shouldEmit(command: Command): Boolean {
        val now = clock()
        val last = lastEmitAt[command]
        if (last != null && now - last < windowMs) return false
        lastEmitAt[command] = now
        return true
    }
}
```

- [ ] **Step 4.4: 跑测试确认通过**

```bash
./gradlew :app:testDebugUnitTest --tests "com.shower.voicectrl.bus.DebouncerTest"
```

Expected: 4 tests passed.

- [ ] **Step 4.5: 提交**

```bash
git add app/src/main/java/com/shower/voicectrl/bus/Debouncer.kt app/src/test/java/com/shower/voicectrl/bus/DebouncerTest.kt
git commit -m "feat(bus): add per-command Debouncer"
```

---

## Task 5：`CommandBus`（TDD）

**Files:**
- Create：`app/src/main/java/com/shower/voicectrl/bus/CommandBus.kt`

基于 `MutableSharedFlow`，带 `extraBufferCapacity` 避免发射阻塞。

- [ ] **Step 5.1: 写失败测试**

Create `app/src/test/java/com/shower/voicectrl/bus/CommandBusTest.kt`:

```kotlin
package com.shower.voicectrl.bus

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommandBusTest {

    @Test
    fun `emit reaches a single subscriber`() = runTest {
        val bus = CommandBus()
        val collectJob = launch {
            val received = bus.events.take(2).toList()
            assertEquals(listOf(Command.NEXT, Command.PAUSE), received)
        }
        // give the collector a tick to subscribe
        kotlinx.coroutines.yield()
        bus.emit(Command.NEXT)
        bus.emit(Command.PAUSE)
        collectJob.join()
    }

    @Test
    fun `emit does not block when no subscribers`() = runTest {
        val bus = CommandBus()
        // should not throw / suspend forever
        repeat(8) { bus.emit(Command.NEXT) }
    }
}
```

- [ ] **Step 5.2: 跑测试确认失败**

```bash
./gradlew :app:testDebugUnitTest --tests "com.shower.voicectrl.bus.CommandBusTest"
```

Expected: 编译失败 `Unresolved reference: CommandBus`。

- [ ] **Step 5.3: 实现**

Create `app/src/main/java/com/shower/voicectrl/bus/CommandBus.kt`:

```kotlin
package com.shower.voicectrl.bus

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class CommandBus {
    private val _events = MutableSharedFlow<Command>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<Command> = _events.asSharedFlow()

    fun emit(command: Command) {
        _events.tryEmit(command)
    }

    companion object {
        val INSTANCE: CommandBus by lazy { CommandBus() }
    }
}
```

- [ ] **Step 5.4: 跑测试确认通过**

```bash
./gradlew :app:testDebugUnitTest --tests "com.shower.voicectrl.bus.CommandBusTest"
```

Expected: 2 tests passed.

- [ ] **Step 5.5: 提交**

```bash
git add app/src/main/java/com/shower/voicectrl/bus/CommandBus.kt app/src/test/java/com/shower/voicectrl/bus/CommandBusTest.kt
git commit -m "feat(bus): add CommandBus with SharedFlow fan-out"
```

---

## Task 6：`GestureConfig` 坐标计算（TDD）

**Files:**
- Create：`app/src/main/java/com/shower/voicectrl/accessibility/GestureConfig.kt`

百分比 → 绝对像素坐标，纯函数。

- [ ] **Step 6.1: 写失败测试**

Create `app/src/test/java/com/shower/voicectrl/accessibility/GestureConfigTest.kt`:

```kotlin
package com.shower.voicectrl.accessibility

import com.shower.voicectrl.bus.Command
import org.junit.Assert.assertEquals
import org.junit.Test

class GestureConfigTest {

    @Test
    fun `NEXT swipes from lower to upper center`() {
        val g = GestureConfig.default().toGesture(Command.NEXT, widthPx = 1260, heightPx = 2800)
        assertEquals(GestureType.SWIPE, g.type)
        assertEquals(630, g.startX)   // 50%
        assertEquals(2100, g.startY)  // 75%
        assertEquals(630, g.endX)
        assertEquals(700, g.endY)     // 25%
        assertEquals(150L, g.durationMs)
    }

    @Test
    fun `PREV swipes from upper to lower center`() {
        val g = GestureConfig.default().toGesture(Command.PREV, widthPx = 1260, heightPx = 2800)
        assertEquals(GestureType.SWIPE, g.type)
        assertEquals(630, g.startX)
        assertEquals(700, g.startY)   // 25%
        assertEquals(630, g.endX)
        assertEquals(2100, g.endY)    // 75%
    }

    @Test
    fun `PAUSE taps center`() {
        val g = GestureConfig.default().toGesture(Command.PAUSE, widthPx = 1260, heightPx = 2800)
        assertEquals(GestureType.TAP, g.type)
        assertEquals(630, g.startX)
        assertEquals(1400, g.startY)  // 50%
    }

    @Test
    fun `UNMATCHED has no gesture`() {
        val g = GestureConfig.default().toGesture(Command.UNMATCHED, widthPx = 1260, heightPx = 2800)
        assertEquals(GestureType.NONE, g.type)
    }
}
```

- [ ] **Step 6.2: 跑测试确认失败**

```bash
./gradlew :app:testDebugUnitTest --tests "com.shower.voicectrl.accessibility.GestureConfigTest"
```

Expected: 编译失败。

- [ ] **Step 6.3: 实现**

Create `app/src/main/java/com/shower/voicectrl/accessibility/GestureConfig.kt`:

```kotlin
package com.shower.voicectrl.accessibility

import com.shower.voicectrl.bus.Command

enum class GestureType { SWIPE, TAP, NONE }

data class Gesture(
    val type: GestureType,
    val startX: Int,
    val startY: Int,
    val endX: Int,
    val endY: Int,
    val durationMs: Long,
)

data class GestureConfig(
    val centerXPct: Float,     // 0f..1f
    val swipeTopYPct: Float,   // 向上滑的起点 Y（下一条从这里下方滑到这里）
    val swipeBottomYPct: Float,
    val tapYPct: Float,
    val swipeDurationMs: Long,
    val tapDurationMs: Long,
) {
    fun toGesture(command: Command, widthPx: Int, heightPx: Int): Gesture {
        val cx = (centerXPct * widthPx).toInt()
        val topY = (swipeTopYPct * heightPx).toInt()
        val botY = (swipeBottomYPct * heightPx).toInt()
        val tapY = (tapYPct * heightPx).toInt()
        return when (command) {
            Command.NEXT -> Gesture(GestureType.SWIPE, cx, botY, cx, topY, swipeDurationMs)
            Command.PREV -> Gesture(GestureType.SWIPE, cx, topY, cx, botY, swipeDurationMs)
            Command.PAUSE -> Gesture(GestureType.TAP, cx, tapY, cx, tapY, tapDurationMs)
            Command.UNMATCHED -> Gesture(GestureType.NONE, 0, 0, 0, 0, 0)
        }
    }

    companion object {
        fun default() = GestureConfig(
            centerXPct = 0.50f,
            swipeTopYPct = 0.25f,
            swipeBottomYPct = 0.75f,
            tapYPct = 0.50f,
            swipeDurationMs = 150L,
            tapDurationMs = 40L,
        )
    }
}
```

- [ ] **Step 6.4: 跑测试确认通过**

```bash
./gradlew :app:testDebugUnitTest --tests "com.shower.voicectrl.accessibility.GestureConfigTest"
```

Expected: 4 tests passed.

- [ ] **Step 6.5: 提交**

```bash
git add app/src/main/java/com/shower/voicectrl/accessibility/GestureConfig.kt app/src/test/java/com/shower/voicectrl/accessibility/GestureConfigTest.kt
git commit -m "feat(accessibility): add percentage-based GestureConfig"
```

---

## Task 7：提示音资源 + `SoundFeedback`

**Files:**
- Create：`app/src/main/res/raw/fb_next.ogg`、`fb_prev.ogg`、`fb_pause.ogg`、`fb_unmatched.ogg`
- Create：`app/src/main/java/com/shower/voicectrl/sound/SoundFeedback.kt`

短音素材：合成 4 段 100-200ms 的正弦波，不同频率 → ogg。

- [ ] **Step 7.1: 生成 4 个提示音**

需要 `ffmpeg`，没装的话：`brew install ffmpeg`。

```bash
cd /Users/kaifa/workspace/my-project/shower-voice-ctrl
mkdir -p app/src/main/res/raw
# next: 上扬双音 (C5 -> E5)
ffmpeg -y -f lavfi -i "sine=frequency=523:duration=0.08" -f lavfi -i "sine=frequency=659:duration=0.08" -filter_complex "[0][1]concat=n=2:v=0:a=1" -ar 44100 app/src/main/res/raw/fb_next.ogg
# prev: 下行双音 (E5 -> C5)
ffmpeg -y -f lavfi -i "sine=frequency=659:duration=0.08" -f lavfi -i "sine=frequency=523:duration=0.08" -filter_complex "[0][1]concat=n=2:v=0:a=1" -ar 44100 app/src/main/res/raw/fb_prev.ogg
# pause: 平稳中音 (A4)
ffmpeg -y -f lavfi -i "sine=frequency=440:duration=0.18" app/src/main/res/raw/fb_pause.ogg
# unmatched: 闷音 (低频短响 + 衰减)
ffmpeg -y -f lavfi -i "sine=frequency=180:duration=0.12" -af "afade=t=out:st=0:d=0.12" app/src/main/res/raw/fb_unmatched.ogg
ls app/src/main/res/raw/
```

Expected: 四个 `.ogg` 文件存在，每个 1-5KB。

- [ ] **Step 7.2: 实现 `SoundFeedback`**

Create `app/src/main/java/com/shower/voicectrl/sound/SoundFeedback.kt`:

```kotlin
package com.shower.voicectrl.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.shower.voicectrl.R
import com.shower.voicectrl.bus.Command

class SoundFeedback(context: Context) {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()

    private val ids: Map<Command, Int> = mapOf(
        Command.NEXT to pool.load(context, R.raw.fb_next, 1),
        Command.PREV to pool.load(context, R.raw.fb_prev, 1),
        Command.PAUSE to pool.load(context, R.raw.fb_pause, 1),
        Command.UNMATCHED to pool.load(context, R.raw.fb_unmatched, 1),
    )

    fun play(command: Command) {
        val id = ids[command] ?: return
        pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        pool.release()
    }
}
```

- [ ] **Step 7.3: 构建确认编译通过**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 7.4: 提交**

```bash
git add app/src/main/res/raw/ app/src/main/java/com/shower/voicectrl/sound/SoundFeedback.kt
git commit -m "feat(sound): add 4 feedback tones and SoundFeedback wrapper"
```

---

## Task 8：`VoskRecognizer` 封装 + androidTest

**Files:**
- Create：`app/src/main/java/com/shower/voicectrl/voice/VoskRecognizer.kt`
- Create：`app/src/androidTest/java/com/shower/voicectrl/voice/VoskRecognizerTest.kt`
- Create：`app/src/androidTest/assets/test_audio/next_clean.wav` 等（Task 8.1 生成）

Vosk 的 `Recognizer` 接收 PCM 字节流，产生 JSON。我们封装成 "喂字节 → 回调 Command"。

- [ ] **Step 8.1: 准备测试音频**

录 3 句你自己的声音作为基准样本（手机录音转 WAV 也可）。为了测试可复现，推荐用 TTS 生成：

macOS 自带 `say` 命令可以生成中文音频，但需要安装中文语音包（系统设置 → 辅助功能 → 朗读内容 → 系统语音 → 添加"Tingting"）。

```bash
mkdir -p app/src/androidTest/assets/test_audio
say -v Tingting -o /tmp/next.aiff --data-format=LEI16@16000 "下一条"
say -v Tingting -o /tmp/prev.aiff --data-format=LEI16@16000 "上一条"
say -v Tingting -o /tmp/pause.aiff --data-format=LEI16@16000 "暂停"
say -v Tingting -o /tmp/unmatched.aiff --data-format=LEI16@16000 "今天天气不错"
for f in next prev pause unmatched; do
  ffmpeg -y -i /tmp/$f.aiff -ar 16000 -ac 1 -sample_fmt s16 app/src/androidTest/assets/test_audio/${f}_clean.wav
done
ls app/src/androidTest/assets/test_audio/
```

Expected: 4 个 WAV 文件，16kHz 单声道。如果没装 Tingting 语音，手动在手机上录 4 段 mp3 → 用 `ffmpeg` 转 16kHz PCM WAV，放到同一路径。

- [ ] **Step 8.2: 实现 `VoskRecognizer`**

Create `app/src/main/java/com/shower/voicectrl/voice/VoskRecognizer.kt`:

```kotlin
package com.shower.voicectrl.voice

import android.content.Context
import com.shower.voicectrl.bus.Command
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 封装 Vosk：
 *   1. 首次使用前调 [prepare]（把 assets 里的模型解压到 filesDir）
 *   2. 喂 PCM 字节流给 [acceptPcm]，识别到关键词时调用回调
 *
 * 不处理麦克风采集，也不分发事件，保持单一职责。
 */
class VoskRecognizer private constructor(
    private val recognizer: Recognizer,
    private val onCommand: (Command) -> Unit,
) {

    fun acceptPcm(buffer: ShortArray, readSize: Int) {
        val hasResult = recognizer.acceptWaveForm(buffer, readSize)
        val json = if (hasResult) recognizer.result else recognizer.partialResult
        val text = JSONObject(json).optString(if (hasResult) "text" else "partial").trim()
        if (text.isEmpty()) return
        val cmd = Command.fromKeyword(text) ?: if (hasResult) Command.UNMATCHED else return
        onCommand(cmd)
    }

    fun close() {
        recognizer.close()
    }

    companion object {
        private const val MODEL_ASSETS_DIR = "vosk-model-small-cn-0.22"
        private const val GRAMMAR_JSON = "[\"下一条\",\"上一条\",\"暂停\",\"[unk]\"]"

        suspend fun create(
            context: Context,
            sampleRate: Int = 16_000,
            onCommand: (Command) -> Unit,
        ): VoskRecognizer {
            val model = loadModel(context)
            val recognizer = Recognizer(model, sampleRate.toFloat(), GRAMMAR_JSON)
            return VoskRecognizer(recognizer, onCommand)
        }

        private suspend fun loadModel(context: Context): Model =
            suspendCancellableCoroutine { cont ->
                StorageService.unpack(
                    context, MODEL_ASSETS_DIR, "model",
                    { model -> cont.resume(model) },
                    { e -> cont.resumeWithException(e) },
                )
            }
    }
}
```

- [ ] **Step 8.3: 写仪器测试**

Create `app/src/androidTest/java/com/shower/voicectrl/voice/VoskRecognizerTest.kt`:

```kotlin
package com.shower.voicectrl.voice

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shower.voicectrl.bus.Command
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class VoskRecognizerTest {

    private fun readWavPcm(name: String): ShortArray {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val stream = ctx.assets.open("test_audio/$name")
        DataInputStream(stream).use { dis ->
            // Skip 44-byte WAV header
            dis.skipBytes(44)
            val bytes = dis.readBytes()
            val shorts = ShortArray(bytes.size / 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer().get(shorts)
            return shorts
        }
    }

    private fun recognizeFile(fileName: String): List<Command> = runBlocking {
        val received = mutableListOf<Command>()
        val recognizer = VoskRecognizer.create(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ) { received += it }
        val pcm = readWavPcm(fileName)
        // feed in chunks of 1024 samples
        var i = 0
        while (i < pcm.size) {
            val size = minOf(1024, pcm.size - i)
            val chunk = pcm.copyOfRange(i, i + size)
            recognizer.acceptPcm(chunk, size)
            i += size
        }
        recognizer.close()
        Log.d("VoskTest", "recognized: $received")
        received
    }

    @Test fun recognizes_next() {
        assertTrue(recognizeFile("next_clean.wav").contains(Command.NEXT))
    }

    @Test fun recognizes_prev() {
        assertTrue(recognizeFile("prev_clean.wav").contains(Command.PREV))
    }

    @Test fun recognizes_pause() {
        assertTrue(recognizeFile("pause_clean.wav").contains(Command.PAUSE))
    }

    @Test fun unmatched_stays_unmatched_or_empty() {
        val cmds = recognizeFile("unmatched_clean.wav")
        assertTrue(cmds.isEmpty() || cmds.all { it == Command.UNMATCHED })
    }
}
```

- [ ] **Step 8.4: 跑仪器测试（需要手机连着）**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.shower.voicectrl.voice.VoskRecognizerTest"
```

Expected: 4 tests passed。第一次会把 40MB APK 安装到手机，可能慢。
如果 `unmatched_clean.wav` 测试失败（有时 Vosk KWS 会硬塞一个关键词），把断言放宽为"不抛异常即可"，但先按原样试。

- [ ] **Step 8.5: 提交**

```bash
git add app/src/main/java/com/shower/voicectrl/voice/VoskRecognizer.kt app/src/androidTest/
git commit -m "feat(voice): add VoskRecognizer wrapper + instrumented tests"
```

---

## Task 9：`ShowerAccessibilityService` + XML 配置

**Files:**
- Create：`app/src/main/java/com/shower/voicectrl/accessibility/ShowerAccessibilityService.kt`
- Create：`app/src/main/res/xml/accessibility_service_config.xml`
- Modify：`app/src/main/AndroidManifest.xml`

- [ ] **Step 9.1: 写无障碍服务配置 XML**

Create `app/src/main/res/xml/accessibility_service_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canPerformGestures="true"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="100"
    android:description="@string/accessibility_description"
    android:packageNames="com.ss.android.ugc.aweme" />
```

Add to `app/src/main/res/values/strings.xml`:

```xml
<string name="accessibility_description">在抖音里接收语音命令并模拟滑动 / 点击手势</string>
```

- [ ] **Step 9.2: 实现无障碍服务**

Create `app/src/main/java/com/shower/voicectrl/accessibility/ShowerAccessibilityService.kt`:

```kotlin
package com.shower.voicectrl.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.shower.voicectrl.bus.Command
import com.shower.voicectrl.bus.CommandBus
import com.shower.voicectrl.sound.SoundFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob

class ShowerAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var collectJob: Job? = null
    private lateinit var sound: SoundFeedback

    override fun onServiceConnected() {
        super.onServiceConnected()
        sound = SoundFeedback(applicationContext)
        collectJob = scope.launch {
            CommandBus.INSTANCE.events.onEach(::handleCommand).collect()
        }
        Log.i(TAG, "connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* unused */ }
    override fun onInterrupt() { /* unused */ }

    override fun onDestroy() {
        collectJob?.cancel()
        if (this::sound.isInitialized) sound.release()
        super.onDestroy()
    }

    private fun handleCommand(command: Command) {
        if (command == Command.UNMATCHED) {
            sound.play(Command.UNMATCHED)
            return
        }
        if (!isDouyinForeground()) {
            sound.play(Command.UNMATCHED)
            return
        }
        val metrics = resources.displayMetrics
        val gesture = GestureConfig.default().toGesture(command, metrics.widthPixels, metrics.heightPixels)
        if (gesture.type == GestureType.NONE) return

        val path = Path().apply {
            moveTo(gesture.startX.toFloat(), gesture.startY.toFloat())
            if (gesture.type == GestureType.SWIPE) {
                lineTo(gesture.endX.toFloat(), gesture.endY.toFloat())
            }
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, gesture.durationMs)
        val desc = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(desc, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) {
                sound.play(command)
            }
            override fun onCancelled(g: GestureDescription?) {
                Log.w(TAG, "gesture cancelled for $command")
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun isDouyinForeground(): Boolean {
        val pkg = rootInActiveWindow?.packageName?.toString()
        return pkg == DOUYIN_PACKAGE
    }

    companion object {
        private const val TAG = "ShowerAccess"
        private const val DOUYIN_PACKAGE = "com.ss.android.ugc.aweme"
    }
}
```

- [ ] **Step 9.3: 在 Manifest 注册服务**

Edit `app/src/main/AndroidManifest.xml`，在 `<application>` 内加入：

```xml
<service
    android:name=".accessibility.ShowerAccessibilityService"
    android:exported="true"
    android:label="@string/app_name"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

- [ ] **Step 9.4: 构建确认通过**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 9.5: 提交**

```bash
git add app/src/main/java/com/shower/voicectrl/accessibility/ShowerAccessibilityService.kt \
        app/src/main/res/xml/accessibility_service_config.xml \
        app/src/main/res/values/strings.xml \
        app/src/main/AndroidManifest.xml
git commit -m "feat(accessibility): add ShowerAccessibilityService with gesture dispatch"
```

---

## Task 10：`VoiceForegroundService`

**Files:**
- Create：`app/src/main/java/com/shower/voicectrl/voice/VoiceForegroundService.kt`
- Modify：`app/src/main/AndroidManifest.xml`

- [ ] **Step 10.1: 实现前台服务**

Create `app/src/main/java/com/shower/voicectrl/voice/VoiceForegroundService.kt`:

```kotlin
package com.shower.voicectrl.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.shower.voicectrl.MainActivity
import com.shower.voicectrl.R
import com.shower.voicectrl.bus.Command
import com.shower.voicectrl.bus.CommandBus
import com.shower.voicectrl.bus.Debouncer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class VoiceForegroundService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var captureJob: Job? = null
    private var recognizer: VoskRecognizer? = null
    private val debouncer = Debouncer()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundWithNotification()
        if (captureJob?.isActive != true) {
            captureJob = scope.launch { runCaptureLoop() }
        }
        return START_STICKY
    }

    private suspend fun runCaptureLoop() {
        recognizer = VoskRecognizer.create(applicationContext) { cmd ->
            if (debouncer.shouldEmit(cmd)) {
                CommandBus.INSTANCE.emit(cmd)
            }
        }

        val bufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(SAMPLE_RATE / 2)

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, bufSize
        )

        val buffer = ShortArray(1024)
        try {
            record.startRecording()
            while (scope.isActive) {
                val n = record.read(buffer, 0, buffer.size)
                if (n > 0) recognizer?.acceptPcm(buffer, n)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "capture loop crashed", t)
        } finally {
            try { record.stop() } catch (_: Throwable) {}
            record.release()
        }
    }

    override fun onDestroy() {
        captureJob?.cancel()
        recognizer?.close()
        recognizer = null
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundWithNotification() {
        ensureChannel()
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, VoiceForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("正在监听语音")
            .setContentText("说 \"下一条\" / \"上一条\" / \"暂停\"")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "停止", stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun ensureChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "语音监听", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        const val ACTION_STOP = "com.shower.voicectrl.STOP"
        private const val CHANNEL_ID = "voice_listen"
        private const val NOTIF_ID = 1001
        private const val SAMPLE_RATE = 16_000
        private const val TAG = "VoiceFgService"

        fun start(context: Context) {
            val intent = Intent(context, VoiceForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, VoiceForegroundService::class.java)
                .setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
```

- [ ] **Step 10.2: Manifest 加权限 + 服务声明**

Edit `app/src/main/AndroidManifest.xml`，在 `<manifest>` 顶层加：

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

在 `<application>` 内加：

```xml
<service
    android:name=".voice.VoiceForegroundService"
    android:exported="false"
    android:foregroundServiceType="microphone" />
```

- [ ] **Step 10.3: 构建验证**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 10.4: 提交**

```bash
git add app/src/main/java/com/shower/voicectrl/voice/VoiceForegroundService.kt \
        app/src/main/AndroidManifest.xml
git commit -m "feat(voice): add foreground service with AudioRecord capture loop"
```

---

## Task 11：主界面 UI + 权限 + 启停

**Files:**
- Create：`app/src/main/java/com/shower/voicectrl/ui/MainScreen.kt`
- Modify：`app/src/main/java/com/shower/voicectrl/MainActivity.kt`

- [ ] **Step 11.1: 写 `MainScreen` Composable**

Create `app/src/main/java/com/shower/voicectrl/ui/MainScreen.kt`:

```kotlin
package com.shower.voicectrl.ui

import androidx.compose.foundation.layout.*
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
```

- [ ] **Step 11.2: 实现 `MainActivity`**

Replace `app/src/main/java/com/shower/voicectrl/MainActivity.kt` (Android Studio 生成的内容丢弃):

```kotlin
package com.shower.voicectrl

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.shower.voicectrl.accessibility.ShowerAccessibilityService
import com.shower.voicectrl.ui.MainScreen
import com.shower.voicectrl.ui.MainUiState
import com.shower.voicectrl.voice.VoiceForegroundService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestMic = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* state will be recomputed on next render */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var micGranted by remember { mutableStateOf(isMicGranted()) }
            var accessibilityEnabled by remember { mutableStateOf(isAccessibilityEnabled()) }
            var listening by remember { mutableStateOf(false) }

            // 每 1s 轮询一次状态（简单可靠）
            androidx.compose.runtime.LaunchedEffect(Unit) {
                while (true) {
                    micGranted = isMicGranted()
                    accessibilityEnabled = isAccessibilityEnabled()
                    delay(1000)
                }
            }

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
                    listening = !listening
                },
            )
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
```

- [ ] **Step 11.3: 构建**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 11.4: 提交**

```bash
git add app/src/main/java/com/shower/voicectrl/ui/MainScreen.kt \
        app/src/main/java/com/shower/voicectrl/MainActivity.kt
git commit -m "feat(ui): add MainScreen with permission flow and start/stop toggle"
```

---

## Task 12：端到端手动验证

**Files:** 无

- [ ] **Step 12.1: 安装到手机**

```bash
./gradlew :app:installDebug
```

Expected: `Installed on 1 device`。

- [ ] **Step 12.2: 首次启动流程**

在 Vivo X200 Pro 上：
1. 打开 "shower-voice-ctrl" App
2. 点 "授予麦克风权限" → 系统弹窗点 "仅在使用时允许"
3. 回到 App，麦克风状态应变 "✓ 就绪"
4. 点 "去开启无障碍服务" → 系统无障碍列表中找到 "shower-voice-ctrl" → 打开开关
5. 回到 App，无障碍状态应变 "✓ 就绪"
6. "开始监听" 按钮变可点

- [ ] **Step 12.3: 基础命令验证（安静环境）**

1. 打开抖音 App，停在首页推荐 feed
2. 回 shower-voice-ctrl 点 "开始监听"
3. 通知栏应出现 "正在监听语音"
4. 打开抖音，对手机说 "下一条" → 抖音应上滑切视频 + 响起 "↑调" 提示音
5. 说 "上一条" → 下滑 + "↓调"
6. 说 "暂停" → 点击中央 + 平调
7. 说 "今天天气不错" → 闷音（未匹配）

如果识别率低于 80%，把 `Debouncer` 窗口降到 500ms 试试；或重录测试样本再评估。

- [ ] **Step 12.4: 浴室实测记录**

打开水 → 手机放洗手台上（距离 1 米内）→ 分别说每条命令 10 次 → 记下成功次数和误触发次数。

如果水声下成功率 <70%，可能的调参方向（后续迭代，不在本 MVP 硬改）：
- 把 Vosk grammar 加权重（给命令词更高置信度）
- `AudioRecord` 换 `VOICE_COMMUNICATION` 源（自带回声抑制 + 降噪）
- 减小 PCM 帧到 512 样本降低识别延迟

- [ ] **Step 12.5: 系统杀进程 / 自启动白名单**

OriginOS 默认会杀长时间前台服务。设置 → 应用与权限 → 应用管理 → shower-voice-ctrl：
- "自启动" 打开
- "后台运行" 允许
- "耗电管理" 设为 "允许后台高耗电"

再测 30 分钟持续监听，看通知栏是否消失。

- [ ] **Step 12.6: 提交验证结果**

把识别率、误触发率、长时间运行结果记到一个临时文件 `docs/verify/2026-04-17-mvp-verify.md`（结构自由），提交。

```bash
git add docs/verify/
git commit -m "docs: record MVP manual verification results"
```

---

## Task 13：README

**Files:**
- Create：`README.md`

- [ ] **Step 13.1: 写 README**

Create `README.md`:

```markdown
# shower-voice-ctrl

Android 应用：洗澡时用"下一条 / 上一条 / 暂停"语音命令控制抖音。

## 构建

1. 安装 Android Studio Ladybug+，Android SDK API 35
2. `./scripts/install_vosk_model.sh`（下载 Vosk 中文模型到 assets）
3. `./gradlew :app:installDebug`

## 使用

1. 首次启动：授予麦克风权限 + 启用无障碍服务
2. 打开抖音停在首页 feed
3. 回本 App 点 "开始监听"
4. 对手机说 "下一条" / "上一条" / "暂停"

## 设计文档

见 `docs/superpowers/specs/2026-04-17-shower-voice-ctrl-design.md`。
```

- [ ] **Step 13.2: 提交**

```bash
git add README.md
git commit -m "docs: add README with build and usage instructions"
```

---

## 完成标准（MVP 验收）

- 在 Vivo X200 Pro 上能装、能开、能走完权限流
- 抖音前台时 "下一条 / 上一条 / 暂停" 安静环境识别率 ≥ 95%
- 浴室环境（水声、1 米）识别率 ≥ 70%
- 每个命令都有对应反馈音
- 非抖音前台时不发手势（只播"未匹配"音）
- 连续监听 30 分钟不崩溃、前台通知不消失

---

## 已知未覆盖项（留给后续迭代）

- OriginOS 的白名单需要用户手动配置，暂不自动引导
- 手势坐标硬编码为 `GestureConfig.default()`，没做调试面板
- 未做功耗优化（30 分钟自动停止）
- 未做快手 / 视频号等其他短视频 App 适配
