# 开发与调试手册

> 把我们调试 MVP 期间积累的操作命令和踩坑经验沉淀在这里。下次改代码前先扫一眼。

## 0. 一次性环境搭建

### 0.1 macOS（本机已验证）
```bash
# Android Studio 自带 JDK 21 (JBR)，不用单独装 Java
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
```
写入 `~/.zshrc` 后 `source ~/.zshrc`，验证：
```bash
java -version   # openjdk 21.x
adb --version   # Android Debug Bridge 1.0.41
```

### 0.2 设备端（Vivo X200 Pro / OriginOS）
1. 设置 → 我的设备 → 版本信息 → 连点 "软件版本号" 7 次
2. 开发者选项 → 打开 `USB 调试` + `USB 安装`
3. USB 连接 Mac，手机弹"允许 USB 调试" → "始终允许"
4. `adb devices` 能看到一台 `device` 状态

### 0.3 Vosk 模型
模型 ~42MB 被 `.gitignore` 排除，克隆新机后必须跑：
```bash
./scripts/install_vosk_model.sh
```
脚本会下载并在 `assets/vosk-model-small-cn-0.22/` 生成 `uuid` 标记文件（这个文件官方 zip 不带，但 `StorageService.unpack()` 会读它，缺就崩）。

---

## 1. 日常开发命令

### 1.1 构建
```bash
./gradlew :app:assembleDebug                # 构建 debug APK
./gradlew :app:testDebugUnitTest            # 跑单元测试
./gradlew :app:lint                         # lint 检查
```
APK 产物：`app/build/outputs/apk/debug/app-debug.apk` (~45MB)

### 1.2 安装到手机
**不要**用 `./gradlew :app:installDebug`——Vivo USB 安装权限对话框超时非常快（~3 秒），gradle 的流程会被拒。用：
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
`-r` 是重装（保留权限授权，包括无障碍服务），不会出现"重复安装"。
只有跨 major 版本或签名变了才用 `adb uninstall com.shower.voicectrl` 先卸。

### 1.3 装完的典型节奏
```bash
./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```
一行搞定 build + install。

---

## 2. 调试 Recipe

### 2.1 查前台服务是否活着
```bash
adb shell "pidof com.shower.voicectrl"                  # 看进程号
adb shell "dumpsys activity services com.shower.voicectrl" | head -40
```
关键字段：
- `isForeground=true` — 服务挂在前台
- `foregroundId=1001` — 我们的通知 ID
- `types=0x00000080` — microphone 类型

### 2.2 抓 logcat 日志

**基础**（看所有日志）：
```bash
PID=$(adb shell "pidof com.shower.voicectrl" | tr -d '\r')
adb logcat -d --pid=$PID -t 500
```

**按我们自定义的 tag 过滤**：
```bash
adb shell "logcat -d -v time -t 2000" | grep -E "VoskRecognizer|ShowerAccess|SoundFeedback"
```

**实时抓一段时间**（比如你说命令时）：
```bash
adb shell "logcat -c"   # 清缓冲区
(adb logcat -v time > /tmp/voicectrl.log 2>&1 & LPID=$!; sleep 20; kill $LPID)
grep -E "VoskRecognizer|ShowerAccess" /tmp/voicectrl.log
```

**注意**：macOS 上没有 `timeout` 命令；用 `(&; sleep; kill)` 模式代替。

### 2.3 自定义 tag 速查
| Tag | 来源 | 作用 |
|---|---|---|
| `VoskRecognizer` | `VoskRecognizer.kt` | 每次 final 识别的 raw/text |
| `ShowerAccess` | `ShowerAccessibilityService.kt` | 每次 command 分发（含前台 App） |
| `VoiceFgService` | `VoiceForegroundService.kt` | 录音循环异常 |
| `VoskAPI` | Vosk 库内部 | 模型加载、grammar 构建（启动时会列出 vocab 丢失） |
| `SoundFeedback` | `SoundFeedback.kt` | 历史遗留（已废），只有 play 失败才会出现 |

