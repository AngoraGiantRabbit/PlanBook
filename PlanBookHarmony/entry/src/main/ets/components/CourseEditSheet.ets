import { AppColors } from '../common/Theme';
import { CourseInfo } from '../common/CourseInfo';
import { DateUtils } from '../common/DateUtils';
import { CourseOccDraft, Task, TaskType } from '../model/Models';
import { Repository } from '../data/Repository';
import { DialogShell, TextButton } from './Ui';
import { DatePickerOverlay, TimePickerOverlay, parseTime } from './Pickers';

/**
 * 课程编辑表单（整门课，课表子本专用）：
 * - 课程信息：名称（简称）/教室/老师——保存时对该课表内这门课的**全部节次**生效；
 * - 节次列表：这门课在该课表内的所有次课（日期 + 起止时间），逐次可改/可删/可加；
 * - 课程不是任务：无四类型/重复规则/完成态，不进待办列表；
 * - readOnly：课程所在课表未设为活动时只读查看（设为活动后可编辑）。
 */
@ComponentV2
export struct CourseEditSheet {
  @Param notebookId: number = 0;
  /** 点课程块进入时为代表任务（取其 title）；新建时为预填模板（id=0） */
  @Param existing: Task | null = null;
  @Param readOnly: boolean = false;
  /** 保存整门课：名称/教室/老师 + 全部节次（id>0 更新、id=0 新增、缺失的既有节次删除） */
  @Event onConfirm: (name: string, room: string, teacher: string, occs: CourseOccDraft[]) =>
    void = (_n: string, _r: string, _t: string, _o: CourseOccDraft[]) => {};
  @Event onDismiss: () => void = () => {};
  /** 删除整门课（该课表内同名全部节次），仅编辑已持久化课程时显示 */
  @Event onDelete: () => void = () => {};

  @Local name: string = '';
  @Local room: string = '';
  @Local teacher: string = '';
  @Local occs: CourseOccDraft[] = [];
  @Local loaded: boolean = false;
  /** 正在编辑的节次与字段：pickers 定位用 */
  @Local pickingOcc: number = -1;
  @Local pickingDate: boolean = false;
  @Local pickingStart: boolean = false;
  @Local pickingEnd: boolean = false;

  aboutToAppear(): void {
    const e = this.existing;
    this.name = e === null ? '' : e.title;
    this.room = e === null ? '' : (e.location ?? '');
    this.teacher = e === null ? '' : CourseInfo.teacherOf(e.description);
    if (e !== null && e.id > 0) {
      // 编辑整门课：载入该课表内同名的全部节次
      Repository.getCourseTasks(this.notebookId, e.title).then((tasks: Task[]) => {
        const occs: CourseOccDraft[] = [];
        for (const t of tasks) {
          occs.push(new CourseOccDraft(t.id, t.startDate, t.startTime ?? '', t.endTime ?? ''));
        }
        this.occs = occs;
        this.loaded = true;
      });
    } else {
      // 新建：以预填模板（日期/时段来自空白时段点击）或空白节次起步
      this.occs = [new CourseOccDraft(0,
        e === null ? DateUtils.today() : e.startDate,
        e === null ? '' : (e.startTime ?? ''), e === null ? '' : (e.endTime ?? ''))];
      this.loaded = true;
    }
  }

  private get isPersisted(): boolean {
    return this.existing !== null && this.existing.id > 0;
  }

  private occValid(o: CourseOccDraft): boolean {
    return DateUtils.isValid(o.date) && o.start.length > 0 && o.end.length > 0 &&
      DateUtils.timeToMinutes(o.end) > DateUtils.timeToMinutes(o.start);
  }

  private get canSave(): boolean {
    if (this.name.trim().length === 0 || this.occs.length === 0) {
      return false;
    }
    for (const o of this.occs) {
      if (!this.occValid(o)) {
        return false;
      }
    }
    return true;
  }

