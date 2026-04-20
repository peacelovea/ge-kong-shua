# 隔空刷（shower-voice-ctrl）设计文档

**日期**：2026-04-17（初稿），2026-04-18（As-built 更新）
**作者**：shiyubo.code@gmail.com（头脑风暴：Claude Opus 4.7）
**目标平台**：Android（Vivo X200 Pro / OriginOS）
**品牌名**：隔空刷（代码仓库名 `shower-voice-ctrl` 保留为内部标识）
**状态**：MVP 已上机验证通过，可日常使用

> 本文档在 MVP 完成后做了 as-built 更新，原始决策以正文描述，实施过程中偏离的地方在 §12 列出变更记录。

---

## 1. 背景与目标

### 1.1 痛点
用户刷抖音时启用了自动连播，遇到"不喜欢且时长较长的视频"想跳过——自动连播需等视频播完才切下一条，而手常常被其它事占着，没法或不想碰屏幕。

原始触发场景是**洗澡**（湿手），但痛点本质是"**手不空 / 不方便触屏**"，在大量日常场景通用：
- 洗澡（湿手）
- 做饭（油手 / 面粉 / 端锅）
- 吃饭（筷子、饭盒占手）
- 护肤 / 化妆（满脸乳液）
- 哺乳 / 抱娃（单手甚至无手）
- 健身 / 拉伸 / 平板支撑（手支地）
- 躺床上懒得伸手

### 1.2 目标
开发一个 Android 应用，在上述"手不方便"场景下通过**语音指令**控制抖音的三个基本操作：**下一条 / 上一条 / 暂停**。

洗澡是首个验证场景（水声 + 距离 + 外放干扰是最难的综合环境），其它场景难度都更低，一般能用即代表全场景能用。

### 1.3 非目标（YAGNI）
- 唤醒词机制
- 快手 / 小红书 / 视频号 / B 站 等其他 App 适配
- 云端识别、网络依赖功能
- 点赞 / 关注 / 音量 / 评论 等扩展命令
- 命令历史、统计、视频内容理解

---

## 2. 关键设计决策

| 决策点 | 选择 | 核心理由 |
|---|---|---|
| 平台 | 仅 Android | iOS 沙箱无法控制其他 App |
| 命令集 | 下一条 / 上一条 / 暂停 | 核心痛点即"跳过"；命令越少误识越低 |
| 激活模式 | 会话模式 + 模糊关键词匹配 | 避免唤醒词的双音节成本；包含 "下一"/"下条" 等变体以容忍 Vosk 漏字（§12.2） |
| 识别引擎 | Vosk（开源离线 KWS） | 纯本地、低延迟、命令词场景天然契合 |
| 录音源 | `VOICE_COMMUNICATION` + AEC + NS | 抖音外放时声学回声干扰麦克风，此源启用回声消除/降噪（§12.3） |
| App 范围 | 仅抖音（`com.ss.android.ugc.aweme`） | MVP 最小化；配置化为未来扩展留口 |
| 反馈机制 | iOS 风格顶部横幅（`TYPE_ACCESSIBILITY_OVERLAY`） | 提示音在浴室水声下仍难辨，改为视觉反馈更直观；用户可见矢量图标 + 颜色区分（§12.4） |

---

## 3. 整体架构

```
┌─────────────────────────────────────────────────────────┐
│                 用户（浴室，说话）                          │
└───────────────────────┬─────────────────────────────────┘
                        │ 声音
                        ▼
┌─────────────────────────────────────────────────────────┐
│       Android 设备（Vivo X200 Pro / OriginOS）           │
│                                                          │
│  ┌────────────┐   PCM    ┌──────────────┐  命令事件       │
│  │ 麦克风采集  │ ───────► │ Vosk 识别引擎 │ ──────────┐   │
│  │(AudioRecord│          │  (KWS 模式)   │           │   │
│  │ in FG Svc) │          └──────────────┘           ▼   │
│  └────────────┘                                ┌────────┴────┐
│        ▲                                       │ CommandBus  │
│        │                                       │ (SharedFlow)│
│  ┌─────┴──────┐                                └──────┬──────┘
│  │ MainActivity│                                      │
│  │（开始/停止）│                                       │
│  └────────────┘              ┌────────────────────────┤
│                              ▼                        ▼
│                    ┌──────────────────────────────────────┐
│                    │       ShowerAccessibilityService      │
│                    │  · 上滑/下滑/点击 (dispatchGesture)   │
│                    │  · 顶部反馈横幅 (OVERLAY + 矢量图标)    │
│                    └────────┬─────────────────────────────┘
│                             │
│                             ▼
│                         抖音 App
└─────────────────────────────────────────────────────────┘
```

