import { AppColors } from '../common/Theme';
import { DateUtils, Ymd } from '../common/DateUtils';
import { DialogShell, TextButton } from './Ui';

/**
 * 日期 / 时间选择器（对应 Android 版 Pickers.kt）。
 * 用 TextPicker 三列/两列自组（24 小时制），避免系统弹窗 API 的版本差异。
 */

/** 可点击的只读字段（选择器入口） */
@ComponentV2
export struct PickerField {
  @Param value: string = '';
  @Param label: string = '';
  @Param isEnabled: boolean = true;
  @Event onTap: () => void = () => {};

  build() {
    Column({ space: 4 }) {
      Text(this.label)
        . fontSize(12)
        . fontColor(AppColors.onSurfaceVariant)
        . alignSelf(ItemAlign.Start)
      Text(this.value.length > 0 ? this.value : '未设置')
        . fontSize(15)
        . fontColor(this.value.length > 0 ? AppColors.onSurface : AppColors.onSurfaceVariant)
        . width('100%')
        . padding(12)
        . borderRadius(8)
        . border({ width: 1, color: this.isEnabled ? AppColors.outline : AppColors.surfaceVariant })
        . onClick(() => {
          if (this.isEnabled) {
            this.onTap();
          }
        })
    }
    . width('100%')
    . opacity(this.isEnabled ? 1 : 0.55)
  }
}

/** 解析 "HH:mm" → [h, m]；空/非法返回 null */
export function parseTime(hhmm: string): number[] | null {
  if (hhmm.length === 0) {
    return null;
  }
  const parts = hhmm.split(':');
  if (parts.length !== 2) {
    return null;
  }
  const h = Number(parts[0]);
  const m = Number(parts[1]);
  if (!Number.isFinite(h) || !Number.isFinite(m)) {
    return null;
  }
  return [h, m];
}

/** 日期选择器（年/月/日 三列） */
@ComponentV2
export struct DatePickerOverlay {
  @Event onConfirm: (date: string) => void = (d: string) => {};
  @Event onDismiss: () => void = () => {};
  @Param initialDate: string = '';
  /** 供内部使用的选中索引 */
  @Local yearIdx: number = 0;
  @Local monthIdx: number = 0;
  @Local dayIdx: number = 0;
  @Local dayRange: string[] = [];

  aboutToAppear(): void {
    let init = DateUtils.toParts(this.initialDate);
    if (init === null) {
      init = DateUtils.toParts(DateUtils.today());
    }
    if (init === null) {
      init = new Ymd(2000, 1, 1);
    }
    this.yearIdx = init.y - 2000;
    this.monthIdx = init.m - 1;
    this.dayIdx = init.d - 1;
    this.dayRange = DatePickerOverlay.dayList(this.year(), this.month());
  }

  private year(): number {
    return 2000 + this.yearIdx;
  }

  private month(): number {
    return this.monthIdx + 1;
  }

  private static dayList(y: number, m: number): string[] {
    const len = DateUtils.monthLength(y, m);
    const out: string[] = [];
    for (let d = 1; d <= len; d++) {
      out.push(`${d}日`);
    }
    return out;
  }

  private static yearList(): string[] {
    const out: string[] = [];
    for (let y = 2000; y <= 2100; y++) {
      out.push(`${y}年`);
    }
    return out;
  }

  private static monthList(): string[] {
    const out: string[] = [];
    for (let m = 1; m <= 12; m++) {
      out.push(`${m}月`);
    }
    return out;
  }

  build() {
    DialogShell({ onDismiss: () => this.onDismiss() }) {
      Column({ space: 12 }) {
        Text('选择日期')
          . fontSize(17)
          . fontWeight(FontWeight.Bold)
        Row({ space: 6 }) {
          TextPicker({ range: DatePickerOverlay.yearList(), selected: this.yearIdx })
            . canLoop(false)
            . onChange((value: string | string[], index: number | number[]) => {
              this.yearIdx = index as number;
              this.dayRange = DatePickerOverlay.dayList(this.year(), this.month());
              if (this.dayIdx >= this.dayRange.length) {
                this.dayIdx = this.dayRange.length - 1;
              }
            })
            . layoutWeight(2)
          TextPicker({ range: DatePickerOverlay.monthList(), selected: this.monthIdx })
            . canLoop(false)
            . onChange((value: string | string[], index: number | number[]) => {
              this.monthIdx = index as number;
              this.dayRange = DatePickerOverlay.dayList(this.year(), this.month());
              if (this.dayIdx >= this.dayRange.length) {
                this.dayIdx = this.dayRange.length - 1;
              }
            })
            . layoutWeight(1)
          TextPicker({ range: this.dayRange, selected: this.dayIdx })
            . canLoop(false)
            . onChange((value: string | string[], index: number | number[]) => {
              this.dayIdx = index as number;
            })
            . layoutWeight(1)
        }
        . height(160)
        Row({ space: 8 }) {
          TextButton({ label: '取消', onTap: () => this.onDismiss() })
          Blank()
          TextButton({
            label: '确定',
            onTap: () => {
              this.onConfirm(DateUtils.fromParts(this.year(), this.month(), this.dayIdx + 1));
            }
          })
        }
        . width('100%')
      }
    }
  }
}

/** 时间选择器（时/分 两列，24 小时制） */
@ComponentV2
export struct TimePickerOverlay {
  @Event onConfirm: (hhmm: string) => void = (t: string) => {};
  @Event onDismiss: () => void = () => {};
  @Param initialHour: number = 9;
  @Param initialMinute: number = 0;
  @Local hourIdx: number = 9;
  @Local minuteIdx: number = 0;

  aboutToAppear(): void {
    this.hourIdx = Math.max(0, Math.min(23, this.initialHour));
    this.minuteIdx = Math.max(0, Math.min(59, this.initialMinute));
  }

  build() {
    DialogShell({ onDismiss: () => this.onDismiss() }) {
      Column({ space: 12 }) {
        Text('选择时间')
          . fontSize(17)
          . fontWeight(FontWeight.Bold)
        Row({ space: 6 }) {
          TextPicker({ range: TimePickerOverlay.hourList(), selected: this.hourIdx })
            . canLoop(false)
            . onChange((value: string | string[], index: number | number[]) => {
              this.hourIdx = index as number;
            })
            . layoutWeight(1)
          TextPicker({ range: TimePickerOverlay.minuteList(), selected: this.minuteIdx })
            . canLoop(false)
            . onChange((value: string | string[], index: number | number[]) => {
              this.minuteIdx = index as number;
            })
            . layoutWeight(1)
        }
        . height(160)
        Row({ space: 8 }) {
          TextButton({ label: '取消', onTap: () => this.onDismiss() })
          Blank()
          TextButton({
            label: '确定',
            onTap: () => {
              this.onConfirm(DateUtils.minutesToTime(this.hourIdx * 60 + this.minuteIdx));
            }
          })
        }
        . width('100%')
      }
    }
  }

  private static hourList(): string[] {
    const out: string[] = [];
    for (let h = 0; h <= 23; h++) {
      out.push(`${h}`.padStart(2, '0') + '时');
    }
    return out;
  }

  private static minuteList(): string[] {
    const out: string[] = [];
    for (let m = 0; m <= 59; m++) {
      out.push(`${m}`.padStart(2, '0') + '分');
    }
    return out;
  }
}
