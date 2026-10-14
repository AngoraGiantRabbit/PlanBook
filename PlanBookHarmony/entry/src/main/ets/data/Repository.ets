import { relationalStore } from '@kit.ArkData';
import { common } from '@kit.AbilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { DateUtils } from '../common/DateUtils';
import { IcsTask } from './IcsParser';
import {
  Notebook, Review, ReviewSetting, ReviewType, RepeatRule, SubNotebookPalette, Task, TaskType
} from '../model/Models';

const TAG = 'PlanBook.Repository';
const DB_NAME = 'planbook.db';

/** 变更类型（观察者据此判断是否需要重查；当前统一全量重查，仅用于日志） */
export enum ChangeKind {
  NOTEBOOKS = 'notebooks',
  TASKS = 'tasks',
  COMPLETIONS = 'completions',
  REVIEWS = 'reviews',
  REVIEW_SETTINGS = 'review_settings'
}

/** #11/#12：ICS 导入结果（replacedOld = 是否替换了同名旧导入子本） */
export class IcsImportResult {
  subNotebookId: number = 0;
  replacedOld: boolean = false;

  constructor(subNotebookId: number, replacedOld: boolean) {
    this.subNotebookId = subNotebookId;
    this.replacedOld = replacedOld;
  }
}

/**
 * 唯一数据门面（对应 Android 侧 PlanBookRepository + Room DAO + Hilt 单例）。
 *
 * 响应式三页联动（v1.8 架构）在鸿蒙侧的实现：
 * 所有写操作经此门面（同一纪律），写完通过 listener 通知观察者；
 * 各页面 VM 订阅变更并重查——语义等同 Room Flow 的「任何表写操作自动重发」。
 * 惰性维护（过期标记/补色/复盘生成）仍只在动作时机触发，其引发的写自然回流通知管道。
 */
export class Repository {
  private static store: relationalStore.RdbStore | null = null;
  private static readyPromise: Promise<void> | null = null;
  private static listeners: Set<() => void> = new Set<() => void>();
  /** 写操作串行链（保证 last_insert_rowid 正确、通知顺序确定） */
  private static chain: Promise<void> = Promise.resolve();

  static init(context: common.Context): void {
    if (Repository.readyPromise !== null) {
      return;
    }
    Repository.readyPromise = Repository.open(context);
  }

  private static async open(context: common.Context): Promise<void> {
    const config: relationalStore.StoreConfig = {
      name: DB_NAME,
      securityLevel: relationalStore.SecurityLevel.S1
    };
    Repository.store = await relationalStore.getRdbStore(context, config);
    const ddl: string[] = [
      `CREATE TABLE IF NOT EXISTS notebooks (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        parentId INTEGER,
        color TEXT,
        isVisible INTEGER NOT NULL DEFAULT 1,
        isActive INTEGER NOT NULL DEFAULT 0,
        importSource TEXT,
        createdAt INTEGER NOT NULL
      )`,
      `CREATE TABLE IF NOT EXISTS tasks (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        notebookId INTEGER NOT NULL,
        title TEXT NOT NULL,
        description TEXT NOT NULL DEFAULT '',
        location TEXT,
        type TEXT NOT NULL,
        startDate TEXT NOT NULL,
        endDate TEXT NOT NULL,
        startTime TEXT,
        endTime TEXT,
        repeatRule TEXT,
        weeklyDays TEXT,
        isCompleted INTEGER NOT NULL DEFAULT 0,
        completedAt INTEGER,
        isAutoReview INTEGER NOT NULL DEFAULT 0,
        reviewType TEXT,
        createdAt INTEGER NOT NULL
      )`,
      `CREATE TABLE IF NOT EXISTS reviews (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        notebookId INTEGER NOT NULL,
        date TEXT NOT NULL,
        content TEXT NOT NULL DEFAULT '',
        updatedAt INTEGER NOT NULL
      )`,
      `CREATE TABLE IF NOT EXISTS review_settings (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        notebookId INTEGER NOT NULL,
        reviewType TEXT NOT NULL,
        enabled INTEGER NOT NULL DEFAULT 1,
        startTime TEXT NOT NULL,
        endTime TEXT NOT NULL,
        UNIQUE(notebookId, reviewType)
      )`,
      `CREATE TABLE IF NOT EXISTS task_completions (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        taskId INTEGER NOT NULL,
        date TEXT NOT NULL,
        completedAt INTEGER NOT NULL,
        UNIQUE(taskId, date)
      )`
    ];
    for (const sql of ddl) {
      await Repository.store.executeSql(sql);
    }
  }

  static async whenReady(): Promise<void> {
    if (Repository.readyPromise === null) {
      throw new Error('Repository 未初始化：需要先在 EntryAbility 调用 init(context)');
    }
    await Repository.readyPromise;
  }

