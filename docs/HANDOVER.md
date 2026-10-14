# PlanBook 交接文档

> 更新于 2026-09-14 · 版本 1.11（versionCode 12）· 单人开发项目，历史上由 AI agent 辅助迭代
> 配套文档：领域语言见 [CONTEXT.md](../CONTEXT.md)，架构决策见 [docs/adr/](./adr/)，agent 工作约定见 [docs/agents/](./agents/)

## 1. 项目是什么

**PlanBook**：面向华为鸿蒙（经卓易通运行 Android 应用）和普通 Android 手机（实测小米）的**周视图计划本 App**。核心场景：

- 一周日程以彩色块呈现（课表导入后按子计划本着色，块上显示教室）
- 主计划本 + 子计划本两层结构：日程分集合管理（课程/健身/考研…），显示开关聚合、活动子本作为写操作目标
- ICS 课程表导入：教务系统导出的 ics（RRULE 或 RDATE 形态）逐次展开为单天课程，只读快照、重导整本替换
- 复盘体系：每日/每周/每月复盘自动生成待办，复盘页看当日完成 + 拆分长期目标
- 桌面小部件（只读）：一周日程网格 + 今日待办，手动刷新
- 纯本地 Room 存储，不联网不登录

## 2. 快速上手

### 2.1 环境与构建

```
仓库：https://github.com/AngoraGiantRabbit/PlanBook
平台：Windows + Git Bash（路径坑见 §7.3）
JDK ：未设全局 JAVA_HOME，用 Android Studio 自带：
      JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
```

```bash
cd PlanBook
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug          # 构建 debug APK
JAVA_HOME=... ./gradlew :app:testDebugUnitTest                                          # 单测（IcsParser 19 例）
# APK 产物：app/build/outputs/apk/debug/app-debug.apk（debug 签名可直接安装）
# 根目录 PlanBook-debug.apk 是最近一次构建的副本
```

- minSdk 26 / targetSdk 35 / compileSdk 35；Kotlin + Compose + Room 2.6.1 + Hilt + Glance 1.1.0
- **版本约定**：每完成一批功能 minor +1、versionCode +1（`app/build.gradle.kts`，有注释），无 release 签名配置，只发 debug 包

### 2.2 安装与数据

- 覆盖安装即可，**所有 schema 变更都有数据保留迁移**（见 §5.3）；仅 v2 及更早的远古版本走破坏性重建
- 全新安装首次打开点「新建计划本」引导（主计划本 + 第一个子计划本）

### 2.3 Issue 流程

GitHub Issues + 五个 triage 标签（needs-triage / needs-info / ready-for-agent / ready-for-human / wontfix）。当前全部 issue 已关闭（PRD #5 及其十个切片 #6~#15 已交付）。`gh` CLI 可用，但**GitHub 访问依赖 Watt Toolkit 加速器开着**（hosts 文件把 github 域名指向 127.0.0.1，加速器没开时 gh/git push 全部失败）。

## 3. 代码地图（单模块 `app`）

```
com.example.planbook/
├── data/
│   ├── PlanBookRepository.kt   # 唯一数据门面：所有读写经此；响应式观察方法也在这里
│   ├── local/                  # Room：Entities / Daos / PlanBookDatabase（含迁移链）
│   └── ics/                    # IcsParser + IcsTask：纯 Kotlin 无 Android 依赖（可单测）
├── model/Models.kt             # 领域模型 + TaskType/RepeatRule/ReviewType + SubNotebookPalette
├── viewmodel/                  # PlanBook（周视图）/ TaskList（待办）/ Review（复盘+设置相关）
│                               # / NotebookManager / Settings
├── ui/screens/                 # Compose 界面；AddTaskDialog.kt 的 TaskEditSheet 是
│                               # 新建+编辑共用的任务表单（readOnly 参数支持导入只读）
├── widget/PlanBookWidget.kt    # Glance 小部件（EntryPoint 直取 repository）
└── di/DatabaseModule.kt        # Hilt：Room 构建 + 迁移注册
```

## 4. 核心架构：响应式三页联动（v1.8 起，最重要的一处设计）

**问题史**：三页任务数据原为一次性拉取，跨页勾选互不联动。

**现状**：共享响应式数据源，任何页面写库 → 三页自动刷新：

```
Room Flow（tasks 表按子本集 + task_completions 按日期集）
        │ combine
        ▼
Repository.observeExpandedTasks(masterId, subIds, days)   ← 唯一展开/完成态合并逻辑
        │ flatMapLatest（由各 VM 的 weekStart/selectedDate/editDate 状态驱动）
        ├── PlanBookViewModel  → 周视图（7 天）
        ├── TaskListViewModel  → 待办页（1 天）
        └── ReviewViewModel    → 复盘页（当日完成 + 待细化长期）
```

