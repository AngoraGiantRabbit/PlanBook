import { AppColors, ColorUtil, Dimens } from '../common/Theme';
import { DateUtils } from '../common/DateUtils';
import { Notebook, Task, TaskType } from '../model/Models';
import { PlanBookVM } from '../viewmodel/ViewModels';
import { TaskEditSheet } from '../components/TaskEditSheet';
import { WidgetSync } from '../widget/WidgetSync';
import { common } from '@kit.AbilityKit';

/** 周视图页面（对应 Android PlanBookScreen）：表头 + 时段网格 + 灵活待办 + 长期待办 */
@ComponentV2
export struct PlanBookPage {
  @Event onOpenReview: (date: string) => void = (_d: string) => {};
  @Local vm: PlanBookVM = new PlanBookVM();

  aboutToAppear(): void {
    this.vm.attach();
  }

  aboutToDisappear(): void {
    this.vm.detach();
  }

  build() {
    Stack({ alignContent: Alignment.BottomEnd }) {
      Column() {
        this.TopBar()
        if (this.vm.master === null) {
          this.EmptyNotebookState()
        } else {
          this.SubNotebookChips()
          this.WeekView()
        }
      }
      . width('100%')
      . height('100%')

      // FAB
      Column() {
        SymbolGlyph($r('sys.symbol.plus'))
          . fontSize(26)
          . fontColor([AppColors.onPrimary])
      }
      . justifyContent(FlexAlign.Center)
      . alignItems(HorizontalAlign.Center)
      . width(56)
      . height(56)
      . borderRadius(28)
      . backgroundColor(AppColors.primary)
      . shadow(ShadowStyle.OUTER_DEFAULT_MD)
      . margin(16)
      . onClick(() => this.vm.showAddTaskDialog())

      if (this.vm.showAddDialog) {
        TaskEditSheet({
          notebookId: this.vm.activeSub !== null ? this.vm.activeSub.id
            : (this.vm.subs.length > 0 ? this.vm.subs[0].id : 0),
          existing: this.vm.prefill,
          onDismiss: () => this.vm.hideAddTaskDialog(),
          onConfirm: (t: Task) => this.vm.addTask(t)
        })
      }
      if (this.vm.editing !== null) {
        TaskEditSheet({
          notebookId: this.vm.editing.notebookId,
          existing: this.vm.editing,
          readOnly: this.vm.editingReadOnly,
          onDismiss: () => this.vm.closeTaskEditor(),
          onConfirm: (t: Task) => this.vm.saveEditedTask(t),
          onDelete: () => {
            if (this.vm.editing !== null) {
              this.vm.deleteTask(this.vm.editing);
            }
          },
          onToggleComplete: () => {
            if (this.vm.editing !== null) {
              this.vm.toggleTaskComplete(this.vm.editing);
            }
            this.vm.closeTaskEditor();
          }
        })
      }
    }
    . width('100%')
    . height('100%')
    . backgroundColor(AppColors.surface)
    . gesture(PanGesture({ fingers: 1, direction: PanDirection.Horizontal })
      . onActionEnd((e: GestureEvent) => {
        // 左右划翻周：右划上一周、左划下一周（与翻页手势同向）
        if (e.offsetX > 50) {
          this.vm.changeWeek(-1);
        } else if (e.offsetX < -50) {
          this.vm.changeWeek(1);
        }
      }))
  }