  private confirm(): void {
    this.onConfirm(this.name.trim(), this.room.trim(), this.teacher.trim(), this.occs);
  }

  build() {
    Stack() {
      DialogShell({ onDismiss: () => this.onDismiss() }) {
        Column({ space: 10 }) {
          Text(this.readOnly ? '课程详情（只读）' : (this.isPersisted ? `编辑课程（${this.occs.length} 次）` : '新建课程'))
            . fontSize(17)
            . fontWeight(FontWeight.Bold)
            . alignSelf(ItemAlign.Start)

          if (this.readOnly) {
            Text('课程所在课表未设为活动：在首页点对应课表 chip 设为活动后即可编辑。')
              . fontSize(12)
              . fontColor(AppColors.tertiary)
          }

          TextInput({ placeholder: '课程名称（简称）', text: this.name })
            . enabled(!this.readOnly)
            . onChange((v: string) => this.name = v)
          Row({ space: 8 }) {
            TextInput({ placeholder: '教室（可选）', text: this.room })
              . enabled(!this.readOnly)
              . onChange((v: string) => this.room = v)
              . layoutWeight(1)
            TextInput({ placeholder: '老师（可选）', text: this.teacher })
              . enabled(!this.readOnly)
              . onChange((v: string) => this.teacher = v)
              . layoutWeight(1)
          }
          . width('100%')

          Text(`节次 (${this.occs.length})：名称/教室/老师对全部节次生效`)
            . fontSize(12)
            . fontColor(AppColors.onSurfaceVariant)
            . width('100%')

          if (this.loaded) {
            Scroll() {
              Column({ space: 6 }) {
                ForEach(this.occs, (o: CourseOccDraft, idx: number) => {
                  this.OccRow(o, idx)
                }, (o: CourseOccDraft, idx: number) => `${o.id}-${idx}-${o.date}-${o.start}-${o.end}`)

                if (!this.readOnly) {
                  Text('+ 添加节次')
                    . fontSize(13)
                    . fontColor(AppColors.primary)
                    . width('100%')
                    . textAlign(TextAlign.Center)
                    . padding(8)
                    . borderRadius(8)
                    . border({ width: 1, color: AppColors.outline })
                    . onClick(() => {
                      this.occs.push(new CourseOccDraft(0, DateUtils.today(), '', ''));
                    })
                }
              }
              . width('100%')
            }
            . constraintSize({ maxHeight: 260 })
            . scrollBar(BarState.Off)
            . width('100%')
          }

          Row({ space: 4 }) {
            if (!this.readOnly && this.isPersisted) {
              TextButton({ label: '删除整门课', color: AppColors.error, onTap: () => this.onDelete() })
            }
            Blank()
            if (!this.readOnly) {
              TextButton({ label: '取消', onTap: () => this.onDismiss() })
              TextButton({
                label: '保存',
                isEnabled: this.canSave,
                onTap: () => this.confirm()
              })
            } else {
              TextButton({ label: '关闭', onTap: () => this.onDismiss() })
            }
          }
          . width('100%')
        }
        . width('100%')
      }

      if (this.pickingDate && this.pickingOcc >= 0) {
        DatePickerOverlay({
          initialDate: this.occDate(this.pickingOcc),
          onConfirm: (d: string) => {
            this.setOccDate(this.pickingOcc, d);
            this.pickingDate = false;
          },
          onDismiss: () => this.pickingDate = false
        })
      }
      if (this.pickingStart && this.pickingOcc >= 0) {
        TimePickerOverlay({
          initialHour: this.initialHour(this.pickingOcc, true),
          initialMinute: this.initialMinute(this.pickingOcc, true),
          onConfirm: (t: string) => {
            this.setOccStart(this.pickingOcc, t);
            this.pickingStart = false;
          },
          onDismiss: () => this.pickingStart = false
        })
      }
      if (this.pickingEnd && this.pickingOcc >= 0) {
        TimePickerOverlay({
          initialHour: this.initialHour(this.pickingOcc, false),
          initialMinute: this.initialMinute(this.pickingOcc, false),
          onConfirm: (t: string) => {
            this.setOccEnd(this.pickingOcc, t);
            this.pickingEnd = false;
          },
          onDismiss: () => this.pickingEnd = false
        })
      }
    }
    . width('100%')
    . height('100%')
  }