要点：
- **写副作用与观察分离**：惰性维护（长期任务过期标记、迁移子本补色、复盘待办生成）聚合在 `ensureDerivedData`，只在动作时机（页面加载/翻周/刷新按钮）触发；其引发的写会自然回流观察管道。**不要在 Flow 的 map/flatMap 里写库**（会自触发死循环）
- `expandTask`（Repository 内）是四类任务展开的单一实现：每日按重复规则逐天 copy；灵活/临时恒单天原样返回；**长期任务做区间过滤**（ADR-0006，三页同口径）
- 各 ViewModel 里没有手动 reload——如果发现要加 reload，先怀疑观察管道没接对

## 5. 领域模型速查

### 5.1 两层计划本（ADR-0004）

- **主计划本**全局唯一（`parentId IS NULL`）；复盘、复盘设置、自动复盘待办挂主本
- **子计划本**：`color("#RRGGBB")`、`isVisible`（显示开关，只影响聚合显示）、`isActive`（活动子本 = 写操作目标，同父唯一）、`importSource`（非 null = 导入子本，存源文件名）
- 首页 chips 切活动子本；设置页「计划本管理」全量管理（新建/重命名/删除/颜色含 HSV 自定义/显示开关）

### 5.2 四类任务（字段 `type`）

| 类型 | 日期语义 | 完成 | 显示位置 |
|---|---|---|---|
| 灵活 FLEX | 单天（start==end） | 整体 | 底部灵活待办区 |
| 临时 ONE_OFF | 单天，可选时段 | 整体 | 有时段进网格；无时段进灵活区 |
| 每日 DAILY | [start..end] 区间 + 重复规则（每天/每周几） | **按天**（task_completions 表） | 按天展开进网格/列表 |
| 长期 LONG_TERM | [开始日..DDL] | 整体 | **三页统一区间可见**（ADR-0006） |

- `location` 字段（v6 起）：教室，块上第二行显示；教师/全称等其余备注在 `description`，仅详情可见
- 长期待办三页统一规则（ADR-0006）：区间内未完成正常显示、手动完成灰显可撤销；复盘页完成当天入「当日完成」（按 completedAt 日期判定）；DDL 后三页一律不显示（未完成的被惰性标记归档）
- 复盘开关：关闭即删未完成的该类自动待办（已完成保留作历史）；时段设置行常显

### 5.3 数据库（版本 6，5 张表）

| 表 | 要点 |
|---|---|
| notebooks | 两层结构 + color/isVisible/isActive/importSource |
| tasks | 四类任务统一表；type/startDate/endDate/startTime/endTime/repeatRule/weeklyDays/location/isCompleted/completedAt/isAutoReview/reviewType |
| task_completions | DAILY 按天完成记录（taskId+date 唯一） |
| reviews / review_settings | 复盘文本与三类复盘设置（notebookId+reviewType 唯一），挂主本 |

**迁移链**（`PlanBookDatabase.kt` companion + `DatabaseModule` 注册）：
- 3→4：两层模型重构（数据保留：旧计划本转子本、复盘归属改主本；含多计划本 review_settings 唯一索引去重——曾在此崩溃过，见 git log）
- 4→5：notebooks 加 importSource（可空列零风险）
- 5→6：tasks 加 location
- 更早版本：fallbackToDestructiveMigration

**ICS 导入**（ADR-0005）：`importIcsSubNotebook` 整体在一个 `withTransaction`——建子本 + 逐条插入展开的单天 ONE_OFF；同 importSource 旧本整本删除重建（保留颜色、继承活动态、完成态丢弃）。解析器支持 RRULE（WEEKLY/BYDAY/UNTIL/COUNT/BYWEEKNO 单双周/EXDATE）与 RDATE（教务系统常见形态），DESCRIPTION 按「教师：/全称：」模式重组备注、周次丢弃。

## 6. 版本史（要点）

| 版本 | 内容 |
|---|---|
| 1.0 | 初版：四类任务、周视图、复盘、（旧）多计划本 |
| 1.1 | 主/子计划本两层 + 迁移修复 + IcsParser + 小部件骨架 |
| 1.2 | 子计划本管理页 + 首页 chips/着色 |
| 1.3 | ICS 导入建本（SAF 全链路） |
| 1.4 | 导入只读保护 + 重导整本重建 |
| 1.5–1.6 | 小部件周网格 + 今日待办区（只读版收尾） |
| 1.7 | RDATE 支持 + 教室独立显示（块上只显教室） |
| 1.8 | **三页响应式联动**（架构升级）+ 复盘页独立路由竞态修复 |
| 1.9 | 编辑弹窗完成按钮 + 条目 ✕ 删除 + 待办页可编辑 |
| 1.10 | 颜色选择修复（paletteColors 漏 i++）+ HSV 自定义调色盘 |
| 1.11 | 长期待办三页统一可见性（ADR-0006）+ 待细化恒空的潜伏 bug 修复 |