**三条核心链路：**
1. **音频采集链**：前台 Service 持续录音（`VOICE_COMMUNICATION` + AEC + NS）→ Vosk KWS → 发出命令事件
2. **指令执行链**：命令事件 → 无障碍服务注入手势 → 抖音响应
3. **反馈链**：命令事件 → 无障碍服务在屏幕顶部弹出 iOS 风格横幅（命令名 + 彩色矢量图标）

---

## 4. 组件设计

### 4.1 `MainActivity`
唯一的 UI 页面。

**首次启动两步引导：**
1. 请求麦克风权限（`RECORD_AUDIO`）
2. 引导跳转系统无障碍服务页（`Settings.ACTION_ACCESSIBILITY_SETTINGS`）启用 `ShowerAccessibilityService`

**主界面：**
- 一个大按钮：`开始监听 / 停止监听`
- 一个状态行：显示"Vosk 已就绪 / 前台是否抖音 / 无障碍服务是否启用"

### 4.2 `VoiceForegroundService`
前台服务，负责录音 + 识别。

- **启动**：`MainActivity` 点"开始"后通过 `startForegroundService()` 启动
- **通知**：常驻通知，标题"隔空刷 · 监听中"，带"停止"按钮
- **录音参数**：`AudioRecord`，源 `VOICE_COMMUNICATION`（含 AEC + NS），16kHz / 单声道 / PCM 16-bit，1024 样本一帧
- **识别**：Vosk 中文小模型 `vosk-model-small-cn-0.22`，grammar 用**空格分隔的单字**：`["下 一 条","上 一 条","暂 停","[unk]"]`（§12.2）
- **结果处理**：只处理 `acceptWaveForm` 返回 true 的 final 结果，忽略 partial（防止识别过程中重复触发）
- **文本规整**：去空格 + 过滤 `[unk]` 片段后交给 `Command.fromKeyword()` 做模糊匹配
- **防抖**：同一命令 800ms 内只触发一次
- **事件发布**：
  - 识别到匹配词 → `CommandBus.emit(Command.NEXT / PREV / PAUSE)`
  - 识别到纯噪音或非命令 → `CommandBus.emit(Command.UNMATCHED)`
- **生命周期**：`START_STICKY`；停止监听时释放 `AudioRecord` 和 `Recognizer`
- **模型包结构要求**：Vosk `StorageService.unpack()` 需要 assets 模型根目录含 `uuid` 文件做版本比对，官方 zip 不带，由 `scripts/install_vosk_model.sh` 生成（§12.1）

### 4.3 `ShowerAccessibilityService`
无障碍服务，负责手势注入 + 视觉反馈。

- 订阅 `CommandBus`
- 收到命令时先查 `rootInActiveWindow.packageName`
  - 不是 `com.ss.android.ugc.aweme` → 弹 "未匹配（非抖音前台）" 横幅并返回
- 手势（基于屏幕尺寸按百分比计算，支持竖屏）：
  - **下一条**：从 `(50%, 75%)` 滑到 `(50%, 25%)`，时长 150ms
  - **上一条**：从 `(50%, 25%)` 滑到 `(50%, 75%)`，时长 150ms
  - **暂停**：单击 `(50%, 50%)`
- 使用 `dispatchGesture(GestureDescription, callback, handler)` 执行
- **手势参数可配置**：通过 `DataStore` 存储坐标百分比 / 时长，实测后可在调试页调整（MVP 阶段硬编码）
- **视觉反馈**：在屏幕顶部弹出叠层横幅（§4.5）

### 4.4 `CommandBus`
单例对象（Kotlin `class` + `companion object { val INSTANCE }`）。

- 内部：`MutableSharedFlow<Command>(replay = 0, extraBufferCapacity = 8, onBufferOverflow = DROP_OLDEST)`
- 订阅者：`ShowerAccessibilityService` 在 `onServiceConnected()` 启动 coroutine 订阅
- 扩展性：未来新增 "日志记录" / "替换识别引擎" 只需加 / 改订阅者，其余组件不动

