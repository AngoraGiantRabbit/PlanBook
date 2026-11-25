import { AppColors } from '../common/Theme';
import { DateUtils } from '../common/DateUtils';
import { CourseDraft, PeriodConfig, PeriodTimes, SlotDraft, TaskTypeLabels } from '../model/Models';
import { PeriodEditor } from '../components/PeriodEditor';
import { Repository } from '../data/Repository';
import { DatePickerOverlay, TimePickerOverlay, parseTime } from '../components/Pickers';
import { DialogShell, TextButton } from '../components/Ui';
import { router } from '@kit.ArkUI';

/**
 * 新建 / 编辑课表：
 * - 课表信息：课表名（子本名）、学期第一周、总周数；
 * - 课程（可增删）：名称/教室/老师 + 多个时间段；
 *   时间段 = 周几 × 第a-b节 × 第a-b教学周（分行布局，节次自动带出时间）；
 * - 节次时间表固定为大学 10 节（页内展示）；
 * - 新建 = 建课桌子本 + 展开任务；编辑 = 更新定义并整本重建任务。
 */
@Entry
@ComponentV2
struct CourseBuilderPage {
  @Local tbName: string = '';
  @Local termStart: string = '';
  @Local totalWeeks: number = 20;
  @Local courses: CourseDraft[] = [];
  @Local showTermPicker: boolean = false;
  @Local message: string | null = null;
  @Local saving: boolean = false;
  /** 编辑模式：目标课桌子本 id */
  private subId: number = 0;
  /** 节次配置（节数 + 每节起止时间，可编辑） */
  @Local periods: PeriodConfig = PeriodConfig.default();
  /** 节次小块编辑链：先开始后结束（选择器在页面根部渲染） */
  @Local editingPeriodN: number = -1;
  @Local pickPeriodStart: boolean = false;
  @Local pickPeriodEnd: boolean = false;

  aboutToAppear(): void {
    this.courses = [new CourseDraft()];
    const params = router.getParams() as Record<string, Object>;
    const sid = params !== undefined && params !== null && params['subId'] !== undefined
      ? Number(params['subId']) : 0;
    if (sid > 0) {
      // 编辑模式：载入已有课表定义与节次配置
      this.subId = sid;
      Repository.getNotebookById(sid).then((nb) => {
        if (nb === null) {
          return;
        }
        this.tbName = nb.name;
        const def = Repository.timetableFromJson(nb.timetableJson);
        if (def !== null) {
          this.termStart = def.termStart;
          this.totalWeeks = def.totalWeeks;
          if (def.courses.length > 0) {
            this.courses = def.courses;
          }
        }
      });
      Repository.getTimetablePeriods(sid).then((p: PeriodConfig) => {
        this.periods = p;
      });
    } else {
      Repository.getTermStart().then((t: string | null) => {
        this.termStart = t ?? DateUtils.weekStartOf(DateUtils.today());
      });
    }
  }

  private valid(): boolean {
    if (this.tbName.trim().length === 0) {
      return false;
    }
    for (const c of this.courses) {
      if (c.name.trim().length === 0 || c.slots.length === 0) {
        return false;
      }
    }
    return this.courses.length > 0;
  }

  private save(): void {
    if (!this.valid() || this.saving) {
      return;
    }
    this.saving = true;
    Repository.getMasterNotebookOnce().then((master) => {
      if (master === null) {
        this.message = '请先在计划本页初始化主计划本';
        this.saving = false;
        return;
      }
      const finish = (): void => {
        this.saving = false;
        router.back({ url: 'pages/NotebookManagerPage' });
      };
      const fail = (e: Error): void => {
        this.saving = false;
        this.message = `保存失败：${e.message ?? e}`;
      };
      if (this.subId > 0) {
        Repository.setTermStart(this.termStart).then(() => {
          return Repository.updateTimetable(this.subId, this.tbName.trim(),
            this.termStart, this.totalWeeks, this.courses, this.periods);
        }).then(finish).catch(fail);
      } else {
        Repository.setTermStart(this.termStart).then(() => {
          return Repository.createTimetableSubNotebook(
            master.id, this.tbName.trim(), this.termStart, this.totalWeeks, this.courses, this.periods);
        }).then(finish).catch(fail);
      }
    });
  }