  /** 订阅数据变更（写操作后触发）；返回取消订阅函数 */
  static subscribe(listener: () => void): () => void {
    Repository.listeners.add(listener);
    return () => {
      Repository.listeners.delete(listener);
    };
  }

  private static notify(): void {
    for (const l of Repository.listeners) {
      try {
        l();
      } catch (e) {
        hilog.error(0, TAG, 'listener error: %{public}s', `${e}`);
      }
    }
  }

  /** 写操作统一走串行链：避免并发写交错，保证 last_insert_rowid 可靠 */
  private static enqueue<T>(job: () => Promise<T>): Promise<T> {
    const run = Repository.chain.then(job);
    Repository.chain = run.then(() => {}, () => {});
    return run;
  }

  // ---------- 底层查询助手（SELECT 列序固定，按索引读取） ----------

  /** 已就绪的 RDB 句柄（whenReady 之后必非空；收窄给编译器看） */
  private static db(): relationalStore.RdbStore {
    const s = Repository.store;
    if (s === null) {
      throw new Error('数据库未初始化：需要先在 EntryAbility/FormAbility 调用 Repository.init(context)');
    }
    return s;
  }

  private static async q(sql: string, args: relationalStore.ValueType[]): Promise<relationalStore.ResultSet> {
    await Repository.whenReady();
    return Repository.db().querySql(sql, args);
  }

  private static async run(sql: string, args: relationalStore.ValueType[]): Promise<relationalStore.ValueType> {
    await Repository.whenReady();
    return Repository.db().execute(sql, args);
  }

  private static notebookFromRow(rs: relationalStore.ResultSet): Notebook {
    const n = new Notebook();
    n.id = rs.getLong(0);
    n.name = rs.getString(1);
    n.parentId = rs.isColumnNull(2) ? null : rs.getLong(2);
    n.color = rs.isColumnNull(3) ? null : rs.getString(3);
    n.isVisible = rs.getLong(4) !== 0;
    n.isActive = rs.getLong(5) !== 0;
    n.importSource = rs.isColumnNull(6) ? null : rs.getString(6);
    n.createdAt = rs.getLong(7);
    return n;
  }

  private static taskFromRow(rs: relationalStore.ResultSet): Task {
    const t = new Task();
    t.id = rs.getLong(0);
    t.notebookId = rs.getLong(1);
    t.title = rs.getString(2);
    t.description = rs.getString(3);
    t.location = rs.isColumnNull(4) ? null : rs.getString(4);
    t.type = rs.getString(5) as TaskType;
    t.startDate = rs.getString(6);
    t.endDate = rs.getString(7);
    t.startTime = rs.isColumnNull(8) ? null : rs.getString(8);
    t.endTime = rs.isColumnNull(9) ? null : rs.getString(9);
    t.repeatRule = rs.isColumnNull(10) ? null : rs.getString(10) as RepeatRule;
    t.weeklyDays = rs.isColumnNull(11) ? null : rs.getString(11);
    t.isCompleted = rs.getLong(12) !== 0;
    t.completedAt = rs.isColumnNull(13) ? null : rs.getLong(13);
    t.isAutoReview = rs.getLong(14) !== 0;
    t.reviewType = rs.isColumnNull(15) ? null : rs.getString(15) as ReviewType;
    t.createdAt = rs.getLong(16);
    return t;
  }

  private static taskCols(): string {
    return 'id, notebookId, title, description, location, type, startDate, endDate, startTime, endTime, ' +
      'repeatRule, weeklyDays, isCompleted, completedAt, isAutoReview, reviewType, createdAt';
  }

  // ---------- Notebook（ADR-0004） ----------

  /** 主计划本（全局唯一；复盘、复盘设置、自动复盘待办挂它） */
  static async getMasterNotebookOnce(): Promise<Notebook | null> {
    const rs = await Repository.q('SELECT * FROM notebooks WHERE parentId IS NULL LIMIT 1', []);
    let result: Notebook | null = null;
    if (rs.goToNextRow()) {
      result = Repository.notebookFromRow(rs);
    }
    rs.close();
    return result;
  }

  /** 某主计划本下的全部子计划本（含隐藏），按创建序 */
  static async getSubNotebooksOnce(masterId: number): Promise<Notebook[]> {
    const rs = await Repository.q('SELECT * FROM notebooks WHERE parentId = ? ORDER BY createdAt', [masterId]);
    const out: Notebook[] = [];
    while (rs.goToNextRow()) {
      out.push(Repository.notebookFromRow(rs));
    }
    rs.close();
    return out;
  }