  @Builder
  TopBar() {
    Row({ space: 4 }) {
      Column({ space: 2 }) {
        Text(this.vm.master !== null ? this.vm.master.name : '计划本')
          . fontSize(20)
          . fontWeight(FontWeight.Bold)
          . fontColor(AppColors.onSurface)
        Text(`${this.vm.weekNo.length > 0 ? this.vm.weekNo + ' · ' : ''}${DateUtils.md(this.vm.weekStart)} - ${DateUtils.md(DateUtils.addDays(this.vm.weekStart, 6))}`)
          . fontSize(11)
          . fontColor(AppColors.onSurfaceVariant)
      }
      . layoutWeight(1)
      . alignItems(HorizontalAlign.Start)

      SymbolGlyph($r('sys.symbol.chevron_left'))
        . fontSize(26)
        . fontColor([AppColors.onSurfaceVariant])
        . padding({ left: 8, right: 4, top: 8, bottom: 8 })
        . onClick(() => this.vm.changeWeek(-1))
      Text('本周')
        . fontSize(14)
        . fontColor(AppColors.primary)
        . padding({ left: 8, right: 8, top: 8, bottom: 8 })
        . onClick(() => this.vm.goToThisWeek())
      SymbolGlyph($r('sys.symbol.chevron_right'))
        . fontSize(26)
        . fontColor([AppColors.onSurfaceVariant])
        . padding({ left: 4, right: 4, top: 8, bottom: 8 })
        . onClick(() => this.vm.changeWeek(1))
      // 刷新与翻周组拉开距离，避免误触
      SymbolGlyph($r('sys.symbol.arrow_clockwise'))
        . fontSize(24)
        . fontColor([this.vm.isLoading ? AppColors.primary : AppColors.onSurfaceVariant])
        . padding({ left: 14, right: 4, top: 8, bottom: 8 })
        . onClick(() => {
          this.vm.refresh();
          // 同步刷新桌面卡片（Issue #13：手动刷新机制）
          WidgetSync.updateAll(this.getUIContext().getHostContext() as common.UIAbilityContext);
        })
    }
    . width('100%')
    . padding({ left: 16, right: 8, top: 10, bottom: 6 })
  }

  @Builder
  EmptyNotebookState() {
    Column({ space: 16 }) {
      Text('还没有计划本')
        . fontSize(20)
        . fontWeight(FontWeight.Bold)
        . fontColor(AppColors.onSurface)
      Button('新建计划本')
        . backgroundColor(AppColors.primary)
        . onClick(() => this.vm.createNotebook('我的计划本'))
    }
    . width('100%')
    . height('100%')
    . justifyContent(FlexAlign.Center)
  }

  /**
   * #9：子计划本 chips——全部子本各一枚（带颜色点）：
   * 点按 = 切换活动子本（写操作目标）；长按 = 切换显示开关。
   * 隐藏的子本仍留在行内：半透明 + 删除线 + 虚线边框，长按恢复显示。
   */
  @Builder
  SubNotebookChips() {
    Scroll() {
      Row({ space: 6 }) {
        ForEach(this.vm.subs, (sub: Notebook) => {
          Row({ space: 5 }) {
            Circle({ width: 9, height: 9 })
              . fill(ColorUtil.safe(sub.color))
            Text(sub.name)
              . fontSize(13)
              . fontColor(sub.id === this.activeSubId() ? AppColors.onPrimaryContainer : AppColors.onSurfaceVariant)
              . maxLines(1)
              . decoration({ type: sub.isVisible ? TextDecorationType.None : TextDecorationType.LineThrough })
          }
          . padding({ left: 12, right: 12, top: 7, bottom: 7 })
          . borderRadius(10)
          . opacity(sub.isVisible ? 1 : 0.45)
          . backgroundColor(sub.id === this.activeSubId() ? AppColors.primaryContainer : '#00000000')
          . border({
            width: 1,
            color: sub.id === this.activeSubId() ? AppColors.primaryContainer : AppColors.outline,
            style: sub.isVisible ? BorderStyle.Solid : BorderStyle.Dashed
          })
          . onClick(() => this.vm.switchActiveSub(sub))
          . gesture(LongPressGesture({ repeat: false, duration: 350 })
            . onAction((_e: GestureEvent) => this.vm.toggleSubVisible(sub)))
        }, (sub: Notebook) => `${sub.id}-${sub.name}-${sub.isVisible}-${sub.isActive}`)
      }
      . padding({ left: 8, right: 8, top: 2, bottom: 2 })
    }
    . scrollable(ScrollDirection.Horizontal)
    . scrollBar(BarState.Off)
    . width('100%')
  }