### 2.4 确认目标 App 包名
```bash
adb shell "pm list packages" | grep aweme
```
抖音应返回 `package:com.ss.android.ugc.aweme`。如果是其他短视频 App 或特殊版本，先查到真实包名，再确认它已在 `SupportedApp.DEFAULT_APPS` 和 `accessibility_service_config.xml` 的 `packageNames` 中。

### 2.5 手动触发 Service 停止
```bash
adb shell am stopservice com.shower.voicectrl/.voice.VoiceForegroundService
```

---

## 3. 已知平台坑

### 3.1 Vivo USB 安装权限超时
**现象**：`./gradlew :app:installDebug` 或 IDE Run 点下去，手机弹"是否允许 USB 安装"对话框，10-15 秒不点就自动拒绝，gradle 报 `INSTALL_FAILED_ABORTED: User rejected permissions`。
**解决**：用 `adb install -r` 命令行装，弹框出现时手快一点点"安装"。
**根治**：设置 → 安全 → 更多安全设置 → 安装未知应用 → 给本 App 打勾（可能需要打开好几层）。

### 3.2 OriginOS 后台杀进程
**现象**：监听开着，手机锁屏放置 10-30 分钟后通知栏"隔空刷 · 监听中"消失。
**解决**：设置 → 应用与权限 → 应用管理 → 隔空刷：
- `自启动` 开启
- `后台运行` 允许
- `耗电管理` 改 `允许后台高耗电`

### 3.3 Vosk 模型缺 `uuid` 文件
**现象**：`FileNotFoundException: vosk-model-small-cn-0.22/uuid`，App 启动就闪退。
**解决**：跑 `./scripts/install_vosk_model.sh` 重新生成模型目录（脚本里 `echo "..." > uuid`）。

### 3.4 抖音视频外放时识别率低
**现象**：抖音视频在放，说"下一条"识别成 `[unk]`。
**原因**：麦克风收到用户 + 抖音双路声音。
**解决**：代码已用 `VOICE_COMMUNICATION` 源 + AEC + NS，如果还不行：
- 手机离人近一点（≤1m）
- 手机不要紧贴喇叭
- 说话略大声一点

### 3.5 Vosk 中文小模型按单字索引
**现象**：grammar 里写 `"下一条"` → `Ignoring word missing in vocabulary`
**解决**：grammar 必须写空格分隔的单字 `"下 一 条"`。识别结果也是单字带空格，匹配前 `replace(" ", "")`。

---

## 4. 扩展操作

### 4.1 加一个新的语音命令（如 "点赞"）
1. 改 `Command.kt` 枚举，加 `LIKE`
2. 改 `Command.fromKeyword()`，加 `t.contains("点赞") -> LIKE`
3. 改 `VoskRecognizer.GRAMMAR_JSON`，加 `"点 赞"`
4. 改 `GestureConfig.toGesture()`，加 `LIKE -> Gesture(...)`（手势是双击屏幕中央 / 点爱心按钮，看你想怎么映射）
5. 改 `ShowerAccessibilityService.labelOf()` 和 `iconResOf()`，加新命令的展示
6. 单测更新
7. 手工验证

### 4.2 适配一个新的 App（如快手）
1. 查包名：`adb shell "pm list packages" | grep kuaishou` → `com.smile.gifmaker`
2. 在 `SupportedApp.DEFAULT_APPS` 里增加包名、显示名称，必要时加专属 `GestureConfig`
3. 在 `app/src/main/res/xml/accessibility_service_config.xml` 的 `packageNames` 中加上新包名
4. 如果 App 内做了启用/禁用 UI，同步检查 `AppConfig.enabledAppPackages` 是否包含它
5. 实测滑动坐标是否和现有 App 一致；底部导航区高度不同的 App 可能需要专属坐标
6. 给 `SupportedApp` / 手势映射补单测

### 4.3 查看构建出的 APK 大小
```bash
ls -lh app/build/outputs/apk/debug/app-debug.apk
```
典型 ~45MB，其中 ~42MB 是 Vosk 模型 assets。