  /** 活动子计划本（写操作目标） */
  static async getActiveSubNotebookOnce(): Promise<Notebook | null> {
    const master = await Repository.getMasterNotebookOnce();
    if (master === null) {
      return null;
    }
    const subs = await Repository.getSubNotebooksOnce(master.id);
    for (const s of subs) {
      if (s.isActive) {
        return s;
      }
    }
    return null;
  }

  /** 首次启动引导：创建主计划本（带默认复盘设置）+ 第一个子计划本（活动） */
  static bootstrapNotebook(firstSubName: string): Promise<number> {
    return Repository.enqueue(async (): Promise<number> => {
      const masterId = await Repository.insertNotebookRow('主计划本', null, null, true, false, null);
      await Repository.createDefaultReviewSettings(masterId);
      await Repository.createSubNotebookLocked(masterId, firstSubName);
      Repository.notify();
      return masterId;
    });
  }

  private static async insertNotebookRow(name: string, parentId: number | null, color: string | null,
    isVisible: boolean, isActive: boolean, importSource: string | null): Promise<number> {
    const v = await Repository.run(
      'INSERT INTO notebooks (name, parentId, color, isVisible, isActive, importSource, createdAt) ' +
      'VALUES (?, ?, ?, ?, ?, ?, ?)',
      [name, parentId, color, isVisible ? 1 : 0, isActive ? 1 : 0, importSource, Date.now()]);
    return Repository.toRowId(v);
  }

  /** INSERT 返回值 → 行 id（execute 对 INSERT 返回生成行号） */
  private static toRowId(v: relationalStore.ValueType): number {
    return typeof v === 'number' ? v : -1;
  }

  /** 新建子计划本：调色板按现有数量轮转分配颜色；若当前无活动子本则设为活动 */
  static createSubNotebook(masterId: number, name: string): Promise<number> {
    return Repository.enqueue(async (): Promise<number> => {
      const id = await Repository.createSubNotebookLocked(masterId, name);
      Repository.notify();
      return id;
    });
  }

  private static async createSubNotebookLocked(masterId: number, name: string): Promise<number> {
    const existing = await Repository.getSubNotebooksOnce(masterId);
    let anyActive = false;
    for (const s of existing) {
      if (s.isActive) {
        anyActive = true;
        break;
      }
    }
    return Repository.insertNotebookRow(name, masterId, SubNotebookPalette.forIndex(existing.length),
      true, !anyActive, null);
  }

  /**
   * #11（ADR-0005）：ICS 导入建本——生成导入子计划本（只读快照标记 importSource），
   * 任务为解析展开出的单天临时任务。
   * #12：同 importSource 的旧导入子本整本删除重建（完成态丢弃）；旧本为活动子本时新本继承。
   * 整体在一个事务里：失败回滚，不产生半成品。
   */
  static importIcsSubNotebook(masterId: number, sourceName: string, icsTasks: IcsTask[]): Promise<IcsImportResult> {
    return Repository.enqueue(async (): Promise<IcsImportResult> => {
      await Repository.whenReady();
      // #12：重导整本重建——先删同名旧导入子本（含其任务）
      const oldRs = await Repository.q(
        'SELECT * FROM notebooks WHERE parentId = ? AND importSource = ? LIMIT 1', [masterId, sourceName]);
      let old: Notebook | null = null;
      if (oldRs.goToNextRow()) {
        old = Repository.notebookFromRow(oldRs);
      }
      oldRs.close();
      let inheritActive = false;
      let inheritColor: string | null = null;
      if (old !== null) {
        await Repository.run('DELETE FROM task_completions WHERE taskId IN (SELECT id FROM tasks WHERE notebookId = ?)', [old.id]);
        await Repository.run('DELETE FROM tasks WHERE notebookId = ?', [old.id]);
        await Repository.run('DELETE FROM notebooks WHERE id = ?', [old.id]);
        inheritActive = old.isActive;
        inheritColor = old.color; // 保留旧本颜色，用户改过的不丢
      }
      const existing = await Repository.getSubNotebooksOnce(masterId);
      const subId = await Repository.insertNotebookRow(sourceName, masterId,
        inheritColor ?? SubNotebookPalette.forIndex(existing.length), true, inheritActive, sourceName);
      const store = Repository.db();
      store.beginTransaction();
      try {
        for (const t of icsTasks) {
          await store.executeSql(
            'INSERT INTO tasks (notebookId, title, description, location, type, startDate, endDate, ' +
            'startTime, endTime, isCompleted, completedAt, isAutoReview, reviewType, createdAt) ' +
            'VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NULL, 0, NULL, ?)',
            [subId, t.title, t.description, t.location, TaskType.ONE_OFF, t.date, t.date,
              t.startTime, t.endTime, Date.now()]);
        }
        store.commit();
      } catch (e) {
        store.rollBack();
        throw new Error(`ICS 导入失败：${e}`);
      }
      Repository.notify();
      return new IcsImportResult(subId, old !== null);
    });
  }