  build() {
    Stack() {
      Column() {
        Row({ space: 4 }) {
          SymbolGlyph($r('sys.symbol.chevron_left'))
            . fontSize(26)
            . fontColor([AppColors.onSurface])
            . onClick(() => router.back())
          Text(this.subId > 0 ? '编辑课表' : '新建课表')
            . fontSize(19)
            . fontWeight(FontWeight.Bold)
            . fontColor(AppColors.onSurface)
            . layoutWeight(1)
          TextButton({ label: this.saving ? '保存中…' : '保存', isEnabled: this.valid(), onTap: () => this.save() })
        }
        . width('100%')
        . padding({ left: 8, right: 12, top: 8, bottom: 4 })

        Scroll() {
          Column({ space: 12 }) {
            // ---------- 课表信息 ----------
            Column({ space: 8 }) {
              Text('课表信息')
                . fontSize(14)
                . fontWeight(FontWeight.Bold)
                . fontColor(AppColors.onSurface)
                . width('100%')
              TextInput({ placeholder: '课表名称（如：我的课表）', text: this.tbName })
                . onChange((v: string) => this.tbName = v)
              Row({ space: 10 }) {
                Text('学期第一周')
                  . fontSize(14)
                  . fontColor(AppColors.onSurface)
                  . layoutWeight(1)
                Text(this.termStart)
                  . fontSize(14)
                  . fontColor(AppColors.primary)
                  . onClick(() => this.showTermPicker = true)
                Text('总周数')
                  . fontSize(14)
                  . fontColor(AppColors.onSurface)
                  . margin({ left: 8 })
                this.Stepper(this.totalWeeks, 1, 30, (v: number): void => { this.totalWeeks = v; })
              }
              . width('100%')
            }
            . width('100%')
            . padding(12)
            . borderRadius(12)
            . backgroundColor(AppColors.surfaceVariant)

            // ---------- 节次时间（节数与每节起止可编辑） ----------
            Column({ space: 6 }) {
              Text('节次时间')
                . fontSize(14)
                . fontWeight(FontWeight.Bold)
                . fontColor(AppColors.onSurface)
                . width('100%')
              PeriodEditor({
                config: this.periods,
                onEditPeriod: (n: number) => {
                  this.editingPeriodN = n;
                  this.pickPeriodStart = true;
                }
              })
            }
            . width('100%')
            . padding(12)
            . borderRadius(12)
            . backgroundColor(AppColors.surfaceVariant)


            // ---------- 课程列表 ----------
            ForEach(this.courses, (course: CourseDraft, ci: number) => {
              this.CourseCard(course, ci)
            }, (course: CourseDraft, ci: number) => `${ci}-${course.name}-${course.slots.length}`)

            // 添加课程
            Text('+ 添加课程')
              . fontSize(15)
              . fontColor(AppColors.primary)
              . width('100%')
              . textAlign(TextAlign.Center)
              . padding(12)
              . borderRadius(12)
              . border({ width: 1, color: AppColors.outline })
              . onClick(() => {
                this.courses.push(new CourseDraft());
              })

            Blank()
              . height(24)
          }
          . width('100%')
          . padding(16)
        }
        . scrollBar(BarState.Off)
        . layoutWeight(1)
        . align(Alignment.TopStart)
      }
      . width('100%')
      . height('100%')

      if (this.showTermPicker) {
        DatePickerOverlay({
          initialDate: this.termStart.length > 0 ? this.termStart : DateUtils.today(),
          onConfirm: (d: string) => {
            this.termStart = DateUtils.weekStartOf(d);
            this.showTermPicker = false;
          },
          onDismiss: () => this.showTermPicker = false
        })
      }

      // 节次起止选择器（根部渲染）：先开始后结束
      if (this.pickPeriodStart && this.editingPeriodN > 0) {
        TimePickerOverlay({
          initialHour: this.periodInitHour(true),
          initialMinute: this.periodInitMinute(true),
          onConfirm: (t: string) => {
            this.applyPeriodTime(true, t);
            this.pickPeriodStart = false;
            this.pickPeriodEnd = true;
          },
          onDismiss: () => this.pickPeriodStart = false
        })
      }
      if (this.pickPeriodEnd && this.editingPeriodN > 0) {
        TimePickerOverlay({
          initialHour: this.periodInitHour(false),
          initialMinute: this.periodInitMinute(false),
          onConfirm: (t: string) => {
            this.applyPeriodTime(false, t);
            this.pickPeriodEnd = false;
          },
          onDismiss: () => this.pickPeriodEnd = false
        })
      }
      if (this.message !== null) {
        DialogShell({ onDismiss: () => this.message = null }) {
          Column({ space: 10 }) {
            Text('课表')
              . fontSize(17)
              . fontWeight(FontWeight.Bold)
            Text(this.message ?? '')
              . fontSize(13)
              . fontColor(AppColors.onSurfaceVariant)
            TextButton({ label: '知道了', onTap: () => this.message = null })
          }
          . width('100%')
        }
      }
    }
    . width('100%')
    . height('100%')
    . backgroundColor(AppColors.surface)
  }

