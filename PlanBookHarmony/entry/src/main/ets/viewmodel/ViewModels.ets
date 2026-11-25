import { Repository } from '../data/Repository';
import { IcsParser } from '../data/IcsParser';
import { CourseInfo } from '../common/CourseInfo';
import { DateUtils } from '../common/DateUtils';
import {
  CourseOccDraft, Notebook, Review, ReviewSetting, ReviewType, SubNotebookPalette, Task, TaskType
} from '../model/Models';

/**
 * 页面 ViewModel 集（对应 Android 侧 viewmodel/ 五个类）。
 *
 * 三页联动的实现：每个 VM 订阅 Repository 变更通知，写操作（任意页面勾选/增删/导入/复盘生成）
 * 之后统一重查——语义等同 Android 的 combine + flatMapLatest 观察管道。
 * VM 在页面 aboutToAppear 订阅、aboutToDisappear 退订。
 */

/** 计划本页（周视图）VM */
@ObservedV2
export class PlanBookVM {
  @Trace master: Notebook | null = null;
  @Trace subs: Notebook[] = [];
  @Trace activeSub: Notebook | null = null;
  @Trace tasks: Task[] = [];
  @Trace weekStart: string = DateUtils.weekStartOf(DateUtils.today());
  @Trace selectedDate: string = DateUtils.today();
  @Trace showAddDialog: boolean = false;
  @Trace prefill: Task | null = null;
  @Trace editing: Task | null = null;
  /** 课程编辑（课表子本的任务以课程形态编辑，见 CourseEditSheet） */
  @Trace showAddCourse: boolean = false;
  @Trace coursePrefill: Task | null = null;
  @Trace editingCourse: Task | null = null;
  @Trace courseReadOnly: boolean = false;
  /** 课程颜色（按课表：课表id → 课程名 → 颜色；未设置的课回退课表色） */
  @Trace courseColors: Map<string, Map<string, string>> = new Map<string, Map<string, string>>();
  @Trace weekNo: string = '';
  @Trace isLoading: boolean = false;

  private unsub: (() => void) | null = null;
  private ensureOnceDone: boolean = false;

  attach(): void {
    this.unsub = Repository.subscribe(() => {
      this.reload();
    });
    this.reload();
    if (!this.ensureOnceDone) {
      this.ensureOnceDone = true;
      // 首次触发惰性维护（过期标记/补色/复盘生成），引发的写由下一次 reload 收敛
      this.ensureDerived();
    }
  }

  detach(): void {
    if (this.unsub !== null) {
      this.unsub();
      this.unsub = null;
    }
  }

  private async ensureDerived(): Promise<void> {
    try {
      const master = await Repository.getMasterNotebookOnce();
      if (master !== null) {
        await Repository.ensureDerivedData(master.id, this.weekStart);
      }
      this.reload();
    } catch (e) {
      console.error(`PlanBookVM.ensureDerived: ${e}`);
    }
  }

  /** 全量重查（订阅回调 / 状态变化后调用） */
  async reload(): Promise<void> {
    try {
      const master = await Repository.getMasterNotebookOnce();
      const subs = master === null ? [] : await Repository.getSubNotebooksOnce(master.id);
      let active: Notebook | null = null;
      for (const s of subs) {
        if (s.isActive) {
          active = s;
          break;
        }
      }
      this.master = master;
      this.subs = subs;
      this.activeSub = active;
      this.courseColors = await Repository.getAllCourseColors();
      if (master !== null) {
        const visibleIds: number[] = [];
        for (const s of subs) {
          if (s.isVisible) {
            visibleIds.push(s.id);
          }
        }
        this.tasks = await Repository.loadExpandedTasks(
          master.id, visibleIds, DateUtils.weekDays(this.weekStart));
        const weekNo = await Repository.termWeekNo(this.weekStart);
        this.weekNo = weekNo === null ? '' : `第${weekNo}周`;
      } else {
        this.tasks = [];
      }
    } catch (e) {
      console.error(`PlanBookVM.reload: ${e}`);
    }
  }