### 4.5 视觉反馈横幅（替代原设计的提示音）
- 在 `ShowerAccessibilityService` 内用 `WindowManager` + `TYPE_ACCESSIBILITY_OVERLAY` 盖在抖音上方
- 位置：屏幕顶部居中，`y = 56dp` 避开状态栏 / 刘海
- 外观：圆角 22dp 深色卡片 + 细白边框；带矢量图标（白色图符 + 命令对应 iOS 颜色的圆底）
- 命令与颜色：下一条 / 绿 `#34C759`，上一条 / 蓝 `#0A84FF`，暂停 / 橙 `#FF9F0A`，未匹配 / 灰 `#8E8E93`
- 动画：淡入 180ms + 轻微下移；停留 1200ms；淡出 140ms
- 资源：`app/src/main/res/drawable/ic_chevron_down.xml` 等四个矢量 drawable

---

## 5. 数据流

```
[麦克风帧] → [Vosk Recognizer.acceptWaveForm]
                │
                │ 仅在返回 true（final result）时处理
                ▼
          [取 result.text，去空格 & 去 [unk]]
                │
                ├── Command.fromKeyword 匹配成功（NEXT/PREV/PAUSE）
                │       │
                │       ▼
                │   [Debouncer 800ms] ──► CommandBus.emit(cmd)
                │                              │
                │                              ▼
                │                       ShowerAccessibilityService
                │                         ├─ 检查前台 == 抖音
                │                         ├─ dispatchGesture
                │                         └─ 弹顶部反馈横幅
                │
                └── 匹配失败
                        └── CommandBus.emit(UNMATCHED)
                              └── 仅弹 "未匹配" 横幅（不发手势）
```

---

## 6. 错误处理与边界情况

| 场景 | 处理 |
|---|---|
| 麦克风权限被拒 | "开始"按钮置灰，点击提示去设置授权 |
| 无障碍服务未开启 | 开始监听时检测未启用 → 弹提示 + 一键跳设置 |
| Vosk 模型加载失败 | 前台服务启动失败，通知栏显示"模型加载失败"，不进入监听 |
| 用户不在抖音前台 | 无障碍服务判定 `packageName != 抖音` → 弹 "未匹配（非抖音前台）" 横幅，不滑 |
| 抖音在非视频页面（评论 / 个人页） | MVP 不区分，照常发手势；用户自己知道要回主 feed |
| 系统杀掉前台服务 | 通知栏消失 = 监听已停；依赖前台服务 + `START_STICKY`，首次引导提示加入系统自启动白名单 |
| 误识别（水声、哼歌） | Vosk 词表限定 + 800ms 防抖；实测误触发率高再调整 VAD 阈值 |
| 连续命令并发 | `SharedFlow` 串行消费；执行中的手势（~200ms）期间忽略新命令 |
| 电量消耗 | MVP 不做优化，前台服务 + 持续录音预估每小时 5-8% 电量；后续可加"30 分钟无指令自动停止" |

---

## 7. 测试策略

### 7.1 单元测试
- `CommandBus` 的发射与订阅顺序
- 防抖逻辑：800ms 内第二次同命令被丢弃
- 前台 App 检测：mock `AccessibilityNodeInfo`，确认非抖音时不触发手势

### 7.2 仪器测试（androidTest）
- Vosk 识别器：喂预录的 3 条命令 WAV，确认输出正确
- 手势注入：在测试 Activity 上验证 `dispatchGesture` 被调用且坐标符合百分比

### 7.3 人工验收（必须）
自动化测试验证代码正确性，真正的成功指标只能靠实测。

| 场景 | 指标 |
|---|---|
| 安静环境，手机 1 米内 | 3 个命令各 10 次，成功率 ≥ 95% |
| 浴室水开着，手机 1 米内 | 3 个命令各 10 次，记录成功率和误触发率 |
| 连续长时运行 | 监听 30 分钟不崩溃 / 不被杀 |

---

## 8. 已知风险 / 待实测项

1. **OriginOS 的后台限制**：Vivo 系统可能在长时间录音 / 锁屏 / 后台时强杀前台服务或无障碍服务。需实测；预案是引导用户加入自启动白名单、关闭电池优化。
2. **手势坐标微调**：抖音版本不同、底部导航条差异，`50%, 75%` 可能不是最优起点。MVP 先用百分比硬编码，后续做调试开关。
3. **水声下 Vosk KWS 准确率**：未知，若实测不达预期，备选路径是换成讯飞命令词识别（离线 SDK）。

