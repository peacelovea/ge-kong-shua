# 隔空刷 v0.1.0 · 首个公开版本

> 手被占着也能刷抖音/快手。说 "下一条" / "上一条" / "暂停"，手机自动切。

## ✨ 核心功能
- **离线语音识别**（Vosk，中文）：3 条命令触发上/下滑 + 中心点击
- **苹果风顶部反馈横幅**：识别成功/失败一眼可见
- **iOS 风格主界面**：呼吸指示灯、状态卡、大按钮
- **支持 4 款短视频 App**：抖音 / 抖音极速版 / 快手 / 快手极速版

## 📦 安装

1. 下载本页底部的 `app-release.apk`
2. 在手机上点击安装（可能需要先开启"允许未知来源应用安装"）
3. 首次打开后，给**麦克风权限** + 开启**无障碍服务**（系统会一键引导）
4. 点"开始监听"→ 切到抖音/快手 → 说命令

## ✅ 兼容性

- **已实测**：Vivo X200 Pro / OriginOS / Android 15（ARM64）
- **理论支持**：Android 9+（API 28+），ARM64 或 ARMv7 手机
- **不支持**：iOS、纯鸿蒙 NEXT、Android < 9、抖音极速版以外的变体、折叠屏（手势坐标可能偏）

## ⚠️ 已知限制

- **只适配 4 款短视频 App**（微信视频号、小红书、B 站 Story、YouTube Shorts 暂不支持）
- **国产 ROM 后台限制**：请为本 App 开启"自启动" + "后台运行" + "允许后台高耗电"，否则监听中途可能被杀
- **高噪音环境**识别率会下降（水声、吹风机、油烟机）
- 自动连播关闭时暂停命令可能意外导致 feed 抓不住焦点
- 首次识别前需要 1-2 秒加载 Vosk 模型

## 🔒 隐私

- **完全离线**：语音识别在本地执行，麦克风数据不上传任何服务器
- **不收集任何数据**：无统计、无日志上传、无账号系统

## 📖 文档

- 项目入口：[README.md](https://github.com/peacelovea/ge-kong-shua/blob/main/README.md)
- 设计文档：[docs/superpowers/specs/2026-04-17-shower-voice-ctrl-design.md](https://github.com/peacelovea/ge-kong-shua/blob/main/docs/superpowers/specs/2026-04-17-shower-voice-ctrl-design.md)
- 开发手册：[docs/development.md](https://github.com/peacelovea/ge-kong-shua/blob/main/docs/development.md)

## 💬 反馈

踩坑 / 建议 / 适配其它 App 请求，欢迎开 issue。
