import { AppColors } from '../common/Theme';
import { DateUtils } from '../common/DateUtils';
import { RepeatRule, Task, TaskType, TaskTypeLabels } from '../model/Models';
import { DialogShell, TextButton } from './Ui';
import { DatePickerOverlay, PickerField, parseTime, TimePickerOverlay } from './Pickers';

/**
 * 复用的任务编辑表单（对应 Android AddTaskDialog.kt 的 TaskEditSheet）：
 * 新建与编辑共用；readOnly = 导入子计划本只读快照（#12：禁编辑/删除，勾选完成不受限）。
 * 以全屏覆盖层呈现。
 */
@ComponentV2
export struct TaskEditSheet {
  @Param notebookId: number = 0;
  @Param existing: Task | null = null;
  @Param readOnly: boolean = false;
  @Event onDismiss: () => void = () => {};
  @Event onConfirm: (task: Task) => void = (_t: Task) => {};
  @Event onDelete: () => void = () => {};
  /** 非空且为编辑场景时显示「完成/撤销完成」按钮 */
  @Event onToggleComplete: () => void = () => {};

  @Local title: string = '';
  @Local description: string = '';
  @Local location: string = '';
  @Local selectedType: TaskType = TaskType.ONE_OFF;
  @Local startDate: string = '';
  @Local endDate: string = '';
  @Local startTime: string = '';
  @Local endTime: string = '';
  @Local repeatRule: RepeatRule = RepeatRule.EVERY_DAY;
  @Local selectedDays: Set<number> = new Set<number>();
  @Local showStartPicker: boolean = false;
  @Local showEndPicker: boolean = false;
  @Local showStartPickerTime: boolean = false;
  @Local showEndPickerTime: boolean = false;

  aboutToAppear(): void {
    const e = this.existing;
    const today = DateUtils.today();
    this.title = e === null ? '' : e.title;
    this.description = e === null ? '' : e.description;
    this.location = e === null ? '' : (e.location ?? '');
    this.selectedType = e === null ? TaskType.ONE_OFF : e.type;
    this.startDate = e === null ? today : e.startDate;
    this.endDate = e === null ? today : e.endDate;
    this.startTime = e === null ? '' : (e.startTime ?? '');
    this.endTime = e === null ? '' : (e.endTime ?? '');
    this.repeatRule = e === null || e.repeatRule === null ? RepeatRule.EVERY_DAY : e.repeatRule;
    const days = new Set<number>();
    if (e !== null && e.weeklyDays !== null) {
      for (const tok of e.weeklyDays.split(',')) {
        const n = Number(tok);
        if (Number.isFinite(n) && n >= 1 && n <= 7) {
          days.add(n);
        }
      }
    } else {
      days.add(DateUtils.dayOfWeek(today));
    }
    this.selectedDays = days;
  }

  private get isNew(): boolean {
    return this.existing === null;
  }

  /** 编辑的是否为已持久化任务（id > 0）；预填新建模板 id=0，不显示删除/完成按钮 */
  private get isPersisted(): boolean {
    return this.existing !== null && this.existing.id > 0;
  }

  private get isSingleDayType(): boolean {
    return this.selectedType === TaskType.FLEX || this.selectedType === TaskType.ONE_OFF;
  }

  private selectType(type: TaskType): void {
    this.selectedType = type;
    // 切到单天类型时把 endDate 收敛到 startDate，避免脏数据（ADR-0002）
    if (type === TaskType.FLEX || type === TaskType.ONE_OFF) {
      this.endDate = this.startDate;
    }
  }

  private toggleDay(day: number): void {
    const next = new Set<number>(this.selectedDays);
    if (next.has(day)) {
      next.delete(day);
    } else {
      next.add(day);
    }
    this.selectedDays = next;
  }

  private confirm(): void {
    // ADR-0002：灵活/临时为单天，强制 startDate == endDate
    const finalEnd = this.isSingleDayType ? this.startDate : this.endDate;
    const t = new Task();
    const e = this.existing;
    t.id = e === null ? 0 : e.id;
    t.notebookId = this.notebookId;
    t.title = this.title;
    t.description = this.description;
    t.location = this.location.trim().length > 0 ? this.location.trim() : null;
    t.type = this.selectedType;
    t.startDate = this.startDate;
    t.endDate = finalEnd;
    t.startTime = this.startTime.trim().length > 0 ? this.startTime : null;
    t.endTime = this.endTime.trim().length > 0 ? this.endTime : null;
    t.repeatRule = this.selectedType === TaskType.DAILY ? this.repeatRule : null;
    if (this.selectedType === TaskType.DAILY && this.repeatRule === RepeatRule.WEEKDAYS) {
      const sorted = Array.from(this.selectedDays).sort((a, b) => a - b);
      t.weeklyDays = sorted.join(',');
    } else {
      t.weeklyDays = null;
    }
    t.isCompleted = e === null ? false : e.isCompleted;
    t.completedAt = e === null ? null : e.completedAt;
    t.isAutoReview = e === null ? false : e.isAutoReview;
    t.reviewType = e === null ? null : e.reviewType;
    t.createdAt = e === null || e.createdAt === 0 ? Date.now() : e.createdAt;
    this.onConfirm(t);
  }