---

## 9. MVP 交付物（验收口径）

- 一个可安装的 APK（as-built：~45MB，含 Vosk 中文模型）
- 首次启动能走完两步授权
- 点"开始监听"后，在抖音首页说"下一条" / "上一条" / "暂停"，分别触发对应手势 + 顶部横幅反馈
- 浴室实测成功率 ≥ 70%（MVP 门槛；正式版目标 ≥ 90%）

**as-built 状态**：在 Vivo X200 Pro / OriginOS / 抖音外放条件下，安静环境 ≥ 90%，抖音外放开启 ≥ 70%（已通过 `VOICE_COMMUNICATION` + AEC/NS + 模糊匹配打到这个水平）。真实浴室长时数据待用户记录。

---

## 10. 技术栈

| 层 | 选型（as-built） |
|---|---|
| 语言 | Kotlin 2.2.10 |
| 构建 | Gradle 9.x + Kotlin DSL（KTS），Android Gradle Plugin 9.1.1 |
| SDK 范围 | `minSdk 28`（Android 9）/ `targetSdk 36`（Android 16） |
| UI | Jetpack Compose + Material 3（Compose BOM 2026.02.01） |
| 并发 | Kotlin Coroutines 1.9.0 + `MutableSharedFlow` |
| 音频采集 | AOSP `AudioRecord`，源 `VOICE_COMMUNICATION`（16kHz / 单声道 / PCM 16-bit），启用 `AcousticEchoCanceler` + `NoiseSuppressor` |
| 语音识别 | `com.alphacephei:vosk-android:0.3.47` + `vosk-model-small-cn-0.22` 模型（打包进 `assets/`，含脚本生成的 `uuid` 标记文件） |
| 无障碍 | AOSP `AccessibilityService` + `dispatchGesture` + `TYPE_ACCESSIBILITY_OVERLAY` |
| 反馈 UI | `WindowManager` 叠层 + 矢量图标 drawable + Android 动画（无需 `SYSTEM_ALERT_WINDOW` 权限） |
| 存储 | Jetpack `DataStore (Preferences)` 1.1.1（当前 MVP 未启用，保留依赖以便后续调参落盘） |
| DI | 不引入（手动 `class` + `companion object { val INSTANCE }`） |
| 日志 | `android.util.Log` |
| 单元测试 | JUnit4 + MockK（14 个测试） |
| 仪器测试 | AndroidX Test + Compose UI Test（Vosk 识别测试已写但受 Vivo USB 安装权限影响未执行） |