  private periodList(): string[] {
    const out: string[] = [];
    for (let n = 1; n <= this.periods.count; n++) {
      out.push(this.periods.label(n));
    }
    return out;
  }



  /** 数字步进器：[−] 值 [+] */
  @Builder
  Stepper(value: number, min: number, max: number, onChange: (v: number) => void) {
    Row({ space: 6 }) {
      Text('−')
        . fontSize(16)
        . fontColor(value > min ? AppColors.primary : AppColors.outline)
        . padding({ left: 8, right: 8, top: 2, bottom: 2 })
        . onClick(() => {
          if (value > min) {
            onChange(value - 1);
          }
        })
      Text(`${value}`)
        . fontSize(14)
        . fontColor(AppColors.onSurface)
        . width(28)
        . textAlign(TextAlign.Center)
      Text('+')
        . fontSize(16)
        . fontColor(value < max ? AppColors.primary : AppColors.outline)
        . padding({ left: 8, right: 8, top: 2, bottom: 2 })
        . onClick(() => {
          if (value < max) {
            onChange(value + 1);
          }
        })
    }
  }

  /** 课程卡 */
  @Builder
  CourseCard(course: CourseDraft, ci: number) {
    Column({ space: 8 }) {
      Row() {
        Text(`课程 ${ci + 1}`)
          . fontSize(14)
          . fontWeight(FontWeight.Bold)
          . fontColor(AppColors.onSurface)
          . layoutWeight(1)
        if (this.courses.length > 1) {
          Text('删除课程')
            . fontSize(12)
            . fontColor(AppColors.error)
            . padding(4)
            . onClick(() => {
              this.courses.splice(ci, 1);
            })
        }
      }
      . width('100%')

      TextInput({ placeholder: '课程名称', text: course.name })
        . onChange((v: string) => course.name = v)
      Row({ space: 8 }) {
        TextInput({ placeholder: '教室（可选）', text: course.room })
          . onChange((v: string) => course.room = v)
          . layoutWeight(1)
        TextInput({ placeholder: '老师（可选）', text: course.teacher })
          . onChange((v: string) => course.teacher = v)
          . layoutWeight(1)
      }
      . width('100%')

      ForEach(course.slots, (slot: SlotDraft, si: number) => {
        this.SlotCard(course, slot, si)
      }, (slot: SlotDraft, si: number) => `${si}-${slot.dow}-${slot.periodStart}-${slot.periodEnd}-${slot.weekStart}-${slot.weekEnd}`)

      Text('+ 添加时间段')
        . fontSize(13)
        . fontColor(AppColors.primary)
        . padding(6)
        . onClick(() => {
          course.slots.push(new SlotDraft(1, 1, 2, 1, this.totalWeeks));
        })
    }
    . width('100%')
    . padding(12)
    . borderRadius(12)
    . backgroundColor(AppColors.surfaceVariant)
  }