  build() {
    Stack() {
      DialogShell({ onDismiss: () => this.onDismiss() }) {
      Column({ space: 10 }) {
        Text(this.readOnly ? '任务详情（只读）' : (this.isNew ? '添加任务' : '编辑任务'))
          . fontSize(17)
          . fontWeight(FontWeight.Bold)
          . alignSelf(ItemAlign.Start)

        Scroll() {
          Column({ space: 10 }) {
            if (this.readOnly) {
              // #12：导入子计划本为只读快照，防止 App 内改动后与源文件失去对应
              Text('该任务来自导入子计划本（只读快照），不支持编辑或删除。课表有变请重新导入。')
                . fontSize(12)
                . fontColor(AppColors.tertiary)
            }
            TextInput({ placeholder: '任务名称', text: this.title })
              . enabled(!this.readOnly)
              . onChange((v: string) => this.title = v)
            TextInput({ placeholder: '备注（可选）', text: this.description })
              . enabled(!this.readOnly)
              . onChange((v: string) => this.description = v)
            TextInput({ placeholder: '教室（可选，显示在任务块上）', text: this.location })
              . enabled(!this.readOnly)
              . onChange((v: string) => this.location = v)

            // 四类型分段
            Row() {
              ForEach([TaskType.FLEX, TaskType.ONE_OFF, TaskType.DAILY, TaskType.LONG_TERM],
                (type: TaskType) => {
                  Text(TaskTypeLabels.label(type))
                    . fontSize(13)
                    . textAlign(TextAlign.Center)
                    . fontColor(this.selectedType === type ? AppColors.onPrimaryContainer : AppColors.onSurfaceVariant)
                    . padding({ top: 7, bottom: 7 })
                    . backgroundColor(this.selectedType === type ? AppColors.primaryContainer : '#00000000')
                    . borderRadius(8)
                    . layoutWeight(1)
                    . onClick(() => {
                      if (!this.readOnly) {
                        this.selectType(type);
                      }
                    })
                }, (type: TaskType) => type)
            }
            . width('100%')
            . border({ width: 1, color: AppColors.outline, radius: 10 })

            // 日期入口：灵活/临时单天（一个），每日/长期两个（ADR-0002）
            PickerField({
              value: this.startDate,
              label: this.isSingleDayType ? '日期' : '开始日期',
              isEnabled: !this.readOnly,
              onTap: () => this.showStartPicker = true
            })
            if (!this.isSingleDayType) {
              PickerField({
                value: this.endDate,
                label: this.selectedType === TaskType.LONG_TERM ? '截止日期 DDL' : '结束日期',
                isEnabled: !this.readOnly,
                onTap: () => this.showEndPicker = true
              })
            }

            if (this.selectedType === TaskType.ONE_OFF || this.selectedType === TaskType.DAILY) {
              PickerField({
                value: this.startTime,
                label: '开始时间（可选，留空则无时段）',
                isEnabled: !this.readOnly,
                onTap: () => this.showStartPickerTime = true
              })
              PickerField({
                value: this.endTime,
                label: '结束时间（可选）',
                isEnabled: !this.readOnly,
                onTap: () => this.showEndPickerTime = true
              })
            }

            if (this.selectedType === TaskType.DAILY) {
              Text('重复规则')
                . fontSize(12)
                . fontColor(AppColors.onSurfaceVariant)
                . alignSelf(ItemAlign.Start)
              Row({ space: 8 }) {
                ForEach([RepeatRule.EVERY_DAY, RepeatRule.WEEKDAYS], (rule: RepeatRule) => {
                  Text(TaskTypeLabels.repeatRuleLabel(rule))
                    . fontSize(13)
                    . fontColor(this.repeatRule === rule ? AppColors.onPrimaryContainer : AppColors.onSurfaceVariant)
                    . padding({ left: 14, right: 14, top: 6, bottom: 6 })
                    . backgroundColor(this.repeatRule === rule ? AppColors.primaryContainer : '#00000000')
                    . borderRadius(10)
                    . border({
                      width: 1,
                      color: this.repeatRule === rule ? AppColors.primaryContainer : AppColors.outline
                    })
                    . onClick(() => {
                      if (!this.readOnly) {
                        this.repeatRule = rule;
                      }
                    })
                }, (rule: RepeatRule) => rule)
              }
              if (this.repeatRule === RepeatRule.WEEKDAYS) {
                Text('选择每周哪几天')
                  . fontSize(12)
                  . fontColor(AppColors.onSurfaceVariant)
                  . alignSelf(ItemAlign.Start)
                Flex({ wrap: FlexWrap.Wrap }) {
                  ForEach([1, 2, 3, 4, 5, 6, 7], (day: number) => {
                    Text(TaskTypeLabels.dayName(day))
                      . fontSize(13)
                      . margin(2)
                      . fontColor(this.selectedDays.has(day) ? AppColors.onPrimaryContainer : AppColors.onSurfaceVariant)
                      . padding({ left: 12, right: 12, top: 6, bottom: 6 })
                      . backgroundColor(this.selectedDays.has(day) ? AppColors.primaryContainer : '#00000000')
                      . borderRadius(10)
                      . border({
                        width: 1,
                        color: this.selectedDays.has(day) ? AppColors.primaryContainer : AppColors.outline
                      })
                      . onClick(() => {
                        if (!this.readOnly) {
                          this.toggleDay(day);
                        }
                      })
                  }, (day: number) => `${day}`)
                }
                . width('100%')
              }
            }
          }
          . width('100%')
          . alignItems(HorizontalAlign.Start)
        }
        . constraintSize({ maxHeight: 360 })
        . scrollBar(BarState.Off)
        . width('100%')

        // 底部按钮区
        Row({ space: 4 }) {
          if (!this.readOnly && this.isPersisted) {
            TextButton({ label: '删除', color: AppColors.error, onTap: () => this.onDelete() })
          }
          if (this.isPersisted) {
            TextButton({
              label: this.existing !== null && this.existing.isCompleted ? '撤销完成' : '完成',
              onTap: () => this.onToggleComplete()
            })
          }
          Blank()
          if (!this.readOnly) {
            TextButton({ label: '取消', onTap: () => this.onDismiss() })
            TextButton({
              label: this.isNew ? '确定' : '保存',
              isEnabled: this.title.trim().length > 0,
              onTap: () => this.confirm()
            })
          } else {
            // 只读：仅保留关闭（勾选完成不受限）
            TextButton({ label: '关闭', onTap: () => this.onDismiss() })
          }
        }
      }
      . width('100%')
      }

      // 日期/时间选择器（叠加在表单之上）
      if (this.showStartPicker) {
        DatePickerOverlay({
          initialDate: this.startDate,
          onConfirm: (d: string) => {
            this.startDate = d;
            // 单天类型：startDate 改动同步 endDate（ADR-0002）
            if (this.selectedType === TaskType.FLEX || this.selectedType === TaskType.ONE_OFF) {
              this.endDate = d;
            }
            this.showStartPicker = false;
          },
          onDismiss: () => this.showStartPicker = false
        })
      }
      if (this.showEndPicker) {
        DatePickerOverlay({
          initialDate: this.endDate,
          onConfirm: (d: string) => {
            this.endDate = d;
            this.showEndPicker = false;
          },
          onDismiss: () => this.showEndPicker = false
        })
      }
      if (this.showStartPickerTime) {
        TimePickerOverlay({
          initialHour: parseTime(this.startTime)?.[0] ?? 9,
          initialMinute: parseTime(this.startTime)?.[1] ?? 0,
          onConfirm: (t: string) => {
            this.startTime = t;
            this.showStartPickerTime = false;
          },
          onDismiss: () => this.showStartPickerTime = false
        })
      }
      if (this.showEndPickerTime) {
        TimePickerOverlay({
          initialHour: parseTime(this.endTime)?.[0] ?? ((parseTime(this.startTime)?.[0] ?? 9) + 1),
          initialMinute: parseTime(this.endTime)?.[1] ?? 0,
          onConfirm: (t: string) => {
            this.endTime = t;
            this.showEndPickerTime = false;
          },
          onDismiss: () => this.showEndPickerTime = false
        })
      }
    }
    . width('100%')
    . height('100%')
  }
}
