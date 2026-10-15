# PlanBookHarmony — PlanBook 鸿蒙原生版

Android 版（`../PlanBook/`，Kotlin + Compose + Room + Glance，经卓易通运行）的**鸿蒙原生重写**：
ArkTS / ArkUI（Stage 模型，状态管理 V2）+ RelationalStore（RDB）+ ArkTS 桌面卡片。

领域语义与 Android 版完全一致（见根目录 `CONTEXT.md` 与 `docs/adr/`）：
主计划本 + 子计划本两层结构、四类任务（灵活/临时/每日/长期）、完成态双模型（ADR-0001）、
单天语义（ADR-0002）、长期任务过期自动完成（ADR-0003）、ICS 课程表导入快照（ADR-0005）、
长期任务三页统一可见性（ADR-0006）、每日/每周/每月复盘体系、只读桌面卡片 + 手动刷新。

## 环境

- DevEco Studio 26（HarmonyOS 26 / API 26 Release SDK，`compatibleSdkVersion: 26.0.0`）
- hvigor 6.26.4（`hvigor/hvigor-config.json5` 以 `file:` 指向本机 DevEco 安装，离线可用）
- 无三方 ohpm 依赖

## 构建

```bash
bash tools/build.sh            # 产出 entry/build/default/outputs/default/entry-default-unsigned.hap（未签名）
bash tools/build.sh clean      # 清理后再 bash tools/build.sh 即冷构建
bash tools/test.sh             # IcsParser 单测（19 例，移植自 Android 版 IcsParserTest.kt）
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
│   ├── Repository.ets  # 唯一数据门面：RDB 5 表 + 写串行链 + 变更通知（三页联动）
│   └── （CardData.ets 在 widget/ 下）
├── viewmodel/ViewModels.ets  # 五个 @ObservedV2 VM（订阅 Repository 通知重查，等同 Flow 管道）
├── components/         # DialogShell / 选择器（TextPicker）/ TaskEditSheet（新建+编辑共用表单）
├── pages/              # Index（四 Tab）/ PlanBook（周视图）/ TaskList / Review / ReviewEdit / Settings / NotebookManager
├── entryability/EntryAbility.ets
├── entryformability/PlanBookFormAbility.ets   # 桌面卡片 Form 扩展
└── widget/             # PlanBookCard.ets（ArkTS 卡片）+ CardData + WidgetSync（App 内手动刷新）
```

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
| 计划本·整页周课表 | 4x4 | 整页周网格（时间/课程/教室）+ 底部今日待办条 + 翻周/刷新 |

- **多开独立配置**：同一卡片可加多张（如一张看自己的课表、一张看对象的）；点卡片右上角
  设置角标 → CardConfigAbility 配置页勾选该卡显示哪些子计划本（都不勾 = 恢复默认显示全部可见子本）。
  存 `card_prefs` 表（formId → notebookIds / weekOffset）。
- **卡内翻周**：`< >` 经 postCardAction message → FormAbility `onFormMessage` 改 weekOffset 后推回；
  每张卡的翻周状态独立持久化。
- **自动同步**：App 内任何写操作后 2s 节流推送全部卡片（`WidgetSync.installAutoSync`，
  EntryAbility 注册）；卡内 `↻` 为兜底（顺带触发惰性维护）。
- **教学周数**：设置 → 学期 → 配置学期第一周的周一（自动归一到周一）后，
  周视图标题与卡片周条显示「第 N 周」。
- **首页长按 chip**：长按子计划本 chip 约 0.5s 切换其显示开关（隐藏的子本在
  设置 → 计划本管理里重新打开）。