  /** 首次启动空态：创建主计划本 + 第一个子计划本（ADR-0004 引导） */
  createNotebook(name: string): void {
    Repository.bootstrapNotebook(name).then(() => this.reload());
  }

  /** 新建子计划本（顶栏入口；颜色由调色板轮转分配） */
  createSubNotebook(name: string): void {
    Repository.getMasterNotebookOnce().then((master) => {
      if (master !== null) {
        Repository.createSubNotebook(master.id, name).then(() => this.reload());
      }
    });
  }

  /** 切换活动子计划本（chips 点按 = 换写操作目标） */
  switchActiveSub(notebook: Notebook): void {
    Repository.setActiveSubNotebook(notebook.id).then(() => this.reload());
  }

  showAddTaskDialog(): void {
    this.showAddDialog = true;
    this.prefill = null;
  }

  /** 长按首页 chip：切换该子计划本的显示开关（只影响聚合显示，不动数据） */
  toggleSubVisible(sub: Notebook): void {
    Repository.setSubNotebookVisible(sub.id, !sub.isVisible).then(() => this.reload());
  }

  /** 点空白时段时调用：预填日期和时段，落到活动子计划本 */
  showAddTaskDialogWithPrefill(date: string, startHour: number): void {
    const prefill = new Task();
    prefill.notebookId = this.activeSub === null ? 0 : this.activeSub.id;
    prefill.title = '';
    prefill.type = TaskType.ONE_OFF;
    prefill.startDate = date;
    prefill.endDate = date;
    prefill.startTime = `${startHour}`.padStart(2, '0') + ':00';
    prefill.endTime = `${startHour + 1}`.padStart(2, '0') + ':00';
    this.showAddDialog = true;
    this.prefill = prefill;
  }

  // ---------- 课程（课表子本专用写路径） ----------

  /** 加号 / 空白时段统一入口：活动子本是课表 → 新建课程；否则新建任务 */
  openAdd(): void {
    if (this.activeIsTimetable()) {
      this.openAddCourse();
    } else {
      this.showAddTaskDialog();
    }
  }

  openAddAt(date: string, startHour: number): void {
    if (this.activeIsTimetable()) {
      this.openAddCourseWithPrefill(date,
        `${startHour}`.padStart(2, '0') + ':00', `${startHour + 1}`.padStart(2, '0') + ':00');
    } else {
      this.showAddTaskDialogWithPrefill(date, startHour);
    }
  }

  private activeIsTimetable(): boolean {
    return this.activeSub !== null && this.activeSub.isTimetable;
  }

  private subOf(notebookId: number): Notebook | null {
    for (const s of this.subs) {
      if (s.id === notebookId) {
        return s;
      }
    }
    return null;
  }

  private openAddCourseWithPrefill(date: string, startTime: string, endTime: string): void {
    const prefill = new Task();
    prefill.notebookId = this.activeSub === null ? 0 : this.activeSub.id;
    prefill.title = '';
    prefill.type = TaskType.ONE_OFF;
    prefill.startDate = date;
    prefill.endDate = date;
    prefill.startTime = startTime;
    prefill.endTime = endTime;
    this.showAddCourse = true;
    this.coursePrefill = prefill;
  }

  openAddCourse(): void {
    this.openAddCourseWithPrefill(this.selectedDate, '', '');
  }

  hideAddCourseDialog(): void {
    this.showAddCourse = false;
    this.coursePrefill = null;
  }

  /** 保存整门课（写路径在 Repository.saveCourse）：oldTitle 为空 = 新建 */
  saveCourse(notebookId: number, oldTitle: string, name: string, room: string,
    teacher: string, occs: CourseOccDraft[]): void {
    Repository.saveCourse(notebookId, oldTitle, name, room, teacher, occs).then(() => {
      this.showAddCourse = false;
      this.coursePrefill = null;
      this.editingCourse = null;
      this.reload();
    }).catch((e: Error) => console.error(`saveCourse: ${e}`));
  }

