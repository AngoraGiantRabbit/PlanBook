import { AppColors, ColorUtil } from '../common/Theme';
import { SubNotebookPalette } from '../model/Models';
import { DialogShell, TextButton } from './Ui';

/**
 * 通用颜色选择弹层：预设调色板 + HSV 自定义调色盘
 * （从 NotebookManagerPage 抽取复用：子计划本改色 / 课程颜色共用）。
 * allowClear = 提供「恢复默认」按钮（onClear，用于课程颜色回退课表色）。
 */
@ComponentV2
export struct ColorPickerSheet {
  @Param current: string | null = null;
  @Param allowClear: boolean = false;
  @Event onPick: (color: string) => void = (_c: string) => {};
  @Event onClear: () => void = () => {};
  @Event onDismiss: () => void = () => {};

  @Local showCustom: boolean = false;
  @Local hue: number = 0;
  @Local sat: number = 0;
  @Local bri: number = 0;
  /** 十六进制输入框文本（与 HSV 滑块双向联动） */
  @Local hexText: string = '';

  aboutToAppear(): void {
    const hsv = ColorUtil.toHsv(this.current);
    this.hue = hsv[0];
    this.sat = hsv[1];
    this.bri = hsv[2];
    this.hexText = this.customHex();
  }

  private customHex(): string {
    return ColorUtil.hsv(this.hue, this.sat, this.bri);
  }

  /** HSV 变化（滑块）→ 同步十六进制文本 */
  private syncFromSliders(): void {
    this.hexText = this.customHex();
  }

  /** 十六进制输入 → 合法（可带/不带 #，6 位）时同步 HSV；文本保持用户输入形态 */
  private syncFromInput(raw: string): void {
    const v = raw.trim().toUpperCase();
    this.hexText = v;
    const body = v.startsWith('#') ? v.substring(1) : v;
    if (body.length === 6 && /^[0-9A-F]{6}$/.test(body)) {
      const hsv = ColorUtil.toHsv(`#${body}`);
      this.hue = hsv[0];
      this.sat = hsv[1];
      this.bri = hsv[2];
    }
  }

  build() {
    Stack() {
      if (this.showCustom) {
        this.CustomColorPicker()
      } else {
        this.PalettePicker()
      }
    }
    . width('100%')
    . height('100%')
  }

  /** 预设调色板色圈（4 个一行）+ 自定义圈入口 */
  @Builder
  PalettePicker() {
    DialogShell({
      onDismiss: () => this.onDismiss()
    }) {
      Column({ space: 12 }) {
        Text('选择颜色')
          . fontSize(17)
          . fontWeight(FontWeight.Bold)
        Flex({ wrap: FlexWrap.Wrap, justifyContent: FlexAlign.Start }) {
          ForEach(SubNotebookPalette.all(), (hex: string) => {
            Stack() {
              Circle({ width: 40, height: 40 })
                . fill(hex)
              if (this.current === hex) {
                Text('✓')
                  . fontSize(18)
                  . fontColor('#FFFFFF')
              }
            }
            . margin(6)
            . borderRadius(20)
            . border({
              width: this.current === hex ? 3 : 1,
              color: this.current === hex ? AppColors.primary : AppColors.outline
            })
            . onClick(() => this.onPick(hex))
          }, (hex: string) => `${hex}-${this.current ?? ''}`)
        }
        . width('100%')

        Row({ space: 8 }) {
          Column()
            . width(40)
            . height(40)
            . borderRadius(20)
            . linearGradient({
              angle: 90,
              colors: [['#E57373', 0], ['#FFB74D', 0.17], ['#FFF176', 0.33], ['#81C784', 0.5],
                ['#4DD0E1', 0.67], ['#7986CB', 0.83], ['#BA68C8', 1]]
            })
            . border({ width: 1, color: AppColors.outline })
          Text('自定义颜色')
            . fontSize(14)
            . fontColor(AppColors.onSurface)
          Blank()
          Text('›')
            . fontSize(16)
            . fontColor(AppColors.onSurfaceVariant)
        }
        . width('100%')
        . padding({ top: 4, bottom: 4 })
        . onClick(() => this.showCustom = true)


        Row({ space: 4 }) {
          if (this.allowClear) {
            TextButton({
              label: '恢复默认',
              color: AppColors.error,
              onTap: () => this.onClear()
            })
          }
          Blank()
          TextButton({ label: '取消', onTap: () => this.onDismiss() })
        }
        . width('100%')
      }
      . width('100%')
    }
  }