  /** 切换活动子计划本（写操作目标） */
  static setActiveSubNotebook(subNotebookId: number): Promise<void> {
    return Repository.enqueue(async (): Promise<void> => {
      const rs = await Repository.q('SELECT * FROM notebooks WHERE id = ? LIMIT 1', [subNotebookId]);
      let sub: Notebook | null = null;
      if (rs.goToNextRow()) {
        sub = Repository.notebookFromRow(rs);
      }
      rs.close();
      if (sub === null || sub.parentId === null) {
        return;
      }
      await Repository.run('UPDATE notebooks SET isActive = 0 WHERE parentId = ?', [sub.parentId]);
      await Repository.run('UPDATE notebooks SET isActive = 1 WHERE id = ?', [subNotebookId]);
      Repository.notify();
    });
  }

  /** 显示开关：只影响计划本页聚合显示，不动数据 */
  static setSubNotebookVisible(subNotebookId: number, visible: boolean): Promise<void> {
    return Repository.enqueue(async (): Promise<void> => {
      await Repository.run('UPDATE notebooks SET isVisible = ? WHERE id = ?',
        [visible ? 1 : 0, subNotebookId]);
      Repository.notify();
    });
  }

  /** 更新计划本整实体（重命名/改色共用） */
  static renameNotebook(notebook: Notebook): Promise<void> {
    return Repository.enqueue(async (): Promise<void> => {
      await Repository.run(
        'UPDATE notebooks SET name = ?, parentId = ?, color = ?, isVisible = ?, isActive = ?, ' +
        'importSource = ?, createdAt = ? WHERE id = ?',
        [notebook.name, notebook.parentId, notebook.color, notebook.isVisible ? 1 : 0,
          notebook.isActive ? 1 : 0, notebook.importSource, notebook.createdAt, notebook.id]);
      Repository.notify();
    });
  }

  /** 删除子计划本（含其任务与按天完成记录）；若删的是活动子本，最早的剩余子本设为活动 */
  static deleteNotebook(notebook: Notebook): Promise<void> {
    return Repository.enqueue(async (): Promise<void> => {
      await Repository.run('DELETE FROM task_completions WHERE taskId IN (SELECT id FROM tasks WHERE notebookId = ?)',
        [notebook.id]);
      await Repository.run('DELETE FROM tasks WHERE notebookId = ?', [notebook.id]);
      await Repository.run('DELETE FROM notebooks WHERE id = ?', [notebook.id]);
      if (notebook.parentId === null) {
        return;
      }
      const remaining = await Repository.getSubNotebooksOnce(notebook.parentId);
      if (notebook.isActive && remaining.length > 0) {
        await Repository.run('UPDATE notebooks SET isActive = 0 WHERE parentId = ?', [notebook.parentId]);
        await Repository.run('UPDATE notebooks SET isActive = 1 WHERE id = ?', [remaining[0].id]);
      }
      Repository.notify();
    });
  }

  // ---------- Task ----------

  static addTask(task: Task): Promise<void> {
    return Repository.enqueue(async (): Promise<void> => {
      await Repository.insertTaskRow(task);
      Repository.notify();
    });
  }

  private static async insertTaskRow(task: Task): Promise<number> {
    const v = await Repository.run(
      'INSERT INTO tasks (notebookId, title, description, location, type, startDate, endDate, ' +
      'startTime, endTime, repeatRule, weeklyDays, isCompleted, completedAt, isAutoReview, reviewType, createdAt) ' +
      'VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
      [task.notebookId, task.title, task.description, task.location, task.type, task.startDate, task.endDate,
        task.startTime, task.endTime, task.repeatRule, task.weeklyDays, task.isCompleted ? 1 : 0,
        task.completedAt, task.isAutoReview ? 1 : 0, task.reviewType, task.createdAt === 0 ? Date.now() : task.createdAt]);
    return Repository.toRowId(v);
  }

  static async getTaskById(taskId: number): Promise<Task | null> {
    const rs = await Repository.q(`SELECT ${Repository.taskCols()} FROM tasks WHERE id = ? LIMIT 1`, [taskId]);
    let result: Task | null = null;
    if (rs.goToNextRow()) {
      result = Repository.taskFromRow(rs);
    }
    rs.close();
    return result;
  }