  /** 删除整门课：该课表内同名全部节次一并删除 */
  deleteCourse(representative: Task): void {
    Repository.deleteCourse(representative.notebookId, representative.title).then(() => {
      this.editingCourse = null;
      this.reload();
    });
  }

  closeCourseEditor(): void {
    this.editingCourse = null;
  }

  hideAddTaskDialog(): void {
    this.showAddDialog = false;
    this.prefill = null;
  }

  addTask(task: Task): void {
    Repository.addTask(task).then(() => {
      this.showAddDialog = false;
      this.prefill = null;
      this.reload();
    });
  }

  /** 翻周：任务数据由重查自动跟随 */
  changeWeek(weeksDelta: number): void {
    const newStart = DateUtils.addWeeks(this.weekStart, weeksDelta);
    this.applyWeek(newStart, DateUtils.addWeeks(this.selectedDate, weeksDelta));
  }

  goToThisWeek(): void {
    const today = DateUtils.today();
    this.applyWeek(DateUtils.weekStartOf(today), today);
  }

  private applyWeek(newStart: string, newSelected: string): void {
    this.weekStart = newStart;
    this.selectedDate = newSelected;
    this.reload();
    // 新一周按需生成复盘待办（写库后重查收敛）
    Repository.getMasterNotebookOnce().then((master) => {
      if (master !== null) {
        Repository.ensureAutoReviewTasks(master.id, newStart).then(() => this.reload());
      }
    });
  }

  /** 选择周视图底部的某一天（灵活待办按天显示） */
  selectDate(date: string): void {
    this.selectedDate = date;
    this.reload();
  }

  updateTask(task: Task): void {
    Repository.updateTask(task).then(() => this.reload());
  }

  deleteTask(task: Task): void {
    Repository.deleteTask(task).then(() => {
      this.editing = null;
      this.reload();
    });
  }

  toggleTaskComplete(task: Task): void {
    // 展开后的任务 startDate 已是该天；长期任务 startDate 不变
    Repository.toggleTaskComplete(task, task.startDate).then(() => this.reload());
  }

  /** 点击任务/课程：课表子本的任务 → 课程编辑器（课表未设为活动时只读）；
   *  普通任务 → 任务编辑器（加载原始任务，避免展开 copy 覆盖原始区间）。 */
  openTaskEditor(task: Task): void {
    Repository.getTaskById(task.id).then((original) => {
      const resolved = original ?? task;
      const sub = this.subOf(resolved.notebookId);
      if (sub !== null && sub.isTimetable) {
        this.editingCourse = resolved;
        this.courseReadOnly = this.activeSub === null || this.activeSub.id !== sub.id;
      } else {
        this.editing = resolved;
      }
    });
  }

  saveEditedTask(task: Task): void {
    Repository.updateTask(task).then(() => {
      this.editing = null;
      this.reload();
    });
  }

  closeTaskEditor(): void {
    this.editing = null;
  }

  /** 手动刷新：重新触发惰性维护；UI 由重查更新 */
  refresh(): void {
    this.isLoading = true;
    Repository.getMasterNotebookOnce().then((master) => {
      const p = master === null ? Promise.resolve() :
        Repository.ensureDerivedData(master.id, this.weekStart);
      return p.then(() => {
        this.isLoading = false;
        this.reload();
      });
    }).catch(() => {
      this.isLoading = false;
    });
  }
}

/** 待办列表页 VM */
@ObservedV2
export class TaskListVM {
  @Trace master: Notebook | null = null;
  @Trace subs: Notebook[] = [];
  @Trace activeSub: Notebook | null = null;
  @Trace selectedDate: string = DateUtils.today();
  @Trace tasks: Task[] = [];
  @Trace loaded: boolean = false;
  @Trace showAddDialog: boolean = false;
  @Trace prefill: Task | null = null;
  @Trace editing: Task | null = null;

  private unsub: (() => void) | null = null;
  private ensureOnceDone: boolean = false;

  attach(): void {
    this.unsub = Repository.subscribe(() => {
      this.reload();
    });
    this.reload();
    if (!this.ensureOnceDone) {
      this.ensureOnceDone = true;
      Repository.getMasterNotebookOnce().then((master) => {
        if (master !== null) {
          Repository.ensureDerivedData(master.id, DateUtils.weekStartOf(this.selectedDate))
            .then(() => this.reload());
        }
      });
    }
  }

