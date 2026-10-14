import { AppColors } from '../common/Theme';
import { DateUtils } from '../common/DateUtils';
import { ReviewCalendarVM } from '../viewmodel/ViewModels';
import { router } from '@kit.ArkUI';

/**
 * 复盘页（对应 Android ReviewScreen）：月历。
 * 今天高亮；有复盘内容的日期下方加小圆点；左右切月；点某天进编辑页。
 */
@ComponentV2
export struct ReviewPage {
  @Local vm: ReviewCalendarVM = new ReviewCalendarVM();

  aboutToAppear(): void {
    this.vm.attach();
  }

  aboutToDisappear(): void {
    this.vm.detach();
  }

  build() {
    Column() {
      Row({ space: 4 }) {
        Text(DateUtils.cnMonth(this.vm.month))
          . fontSize(20)
          . fontWeight(FontWeight.Bold)
          . fontColor(AppColors.onSurface)
          . layoutWeight(1)
          . padding({ left: 16 })
        SymbolGlyph($r('sys.symbol.chevron_left'))
          . fontSize(24)
          . fontColor([AppColors.onSurfaceVariant])
          . onClick(() => this.vm.changeMonth(-1))
        SymbolGlyph($r('sys.symbol.chevron_right'))
          . fontSize(24)
          . fontColor([AppColors.onSurfaceVariant])
          . margin({ right: 12 })
          . onClick(() => this.vm.changeMonth(1))
      }
      . width('100%')
      . padding({ top: 10, bottom: 6 })

      // 星期表头（周一~周日，周末标红）
      Row() {
        ForEach([1, 2, 3, 4, 5, 6, 7], (iso: number) => {
          Text(DateUtils.weekdayCn(iso))
            . fontSize(13)
            . fontColor(iso >= 6 ? AppColors.error : AppColors.onSurfaceVariant)
            . layoutWeight(1)
            . textAlign(TextAlign.Center)
        }, (iso: number) => `${iso}`)
      }
      . width('100%')
      . padding({ bottom: 4 })

      this.MonthGrid()
    }
    . width('100%')
    . height('100%')
    . backgroundColor(AppColors.surface)
  }

  /** 月历格子：null = 非本月空位 */
  private cells(): (string | null)[] {
    const p = DateUtils.toParts(this.vm.month);
    const y = p === null ? 2000 : p.y;
    const m = p === null ? 1 : p.m;
    const monthStart = p === null ? '2000-01-01' : this.vm.month;
    const offset = DateUtils.dayOfWeek(monthStart) - 1;
    const days = DateUtils.monthLength(y, m);
    const out: (string | null)[] = [];
    for (let i = 0; i < offset; i++) {
      out.push(null);
    }
    for (let d = 1; d <= days; d++) {
      out.push(DateUtils.fromParts(y, m, d));
    }
    while (out.length % 7 !== 0) {
      out.push(null);
    }
    return out;
  }

  private dayNum(cell: string): number {
    const p = DateUtils.toParts(cell);
    return p === null ? 0 : p.d;
  }

  @Builder
  MonthGrid() {
    Column({ space: 0 }) {
      ForEach(this.weekRows(), (week: (string | null)[], rowIdx: number) => {
        Row() {
          ForEach(week, (cell: string | null) => {
            this.MonthCell(cell)
          }, (cell: string | null, idx: number) => `${cell}-${idx}-${rowIdx}`)
        }
        . width('100%')
      }, (week: (string | null)[], rowIdx: number) => `${rowIdx}-${this.vm.month}-${this.vm.reviewDates.size}`)
    }
    . width('100%')
  }

  private weekRows(): (string | null)[][] {
    const all = this.cells();
    const rows: (string | null)[][] = [];
    for (let i = 0; i < all.length; i += 7) {
      rows.push(all.slice(i, i + 7));
    }
    return rows;
  }

  @Builder
  MonthCell(cell: string | null) {
    Stack() {
      if (cell !== null) {
        Column({ space: 2 }) {
          Text(`${this.dayNum(cell)}`)
            . fontSize(15)
            . fontWeight(cell === DateUtils.today() ? FontWeight.Bold : FontWeight.Normal)
            . fontColor(cell === DateUtils.today() ? AppColors.primary : AppColors.onSurface)
          if (this.vm.reviewDates.has(cell)) {
            Circle({ width: 5, height: 5 })
              . fill(AppColors.tertiary)
          }
        }
      }
    }
    . layoutWeight(1)
    . aspectRatio(1)
    . margin(2)
    . borderRadius(18)
    . backgroundColor(cell !== null && cell === DateUtils.today()
      ? ColorUtilToday.todayBg() : '#00000000')
    . onClick(() => {
      if (cell !== null) {
        router.pushUrl({ url: 'pages/ReviewEditPage', params: { date: cell } });
      }
    })
  }
}

class ColorUtilToday {
  static todayBg(): string {
    return '#266750A4';
  }
}