  /** HSV 三滑块 + 实时预览，确定后回调 "#RRGGBB" */
  @Builder
  CustomColorPicker() {
    DialogShell({
      onDismiss: () => this.showCustom = false
    }) {
      Scroll() {
        Column({ space: 10 }) {
          Text('自定义颜色')
            . fontSize(17)
            . fontWeight(FontWeight.Bold)
          Row({ space: 12 }) {
            Circle({ width: 52, height: 52 })
              . fill(this.customHex())
              . border({ width: 1, color: AppColors.outline })
            TextInput({ text: this.hexText })
              . fontSize(16)
              . fontWeight(FontWeight.Medium)
              . fontColor(AppColors.primary)
              . constraintSize({ maxWidth: 220 })
              . onChange((v: string) => this.syncFromInput(v))
          }
          . width('100%')

          Text('可键入 6 位十六进制（如 B2E6D9），或拖动下方滑块')
            . fontSize(11)
            . fontColor(AppColors.onSurfaceVariant)
            . width('100%')

          Text('色相')
            . fontSize(12)
            . fontColor(AppColors.onSurfaceVariant)
            . width('100%')
          Row()
            . height(10)
            . width('100%')
            . borderRadius(5)
            . linearGradient({
              angle: 90,
              colors: [['#FF0000', 0], ['#FFFF00', 0.17], ['#00FF00', 0.33], ['#00FFFF', 0.5],
                ['#0000FF', 0.67], ['#FF00FF', 0.83], ['#FF0000', 1]]
            })
          Slider({ value: this.hue, min: 0, max: 360 })
            . selectedColor(AppColors.primary)
            . onChange((v: number) => {
              this.hue = v;
              this.syncFromSliders();
            })

          Text('饱和度')
            . fontSize(12)
            . fontColor(AppColors.onSurfaceVariant)
            . width('100%')
          Row()
            . height(10)
            . width('100%')
            . borderRadius(5)
            . linearGradient({
              angle: 90,
              colors: [[ColorUtil.hsv(this.hue, 0, 1), 0], [ColorUtil.hsv(this.hue, 1, 1), 1]]
            })
          Slider({ value: this.sat * 100, min: 0, max: 100 })
            . selectedColor(AppColors.primary)
            . onChange((v: number) => {
              this.sat = v / 100;
              this.syncFromSliders();
            })

          Text('明度')
            . fontSize(12)
            . fontColor(AppColors.onSurfaceVariant)
            . width('100%')
          Row()
            . height(10)
            . width('100%')
            . borderRadius(5)
            . linearGradient({
              angle: 90,
              colors: [['#000000', 0], [ColorUtil.hsv(this.hue, this.sat, 1), 1]]
            })
          Slider({ value: this.bri * 100, min: 0, max: 100 })
            . selectedColor(AppColors.primary)
            . onChange((v: number) => {
              this.bri = v / 100;
              this.syncFromSliders();
            })

          Row({ space: 4 }) {
            TextButton({ label: '返回', onTap: () => this.showCustom = false })
            Blank()
            TextButton({
              label: '确定',
              onTap: () => this.onPick(this.customHex())
            })
          }
          . width('100%')
        }
        . width('100%')
      }
      . scrollBar(BarState.Off)
      . align(Alignment.TopStart)
    }
  }
}