  detach(): void {
    if (this.unsub !== null) {
      this.unsub();
      this.unsub = null;
    }
  }

  async reload(): Promise<void> {
    try {
      const master = await Repository.getMasterNotebookOnce();
      const subs = master === null ? [] : await Repository.getSubNotebooksOnce(master.id);
      let active: Notebook | null = null;
      for (const s of subs) {
        if (s.isActive) {
          active = s;
          break;
        }
      }
      this.master = master;
      this.subs = subs;
      this.activeSub = active;
      if (master !== null) {
        // 课程（课表子本）不是任务：待办列表只聚合普通子计划本
        const visibleIds: number[] = [];
        for (const s of subs) {
          if (s.isVisible && !s.isTimetable) {
            visibleIds.push(s.id);
          }
        }
        this.tasks = await Repository.loadExpandedTasks(master.id, visibleIds, [this.selectedDate]);
      } else {
        this.tasks = [];
      }
      this.loaded = true;
    } catch (e) {
      console.error(`TaskListVM.reload: ${e}`);
    }
  }

  selectDate(date: string): void {
    this.selectedDate = date;
    this.reload();
    // 切换日期时按需生成该日所在周的复盘待办
    Repository.getMasterNotebookOnce().then((master) => {
      if (master !== null) {
        Repository.ensureAutoReviewTasks(master.id, DateUtils.weekStartOf(date)).then(() => this.reload());
      }
    });
  }

  toggleComplete(task: Task): void {
    Repository.toggleTaskComplete(task, this.selectedDate).then(() => this.reload());
  }

  /** 点加号：预填选中日期，落到活动子计划本（活动的是课表时落到第一个普通子本——课程不进待办列表） */
  openAddDialog(): void {
    let target: Notebook | null = this.activeSub;
    if (target !== null && target.isTimetable) {
      target = null;
      for (const s of this.subs) {
        if (!s.isTimetable && s.isVisible) {
          target = s;
          break;
        }
      }
    }
    const prefill = new Task();
    prefill.notebookId = target === null ? (this.master !== null ? this.master.id : 0) : target.id;
    prefill.title = '';
    prefill.type = TaskType.ONE_OFF;
    prefill.startDate = this.selectedDate;
    prefill.endDate = this.selectedDate;
    this.showAddDialog = true;
    this.prefill = prefill;
  }

  hideAddDialog(): void {
    this.showAddDialog = false;
    this.prefill = null;
  }

  addTask(task: Task): void {
    Repository.addTask(task).then(() => {
      this.showAddDialog = false;
      this.prefill = null;
      this.reload();
    });
  }

  /** 条目点击：加载原始任务，打开编辑器（课程不进待办列表，这里只会是普通任务） */
  openTaskEditor(task: Task): void {
    Repository.getTaskById(task.id).then((original) => {
      this.editing = original ?? task;
    });
  }

  saveEditedTask(task: Task): void {
    Repository.updateTask(task).then(() => {
      this.editing = null;
      this.reload();
    });
  }

  deleteTask(task: Task): void {
    Repository.deleteTask(task).then(() => {
      this.editing = null;
      this.reload();
    });
  }

  closeTaskEditor(): void {
    this.editing = null;
  }
}

/** 复盘页月历 VM */
@ObservedV2
export class ReviewCalendarVM {
  @Trace master: Notebook | null = null;
  @Trace reviewDates: Set<string> = new Set<string>();
  @Trace month: string = DateUtils.monthStartOf(DateUtils.today());

  private unsub: (() => void) | null = null;

  attach(): void {
    this.unsub = Repository.subscribe(() => {
      this.reload();
    });
    this.reload();
  }

  detach(): void {
    if (this.unsub !== null) {
      this.unsub();
      this.unsub = null;
    }
  }