  /** 时间段卡：分行布局（周几 / 节次 / 周次），节次自动带出时间 */
  @Builder
  SlotCard(course: CourseDraft, slot: SlotDraft, si: number) {
    Column({ space: 8 }) {
      Row() {
        Text(`时间段 ${si + 1}`)
          . fontSize(12)
          . fontColor(AppColors.onSurfaceVariant)
          . layoutWeight(1)
        Text('✕')
          . fontSize(13)
          . fontColor(AppColors.error)
          . padding(4)
          . onClick(() => {
            course.slots.splice(si, 1);
          })
      }
      . width('100%')

      // 周几（单选）
      Row({ space: 4 }) {
        Text('周几')
          . fontSize(13)
          . fontColor(AppColors.onSurface)
        ForEach([1, 2, 3, 4, 5, 6, 7], (d: number) => {
          Text(TaskTypeLabels.dayName(d))
            . fontSize(12)
            . fontColor(slot.dow === d ? AppColors.onPrimaryContainer : AppColors.onSurfaceVariant)
            . padding({ left: 9, right: 9, top: 4, bottom: 4 })
            . borderRadius(8)
            . backgroundColor(slot.dow === d ? AppColors.primaryContainer : '#00000000')
            . border({ width: 1, color: slot.dow === d ? AppColors.primaryContainer : AppColors.outline })
            . onClick(() => slot.dow = d)
        }, (d: number) => `${d}-${slot.dow}`)
      }
      . width('100%')

      // 节次（带出时间）
      Row({ space: 8 }) {
        Text('节次')
          . fontSize(13)
          . fontColor(AppColors.onSurface)
          . width(34)
        this.Stepper(slot.periodStart, 1, this.periods.count, (v: number): void => {
          slot.periodStart = v;
          if (slot.periodEnd < v) {
            slot.periodEnd = v;
          }
        })
        Text('至')
          . fontSize(13)
          . fontColor(AppColors.onSurfaceVariant)
        this.Stepper(slot.periodEnd, 1, this.periods.count, (v: number): void => {
          slot.periodEnd = v;
          if (slot.periodStart > v) {
            slot.periodStart = v;
          }
        })
        Text('节')
          . fontSize(13)
          . fontColor(AppColors.onSurface)
        Text(`${this.periods.time(slot.periodStart)[0]}-${this.periods.time(slot.periodEnd)[1]}`)
          . fontSize(12)
          . fontColor(AppColors.primary)
          . layoutWeight(1)
          . textAlign(TextAlign.End)
      }
      . width('100%')

      // 周次
      Row({ space: 8 }) {
        Text('周次')
          . fontSize(13)
          . fontColor(AppColors.onSurface)
          . width(34)
        this.Stepper(slot.weekStart, 1, 30, (v: number): void => {
          slot.weekStart = v;
          if (slot.weekEnd < v) {
            slot.weekEnd = v;
          }
        })
        Text('至')
          . fontSize(13)
          . fontColor(AppColors.onSurfaceVariant)
        this.Stepper(slot.weekEnd, 1, 30, (v: number): void => {
          slot.weekEnd = v;
          if (slot.weekStart > v) {
            slot.weekStart = v;
          }
        })
        Text('周')
          . fontSize(13)
          . fontColor(AppColors.onSurface)
        Blank()
      }
      . width('100%')

      Text(this.slotSummary(slot))
        . fontSize(11)
        . fontColor(AppColors.onSurfaceVariant)
        . width('100%')
    }
    . width('100%')
    . padding(8)
    . borderRadius(8)
    . border({ width: 1, color: AppColors.outline })
  }

  private applyPeriodTime(isStart: boolean, t: string): void {
    const n = this.editingPeriodN;
    const cur = this.periods.time(n);
    const next: string[] = isStart ? [t, cur[1]] : [cur[0], t];
    this.periods.times[n - 1] = next;
  }

  private periodInitHour(isStart: boolean): number {
    const p = parseTime(this.periods.time(this.editingPeriodN)[isStart ? 0 : 1]);
    return p === null ? 8 : p[0];
  }

  private periodInitMinute(isStart: boolean): number {
    const p = parseTime(this.periods.time(this.editingPeriodN)[isStart ? 0 : 1]);
    return p === null ? 0 : p[1];
  }

  /** 时间段摘要：展开次数与首次时间 */
  private slotSummary(slot: SlotDraft): string {
    const wEnd = Math.min(slot.weekEnd, this.totalWeeks);
    const count = Math.max(0, wEnd - slot.weekStart + 1);
    if (count === 0) {
      return '该时间段在学期周数范围外，不会生成课程';
    }
    const first = DateUtils.addDays(this.termStart, (slot.weekStart - 1) * 7 + (slot.dow - 1));
    const t0 = this.periods.time(slot.periodStart)[0];
    const t1 = this.periods.time(slot.periodEnd)[1];
    return `共 ${count} 次 · 首次 ${first} ${t0}-${t1}`;
  }
}
