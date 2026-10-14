import { AppColors } from '../common/Theme';

/**
 * 通用 UI 原语：模态外壳 / chip / 文本按钮。
 * 全屏覆盖层方案：页面根部 Stack 叠放，半透明背景点击关闭，等价 Android AlertDialog。
 */

@Builder
function emptyContent() {
}

/** 模态外壳：暗背景 + 居中卡片；内容经尾随闭包传入（映射到 content BuilderParam） */
@ComponentV2
export struct DialogShell {
  @BuilderParam content: () => void = emptyContent;
  @Event onDismiss: () => void = () => {};

  build() {
    Stack({ alignContent: Alignment.Center }) {
      Column()
        . width('100%')
        . height('100%')
        . backgroundColor('#99000000')
        . onClick(() => this.onDismiss())
      Column() {
        this.content()
      }
      . width('88%')
      . constraintSize({ maxHeight: '82%' })
      . padding(20)
      . borderRadius(24)
      . backgroundColor(AppColors.surface)
      . shadow(ShadowStyle.OUTER_DEFAULT_LG)
    }
    . width('100%')
    . height('100%')
  }
}

/** 文本按钮（对应 Material TextButton） */
@ComponentV2
export struct TextButton {
  @Param label: string = '';
  @Param isEnabled: boolean = true;
  @Param color: string = AppColors.primary;
  @Event onTap: () => void = () => {};

  build() {
    Text(this.label)
      . fontSize(15)
      . fontColor(this.isEnabled ? this.color : '#61000000')
      . padding({ left: 12, right: 12, top: 8, bottom: 8 })
      . onClick(() => {
        if (this.isEnabled) {
          this.onTap();
        }
      })
  }
}

/** FilterChip 等价物：可选中的小胶囊；leadingDot 为颜色点 "#RRGGBB" */
@ComponentV2
export struct Chip {
  @Param label: string = '';
  @Param selected: boolean = false;
  @Param isEnabled: boolean = true;
  @Param leadingDot: string | null = null;
  @Event onTap: () => void = () => {};

  build() {
    Row({ space: 5 }) {
      if (this.leadingDot !== null) {
        Circle({ width: 9, height: 9 })
          . fill(this.leadingDot)
      }
      Text(this.label)
        . fontSize(13)
        . fontColor(this.selected ? AppColors.onPrimaryContainer : AppColors.onSurfaceVariant)
        . maxLines(1)
    }
    . padding({ left: 12, right: 12, top: 7, bottom: 7 })
    . borderRadius(10)
    . backgroundColor(this.selected ? AppColors.primaryContainer : '#00000000')
    . border({ width: 1, color: this.selected ? AppColors.primaryContainer : AppColors.outline })
    . opacity(this.isEnabled ? 1 : 0.45)
    . onClick(() => {
      if (this.isEnabled) {
        this.onTap();
      }
    })
  }
}
