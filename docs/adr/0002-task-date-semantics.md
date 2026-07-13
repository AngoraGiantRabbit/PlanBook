# 任务日期语义与长期任务的活跃区间

四类任务的日期语义重新明确：
- **灵活/临时**：单天（`startDate == endDate`），UI 只暴露一个日期入口。不再支持跨多天——跨多天的需求交给长期任务。
- **每日**：起止区间 + 重复规则，规则在 `[startDate .. endDate]` 内生效（保持不变）。
- **长期**：由「开始日 + DDL」界定**活跃区间**，仅当查看日落在 `[startDate .. DDL]` 内时才在周视图和复盘页显示。

## Considered Options

- **灵活/临时仍允许跨多天**：否决。四类任务里「跨多天」与「单天」混在一起，导致同一类型（ONE_OFF/FLEX）存在两套日期输入、两套展开逻辑、两套完成语义（ADR-0001 曾为它打补丁）。单天后 `expandTask` 的多天分支成为死代码，予以删除。
- **长期任务「只设 DDL」**：否决。只靠 DDL 无法表达「现在该不该关注它」——开始日之前就显示会让长期条提前塞满；必须有开始日才能过滤。
- **长期任务和灵活任务共用一个底部条**：否决。长期目标是「在复盘里跟进的对象」，与「今天要勾掉的待办」语义不同，视觉上分条让用户一眼区分。

## Consequences

- `expandTask`（Repository）中 ONE_OFF/FLEX 的多天分支删除：它们恒为单天，直接原样返回。
- `LongTermTaskArea` 过滤从 `type == LONG_TERM` 改为 `type == LONG_TERM && selectedDate in [startDate..endDate]`。
- 复盘页 `getLongTermActive` 增加 `startDate <= today` 条件，未开始的长期任务也不显示。
- `AddTaskDialog` 对灵活/临时只渲染一个日期选择器，存盘时写 `startDate = endDate`。
