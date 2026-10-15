import { common } from '@kit.AbilityKit';
import { DateUtils } from '../common/DateUtils';
import { Repository } from '../data/Repository';
import { Notebook, Task, TaskType } from '../model/Models';
import { ColorUtil } from '../common/Theme';

/**
 * 桌面卡片数据构建（支持多开、per-formId 配置）：
 * - 每张卡可独立配置显示哪些子计划本（card_prefs.notebookIds；空 = 全部可见子本）；
 * - 每张卡独立翻周状态（card_prefs.weekOffset，卡内 < > 按钮经 FormAbility message 修改）；
 * - 教学周数（app_settings.termStart，设置页配置学期第一周）；
 * - 三种卡片共用同一份数据字段（今日待办卡 / 2x4 周课表卡 / 4x4 整页周课表卡）。
 */
export class CardDataBuilder {
  static async build(context: common.Context, formId: string): Promise<Record<string, Object>> {
    try {
      Repository.init(context);
      await Repository.whenReady();
      const master = await Repository.getMasterNotebookOnce();
      if (master === null) {
        return CardDataBuilder.empty('计划本', '打开 App 初始化');
      }
      const subs = await Repository.getSubNotebooksOnce(master.id);
      // 本卡显示的子本：已配置取配置（可与 App 显示开关解耦，专属卡不受影响）；否则全部可见子本
      const configured = formId.length > 0 ? await Repository.getCardNotebookIds(formId) : [];
      const selected: Notebook[] = [];
      if (configured.length > 0) {
        for (const s of subs) {
          if (configured.indexOf(s.id) >= 0) {
            selected.push(s);
          }
        }
      } else {
        for (const s of subs) {
          if (s.isVisible) {
            selected.push(s);
          }
        }
      }
      const selectedIds: number[] = [];
      const colorMap: Record<string, string> = {};
      const selectedNames: string[] = [];
      for (const s of selected) {
        selectedIds.push(s.id);
        selectedNames.push(s.name);
        const c = s.color;
        if (c !== null && ColorUtil.isHex(c)) {
          colorMap[`${s.id}`] = c;
        }
      }
      const today = DateUtils.today();
      const weekOffset = formId.length > 0 ? await Repository.getCardWeekOffset(formId) : 0;
      const weekStart = DateUtils.addWeeks(DateUtils.weekStartOf(today), weekOffset);
      const weekNo = await Repository.termWeekNo(weekStart);
      const allTasks = await Repository.getExpandedTasksForWeek(master.id, selectedIds, weekStart);

      // 今日待办：FLEX + 无时段 ONE_OFF + LONG_TERM（today ∈ [start..end]，与 App 底部两区同口径）
      // 注意：翻周后"今日"仍指真实的今天（待办卡语义）。
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
            'text': (t.isCompleted ? '✓ ' : '○ ') + t.title,
            'color': colorMap[`${t.notebookId}`] !== undefined ? colorMap[`${t.notebookId}`] : '#9E9E9E'
          });
        }
      }
      todos.sort((a: Record<string, Object>, b: Record<string, Object>): number => {
        return (a['done'] === b['done']) ? 0 : (a['done'] === true ? 1 : -1);
      });

      // 周网格（有时段任务）
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
        const shown = dayTimed.length > 10 ? dayTimed.slice(0, 10) : dayTimed;
        for (const t of shown) {
          const color = colorMap[`${t.notebookId}`];
          const st = t.startTime ?? '00:00';
          const et = t.endTime ?? t.startTime ?? '00:00';
          const sm = Repository.hmsToMinutes(st);
          const em = Math.max(sm + 5, Repository.hmsToMinutes(et));
          dayTasks.push({
            'time': `${t.startTime}-${t.endTime ?? t.startTime}`,
            'startMin': sm,
            'endMin': em,
            'title': t.title,
            'location': (t.location !== null && t.location.length > 0) ? t.location : '',
            'color': color !== undefined ? color : '#E3E3E6',
            'done': t.isCompleted
          });
        }
        if (dayTimed.length > 10) {
          dayTasks.push({
            'time': '',
            'title': `…还有 ${dayTimed.length - 10} 项`,
            'location': '',
            'color': '#00000000',
            'done': false
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
        'rev': `${Date.now()}`,
        'masterName': master.name,
        'selectedNames': selectedNames.join(' · '),
        'weekRange': `${DateUtils.md(weekStart)} - ${DateUtils.md(DateUtils.addDays(weekStart, 6))}`,
        'weekNo': weekNo === null ? '' : `第${weekNo}周`,
        'todayText': DateUtils.cnDate(today),
        'days': days,
        'todos': todos,
        'todoCount': todos.length,
        'hasData': true
      };
    } catch (e) {
      return CardDataBuilder.empty('计划本', '数据加载失败，点刷新重试');
    }
  }

  private static empty(message: string, hint: string): Record<string, Object> {
    return {
      'rev': '0',
      'masterName': message,
      'selectedNames': '',
      'weekRange': '',
      'weekNo': '',
      'todayText': hint,
      'days': [] as Record<string, Object>[],
      'todos': [] as Record<string, Object>[],
      'todoCount': 0,
      'hasData': false
    };
  }
}
