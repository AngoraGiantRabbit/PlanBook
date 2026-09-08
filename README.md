# PlanBook 计划本

一款以「周视图计划本」为核心的 Android 待办/计划应用。设计理念是**桌面小部件即主页面**：把一整周的计划放在桌面一页里，快速查看、快速完成、快速复盘。

> 目标运行环境为华为鸿蒙系统（通过卓易通运行 Android 应用），同时在标准 Android 模拟器上开发调试。

## 功能特性

- **多计划本管理**：工作、学习、旅行……每个计划本独立维护自己的任务与复盘，一键切换当前计划本。
- **四类任务**：
  - 灵活任务：单天、无时段，落在周视图底部的灵活待办区
  - 临时任务：单天、可精确到分钟的开始/结束时间
  - 每日任务：按重复规则（每天 / 每周指定几天）在起止区间内重复
  - 长期任务：跨多天的目标性任务，由开始日 + DDL 界定，在复盘中逐步拆解落地
- **复盘即待办**：每日/每周/每月复盘作为自动生成的普通任务出现在周视图中，点击即写，不让复盘被遗忘。
- **复盘即历史**：复盘页面同时展示当天完成的全部任务，作为个人记录的入口。
- **桌面小部件**（规划中）：占满一页桌面，周视图直接在桌面呈现，80% 的场景无需打开 App。

## 技术栈

| 分类 | 选型 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose |
| 数据库 | Room |
| 依赖注入 | Hilt |
| 桌面小部件 | Jetpack Glance（规划中） |

最低支持 Android 8.0（minSdk 26），目标 API 35。

## 项目结构

```
.
├── PlanBook/               # 主应用（Android Gradle 项目）
├── Flux_aphone-master/     # 开源参考项目 Flux 的源码，仅作实现参考
├── docs/adr/               # 架构决策记录（ADR）
├── CONTEXT.md              # 领域概念与统一语言
└── PRD.md                  # 产品需求文档
```

## 构建

1. 克隆仓库：`git clone https://github.com/AngoraGiantRabbit/PlanBook.git`
2. 用 Android Studio（Ladybug 或更新版本）打开 `PlanBook/` 目录
3. Sync Gradle 后直接运行 `app` 配置即可

## 文档

- [产品需求文档（PRD）](./PRD.md)
- [领域概念（CONTEXT.md）](./CONTEXT.md)
- [架构决策记录（ADR）](./docs/adr/)

## 反馈与贡献

欢迎通过 [Issues](https://github.com/AngoraGiantRabbit/PlanBook/issues) 提交 bug 反馈和功能建议。
