# ICS 导入：展开为单天 ONE_OFF 的只读快照

导入 ICS（主要场景：课程表）时，每个 VEVENT 的 RRULE 在其区间内**逐次展开为单天 ONE_OFF 任务**（`startDate == endDate`，startTime/endTime 取 DTSTART/DTEND 时刻）。SUMMARY → title；LOCATION 与 DESCRIPTION（教室、老师）→ description，任务块内以小字显示。导入生成的子计划本标记为**导入**：其任务不可在 App 内编辑；重复导入同一课程表 = 删除旧子计划本整本重建（完成态丢弃）。

## Considered Options

- **映射为 DAILY（weeklyDays 按 BYDAY）**：否决。课程表常见单双周（BYWEEKNO）、中途调课、EXDATE 例外、同一门课前后半学期不同时段——DAILY 的"每周指定几天 + 区间"模型表达不了，遇到就得退化处理。
- **展开为单天 ONE_OFF（选定）**：任何 RRULE 细节都能吃；复用现有单天任务管线（显示、活跃区间、完成态、按子计划本着色）零改动。代价是一学期约 200-400 条记录，本地 Room 无压力。
- **导入子计划本可编辑 + 增量同步**：否决。要按 (title+日期+时间) 匹配合并完成态，复杂且易错；课程表语义上是"源文件的快照"，整本重建简单可靠。

## Consequences

- ics 解析器需要支持：VEVENT 基本字段、FREQ=WEEKLY（BYDAY、UNTIL/COUNT、BYWEEKNO 奇偶）、EXDATE、全天事件（无时间则落为无时段单天任务）。
- 导入子计划本在 UI 上要有明确的只读标识，编辑入口禁用，防止用户误改后与源文件失去对应。
- 重导丢弃完成态是接受的取舍：课表本身变了，旧课的勾选无意义。