  /** 更新任务整实体 */
  static updateTask(task: Task): Promise<void> {
    return Repository.enqueue(async (): Promise<void> => {
      await Repository.run(
        'UPDATE tasks SET notebookId = ?, title = ?, description = ?, location = ?, type = ?, ' +
        'startDate = ?, endDate = ?, startTime = ?, endTime = ?, repeatRule = ?, weeklyDays = ?, ' +
        'isCompleted = ?, completedAt = ?, isAutoReview = ?, reviewType = ? WHERE id = ?',
        [task.notebookId, task.title, task.description, task.location, task.type, task.startDate, task.endDate,
          task.startTime, task.endTime, task.repeatRule, task.weeklyDays, task.isCompleted ? 1 : 0,
          task.completedAt, task.isAutoReview ? 1 : 0, task.reviewType, task.id]);
      Repository.notify();
    });
  }

  static deleteTask(task: Task): Promise<void> {
    return Repository.enqueue(async (): Promise<void> => {
      await Repository.run('DELETE FROM task_completions WHERE taskId = ?', [task.id]);
      await Repository.run('DELETE FROM tasks WHERE id = ?', [task.id]);
      Repository.notify();
    });
  }

  /**
   * 切换某任务的完成状态（ADR-0001 完成态双模型）。
   * - DAILY：按天记录到 task_completions（每天独立）
   * - 其它（ONE_OFF / FLEX / LONG_TERM）：整体完成，用全局 isCompleted
   * 分流只看 type，不看「是否跨多天」。
   */
  static toggleTaskComplete(task: Task, date: string): Promise<void> {
    return Repository.enqueue(async (): Promise<void> => {
      if (task.type === TaskType.DAILY) {
        const rs = await Repository.q(
          'SELECT EXISTS(SELECT 1 FROM task_completions WHERE taskId = ? AND date = ?)', [task.id, date]);
        let already = false;
        if (rs.goToNextRow()) {
          already = rs.getLong(0) !== 0;
        }
        rs.close();
        if (already) {
          await Repository.run('DELETE FROM task_completions WHERE taskId = ? AND date = ?', [task.id, date]);
        } else {
          await Repository.run(
            'INSERT OR REPLACE INTO task_completions (taskId, date, completedAt) VALUES (?, ?, ?)',
            [task.id, date, Date.now()]);
        }
      } else {
        await Repository.run('UPDATE tasks SET isCompleted = ?, completedAt = ? WHERE id = ?',
          [task.isCompleted ? 0 : 1, task.isCompleted ? null : Date.now(), task.id]);
      }
      Repository.notify();
    });
  }

  // ---------- 展开（四类任务单一实现，ADR-0002/0006） ----------

  /**
   * 长期任务（ADR-0006 三页统一可见性）：仅当查看日落在 [startDate..DDL] 内才返回；
   * 数据层统一做区间过滤，待办页/计划本页/卡片今日待办同口径。
   */
  private static expandTask(task: Task, days: string[]): Task[] {
    if (task.type === TaskType.LONG_TERM) {
      if (!DateUtils.isValid(task.startDate) || !DateUtils.isValid(task.endDate)) {
        return [];
      }
      for (const d of days) {
        if (DateUtils.within(d, task.startDate, task.endDate)) {
          return [task];
        }
      }
      return [];
    }
    if (!DateUtils.isValid(task.startDate) || !DateUtils.isValid(task.endDate)) {
      return [];
    }
    if (task.type === TaskType.DAILY) {
      // 每日任务：按重复规则在 [start..end] 区间内逐天展开，每天独立 copy
      const weeklyDays = Repository.parseWeeklyDays(task.weeklyDays);
      const out: Task[] = [];
      for (const date of days) {
        if (!DateUtils.within(date, task.startDate, task.endDate)) {
          continue;
        }
        let hit = false;
        if (task.repeatRule === RepeatRule.EVERY_DAY) {
          hit = true;
        } else if (task.repeatRule === RepeatRule.WEEKDAYS) {
          hit = weeklyDays.indexOf(DateUtils.dayOfWeek(date)) >= 0;
        }
        if (hit) {
          const copy = Task.copy(task);
          copy.startDate = date;
          copy.endDate = date;
          out.push(copy);
        }
      }
      return out;
    }
    // 灵活/临时任务恒为单天（ADR-0002）：仅当查询日命中任务日期才返回
    for (const d of days) {
      if (DateUtils.within(d, task.startDate, task.endDate)) {
        return [task];
      }
    }
    return [];
  }

  private static parseWeeklyDays(weeklyDays: string | null): number[] {
    if (weeklyDays === null) {
      return [];
    }
    const out: number[] = [];
    for (const tok of weeklyDays.split(',')) {
      const n = Number(tok);
      if (Number.isFinite(n) && n >= 1 && n <= 7) {
        out.push(n);
      }
    }
    return out;
  }

