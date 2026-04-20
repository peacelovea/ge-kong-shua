# 隔空刷

Android 应用：在**不想动手**的时候用语音切抖音——说 "下一条 / 上一条 / 暂停"，手机自动滑屏。

## 适用场景

只要手被占着 / 不方便碰屏幕，都能用：

- **洗澡**（湿手，手机放洗手台） — 原始需求
- **做饭**（油手 / 湿手 / 面粉）
- **吃饭**（筷子和刷子的二选一解决）
- **护肤 / 化妆**（满脸乳液 / 妆容未干）
- **哺乳 / 抱娃**（单手甚至无手）
- **健身 / 拉伸**（躺着举铁时顺便刷）
- **躺在床上纯粹懒得伸手**

凡是"自动连播遇到不喜欢的长视频又懒得划"的场景都适用。

## 效果

- 打开 App → 开始监听 → 切到抖音
- 说 "**下一条**" / "**上一条**" / "**暂停**"，抖音即模拟滑动 / 点击
- 屏幕顶部弹出 iOS 风格的识别反馈横幅

## 技术栈

- Kotlin 2.2 + Jetpack Compose（Material 3）
- Vosk 0.3.47（离线中文关键词识别，~42MB 模型打进 assets）
- `AccessibilityService` + `dispatchGesture` 模拟手势
- `AudioRecord`（`VOICE_COMMUNICATION` 源 + AEC + NS）
- 前台 Service + `CommandBus`（`MutableSharedFlow`）
- 叠层反馈使用 `TYPE_ACCESSIBILITY_OVERLAY`
- minSdk 28 / targetSdk 36

## 构建

1. 安装 Android Studio Ladybug+，Android SDK API 35+
2. 下载 Vosk 中文模型到 `app/src/main/assets/`：
   ```bash
   ./scripts/install_vosk_model.sh
   ```
3. 构建并安装到设备：
   ```bash
   ./gradlew :app:installDebug
   ```

## 首次启动

1. 授予麦克风权限
2. 开启本 App 的无障碍服务（系统设置 → 无障碍 → 已安装的服务 → "隔空刷"）
3. 回到 App 点 "开始监听"

## 文档

- 设计文档：[`docs/superpowers/specs/2026-04-17-shower-voice-ctrl-design.md`](docs/superpowers/specs/2026-04-17-shower-voice-ctrl-design.md)
- 实施计划：[`docs/superpowers/plans/2026-04-17-shower-voice-ctrl.md`](docs/superpowers/plans/2026-04-17-shower-voice-ctrl.md)

## 实测经验

Vivo X200 Pro / OriginOS / 抖音前台场景下：安静环境识别率 ≥ 90%，抖音视频外放时 ≥ 70%（`VOICE_COMMUNICATION` + AEC/NS 抗回声）。手机距离身体 1m 内最稳。

## 已知限制

- 只适配抖音（包名 `com.ss.android.ugc.aweme`）；未适配快手 / 视频号 / 小红书
- 手势坐标硬编码（屏幕 50% 宽，25%/75% 高），未做调试面板
- OriginOS 后台限制：需要在系统里为本 App 打开"自启动"和"允许后台高耗电"
- 高噪音环境（淋浴水声、抽油烟机、吹风机）识别率会降低，手机尽量离说话人近一些