  private activeSubId(): number {
    return this.vm.activeSub === null ? -1 : this.vm.activeSub.id;
  }

  @Builder
  WeekView() {
    Column() {
      this.WeekHeader()
      Scroll() {
        Row() {
          this.TimeAxis()
          ForEach(DateUtils.weekDays(this.vm.weekStart), (date: string) => {
            this.DayColumn(date)
          }, (date: string) => `${this.vm.tasks.length}-${date}`)
        }
        . height(Dimens.hourHeight * (Dimens.endHour - Dimens.startHour))
        . width('100%')
      }
      . scrollBar(BarState.Off)
      . layoutWeight(1)
      . width('100%')
      . align(Alignment.TopStart)

      this.FlexibleArea()
      this.LongTermArea()
    }
    . width('100%')
    . layoutWeight(1)
  }

  @Builder
  WeekHeader() {
    Row() {
      Column()
        . width(44)
      ForEach(DateUtils.weekDays(this.vm.weekStart), (date: string) => {
        Column({ space: 2 }) {
          Text(`周${DateUtils.weekdayCn(DateUtils.dayOfWeek(date))}`)
            . fontSize(11)
            . fontColor(AppColors.onSurfaceVariant)
          Text(DateUtils.md(date))
            . fontSize(11)
            . fontWeight(date === this.vm.selectedDate ? FontWeight.Bold : FontWeight.Normal)
            . fontColor(date === this.vm.selectedDate || date === DateUtils.today()
              ? AppColors.primary : AppColors.onSurface)
        }
        . layoutWeight(1)
        . padding({ top: 4, bottom: 4 })
        . borderRadius(6)
        . backgroundColor(date === this.vm.selectedDate ? AppColors.primaryContainer : '#00000000')
        . onClick(() => this.vm.selectDate(date))
      }, (date: string) => `${date}-${this.vm.selectedDate}`)
    }
    . width('100%')
  }

  private hourLabels(): string[] {
    const out: string[] = [];
    for (let h = Dimens.startHour; h < Dimens.endHour; h++) {
      out.push(`${h}`.padStart(2, '0') + ':00');
    }
    return out;
  }

  @Builder
  TimeAxis() {
    Column() {
      ForEach(this.hourLabels(), (label: string) => {
        Column() {
          Text(label)
            . fontSize(10)
            . lineHeight(11)
            . fontColor(AppColors.onSurfaceVariant)
        }
        . justifyContent(FlexAlign.Start)
        . height(Dimens.hourHeight)
        . width(40)
      }, (label: string) => label)
    }
    . width(40)
    . alignItems(HorizontalAlign.Start)
  }

  /** 某日的时段任务（DAILY 已展开 startDate==endDate==该天；ONE_OFF/FLEX 单天；LONG_TERM 无 startTime 不进入） */
  private timedTasksOf(date: string): Task[] {
    const out: Task[] = [];
    for (const t of this.vm.tasks) {
      if (t.startTime !== null && DateUtils.within(date, t.startDate, t.endDate)) {
        out.push(t);
      }
    }
    return out;
  }

  private subColorOf(notebookId: number): string | null {
    for (const s of this.vm.subs) {
      if (s.id === notebookId && ColorUtil.isHex(s.color)) {
        return s.color;
      }
    }
    return null;
  }

  /** 该任务块起止分钟（相对 06:00） */
  private taskMinutes(task: Task): number[] {
    const s = DateUtils.timeToMinutes(task.startTime ?? '00:00') - Dimens.startHour * 60;
    const e = DateUtils.timeToMinutes(task.endTime ?? task.startTime ?? '00:00') - Dimens.startHour * 60;
    return [s, e];
  }