  /**
   * 带完成态的展开版本（ADR-0001）。
   * - DAILY：按天独立完成，按 completedMap 查
   * - 其它：整体完成态，直接用 task.isCompleted
   */
  private static expandTaskWithCompletion(task: Task, days: string[],
    completedMap: Map<string, Set<number>>): Task[] {
    const expanded = Repository.expandTask(task, days);
    const out: Task[] = [];
    for (const t of expanded) {
      const done = task.type === TaskType.DAILY
        ? (completedMap.get(t.startDate)?.has(task.id) ?? false)
        : task.isCompleted;
      const copy = Task.copy(t);
      copy.isCompleted = done;
      out.push(copy);
    }
    return out;
  }

  /** 某日期集的按天完成记录：date → 完成的 taskId 集 */
  private static async getCompletedMap(dates: string[]): Promise<Map<string, Set<number>>> {
    const map = new Map<string, Set<number>>();
    if (dates.length === 0) {
      return map;
    }
    const placeholders = dates.map(() => '?').join(',');
    const rs = await Repository.q(
      `SELECT taskId, date FROM task_completions WHERE date IN (${placeholders})`, dates);
    while (rs.goToNextRow()) {
      const taskId = rs.getLong(0);
      const date = rs.getString(1);
      let set = map.get(date);
      if (set === undefined) {
        set = new Set<number>();
        map.set(date, set);
      }
      set.add(taskId);
    }
    rs.close();
    return map;
  }

  private static async getTasksByNotebookIdsOnce(notebookIds: number[]): Promise<Task[]> {
    if (notebookIds.length === 0) {
      return [];
    }
    const placeholders = notebookIds.map(() => '?').join(',');
    const rs = await Repository.q(
      `SELECT ${Repository.taskCols()} FROM tasks WHERE notebookId IN (${placeholders}) ` +
      'ORDER BY startDate, startTime', notebookIds);
    const out: Task[] = [];
    while (rs.goToNextRow()) {
      out.push(Repository.taskFromRow(rs));
    }
    rs.close();
    return out;
  }

  /**
   * 周视图聚合数据（#7，ADR-0004）：子计划本任务 + 主计划本的自动复盘待办。
   * 小部件等无观察管道场景的一次性入口；页面 VM 走同口径的 loadExpandedTasks。
   */
  static async getExpandedTasksForWeek(masterId: number, subNotebookIds: number[], weekStart: string): Promise<Task[]> {
    await Repository.expireLongTermTasks();
    const allTasks = await Repository.getTasksByNotebookIdsOnce(subNotebookIds.concat([masterId]));
    const weekDays = DateUtils.weekDays(weekStart);
    const completedMap = await Repository.getCompletedMap(weekDays);
    const out: Task[] = [];
    for (const t of allTasks) {
      const expanded = Repository.expandTaskWithCompletion(t, weekDays, completedMap);
      for (const e of expanded) {
        out.push(e);
      }
    }
    return out;
  }

  /** 待办列表页：某一天该显示的所有任务（四类展开后落在该天的） */
  static async getTasksForDay(masterId: number, subNotebookIds: number[], date: string): Promise<Task[]> {
    await Repository.ensureAutoReviewTasks(masterId, DateUtils.weekStartOf(date));
    await Repository.expireLongTermTasks();
    const allTasks = await Repository.getTasksByNotebookIdsOnce(subNotebookIds.concat([masterId]));
    const completedMap = await Repository.getCompletedMap([date]);
    const out: Task[] = [];
    for (const t of allTasks) {
      const expanded = Repository.expandTaskWithCompletion(t, [date], completedMap);
      for (const e of expanded) {
        out.push(e);
      }
    }
    return out;
  }

  /** 三页联动：VM 的重查入口（语义等同 observeExpandedTasks 的即时值） */
  static async loadExpandedTasks(masterId: number, subNotebookIds: number[], days: string[]): Promise<Task[]> {
    const allTasks = await Repository.getTasksByNotebookIdsOnce(subNotebookIds.concat([masterId]));
    const completedMap = await Repository.getCompletedMap(days);
    const out: Task[] = [];
    for (const t of allTasks) {
      const expanded = Repository.expandTaskWithCompletion(t, days, completedMap);
      for (const e of expanded) {
        out.push(e);
      }
    }
    return out;
  }

  /** 当前活跃长期任务（复盘页「待细化」；按可见子本集合+主本、查看日区间） */
  static async getLongTermActive(notebookIds: number[], today: string): Promise<Task[]> {
    const all = await Repository.getTasksByNotebookIdsOnce(notebookIds);
    const out: Task[] = [];
    for (const t of all) {
      if (t.type === TaskType.LONG_TERM && !t.isCompleted &&
          DateUtils.within(today, t.startDate, t.endDate)) {
        out.push(t);
      }
    }
    out.sort((a, b) => DateUtils.cmp(a.endDate, b.endDate));
    return out;
  }

