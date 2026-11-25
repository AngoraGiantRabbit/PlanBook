import { AppColors } from '../common/Theme';
import { CourseOccDraft } from '../model/Models';
import { CourseColorsVM } from '../viewmodel/ViewModels';
import { ColorPickerSheet } from '../components/ColorPickerSheet';
import { CourseEditSheet } from '../components/CourseEditSheet';
import { router } from '@kit.ArkUI';

/**
 * 现有课程页（管理页「现有课程」入口，按课表）：
 * 该课表的全部课程以色块呈现（块色 = 课程颜色，未设置回退课表色）；
 * 点块设置该课颜色（仅本课表内生效）；右下角加号可新建课程（整门课表单）。
 */
@Entry
@ComponentV2
struct CourseColorsPage {
  @Local vm: CourseColorsVM = new CourseColorsVM();
  /** 正在选色的课程名；null = 无 */
  @Local picking: string | null = null;
  @Local showAddCourse: boolean = false;

  aboutToAppear(): void {
    const params = router.getParams() as Record<string, Object>;
    const subId = params !== undefined && params !== null && params['subId'] !== undefined
      ? Number(params['subId']) : 0;
    this.vm.attach(subId);
  }

  aboutToDisappear(): void {
    this.vm.detach();
  }

  private get subId(): number {
    return this.vm.notebook !== null ? this.vm.notebook.id : 0;
  }

  build() {
    Stack({ alignContent: Alignment.BottomEnd }) {
      Column() {
        Row({ space: 4 }) {
          SymbolGlyph($r('sys.symbol.chevron_left'))
            . fontSize(26)
            . fontColor([AppColors.onSurface])
            . onClick(() => router.back())
          Text(`现有课程 · ${this.vm.notebook !== null ? this.vm.notebook.name : ''}`)
            . fontSize(19)
            . fontWeight(FontWeight.Bold)
            . fontColor(AppColors.onSurface)
            . layoutWeight(1)
            . maxLines(1)
        }
        . width('100%')
        . padding({ left: 8, right: 16, top: 8, bottom: 4 })

        Text('点色块设置该课颜色（仅本课表内生效）；未设置的课显示课表颜色。加号新建课程。')
          . fontSize(12)
          . fontColor(AppColors.onSurfaceVariant)
          . width('100%')
          . padding({ left: 16, right: 16, top: 4, bottom: 8 })

        if (this.vm.courses.length === 0) {
          Text('这个课表还没有课程')
            . fontSize(14)
            . fontColor(AppColors.onSurfaceVariant)
            . padding({ top: 32 })
        } else {
          Scroll() {
            Flex({ wrap: FlexWrap.Wrap, justifyContent: FlexAlign.Start }) {
              ForEach(this.vm.courses, (title: string) => {
                this.CourseBlock(title)
              }, (title: string) => `${title}-${this.vm.colorOf(title) ?? ''}-${this.vm.fallbackColor()}`)
            }
            . width('100%')
            . padding({ left: 16, right: 16, top: 8, bottom: 90 })
          }
          . scrollBar(BarState.Off)
          . layoutWeight(1)
          . align(Alignment.TopStart)
        }
      }
      . width('100%')
      . height('100%')

      // 加号：新建课程（首页同款，落到本课表）
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
      . onClick(() => this.showAddCourse = true)

      if (this.picking !== null) {
        ColorPickerSheet({
          current: this.vm.colorOf(this.picking ?? ''),
          allowClear: this.vm.colorOf(this.picking ?? '') !== null,
          onPick: (c: string) => {
            const t = this.picking;
            if (t !== null) {
              this.vm.setColor(t, c);
            }
            this.picking = null;
          },
          onClear: () => {
            const t = this.picking;
            if (t !== null) {
              this.vm.clearColor(t);
            }
            this.picking = null;
          },
          onDismiss: () => this.picking = null
        })
      }

      if (this.showAddCourse) {
        CourseEditSheet({
          notebookId: this.subId,
          existing: null,
          onConfirm: (name: string, room: string, teacher: string, occs: CourseOccDraft[]) => {
            this.showAddCourse = false;
            this.vm.saveCourse(name, room, teacher, occs);
          },
          onDismiss: () => this.showAddCourse = false
        })
      }
    }
    . width('100%')
    . height('100%')
    . backgroundColor(AppColors.surface)
  }

  /** 课程色块：块色 = 课程颜色（未设置回退课表色），角标区分是否自定义 */
  @Builder
  CourseBlock(title: string) {
    Column({ space: 4 }) {
      Text(title)
        . fontSize(14)
        . fontColor(AppColors.onSurface)
        . maxLines(1)
        . textOverflow({ overflow: TextOverflow.Ellipsis })
        . constraintSize({ maxWidth: 240 })
      Text(this.vm.colorOf(title) === null ? '课表色' : '自定义')
        . fontSize(10)
        . fontColor(AppColors.onSurfaceVariant)
    }
    . justifyContent(FlexAlign.Center)
    . alignItems(HorizontalAlign.Center)
    . padding({ left: 16, right: 16, top: 12, bottom: 12 })
    . margin(6)
    . borderRadius(12)
    . backgroundColor(this.vm.colorOf(title) === null ? this.vm.fallbackColor() : (this.vm.colorOf(title) ?? '#9E9E9E'))
    . border({
      width: this.vm.colorOf(title) === null ? 2 : 0,
      color: AppColors.outline
    })
    . onClick(() => this.picking = title)
  }
}