### 4.4 Clean 构建
```bash
./gradlew clean
```
不要手删 `build/`，偶尔 gradle 元数据会错乱。

---

## 5. 发版（发布到 GitHub Releases）

### 5.0 一次性准备（已完成，作存档）

- **Release keystore**：`~/keystores/ge-kong-shua.jks`
  - **绝对不能丢**，丢了等于这个 App 再也无法发布更新（老用户装不上新版）
  - 建议至少**两处备份**：本地 + 密码管理器（1Password/Bitwarden）加密附件
- **签名密码**存在 `~/.gradle/gradle.properties`（仓库外，不进 git）：
  ```properties
  GEKONG_STORE_FILE=/Users/kaifa/keystores/ge-kong-shua.jks
  GEKONG_STORE_PASSWORD=<填>
  GEKONG_KEY_ALIAS=ge-kong-shua
  GEKONG_KEY_PASSWORD=<填>
  ```
- 换电脑时把 `.jks` 拷过去 + 在新机 `gradle.properties` 重填路径和密码即可

### 5.1 发版 Checklist（每次发版跑一遍）

以 `v0.1.1` 为例：

```bash
# 1. 确认 main 是干净的
git status                              # 应显示 nothing to commit

# 2. 改版本号
#    编辑 app/build.gradle.kts：
#      versionCode 1 → 2
#      versionName "0.1.0" → "0.1.1"
#    写 RELEASE_NOTES_v0.1.1.md

# 3. 单测
./gradlew :app:testDebugUnitTest        # 全绿

# 4. 构建 release APK
./gradlew :app:assembleRelease
ls -lh app/build/outputs/apk/release/app-release.apk   # 看大小

# 5. 签名校验（可选但推荐）
APKSIGNER=$(find $ANDROID_HOME/build-tools -name apksigner | sort -r | head -1)
$APKSIGNER verify --verbose app/build/outputs/apk/release/app-release.apk | head -3
# 期望：Verifies / Verified using v2 scheme: true

# 6. 真机安装测一下别让老用户装了崩掉
adb install -r app/build/outputs/apk/release/app-release.apk
# 手动验证核心流程

# 7. 提交版本号和 release notes
git add app/build.gradle.kts RELEASE_NOTES_v0.1.1.md
git commit -m "release: v0.1.1"
git push

# 8. 打 tag
git tag -a v0.1.1 -m "v0.1.1"
git push origin v0.1.1

# 9. 发 GitHub Release
gh release create v0.1.1 \
  "app/build/outputs/apk/release/app-release.apk#隔空刷-v0.1.1.apk" \
  --title "隔空刷 v0.1.1" \
  --notes-file RELEASE_NOTES_v0.1.1.md
```

### 5.2 常见踩坑

| 错误 | 原因 | 解决 |
|---|---|---|
| `Keystore file '...' not found` | `gradle.properties` 里路径或值末尾有空格 | `build.gradle.kts` 已 `.trim()`；检查 `gradle.properties` 行末 |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE: ... signatures do not match` | release APK 的签名和手机上已装版本不匹配 | 说明之前装的是 debug 版（用的 `~/.android/debug.keystore`），先 `adb uninstall com.shower.voicectrl` 再装 release |
| `Version code X is already in use` | 忘了把 `versionCode` 加 1 | 改 `build.gradle.kts` 的 `versionCode` |
| APK 体积异常大 | 没加 `abiFilters`，x86/x86_64 被打进去 | 检查 `build.gradle.kts` 的 `defaultConfig.ndk.abiFilters` |

### 5.3 回滚（万一发了个崩的版本）

不能删 Release（用户已下载）；只能发一个修复版 `v0.1.2`，然后在 `v0.1.1` 页面加 "⚠️ 有严重 bug，建议升级到 v0.1.2" 的说明。

```bash
gh release edit v0.1.1 --notes "$(cat <<'EOF'
⚠️ **此版本有严重 bug，请升级到 v0.1.2**

[原 release notes]
EOF
)"
```
