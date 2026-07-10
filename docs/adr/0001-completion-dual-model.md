# 完成态的双模型（单实例 vs 周期实例）

任务的完成态按「是否周期性发生」分为两类，只有一条分流规则：
`type == DAILY → 按天记录（task_completions 表）；否则 → 整体记录（TaskEntity.isCompleted 布尔）`。

ONE_OFF、FLEX、LONG_TERM 是「一件事」，勾掉即完成，跨多天的任务在每天列都显示但共享同一个完成态。
DAILY 按重复规则每天发生，每天的完成态独立，过去未勾即如实显示未完成（不自动标记逾期完成）。

## Considered Options

- **全走按天表（task_completions）**：否决。单实例任务不周期发生，按天记录是过度建模，徒增查询与维护。
- **全走全局布尔（isCompleted）**：否决。DAILY 每天独立完成无法表达——周一做完周二仍要显示未完成。
- **用「是否跨多天」分裂同类任务**：否决。这曾导致 ONE_OFF/FLEX 跨多天走全局、单天走按天表，同类任务出现两套完成逻辑，每加一个功能都要 special-case。这正是本次决策要消除的混乱根因。

## Consequences

- 判断只看 `type`，不看「是否跨多天」等临时条件。`toggleTaskComplete` 和 `expandTaskWithCompletion` 各自只剩一条分流分支。
- DB 行为变更：单天 ONE_OFF/FLEX 的完成态从 `task_completions` 回归 `isCompleted`。demo 阶段用 `fallbackToDestructiveMigration`，已有按天完成记录会丢、需重勾，可接受。
