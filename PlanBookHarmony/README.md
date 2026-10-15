# PlanBookHarmony — 计划本 · 鸿蒙原生版

**v1.1.0**（versionCode 2）· Android 版（`../PlanBook/`，Kotlin + Compose + Room + Glance，经卓易通运行）的**鸿蒙原生重写**：
ArkTS / ArkUI（Stage 模型，状态管理 V2）+ RelationalStore（RDB）+ ArkTS 桌面卡片。

领域语义与 Android 版完全一致（见根目录 `CONTEXT.md` 与 `docs/adr/`）：
主计划本 + 子计划本两层结构、四类任务（灵活/临时/每日/长期）、完成态双模型（ADR-0001）、
单天语义（ADR-0002）、长期任务过期自动完成（ADR-0003）、ICS 课程表导入快照（ADR-0005）、
长期任务三页统一可见性（ADR-0006）、每日/每周/每月复盘体系。

## 环境

- DevEco Studio 26（HarmonyOS 26 / API 26 Release SDK，`compatibleSdkVersion: 26.0.0`）
- hvigor 6.26.4（`hvigor/hvigor-config.json5` 以 `file:` 指向本机 DevEco 安装，离线可用）
- 无三方 ohpm 依赖

## 构建

```bash
bash tools/build.sh            # 产出 entry/build/default/outputs/default/*.hap（已配置签名则同时产出 signed 包）
bash tools/build.sh clean      # 清理后再 bash tools/build.sh 即冷构建
bash tools/test.sh             # IcsParser 19 例 + IcsExporter roundtrip 7 例（Node 24 原生跑 .ets）
```

未签名 HAP 安装到真机需在 DevEco Studio 中配置签名（File → Project Structure → Signing Configs，
自动签名即可），或命令行 `hdc install` 一个已签名包。

> Windows Git Bash 构建坑（脚本已内置规避）：带空格路径（Program Files）经 shell→bat→node
> 边界解析不到 node/java → `spawn java ENOENT`。脚本会建 `C:/hm-jbr` junction（免管理员）并经
> `tools/run_hvigor.mjs` 在 node 进程内注入 PATH。若 DevEco 装在别处，`DEVECO=... bash tools/build.sh`。

## 代码地图（单模块 `entry`）

