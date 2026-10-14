import { AppColors } from '../common/Theme';
import { DateUtils } from '../common/DateUtils';
import { Task, TaskType } from '../model/Models';
import { TaskListVM } from '../viewmodel/ViewModels';
import { TaskEditSheet } from '../components/TaskEditSheet';
import { DatePickerOverlay } from '../components/Pickers';

/**
 * 待办列表页（对应 Android TaskListScreen）：
 * 标题日期可点选日期；分四类显示（带时间/DDL）；勾选完成；加号新建（预填选中日期）。
 */
@ComponentV2
export struct TaskListPage {
  @Local vm: TaskListVM = new TaskListVM();
  @Local showDatePicker: boolean = false;

  aboutToAppear(): void {
    this.vm.attach();
  }

  aboutToDisappear(): void {
    this.vm.detach();
  }

  build() {
    Stack({ alignContent: Alignment.BottomEnd }) {
      Column() {
        Row() {
          Text(`${DateUtils.cnDate(this.vm.selectedDate)} 周${DateUtils.weekdayCn(DateUtils.dayOfWeek(this.vm.selectedDate))} 待办`)
            . fontSize(18)
            . fontWeight(FontWeight.Bold)
            . fontColor(AppColors.primary)
            . padding({ top: 10, bottom: 6, left: 16, right: 16 })
            . onClick(() => this.showDatePicker = true)
        }
        . width('100%')

        Scroll() {
          Column({ space: 2 }) {
            if (this.vm.tasks.length === 0 && this.vm.loaded) {
              Text('这天没有待办')
                . fontSize(14)
                . fontColor(AppColors.onSurface)
                . padding({ top: 16 })
            }
            ForEach(this.groupLabels(), (group: string) => {
              if (this.groupTasks(group).length > 0) {
                Text(group)
                  . fontSize(13)
                  . fontWeight(FontWeight.Bold)
                  . fontColor(AppColors.onSurface)
                  . width('100%')
                  . padding({ top: 10, bottom: 4 })
                ForEach(this.groupTasks(group), (task: Task) => {
                  this.TaskRow(task)
                }, (task: Task) => `${task.id}-${task.isCompleted}-${task.title}-${task.startTime}-${task.endTime}`)
              }
            }, (group: string) => `${group}-${this.vm.tasks.length}`)
          }
          . width('100%')
          . padding({ left: 16, right: 16, bottom: 90 })
        }
        . scrollBar(BarState.Off)
        . layoutWeight(1)
        . align(Alignment.TopStart)
      }
      . width('100%')
      . height('100%')

      // FAB
      Column() {
        SymbolGlyph($r('sys.symbol.plus'))
          . fontSize(26)
          . fontColor([AppColors.onPrimary])
      }
      . width(56)
      . height(56)
      . borderRadius(28)
      . backgroundColor(AppColors.primary)
      . shadow(ShadowStyle.OUTER_DEFAULT_MD)
      . margin(16)
      . onClick(() => this.vm.openAddDialog())

      if (this.showDatePicker) {
        DatePickerOverlay({
          initialDate: this.vm.selectedDate,
          onConfirm: (d: string) => {
            this.vm.selectDate(d);
            this.showDatePicker = false;
          },
          onDismiss: () => this.showDatePicker = false
        })
      }
      if (this.vm.showAddDialog) {
        TaskEditSheet({
          notebookId: this.vm.activeSub !== null ? this.vm.activeSub.id
            : (this.vm.subs.length > 0 ? this.vm.subs[0].id : (this.vm.master?.id ?? 0)),
          existing: this.vm.prefill,
          onDismiss: () => this.vm.hideAddDialog(),
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
              this.vm.toggleComplete(this.vm.editing);
            }
            this.vm.closeTaskEditor();
          }
        })
      }
    }
    . width('100%')
    . height('100%')
    . backgroundColor(AppColors.surface)
  }

  private groupLabels(): string[] {
    return ['临时任务', '每日任务', '灵活任务', '长期任务'];
  }

  private groupTasks(group: string): Task[] {
    const type: TaskType = group === '临时任务' ? TaskType.ONE_OFF
      : group === '每日任务' ? TaskType.DAILY
        : group === '灵活任务' ? TaskType.FLEX : TaskType.LONG_TERM;
    const out: Task[] = [];
    for (const t of this.vm.tasks) {
      if (t.type === type) {
        out.push(t);
      }
    }
    return out;
  }

  private timeText(task: Task): string {
    if (task.type === TaskType.ONE_OFF) {
      return task.startTime !== null ? `${task.startTime} - ${task.endTime ?? task.startTime}` : '全天';
    }
    if (task.type === TaskType.DAILY) {
      return task.startTime !== null ? `${task.startTime} - ${task.endTime ?? task.startTime}` : '每日';
    }
    if (task.type === TaskType.FLEX) {
      return '无时段';
    }
    return `DDL ${task.endDate}`;
  }

  @Builder
  TaskRow(task: Task) {
    Row({ space: 8 }) {
      Checkbox()
        . select(task.isCompleted)
        . selectedColor(AppColors.primary)
        . onChange((_v: boolean) => this.vm.toggleComplete(task))
      Column({ space: 2 }) {
        Text(task.title)
          . fontSize(14)
          . fontColor(task.isCompleted ? AppColors.completedText : AppColors.onSurface)
          . decoration({
            type: task.isCompleted ? TextDecorationType.LineThrough : TextDecorationType.None
          })
          . maxLines(1)
        Text(this.timeText(task))
          . fontSize(11)
          . fontColor(AppColors.onSurfaceVariant)
      }
      . alignItems(HorizontalAlign.Start)
      . layoutWeight(1)
      Text('✕')
        . fontSize(13)
        . fontColor(AppColors.onSurfaceVariant)
        . padding(4)
        . onClick(() => this.vm.deleteTask(task))
    }
    . width('100%')
    . height(44)
    . onClick(() => this.vm.openTaskEditor(task))
  }
}
