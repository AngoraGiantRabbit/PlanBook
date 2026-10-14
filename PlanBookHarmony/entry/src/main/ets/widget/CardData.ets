import { common } from '@kit.AbilityKit';
import { DateUtils } from '../common/DateUtils';
import { Repository } from '../data/Repository';
import { Task, TaskType } from '../model/Models';
import { ColorUtil } from '../common/Theme';

/**
 * 桌面卡片数据（对应 Android PlanBookWidget.buildWeekData）：
 * - 一周（周一开始）日程网格：每列当天有时段的任务按时段排序，按子计划本着色，今天列头高亮；
 * - 底部今日待办（灵活 + 无时段临时 + 活跃长期，与 App 底部同口径）。
 * 数据口径与计划本页一致：显示开关打开的子计划本 + 主计划本复盘待办。
 */
export class CardDataBuilder {
  static async build(context: common.Context): Promise<Record<string, Object>> {
    try {
      Repository.init(context);
      await Repository.whenReady();
      const master = await Repository.getMasterNotebookOnce();
      if (master === null) {
        return CardDataBuilder.empty('计划本');
      }
      const subs = await Repository.getSubNotebooksOnce(master.id);
      const visibleIds: number[] = [];
      const colorMap: Record<string, string> = {};
      for (const s of subs) {
        if (s.isVisible) {
          visibleIds.push(s.id);
          const c = s.color;
          if (c !== null && ColorUtil.isHex(c)) {
            colorMap[`${s.id}`] = c;
          }
        }
      }
      const today = DateUtils.today();
      const weekStart = DateUtils.weekStartOf(today);
      const allTasks = await Repository.getExpandedTasksForWeek(master.id, visibleIds, weekStart);

      // 今日待办：FLEX + 无时段 ONE_OFF + LONG_TERM（today ∈ [start..end]，与 App 底部两区同口径）
      const todos: Record<string, Object>[] = [];
      for (const t of allTasks) {
        if (!DateUtils.within(today, t.startDate, t.endDate)) {
          continue;
        }
        if (t.type === TaskType.FLEX || (t.type === TaskType.ONE_OFF && t.startTime === null) ||
            t.type === TaskType.LONG_TERM) {
          todos.push({
            'title': t.title,
            'done': t.isCompleted,
            'text': (t.isCompleted ? '✓ ' : '○ ') + t.title
          });
        }
      }
      todos.sort((a: Record<string, Object>, b: Record<string, Object>) => {
        return (a['done'] === b['done']) ? 0 : (a['done'] === true ? 1 : -1);
      });

      // 周网格
      const timed = allTasks.filter((t: Task): boolean => t.startTime !== null);
      const days: Record<string, Object>[] = [];
      for (let i = 0; i < 7; i++) {
        const date = DateUtils.addDays(weekStart, i);
        const dayTasks: Record<string, Object>[] = [];
        const dayTimed = timed.filter((t: Task): boolean => t.startDate === date)
          .sort((a: Task, b: Task): number => {
            const at = a.startTime ?? '';
            const bt = b.startTime ?? '';
            return at === bt ? 0 : (at < bt ? -1 : 1);
          });
        const shown = dayTimed.length > 5 ? dayTimed.slice(0, 5) : dayTimed;
        for (const t of shown) {
          const color = colorMap[`${t.notebookId}`];
          dayTasks.push({
            'time': `${t.startTime}-${t.endTime ?? t.startTime}`,
            'title': t.title,
            'location': (t.location !== null && t.location.length > 0) ? t.location : '',
            'color': color !== undefined ? color : '#E3E3E6'
          });
        }
        if (dayTimed.length > 5) {
          dayTasks.push({
            'time': '',
            'title': `…还有 ${dayTimed.length - 5} 项`,
            'location': '',
            'color': '#00000000'
          });
        }
        days.push({
          'weekday': `周${DateUtils.weekdayCn(DateUtils.dayOfWeek(date))}`,
          'dateText': DateUtils.md(date),
          'isToday': date === today,
          'tasks': dayTasks
        });
      }
      return {
        'masterName': master.name,
        'weekRange': `${DateUtils.md(weekStart)} - ${DateUtils.md(DateUtils.addDays(weekStart, 6))}`,
        'days': days,
        'todos': todos,
        'todoCount': todos.length,
        'hasData': true
      };
    } catch (e) {
      return CardDataBuilder.empty('暂无数据，打开 App 刷新');
    }
  }

  private static empty(message: string): Record<string, Object> {
    return {
      'masterName': message,
      'weekRange': '',
      'days': [] as Record<string, Object>[],
      'todos': [] as Record<string, Object>[],
      'todoCount': 0,
      'hasData': false
    };
  }
}
