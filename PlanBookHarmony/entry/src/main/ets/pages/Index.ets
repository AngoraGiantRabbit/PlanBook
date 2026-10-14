import { PlanBookPage } from './PlanBookPage';
import { TaskListPage } from './TaskListPage';
import { ReviewPage } from './ReviewPage';
import { SettingsPage } from './SettingsPage';
import { router } from '@kit.ArkUI';

/**
 * 首页：底部四 Tab（计划本 / 待办 / 复盘 / 设置），对应 Android PlanBookApp 的 Scaffold+NavHost。
 * 复盘编辑页与子计划本管理页为独立路由页（router push），返回时 Tab 状态保留。
 */
@Entry
@ComponentV2
struct Index {
  @Local currentTab: number = 0;

  build() {
    Tabs({ barPosition: BarPosition.End, index: this.currentTab }) {
      TabContent() {
        PlanBookPage({
          onOpenReview: (date: string) => {
            router.pushUrl({ url: 'pages/ReviewEditPage', params: { date: date } });
          }
        })
      }
      . tabBar(this.TabBar(0, '计划本', $r('sys.symbol.checkmark_circle')))

      TabContent() {
        TaskListPage()
      }
      . tabBar(this.TabBar(1, '待办', $r('sys.symbol.list_bullet')))

      TabContent() {
        ReviewPage()
      }
      . tabBar(this.TabBar(2, '复盘', $r('sys.symbol.calendar')))

      TabContent() {
        SettingsPage()
      }
      . tabBar(this.TabBar(3, '设置', $r('sys.symbol.gearshape')))
    }
    . scrollable(false)
    . barHeight(56)
    . onChange((idx: number) => this.currentTab = idx)
    . width('100%')
    . height('100%')
  }

  @Builder
  TabBar(index: number, title: string, icon: Resource) {
    Column({ space: 3 }) {
      SymbolGlyph(icon)
        . fontSize(24)
        . fontColor([this.currentTab === index ? '#6750A4' : '#49454F'])
      Text(title)
        . fontSize(11)
        . fontColor(this.currentTab === index ? '#6750A4' : '#49454F')
    }
    . width('100%')
    . height(54)
    . justifyContent(FlexAlign.Center)
  }
}