```
entry/src/main/ets/
├── common/
│   ├── DateUtils.ets   # 纯日期工具（yyyy-MM-dd / ISO 周；无 @ohos 依赖，可 Node 直跑）
│   └── Theme.ets       # 色板令牌 + 颜色工具（HSV / alpha）
├── model/Models.ets    # 领域模型（对应 Android model/Models.kt）
├── data/
│   ├── IcsParser.ets   # ICS 解析 + RRULE 展开（忠实移植，纯逻辑）
│   ├── IcsExporter.ets # ICS 导出（RFC 5545 转义，roundtrip 可还原）
│   └── Repository.ets  # 唯一数据门面：RDB 7 表（v7 加 card_prefs/app_settings/课表列）
│                       # + 写串行链（重入防死锁）+ 变更通知（三页联动 + 卡片自动同步）
├── viewmodel/ViewModels.ets  # 五个 @ObservedV2 VM（订阅 Repository 通知重查，等同 Flow 管道）
├── components/         # DialogShell / 选择器（TextPicker）/ TaskEditSheet（新建+编辑共用表单）
├── pages/              # Index（四 Tab）/ PlanBook（周视图+划动翻周）/ TaskList（划动切日）
│                       # / Review / ReviewEdit / Settings（复盘+学期）/ NotebookManager
│                       # / CourseBuilder（新建/编辑课表）/ CardConfigPage（卡片配置）
├── entryability/EntryAbility.ets          # + installAutoSync（写操作自动推卡）
├── cardconfigability/CardConfigAbility.ets # 卡片配置宿主（formConfigAbility）
├── entryformability/PlanBookFormAbility.ets # 卡片 Form 扩展（onFormEvent 翻周/刷新）
└── widget/             # TodoCard / WeekCard / LargeCard（时间网格）+ CardData + CardOps + WidgetSync

## 与 Android 版的架构映射

| Android                          | 鸿蒙原生                                                                      |
| -------------------------------- | ----------------------------------------------------------------------------- |
| Room + DAO + Flow                | RelationalStore（同 schema 5 表）+ Repository 写后通知（listener 总线）       |
| combine + flatMapLatest 观察管道 | VM 订阅 Repository 变更 → 全量重查（`loadExpandedTasks` 单一展开口径不变） |
| Hilt Singleton                   | `Repository.init(context)` 静态单例（EntryAbility / FormAbility 各自 init） |
| Compose Scaffold + NavHost       | Tabs 四主页 + router 推入 ReviewEdit / NotebookManager 独立页                 |
| Glance 小部件（手动刷新）        | ArkTS 卡片 + FormAbility +`WidgetSync.updateAll`（formId 存 preferences）   |
| SAF 选 .ics                      | DocumentViewPicker + fs(fd)+TextDecoder 读文本                                |
| IcsParserTest 19 例 JVM 单测     | `tools/run_ics_tests.mjs`（Node 24 原生跑 .ets，同 19 例）                  |

已知差异（有意为之）：浅色固定主题（Android 随系统深浅切换）；其余行为按 ADR 对齐。

## 桌面卡片（多开 · 每卡独立配置 · 卡内翻周）

三张卡片（桌面长按 → 服务卡片 → 计划本）：

| 卡片 | 尺寸 | 内容 |
|---|---|---|
| 计划本·今日待办 | 1x2 / 2x2 | 今天 FLEX/无时段 ONE_OFF/长期待办，未完成在前，行首按子本着色 |
| 计划本·周课表 | 2x4 | 7 列周网格 + `< >` 卡内翻周 + `↻` 刷新 |
| 计划本·整页周课表 | 4x4 / 6x4 | **与 App 同构的时间网格**（08:00-24:00 逐时格线、课程按时刻定位、重叠并列、完整课名+教室）+ 列头今天高亮 + 底部今日待办条 + 翻周/刷新 |

- **多开独立配置**：同一卡片可加多张（如一张看自己的课表、一张看对象的）；长按卡片 →「编辑卡片显示内容」
  （CardConfigAbility）勾选该卡显示哪些子计划本（都不勾 = 恢复默认显示全部可见子本）。
  存 `card_prefs` 表（formId → notebookIds / weekOffset）。
- **卡内翻周**：`< >` 经 postCardAction message → FormAbility `onFormEvent` 改 weekOffset 后推回；
  每张卡的翻周状态独立持久化；`↻` 为兜底刷新（顺带触发惰性维护）。
- **自动同步**：App 内任何写操作后 2s 节流推送全部卡片（`WidgetSync.installAutoSync`）；
  失效 formId 自动摘除（自愈）。
- **渲染刷新**：卡片数据带 `rev` 版本号，所有 ForEach key 绑定它，任何数据变化强制重绘。

## 手动课表 & ICS 导出

- **新建课表**（设置 → 计划本管理 → 新建课表）：课表名 + 学期第一周 + 总周数；
  每门课（名称/教室/老师）多个时间段（周几 × 第 a-b 节 × 第 a-b 教学周，节次自动带出时间），
  实时预览「共 N 次 · 首次 日期 时间」。节次时间为预置大学 10 节表（08:00…21:30）。
- **课表可编辑**：课表子本带「课表」标签，管理页 📅 图标进入编辑；保存 = 更新定义并整本事务重建任务。
  定义存 `notebooks.timetableJson`（v7 迁移自动加列）。
- **导出 ICS**：管理页每个子本 ⤴ 图标 → 标准 RFC 5545 文件（系统面板选位置）；
  roundtrip 单测保证「导出 → 本 App 再导入」完整还原（`tools/run_ics_export_tests.mjs`）。

## 交互速查

- 计划本页 / 待办页：**左右划**翻周 / 切前后天（阈值 50vp）
- 子计划本 chip：**点按**切活动子本（写操作目标）；**长按**切显示开关
  （隐藏的子本留在行内：半透明 + 删除线 + 虚线边框，长按恢复）
- 周视图时间轴贴格线对齐；教学周数「第 N 周」显示在标题（设置 → 学期配置起点）