**权限清单（`AndroidManifest.xml`）：**
- `RECORD_AUDIO`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_MICROPHONE`（Android 14+ 强制）
- 声明 `BIND_ACCESSIBILITY_SERVICE`（仅 meta-data，真正启用靠用户在系统设置里手动打开）

**开发环境要求：**
- macOS（用户实际环境）+ Android Studio Ladybug 或更新版本（自带 JDK 21、Android SDK Manager、ADB、Gradle wrapper 支持）
- Android SDK Platform API 35 + Build-Tools 35 + Platform-Tools
- Vivo X200 Pro 开启开发者选项 + USB 调试

## 11. 目录结构约定

```
~/workspace/my-project/shower-voice-ctrl/
├── app/                                # Android 工程根
├── docs/
│   └── superpowers/
│       ├── specs/
│       │   └── 2026-04-17-shower-voice-ctrl-design.md   # 本文档
│       └── plans/
│           └── 2026-04-17-shower-voice-ctrl.md           # 实施计划
├── scripts/
│   └── install_vosk_model.sh           # 下载 Vosk 模型并生成 uuid 标记
└── README.md
```

---

## 12. As-built 变更记录（实施期间的设计偏离）

> 初稿设计与实施中发现的现实之间的差距。保留在这里供未来回顾和复用类似项目时避坑。

### 12.1 Vosk 模型需要 `uuid` 标记文件
**现象**：首次启动闪退，日志 `FileNotFoundException: vosk-model-small-cn-0.22/uuid`
**原因**：`org.vosk.android.StorageService.unpack()` 从 assets 解包到 filesDir 前要先读取根目录的 `uuid` 文件做版本比对；官方 zip 不带此文件。
**解决**：在 `scripts/install_vosk_model.sh` 里解压后 `echo "shower-voice-ctrl-v1" > <model>/uuid` 生成一个固定标记。

### 12.2 Vosk 中文小模型按**单字**索引，grammar 必须空格分隔
**现象**：`Ignoring word missing in vocabulary: '下一条'`，识别始终返回 `[unk]`
**原因**：`vosk-model-small-cn-0.22` 的 lexicon 里没有多字词，只有单个汉字
**解决**：grammar 改成 `["下 一 条", "上 一 条", "暂 停", "[unk]"]`，识别结果里单字之间会带空格，处理时 `replace(" ", "")` 归一化再匹配。

### 12.3 抖音外放时 `VOICE_RECOGNITION` 源抢麦严重
**现象**：抖音视频在播，麦克风把抖音声音也收进去，Vosk 识别结果大量变 `[unk]`
**原因**：`VOICE_RECOGNITION` 源不做回声消除
**解决**：改用 `VOICE_COMMUNICATION` 源（专为"自放声+自收声"的 VoIP 场景设计），并显式创建 `AcousticEchoCanceler` 和 `NoiseSuppressor` 作用于 `audioSessionId`。

### 12.4 提示音反馈失效，改为视觉叠层
**初始方案**：`SoundPool` 预加载 4 个 ogg 文件，识别后播短音
**现象**：`SoundPool.play soundID X not READY` 日志频繁出现；退化到 `ToneGenerator` 后虽能响，但用户反馈"反馈音不明显"
**解决**：完全移除声音反馈；改为在屏幕顶部用 `WindowManager.addView(..., TYPE_ACCESSIBILITY_OVERLAY)` 弹 iOS 风格横幅（卡片 + 矢量图标 + 颜色区分 + 淡入淡出动画）。优点：
- 无需权限（`TYPE_ACCESSIBILITY_OVERLAY` 是无障碍服务专属，不要 `SYSTEM_ALERT_WINDOW`）
- 加载无延迟（构造即可用，相较 `SoundPool` 的异步加载）
- 视觉比听觉在安静环境下更清晰（浴室场景虽然看不见，但调试阶段极有价值）

### 12.5 只处理 final 结果，忽略 partial
**现象**：用户说一次"下一条"，抖音滑了两次
**原因**：Vosk 在说话过程中发 partial 结果，说完再发 final，两次都匹配上 NEXT，但间隔超过 800ms 防抖窗口
**解决**：在 `VoskRecognizer.acceptPcm` 里只处理 `acceptWaveForm(...)` 返回 `true` 的 final 结果。

### 12.6 模糊匹配代替严格匹配
**现象**：Vosk 偶尔漏识别 "下一条" 里的 "一"，输出 "下 条"
**解决**：`Command.fromKeyword` 改为模糊匹配——包含 `下一` 或 `下条` 即算 NEXT，对称处理 `上一` / `上条` / `暂停`。同时先把 `[unk]` 片段剥掉再匹配。

### 12.7 品牌命名
原 App 名 `shower-voice-ctrl` → 正式中文名 **"隔空刷"**（取"隔空投送"的语义 + "刷视频"的动作）。仅改用户可见字符串（`app_name`、主界面标题、前台通知标题），不动 Android 包名 `com.shower.voicectrl` 和仓库名。

### 12.8 技术栈版本跳跃
初稿计划 AGP 8.7.3 + Kotlin 2.1.0 + compileSdk 35；Android Studio 新建工程时默认生成 AGP 9.1.1 + Kotlin 2.2.10 + compileSdk 36，测试能跑就保留了新版本。AGP 9 有个 DSL 变化：
```kotlin
compileSdk {
    version = release(36) { minorApiLevel = 1 }
}
```
代替旧的 `compileSdk = 36`。

### 12.9 无障碍服务的 `isDouyinForeground()` 内联化
无意义的独立方法，合并到 `handleCommand` 中。同时增加日志 `handleCommand cmd=$command fg=$fgPkg` 辅助调试。

### 12.10 logo 设计
原计划沿用 Android Studio 模板的绿色机器人图标 → 改为自绘矢量：
- 背景：iOS 蓝渐变（`#0A84FF` → `#5AC8FA`）
- 前景：白色机器人头（圆角矩形 + 顶部天线）+ 5 根音频波形嘴 + 两侧各 2 条声波弧
- 同时为 banner 建了 4 个矢量 drawable（`ic_chevron_down` / `up` / `pause` / `unmatched`）复用
