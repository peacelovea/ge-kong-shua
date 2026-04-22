# 隔空刷 · 文档索引

> 这是整个项目的文档入口。新来（或 2 个月后回来）的人，从这里读起。

## 一句话状态

MVP 已上线，装在 Vivo X200 Pro 上可日常使用；代码在 `main` 分支，远端 [peacelovea/ge-kong-shua](https://github.com/peacelovea/ge-kong-shua)（public）。最后更新：见 `git log -1`。

---

## 按"我想干什么"导航

### 我想理解项目（架构 / 为什么这么设计）
- [`superpowers/specs/2026-04-17-shower-voice-ctrl-design.md`](superpowers/specs/2026-04-17-shower-voice-ctrl-design.md) — **设计文档**。背景、决策、组件、数据流、错误处理、as-built 偏离记录（§12 含 10 条踩坑）
- [项目根 `README.md`](../README.md) — 用户视角：适用场景、使用方式、已知限制

### 我想改代码 / 调 bug
- [`development.md`](development.md) — **开发手册**：环境设置、常用 gradle/adb 命令、调试 recipe（抓 logcat、查前台服务、重装 APK）、已知平台坑、"怎么加一个命令 / 适配一个 App" 的操作流

### 我想知道下一步做什么
- [`roadmap.md`](roadmap.md) — **路线图**：P1/P2/P3 分组、每项的完成定义（DoD）、Archived 项

### 我想看项目是怎么从零开始的（考古）
- [`superpowers/specs/2026-04-17-shower-voice-ctrl-design.md`](superpowers/specs/2026-04-17-shower-voice-ctrl-design.md) — 初稿设计（2026-04-17）
- [`superpowers/plans/2026-04-17-shower-voice-ctrl.md`](superpowers/plans/2026-04-17-shower-voice-ctrl.md) — 13 个 Task 的实施计划（含完整代码和命令）
- Git 历史（`git log --oneline`）— 每次改动的 what + why

---

## 文档维护规则

1. **新发现的踩坑**：写进 `development.md` 的"已知平台坑" / "调试 recipe"
2. **决策变更**：改 spec 的正文 + 在 §12 追加一条变更记录
3. **新需求 / 待办**：先写进 `roadmap.md`，有进展再挪分组
4. **已过时的想法**：从正文挪到 `roadmap.md` 末尾的"Archived"
5. **文档重构**：更新本 `README.md` 的导航链接
