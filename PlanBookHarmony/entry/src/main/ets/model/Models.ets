/**
 * 领域模型（与 Android 版 model/Models.kt 一一对应，语义见根目录 CONTEXT.md）。
 */

/** 任务四类型（ADR-0002） */
export enum TaskType {
  FLEX = 'FLEX',        // 灵活：单天、无时段
  ONE_OFF = 'ONE_OFF',  // 临时：单天、可选时段
  DAILY = 'DAILY',      // 每日：区间 + 重复规则，按天完成
  LONG_TERM = 'LONG_TERM' // 长期：开始日 + DDL，整体完成
}

export enum RepeatRule {
  EVERY_DAY = 'EVERY_DAY',
  WEEKDAYS = 'WEEKDAYS' // 每周指定几天（weeklyDays）
}

export enum ReviewType {
  DAILY = 'DAILY',
  WEEKLY = 'WEEKLY',
  MONTHLY = 'MONTHLY'
}

/** 计划本（ADR-0004 两层模型）：parentId 为 null → 主计划本（全局唯一） */
export class Notebook {
  id: number = 0;
  name: string = '';
  parentId: number | null = null;
  color: string | null = null;   // 子计划本颜色 "#RRGGBB"
  isVisible: boolean = true;
  isActive: boolean = false;
  importSource: string | null = null; // 非 null = 导入子计划本（只读快照）
  createdAt: number = 0;

  get isMaster(): boolean {
    return this.parentId === null;
  }

  get isImported(): boolean {
    return this.importSource !== null;
  }

  static copy(n: Notebook): Notebook {
    const r = new Notebook();
    r.id = n.id;
    r.name = n.name;
    r.parentId = n.parentId;
    r.color = n.color;
    r.isVisible = n.isVisible;
    r.isActive = n.isActive;
    r.importSource = n.importSource;
    r.createdAt = n.createdAt;
    return r;
  }
}

/** 子计划本调色板：新建时按子计划本序号轮转分配（ADR-0004） */
export class SubNotebookPalette {
  private static readonly colors: string[] = [
    '#7986CB', // 蓝
    '#81C784', // 绿
    '#FFB74D', // 橙
    '#E57373', // 红
    '#BA68C8', // 紫
    '#4DD0E1', // 青
    '#A1887F', // 棕
    '#90A4AE'  // 灰蓝
  ];

  static all(): string[] {
    return SubNotebookPalette.colors.slice();
  }

  static forIndex(index: number): string {
    const n = SubNotebookPalette.colors.length;
    return SubNotebookPalette.colors[((index % n) + n) % n];
  }
}

export class Task {
  id: number = 0;
  notebookId: number = 0;
  title: string = '';
  description: string = '';
  location: string | null = null;  // 教室（ICS LOCATION；块内显示用）
  type: TaskType = TaskType.ONE_OFF;
  startDate: string = '';  // yyyy-MM-dd
  endDate: string = '';    // yyyy-MM-dd
  startTime: string | null = null; // HH:mm，可选
  endTime: string | null = null;   // HH:mm，可选
  repeatRule: RepeatRule | null = null;
  weeklyDays: string | null = null; // "1,3,5"
  isCompleted: boolean = false;
  completedAt: number | null = null;
  isAutoReview: boolean = false;
  reviewType: ReviewType | null = null;
  createdAt: number = 0;

  static copy(t: Task): Task {
    const r = new Task();
    r.id = t.id;
    r.notebookId = t.notebookId;
    r.title = t.title;
    r.description = t.description;
    r.location = t.location;
    r.type = t.type;
    r.startDate = t.startDate;
    r.endDate = t.endDate;
    r.startTime = t.startTime;
    r.endTime = t.endTime;
    r.repeatRule = t.repeatRule;
    r.weeklyDays = t.weeklyDays;
    r.isCompleted = t.isCompleted;
    r.completedAt = t.completedAt;
    r.isAutoReview = t.isAutoReview;
    r.reviewType = t.reviewType;
    r.createdAt = t.createdAt;
    return r;
  }
}

export class Review {
  id: number = 0;
  notebookId: number = 0;
  date: string = '';   // yyyy-MM-dd
  content: string = '';
  updatedAt: number = 0;
}

export class ReviewSetting {
  id: number = 0;
  notebookId: number = 0;
  reviewType: ReviewType = ReviewType.DAILY;
  enabled: boolean = true;
  startTime: string = '21:00';
  endTime: string = '21:30';
}

export class TaskTypeLabels {
  static label(t: TaskType): string {
    switch (t) {
      case TaskType.FLEX:
        return '灵活';
      case TaskType.ONE_OFF:
        return '临时';
      case TaskType.DAILY:
        return '每日';
      case TaskType.LONG_TERM:
        return '长期';
      default:
        return '?';
    }
  }

  static repeatRuleLabel(r: RepeatRule): string {
    return r === RepeatRule.EVERY_DAY ? '每天' : '每周几天';
  }

  static reviewTypeLabel(r: ReviewType): string {
    switch (r) {
      case ReviewType.DAILY:
        return '每日复盘';
      case ReviewType.WEEKLY:
        return '每周复盘';
      case ReviewType.MONTHLY:
        return '每月复盘';
      default:
        return '?';
    }
  }

  /** 每周第几天（ISO 1..7）→ 中文 */
  static dayName(day: number): string {
    const names = ['一', '二', '三', '四', '五', '六', '日'];
    const idx = day - 1;
    return idx >= 0 && idx < 7 ? names[idx] : '?';
  }
}
