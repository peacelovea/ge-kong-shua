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

### 2.4 确认抖音包名
```bash
adb shell "pm list packages" | grep aweme
```
应返回 `package:com.ss.android.ugc.aweme`。如果你的抖音是特殊版（极速版 / TV 版）包名不一样，需要在 `ShowerAccessibilityService.DOUYIN_PACKAGE` 里加/换。

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
2. 改 `ShowerAccessibilityService.DOUYIN_PACKAGE` → 改为 Set 或单独参数
3. 改 accessibility config XML `packageNames=` 加上新包名
4. 实测快手的滑动坐标是否和抖音一致（大概率一致，但可能底部导航区高度不同）
5. 如果手势需要差异化，把 `GestureConfig.default()` 改成按包名返回不同 config

更彻底的方案：把"目标 App + GestureConfig"抽成一个 `SupportedApp` 类，维护一个列表。

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