  async reload(): Promise<void> {
    try {
      const master = await Repository.getMasterNotebookOnce();
      this.master = master;
      if (master !== null) {
        this.reviewDates = await Repository.getReviewDatesWithContent(master.id);
      } else {
        this.reviewDates = new Set<string>();
      }
    } catch (e) {
      console.error(`ReviewCalendarVM.reload: ${e}`);
    }
  }

  changeMonth(delta: number): void {
    this.month = DateUtils.addMonths(this.month, delta);
  }
}

/** 复盘编辑页 VM（独立路由、独立实例——不依赖月历 VM 状态） */
@ObservedV2
export class ReviewEditVM {
  @Trace date: string = DateUtils.today();
  @Trace content: string = '';
  @Trace completedTasks: Task[] = [];
  @Trace longTermTasks: Task[] = [];
  @Trace loaded: boolean = false;
  /** 拆分长期任务时子任务落点（活动子计划本） */
  @Trace activeSubId: number = 0;

  private unsub: (() => void) | null = null;
  private editDate: string | null = null;

  attach(): void {
    this.unsub = Repository.subscribe(() => {
      this.reloadTasks();
    });
  }

  detach(): void {
    if (this.unsub !== null) {
      this.unsub();
      this.unsub = null;
    }
  }

  /** 打开某日复盘编辑：任务列表走观察管道自动跟随，这里加载复盘文本与活动子本。
   *  返回的 Promise 完成时 content 已就绪，页面据此回填输入框。 */
  openReviewEdit(date: string): Promise<void> {
    this.date = date;
    this.loaded = false;
    this.editDate = date;
    this.reloadTasks();
    return Repository.getMasterNotebookOnce().then(async (master) => {
      if (master === null) {
        return;
      }
      const review = await Repository.getReview(master.id, date);
      const subs = await Repository.getSubNotebooksOnce(master.id);
      let activeId = master.id;
      for (const s of subs) {
        if (s.isActive) {
          activeId = s.id;
          break;
        }
      }
      this.activeSubId = activeId;
      this.content = review === null ? '' : review.content;
      this.loaded = true;
    }).catch((e: Error) => console.error(`openReviewEdit: ${e}`));
  }

  /** 当日已完成 + 待细化长期任务（ADR-0006：长期任务仅在「完成当天」且 DDL 内入当日完成） */
  private async reloadTasks(): Promise<void> {
    const date = this.editDate;
    if (date === null) {
      return;
    }
    try {
      const master = await Repository.getMasterNotebookOnce();
      if (master === null) {
        return;
      }
      const subs = await Repository.getSubNotebooksOnce(master.id);
      const visibleIds: number[] = [];
      for (const s of subs) {
        if (s.isVisible) {
          visibleIds.push(s.id);
        }
      }
      const expanded = await Repository.loadExpandedTasks(master.id, visibleIds, [date]);
      const completed: Task[] = [];
      for (const t of expanded) {
        if (t.type === TaskType.LONG_TERM) {
          if (!t.isCompleted || t.completedAt === null || !DateUtils.isValid(t.endDate)) {
            continue;
          }
          const doneDay = DateUtils.fromEpochMillis(t.completedAt);
          if (doneDay === date && DateUtils.cmp(date, t.endDate) <= 0) {
            completed.push(t);
          }
        } else if (t.isCompleted) {
          completed.push(t);
        }
      }
      this.completedTasks = completed;
      this.longTermTasks = await Repository.getLongTermActive(visibleIds.concat([master.id]), date);
    } catch (e) {
      console.error(`ReviewEditVM.reloadTasks: ${e}`);
    }
  }

  /** 自动保存：停止输入 1 秒后调用 */
  saveReviewContent(content: string): void {
    this.content = content;
    Repository.getMasterNotebookOnce().then((master) => {
      if (master === null) {
        return;
      }
      const review = new Review();
      review.notebookId = master.id;
      review.date = this.date;
      review.content = content;
      Repository.saveReview(review);
    });
  }

  /** 长期任务拆分：新建子任务落到活动子计划本，原长期保留 */
  splitLongTermTask(subTask: Task): void {
    const copy = Task.copy(subTask);
    copy.id = 0;
    copy.notebookId = this.activeSubId;
    Repository.addTask(copy);
  }
}