  /** 与该块时间重叠的任务集合（用于并列分列，算法与 Android 版一致） */
  private overlappingOf(task: Task): Task[] {
    const mine = this.taskMinutes(task);
    const out: Task[] = [];
    for (const o of this.timedTasksOf(task.startDate)) {
      const om = this.taskMinutes(o);
      if (om[0] < mine[1] && mine[0] < om[1]) {
        out.push(o);
      }
    }
    return out;
  }

  @Builder
  DayColumn(date: string) {
    Stack({ alignContent: Alignment.TopStart }) {
      // 背景网格 + 空白时段点击
      Column() {
        ForEach(this.hourLabels(), (label: string, idx: number) => {
          Row()
            . width('100%')
            . height(Dimens.hourHeight)
            . border({ width: { bottom: 0.5 }, color: AppColors.gridLine })
            . onClick(() => this.vm.showAddTaskDialogWithPrefill(date, Dimens.startHour + idx))
        }, (label: string, idx: number) => `${label}-${idx}`)
      }
      . width('100%')
      . height('100%')
      . backgroundColor(AppColors.gridColumnBg)

      // 任务块（绝对定位 + 并列分列）
      ForEach(this.timedTasksOf(date), (task: Task) => {
        this.TaskBlock(task)
      }, (task: Task) => `${task.id}-${task.startDate}-${task.isCompleted}-${task.startTime}-${task.endTime}-${this.subColorOf(task.notebookId)}`)
    }
    . layoutWeight(1)
    . height('100%')
    . clip(true)
  }

  /** 单个任务块：按 startTime/endTime 精确放置；重叠任务横向并列；分立圆角 */
  @Builder
  TaskBlock(task: Task) {
    Column() {
      Column({ space: 1 }) {
        Text(task.title)
          . fontSize(10)
          . fontColor(AppColors.onSurface)
          . maxLines(1)
          . textOverflow({ overflow: TextOverflow.Ellipsis })
          . decoration({ type: task.isCompleted ? TextDecorationType.LineThrough : TextDecorationType.None })
        if (task.location !== null && task.location.length > 0) {
          Text(task.location)
            . fontSize(10)
            . fontColor(AppColors.onSurfaceVariant)
            . maxLines(1)
            . textOverflow({ overflow: TextOverflow.Ellipsis })
        }
      }
      . width('100%')
      . height('100%')
      . padding(2)
      . clip(true)
      . borderRadius(6)
      . backgroundColor(this.blockColor(task))
      . onClick(() => this.onTaskClick(task))
    }
    . width(`${100 / this.overlappingOf(task).length}%`)
    . height(this.blockHeightVp(task))
    . position({ x: this.blockXPct(task), y: this.blockYVp(task) })
    . padding(1)
  }

  private blockXPct(task: Task): string {
    const overlap = this.overlappingOf(task);
    let idx = 0;
    for (let i = 0; i < overlap.length; i++) {
      if (overlap[i].id === task.id) {
        idx = i;
        break;
      }
    }
    return `${(idx * 100) / overlap.length}%`;
  }

  private blockYVp(task: Task): number {
    return this.taskMinutes(task)[0] * (Dimens.hourHeight / 60);
  }

  private blockHeightVp(task: Task): number {
    const m = this.taskMinutes(task);
    const dur = m[1] - m[0] <= 0 ? 0 : m[1] - m[0];
    return dur * (Dimens.hourHeight / 60);
  }

  private blockColor(task: Task): string {
    const sub = this.subColorOf(task.notebookId);
    const base = sub !== null ? sub
      : (task.reviewType !== null ? AppColors.tertiaryContainer : AppColors.primaryContainer);
    return ColorUtil.withAlpha(base, task.isCompleted ? 0.35 : 1);
  }

  private onTaskClick(task: Task): void {
    if (task.isAutoReview) {
      // 自动复盘待办点击进入复盘编辑页（PRD 4.4.1）
      this.onOpenReview(task.startDate);
    } else {
      this.vm.openTaskEditor(task);
    }
  }

