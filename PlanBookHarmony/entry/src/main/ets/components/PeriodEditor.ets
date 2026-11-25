import { AppColors } from '../common/Theme';
import { PeriodConfig } from '../model/Models';

/**
 * 节次时间编辑器（新建课表内嵌 / 管理页「改时间」弹层共用）：
 * - 一天几节课（步进器，1..20）；
 * - 每节一个小块「第N节 HH:mm-HH:mm」，点击回调 onEditPeriod(n)，由宿主弹起止时间选择器
 *   （选择器需全屏覆盖，宿主页面根部渲染；先选开始再选结束的链路也由宿主实现）；
 * - 直接修改传入的 PeriodConfig（@ObservedV2 引用共享，宿主保存时读取）。
 */
@ComponentV2
export struct PeriodEditor {
  @Param config: PeriodConfig = PeriodConfig.default();
  @Param compact: boolean = false;
  /** 点击第 n 节小块（1..count） */
  @Event onEditPeriod: (n: number) => void = (_n: number) => {};

  build() {
    Column({ space: 8 }) {
      Row({ space: 10 }) {
        Text('一天几节课')
          . fontSize(14)
          . fontColor(AppColors.onSurface)
        this.Stepper()
        Blank()
        Text(`共 ${this.config.count} 节`)
          . fontSize(12)
          . fontColor(AppColors.onSurfaceVariant)
      }
      . width('100%')

      Flex({ wrap: FlexWrap.Wrap, justifyContent: FlexAlign.Start }) {
        ForEach(this.periodIndexes(), (n: number) => {
          Text(this.config.label(n))
            . fontSize(this.compact ? 11 : 12)
            . fontColor(AppColors.primary)
            . padding({ left: 8, right: 8, top: 4, bottom: 4 })
            . margin(2)
            . borderRadius(8)
            . backgroundColor(AppColors.surface)
            . border({ width: 1, color: AppColors.outline })
            . onClick(() => this.onEditPeriod(n))
        }, (n: number) => `${n}-${this.config.label(n)}`)
      }
      . width('100%')

      Text('点击节次小块修改该节起止时间')
        . fontSize(11)
        . fontColor(AppColors.onSurfaceVariant)
        . width('100%')
    }
    . width('100%')
  }

  private periodIndexes(): number[] {
    const out: number[] = [];
    for (let n = 1; n <= this.config.count; n++) {
      out.push(n);
    }
    return out;
  }

  /** 数字步进器：[−] 值 [+] */
  @Builder
  Stepper() {
    Row({ space: 6 }) {
      Text('−')
        . fontSize(16)
        . fontColor(this.config.count > 1 ? AppColors.primary : AppColors.outline)
        . padding({ left: 8, right: 8, top: 2, bottom: 2 })
        . onClick(() => {
          if (this.config.count > 1) {
            this.config.resize(this.config.count - 1);
          }
        })
      Text(`${this.config.count}`)
        . fontSize(14)
        . fontColor(AppColors.onSurface)
        . width(28)
        . textAlign(TextAlign.Center)
      Text('+')
        . fontSize(16)
        . fontColor(this.config.count < 20 ? AppColors.primary : AppColors.outline)
        . padding({ left: 8, right: 8, top: 2, bottom: 2 })
        . onClick(() => this.config.resize(this.config.count + 1))
    }
  }
}