  // ---------- 惰性维护 ----------

  /** 惰性维护聚合（动作时机触发，幂等）：长期任务过期标记、补色、按周生成复盘自动待办 */
  static async ensureDerivedData(masterId: number, weekStart: string): Promise<void> {
    await Repository.enqueue(async (): Promise<void> => {
      await Repository.expireLongTermTasks();
      await Repository.ensureSubNotebookColors(masterId);
      await Repository.ensureAutoReviewTasks(masterId, weekStart);
    });
  }

  /** ADR-0003：长期任务过 DDL 自动标记完成（惰性触发，无后台任务） */
  private static async expireLongTermTasks(): Promise<void> {
    await Repository.run(
      "UPDATE tasks SET isCompleted = 1, completedAt = ? WHERE type = 'LONG_TERM' AND isCompleted = 0 AND endDate < ?",
      [Date.now(), DateUtils.today()]);
  }

  /** #9：无颜色子计划本按创建序补色（幂等） */
  private static async ensureSubNotebookColors(masterId: number): Promise<void> {
    const subs = await Repository.getSubNotebooksOnce(masterId);
    for (let i = 0; i < subs.length; i++) {
      if (subs[i].color === null) {
        const updated = Notebook.copy(subs[i]);
        updated.color = SubNotebookPalette.forIndex(i);
        await Repository.renameNotebook(updated);
      }
    }
  }

  // ---------- Review ----------

  static async getReview(notebookId: number, date: string): Promise<Review | null> {
    const rs = await Repository.q(
      'SELECT id, notebookId, date, content, updatedAt FROM reviews WHERE notebookId = ? AND date = ? LIMIT 1',
      [notebookId, date]);
    let result: Review | null = null;
    if (rs.goToNextRow()) {
      const r = new Review();
      r.id = rs.getLong(0);
      r.notebookId = rs.getLong(1);
      r.date = rs.getString(2);
      r.content = rs.getString(3);
      r.updatedAt = rs.getLong(4);
      result = r;
    }
    rs.close();
    return result;
  }

  /** 有复盘内容（非空白）的日期集合（复盘页月历圆点用） */
  static async getReviewDatesWithContent(notebookId: number): Promise<Set<string>> {
    const rs = await Repository.q(
      'SELECT date, content FROM reviews WHERE notebookId = ? ORDER BY date', [notebookId]);
    const out = new Set<string>();
    while (rs.goToNextRow()) {
      if (rs.getString(1).trim().length > 0) {
        out.add(rs.getString(0));
      }
    }
    rs.close();
    return out;
  }

  /** 保存复盘文本（按 notebookId+date upsert） */
  static saveReview(review: Review): Promise<void> {
    return Repository.enqueue(async (): Promise<void> => {
      const existing = await Repository.getReview(review.notebookId, review.date);
      if (existing === null) {
        await Repository.run(
          'INSERT INTO reviews (notebookId, date, content, updatedAt) VALUES (?, ?, ?, ?)',
          [review.notebookId, review.date, review.content, Date.now()]);
      } else {
        await Repository.run('UPDATE reviews SET content = ?, updatedAt = ? WHERE id = ?',
          [review.content, Date.now(), existing.id]);
      }
      Repository.notify();
    });
  }

  // ---------- Review Settings ----------

  static async getReviewSettingsOnce(notebookId: number): Promise<ReviewSetting[]> {
    const rs = await Repository.q(
      'SELECT id, notebookId, reviewType, enabled, startTime, endTime FROM review_settings WHERE notebookId = ?',
      [notebookId]);
    const out: ReviewSetting[] = [];
    while (rs.goToNextRow()) {
      const s = new ReviewSetting();
      s.id = rs.getLong(0);
      s.notebookId = rs.getLong(1);
      s.reviewType = rs.getString(2) as ReviewType;
      s.enabled = rs.getLong(3) !== 0;
      s.startTime = rs.getString(4);
      s.endTime = rs.getString(5);
      out.push(s);
    }
    rs.close();
    // 保证三类齐全且固定顺序（DAILY/WEEKLY/MONTHLY）
    const ordered: ReviewSetting[] = [];
    const order: ReviewType[] = [ReviewType.DAILY, ReviewType.WEEKLY, ReviewType.MONTHLY];
    for (const type of order) {
      let found: ReviewSetting | null = null;
      for (const s of out) {
        if (s.reviewType === type) {
          found = s;
          break;
        }
      }
      if (found !== null) {
        ordered.push(found);
      }
    }
    return ordered;
  }