/** 设置页 VM */
@ObservedV2
export class SettingsVM {
  @Trace master: Notebook | null = null;
  @Trace reviewSettings: ReviewSetting[] = [];
  /** 学期第一周周一（yyyy-MM-dd；''=未配置） */
  @Trace termStart: string = '';

  private unsub: (() => void) | null = null;

  attach(): void {
    this.unsub = Repository.subscribe(() => {
      this.reload();
    });
    this.reload();
  }

  detach(): void {
    if (this.unsub !== null) {
      this.unsub();
      this.unsub = null;
    }
  }

  async reload(): Promise<void> {
    try {
      const master = await Repository.getMasterNotebookOnce();
      this.master = master;
      if (master !== null) {
        this.reviewSettings = await Repository.getReviewSettingsOnce(master.id);
      }
      this.termStart = (await Repository.getTermStart()) ?? '';
    } catch (e) {
      console.error(`SettingsVM.reload: ${e}`);
    }
  }

  toggleReview(type: ReviewType, enabled: boolean): void {
    if (this.master === null) {
      return;
    }
    Repository.setReviewEnabled(this.master.id, type, enabled).then(() => this.reload());
  }

  setReviewTime(type: ReviewType, start: string, end: string): void {
    for (const s of this.reviewSettings) {
      if (s.reviewType === type) {
        const updated = s;
        updated.startTime = start;
        updated.endTime = end;
        Repository.saveReviewSetting(updated).then(() => this.reload());
        return;
      }
    }
  }

  /** 设置学期第一周（传任意日期，归一到所在周周一）；null 清除 */
  setTermStart(date: string | null): void {
    Repository.setTermStart(date === null ? null : DateUtils.weekStartOf(date)).then(() => this.reload());
  }
}

/** 子计划本管理页 VM（设置页入口，ADR-0004） */
@ObservedV2
export class NotebookManagerVM {
  @Trace master: Notebook | null = null;
  @Trace subs: Notebook[] = [];
  @Trace activeSubId: number | null = null;
  /** #11：导入结果提示文案；null = 无待提示 */
  @Trace importMessage: string | null = null;

  /** 调色板全部预设色（颜色选择弹窗用） */
  readonly paletteColors: string[] = SubNotebookPalette.all();

  private unsub: (() => void) | null = null;

  attach(): void {
    this.unsub = Repository.subscribe(() => {
      this.reload();
    });
    this.reload();
  }

  detach(): void {
    if (this.unsub !== null) {
      this.unsub();
      this.unsub = null;
    }
  }

  async reload(): Promise<void> {
    try {
      const master = await Repository.getMasterNotebookOnce();
      const subs = master === null ? [] : await Repository.getSubNotebooksOnce(master.id);
      this.master = master;
      this.subs = subs;
      this.activeSubId = null;
      for (const s of subs) {
        if (s.isActive) {
          this.activeSubId = s.id;
          break;
        }
      }
    } catch (e) {
      console.error(`NotebookManagerVM.reload: ${e}`);
    }
  }

  createSubNotebook(name: string): void {
    Repository.getMasterNotebookOnce().then((master) => {
      if (master !== null) {
        Repository.createSubNotebook(master.id, name).then(() => this.reload());
      }
    });
  }

  renameSubNotebook(notebook: Notebook, newName: string): void {
    const updated = Notebook.copy(notebook);
    updated.name = newName;
    Repository.renameNotebook(updated).then(() => this.reload());
  }

  /** 删除子计划本/课表：普通子本至少保留一个；课表可删（删活动本时仓库会自动换活动） */
  canDelete(notebook: Notebook): boolean {
    if (notebook.isTimetable) {
      return true;
    }
    let normalCount = 0;
    for (const s of this.subs) {
      if (!s.isTimetable) {
        normalCount++;
      }
    }
    return normalCount > 1;
  }

  deleteSubNotebook(notebook: Notebook): void {
    if (!this.canDelete(notebook)) {
      return;
    }
    Repository.deleteNotebook(notebook).then(() => this.reload());
  }