  /** 一次节次：日期一行 + 起止时间一行（各自点击改），✕ 删除该次 */
  @Builder
  OccRow(o: CourseOccDraft, idx: number) {
    Column({ space: 4 }) {
      Row({ space: 8 }) {
        Text(`${o.date} 周${DateUtils.weekdayCn(DateUtils.dayOfWeek(o.date))}`)
          . fontSize(13)
          . fontColor(AppColors.onSurface)
          . layoutWeight(1)
          . onClick(() => {
            if (!this.readOnly) {
              this.pickingOcc = idx;
              this.pickingDate = true;
            }
          })
        if (!this.readOnly) {
          Text('✕')
            . fontSize(13)
            . fontColor(AppColors.error)
            . padding(4)
            . onClick(() => this.occs.splice(idx, 1))
        }
      }
      . width('100%')

      Row({ space: 8 }) {
        Text(o.start.length > 0 ? o.start : '开始')
          . fontSize(13)
          . fontColor(o.start.length > 0 ? AppColors.primary : AppColors.onSurfaceVariant)
          . padding({ left: 12, right: 12, top: 6, bottom: 6 })
          . borderRadius(8)
          . border({ width: 1, color: AppColors.outline })
          . onClick(() => {
            if (!this.readOnly) {
              this.pickingOcc = idx;
              this.pickingStart = true;
            }
          })
        Text('-')
          . fontSize(13)
          . fontColor(AppColors.onSurfaceVariant)
        Text(o.end.length > 0 ? o.end : '结束')
          . fontSize(13)
          . fontColor(o.end.length > 0 ? AppColors.primary : AppColors.onSurfaceVariant)
          . padding({ left: 12, right: 12, top: 6, bottom: 6 })
          . borderRadius(8)
          . border({ width: 1, color: AppColors.outline })
          . onClick(() => {
            if (!this.readOnly) {
              this.pickingOcc = idx;
              this.pickingEnd = true;
            }
          })
        Blank()
        if (!this.occValid(o)) {
          Text('起止需合法')
            . fontSize(10)
            . fontColor(AppColors.error)
        }
      }
      . width('100%')
    }
    . width('100%')
    . padding(8)
    . borderRadius(8)
    . border({ width: 1, color: AppColors.outline })
  }

  private occDate(idx: number): string {
    const o = this.occs[idx];
    return o === undefined ? DateUtils.today() : o.date;
  }

  private setOccDate(idx: number, d: string): void {
    const o = this.occs[idx];
    if (o !== undefined) {
      o.date = d;
    }
  }

  private setOccStart(idx: number, t: string): void {
    const o = this.occs[idx];
    if (o !== undefined) {
      o.start = t;
    }
  }

  private setOccEnd(idx: number, t: string): void {
    const o = this.occs[idx];
    if (o !== undefined) {
      o.end = t;
    }
  }

  private initialHour(idx: number, isStart: boolean): number {
    const o = this.occs[idx];
    const raw = o === undefined ? '' : (isStart ? o.start : o.end);
    const p = parseTime(raw.length > 0 ? raw : (isStart ? '08:00' : '09:00'));
    return p === null ? (isStart ? 8 : 9) : p[0];
  }

  private initialMinute(idx: number, isStart: boolean): number {
    const o = this.occs[idx];
    const raw = o === undefined ? '' : (isStart ? o.start : o.end);
    const p = parseTime(raw.length > 0 ? raw : (isStart ? '08:00' : '09:00'));
    return p === null ? 0 : p[1];
  }
}