  private flexTasks(): Task[] {
    const day = this.vm.selectedDate;
    const un: Task[] = [];
    const done: Task[] = [];
    for (const t of this.vm.tasks) {
      if ((t.type === TaskType.FLEX || (t.type === TaskType.ONE_OFF && t.startTime === null)) &&
          DateUtils.within(day, t.startDate, t.endDate)) {
        if (t.isCompleted) {
          done.push(t);
        } else {
          un.push(t);
        }
      }
    }
    return un.concat(done);
  }

  /** 灵活待办区域：选中日的灵活任务 + 无时段临时任务（两者恒单天，ADR-0002） */
  @Builder
  FlexibleArea() {
    Column({ space: 4 }) {
      Text(`灵活待办 (${DateUtils.md(this.vm.selectedDate)})`)
        . fontSize(13)
        . fontWeight(FontWeight.Medium)
        . fontColor(AppColors.onSurface)
      Scroll() {
        Column() {
          ForEach(this.flexTasks(), (task: Task) => {
            this.CheckRow(task, task.isCompleted, false)
          }, (task: Task) => `${task.id}-${task.isCompleted}-${task.title}`)
        }
        . width('100%')
      }
      . constraintSize({ maxHeight: 140 })
      . scrollBar(BarState.Off)
      . width('100%')
    }
    . width('100%')
    . padding({ left: 12, right: 12, top: 4, bottom: 4 })
  }

  private longTermTasks(): Task[] {
    const day = this.vm.selectedDate;
    const un: Task[] = [];
    const done: Task[] = [];
    for (const t of this.vm.tasks) {
      if (t.type === TaskType.LONG_TERM && DateUtils.within(day, t.startDate, t.endDate)) {
        if (t.isCompleted) {
          done.push(t);
        } else {
          un.push(t);
        }
      }
    }
    return un.concat(done);
  }

  /** 长期待办区域：仅查看日落在 [startDate..DDL] 内的长期任务（ADR-0006） */
  @Builder
  LongTermArea() {
    if (this.longTermTasks().length > 0) {
      Column({ space: 4 }) {
        Text('长期待办')
          . fontSize(13)
          . fontWeight(FontWeight.Medium)
          . fontColor(AppColors.onSurface)
        Scroll() {
          Column() {
            ForEach(this.longTermTasks(), (task: Task) => {
              this.CheckRow(task, task.isCompleted, true)
            }, (task: Task) => `${task.id}-${task.isCompleted}-${task.title}`)
          }
          . width('100%')
        }
        . constraintSize({ maxHeight: 120 })
        . scrollBar(BarState.Off)
        . width('100%')
      }
      . width('100%')
      . padding({ left: 12, right: 12, bottom: 4 })
    }
  }

  /** 勾选行：Checkbox + 标题（+ DDL）+ ✕ 删除 */
  @Builder
  CheckRow(task: Task, completed: boolean, showDdl: boolean) {
    Row({ space: 8 }) {
      Checkbox()
        . select(completed)
        . selectedColor(AppColors.primary)
        . onChange((_v: boolean) => this.vm.toggleTaskComplete(task))
      Text(task.title)
        . fontSize(14)
        . fontColor(completed ? AppColors.completedText : AppColors.onSurface)
        . decoration({ type: completed ? TextDecorationType.LineThrough : TextDecorationType.None })
        . maxLines(1)
        . layoutWeight(1)
        . onClick(() => this.onTaskClick(task))
      if (showDdl) {
        Text(`DDL ${task.endDate}`)
          . fontSize(11)
          . fontColor(AppColors.onSurfaceVariant)
      }
      Text('✕')
        . fontSize(13)
        . fontColor(AppColors.onSurfaceVariant)
        . padding(4)
        . onClick(() => this.vm.deleteTask(task))
    }
    . width('100%')
    . height(36)
  }
}