  static saveReviewSetting(setting: ReviewSetting): Promise<void> {
    return Repository.enqueue(async (): Promise<void> => {
      await Repository.run(
        'INSERT OR REPLACE INTO review_settings (id, notebookId, reviewType, enabled, startTime, endTime) ' +
        'VALUES (?, ?, ?, ?, ?, ?)',
        [setting.id === 0 ? null : setting.id, setting.notebookId, setting.reviewType,
          setting.enabled ? 1 : 0, setting.startTime, setting.endTime]);
      Repository.notify();
    });
  }

  /**
   * #6：设置某类复盘的开关。
   * 关闭时同步删除该类型已生成但未完成的自动复盘待办（已完成保留作历史），
   * 首页立即与设置一致；重新打开后按查看的周重新生成。
   */
  static setReviewEnabled(notebookId: number, type: ReviewType, enabled: boolean): Promise<void> {
    return Repository.enqueue(async (): Promise<void> => {
      const settings = await Repository.getReviewSettingsOnce(notebookId);
      let setting: ReviewSetting | null = null;
      for (const s of settings) {
        if (s.reviewType === type) {
          setting = s;
          break;
        }
      }
      if (setting === null) {
        return;
      }
      const updated = setting;
      updated.enabled = enabled;
      await Repository.run(
        'UPDATE review_settings SET enabled = ?, startTime = ?, endTime = ? WHERE id = ?',
        [enabled ? 1 : 0, updated.startTime, updated.endTime, updated.id]);
      if (!enabled) {
        await Repository.run(
          'DELETE FROM tasks WHERE notebookId = ? AND isAutoReview = 1 AND reviewType = ? AND isCompleted = 0',
          [notebookId, type]);
      }
      Repository.notify();
    });
  }

  private static async createDefaultReviewSettings(notebookId: number): Promise<void> {
    const defaults: ReviewType[] = [ReviewType.DAILY, ReviewType.WEEKLY, ReviewType.MONTHLY];
    for (const type of defaults) {
      await Repository.run(
        'INSERT OR REPLACE INTO review_settings (notebookId, reviewType, enabled, startTime, endTime) ' +
        'VALUES (?, ?, 1, ?, ?)', [notebookId, type, '21:00', '21:30']);
    }
  }

  // ---------- 自动复盘待办生成 ----------

  static ensureAutoReviewTasks(notebookId: number, weekStart: string): Promise<void> {
    return Repository.enqueue(async (): Promise<void> => {
      // 清理历史重复生成的复盘任务（保留每组最早一条）
      const dupRs = await Repository.q(
        'SELECT id FROM tasks WHERE isAutoReview = 1 AND id NOT IN ' +
        '(SELECT MIN(id) FROM tasks WHERE isAutoReview = 1 GROUP BY notebookId, reviewType, startDate)', []);
      const dupIds: number[] = [];
      while (dupRs.goToNextRow()) {
        dupIds.push(dupRs.getLong(0));
      }
      dupRs.close();
      for (const id of dupIds) {
        await Repository.run('DELETE FROM tasks WHERE id = ?', [id]);
      }
      const settings = await Repository.getReviewSettingsOnce(notebookId);
      const weekDays = DateUtils.weekDays(weekStart);
      for (const setting of settings) {
        // 目标日期：每日→每天；每周→本周周日；每月→本月最后一天
        const datesToCreate: string[] = [];
        if (setting.reviewType === ReviewType.DAILY) {
          for (const d of weekDays) {
            datesToCreate.push(d);
          }
        } else if (setting.reviewType === ReviewType.WEEKLY) {
          datesToCreate.push(DateUtils.addDays(weekStart, 6)); // 周日
        } else {
          datesToCreate.push(DateUtils.monthEndOf(weekStart));
        }
        const title = setting.reviewType === ReviewType.DAILY ? '📝 每日复盘'
          : setting.reviewType === ReviewType.WEEKLY ? '📝 每周复盘' : '📝 每月复盘';
        for (const date of datesToCreate) {
          // 精确查重：notebookId + reviewType + startDate
          const existRs = await Repository.q(
            'SELECT EXISTS(SELECT 1 FROM tasks WHERE notebookId = ? AND isAutoReview = 1 AND reviewType = ? AND startDate = ?)',
            [notebookId, setting.reviewType, date]);
          let exists = false;
          if (existRs.goToNextRow()) {
            exists = existRs.getLong(0) !== 0;
          }
          existRs.close();
          if (!exists && setting.enabled) {
            const task = new Task();
            task.notebookId = notebookId;
            task.title = title;
            task.type = TaskType.ONE_OFF;
            task.startDate = date;
            task.endDate = date;
            task.startTime = setting.startTime;
            task.endTime = setting.endTime;
            task.isAutoReview = true;
            task.reviewType = setting.reviewType;
            task.createdAt = Date.now();
            await Repository.insertTaskRow(task);
          }
        }
      }
    });
  }
}