  setActiveSubNotebook(subId: number): void {
    Repository.setActiveSubNotebook(subId).then(() => this.reload());
  }

  setSubNotebookVisible(subId: number, visible: boolean): void {
    Repository.setSubNotebookVisible(subId, visible).then(() => this.reload());
  }

  /** 手动改颜色（renameNotebook 实为整实体 update） */
  setSubNotebookColor(notebook: Notebook, color: string): void {
    const updated = Notebook.copy(notebook);
    updated.color = color;
    Repository.renameNotebook(updated).then(() => this.reload());
  }

  /**
   * #11/#12：导入 ICS——解析 + 建导入子本（方式可选：课表 / 普通子计划本）。
   * 同名旧导入子本整本替换重建（按本次选择的类型重建）；失败只更新提示文案，事务回滚无半成品。
   */
  importIcs(sourceName: string, content: string, asTimetable: boolean): void {
    try {
      const tasks = IcsParser.parse(content);
      if (tasks.length === 0) {
        this.importMessage = `「${sourceName}」里没有解析到任何课程事件`;
        return;
      }
      Repository.getMasterNotebookOnce().then((master) => {
        if (master === null) {
          this.importMessage = '导入失败：主计划本不存在';
          return;
        }
        Repository.importIcsSubNotebook(master.id, sourceName, tasks, asTimetable).then((result) => {
          const kind = asTimetable ? '课表' : '子计划本';
          this.importMessage = result.replacedOld
            ? `已重新导入「${sourceName}」：作为${kind}整本替换为 ${tasks.length} 条课程`
            : `已导入「${sourceName}」：${tasks.length} 条课程（作为${kind}）`;
        }).catch((e: Error) => {
          console.error(`ICS import failed: ${e}`);
          this.importMessage = `导入失败：${e}`;
        });
      });
    } catch (e) {
      console.error(`ICS parse failed: ${e}`);
      this.importMessage = `导入失败：${e}`;
    }
  }

  clearImportMessage(): void {
    this.importMessage = null;
  }
}

/** 现有课程页 VM（管理页「现有课程」入口，按课表）：该课表的课程 + 每课颜色 */
@ObservedV2
export class CourseColorsVM {
  @Trace notebook: Notebook | null = null;
  @Trace courses: string[] = [];
  @Trace colors: Map<string, string> = new Map<string, string>();

  private subId: number = 0;
  private unsub: (() => void) | null = null;

  attach(subId: number): void {
    this.subId = subId;
    this.unsub = Repository.subscribe(() => {
      this.reload();
    });
    this.reload();
  }

  detach(): void {
    if (this.unsub !== null) {
      this.unsub();
      this.unsub = null;
    }
  }

  async reload(): Promise<void> {
    if (this.subId <= 0) {
      return;
    }
    try {
      this.notebook = await Repository.getNotebookById(this.subId);
      this.courses = await Repository.getCourseTitles(this.subId);
      this.colors = await Repository.getCourseColors(this.subId);
    } catch (e) {
      console.error(`CourseColorsVM.reload: ${e}`);
    }
  }

  /** 某课的颜色；未设置返回 null（渲染回退课表色） */
  colorOf(title: string): string | null {
    const v = this.colors.get(title);
    return v === undefined ? null : v;
  }

  /** 回退色：所在课表的颜色 */
  fallbackColor(): string {
    return this.notebook === null || this.notebook.color === null ? '#9E9E9E' : this.notebook.color;
  }

  setColor(title: string, color: string): void {
    Repository.setCourseColor(this.subId, title, color).then(() => this.reload());
  }

  clearColor(title: string): void {
    Repository.setCourseColor(this.subId, title, null).then(() => this.reload());
  }

  /** 新建课程（FAB）：整门课保存，落到本课表 */
  saveCourse(name: string, room: string, teacher: string, occs: CourseOccDraft[]): void {
    Repository.saveCourse(this.subId, '', name, room, teacher, occs).then(() => this.reload());
  }
}