## 7. 坑与经验（踩过的，别再踩）

### 7.1 环境
- **模拟器 Pixel_7 只有 2GB 内存**：Google 全家桶会把内存挤爆导致整机假死（kcompactd 高 CPU / LMK 连杀），app 表现为"卡死"但其实是受害者。对策：AVD 调 4GB、Cold Boot、必要时冻结 youtube/gmail
- GitHub 访问依赖加速器（§2.3）

### 7.2 架构
- `review_edit/{date}` 是**独立导航路由持有独立 ViewModel 实例**——ReviewViewModel 的方法不能依赖异步的 calendarState（曾因此页面空白），用 `getMasterNotebookOnce()`
- 旧 `getLongTermActive` 按单一 notebookId 查主本，而任务挂子本——凡是"按本查任务"的地方都要记得两层结构
- Kotlin KDoc 里的 `[startDate..DDL]` 方括号会被当符号引用解析导致编译错——KDoc 避免方括号区间写法

### 7.3 工具与验证方法论（本项目最有价值的经验）
- **关键结论用确定性手段验证**：`adb shell uiautomator dump` 拿 XML（文本/checked/bounds 精确），不要用"看截图问视觉模型"——引导性提问会让它顺着你编造（曾把 2 个 chips 看成 3 个，误导排查方向一整轮）
- **DB 检查必须连 WAL 三件套一起拉**（db/-wal/-shm），否则看到的是旧数据；直接改库注入数据时 push 后要删 wal/shm
- Git Bash 下 adb push 的 `/data/...` 路径会被转义成 Windows 路径——用 `MSYS_NO_PATHCONV=1`
- 常用坐标（1080×2400 模拟器）：底部四 Tab ≈ (127/403/678/953, 2274)；设置→计划本管理按钮 ≈ (540,1662)
- headless 起模拟器：`emulator -avd Pixel_7 -no-window -no-audio -gpu swiftshader_indirect`

## 8. 已知限制与二期候选

**已知限制**：
- 小部件为只读版：Glance 无精确时间轴（时段以文字呈现）；勾选交互、自动刷新未做（PRD 定为二期）
- MIUI 上小部件入口深、新装应用索引延迟（重启可解）；已提供排查话术（见会话记录/issue 注释习惯）
- 测试覆盖仅 IcsParser（19 例 JVM 单测）；UI/DB 层无自动化测试，靠构建 + 模拟器手验
- release 未配签名；无 CI

**二期候选**（PRD issue #5 收尾时记录）：
- 小部件勾选完成交互（Glance action + 状态回写，鸿蒙/卓易通环境需谨慎验证）
- ICS 导出（当前只进不出）
- 通知/提醒；数据导出备份

## 9. 给接手者的三条建议

1. **改行为前先读 CONTEXT.md 和相关 ADR**——领域规则（尤其完成态双模型、长期任务可见性、导入快照语义）都定过决策，别凭直觉改；要推翻就走 ADR 修订（参考 0003→0006 的做法）
2. **保持"写走 repository、读走观察管道"的纪律**——这是三页联动的根基；新页面照抄三个 VM 的管道模式（状态驱动 flatMapLatest）
3. **版本号随手迭代**（minor+1/versionCode+1），提交信息按现有风格写清"改了什么+为什么+验证方式"——这个仓库的历史提交就是最好的变更日志

## 10. 鸿蒙原生版（PlanBookHarmony）

`PlanBookHarmony/` 是本 App 的**鸿蒙原生重写**（ArkTS/ArkUI + RelationalStore + ArkTS 卡片，API 26），
2026-10 完成首版，功能与 Android 版对齐（四类任务、周视图、复盘体系、ICS 导入、只读桌面卡片、手动刷新）。
领域语义仍以根目录 CONTEXT.md 与 docs/adr/ 为准，两版共用同一套决策。

- 构建/测试：`bash PlanBookHarmony/tools/build.sh`（未签名 HAP）、`bash PlanBookHarmony/tools/test.sh`
  （IcsParser 19 例，Node 直跑 .ets）；坑与规避见该目录 README（空格路径 → junction + JS 启动器）
- 架构映射：Room Flow → Repository 写后通知总线；Compose → 状态管理 V2（@ObservedV2 VM）；
  Glance → FormExtensionAbility + ArkTS 卡片；细节见 `PlanBookHarmony/README.md`
- 已知差异：浅色固定主题（无深色模式）；DB 为全新 schema v6 起步（无迁移链，两版数据不互通）
