import { CardOps } from './CardOps';
let storage = new LocalStorage();

/**
 * 周课表卡（2x4）：7 列周网格（按子计划本着色，今天列头高亮）+
 * 翻周（< >，状态按 formId 持久化）与刷新按钮（message → FormAbility）。
 * 点卡片其余区域打开 App。
 */
@Entry(storage)
@Component
struct WeekCard {
  @LocalStorageProp('rev') rev: string = '';
  @LocalStorageProp('masterName') masterName: string = '计划本';
  @LocalStorageProp('selectedNames') selectedNames: string = '';
  @LocalStorageProp('weekRange') weekRange: string = '';
  @LocalStorageProp('weekNo') weekNo: string = '';
  @LocalStorageProp('days') days: Record<string, Object>[] = [];
  @LocalStorageProp('hasData') hasData: boolean = false;

  build() {
    Column({ space: 4 }) {
      // 标题行：主本名 + 本卡显示的子本
      Row({ space: 6 }) {
        Text(this.masterName)
          . fontSize(24)
          . fontWeight(FontWeight.Bold)
          . fontColor('#000000')
          . maxLines(1)
        Blank()
        if (this.selectedNames.length > 0) {
          Text(this.selectedNames)
            . fontSize(14)
            . fontColor('#666666')
            . maxLines(1)
            . textOverflow({ overflow: TextOverflow.Ellipsis })
            . constraintSize({ maxWidth: '45%' })
        }
      }
      . width('100%')

      // 翻周控制行：< 周数/范围 >
      Row({ space: 8 }) {
        Text('<')
          . fontSize(26)
          . fontColor('#4A3B8C')
          . padding({ left: 10, right: 10, top: 2, bottom: 2 })
          . onClick(() => CardOps.send(this, 'prevWeek'))
        Column({ space: 1 }) {
          if (this.weekNo.length > 0) {
            Text(this.weekNo)
              . fontSize(16)
              . fontWeight(FontWeight.Bold)
              . fontColor('#4A3B8C')
          }
          Text(this.weekRange)
            . fontSize(15)
            . fontColor('#444444')
        }
        . layoutWeight(1)
        . alignItems(HorizontalAlign.Center)
        Text('>')
          . fontSize(26)
          . fontColor('#4A3B8C')
          . padding({ left: 10, right: 10, top: 2, bottom: 2 })
          . onClick(() => CardOps.send(this, 'nextWeek'))
        Text('↻')
          . fontSize(22)
          . fontColor('#4A3B8C')
          . padding(4)
          . onClick(() => CardOps.send(this, 'refresh'))
      }
      . width('100%')

      // 7 列周网格
      if (this.days.length === 0) {
        Text(this.hasData ? '本周暂无时段任务' : '打开 App 刷新')
          . fontSize(18)
          . fontColor('#9E9E9E')
          . layoutWeight(1)
          . width('100%')
          . textAlign(TextAlign.Center)
      } else {
        Row({ space: 2 }) {
          ForEach(this.days, (day: Record<string, Object>) => {
            this.DayColumn(day)
          }, (day: Record<string, Object>, idx: number) => `${this.rev}-${idx}-${day['weekday']}`)
        }
        . width('100%')
        . layoutWeight(1)
        . alignItems(VerticalAlign.Top)
      }
    }
    . width('100%')
    . height('100%')
    . padding(8)
    . backgroundColor('#FFFFFF')
    . borderRadius(20)
    . onClick(() => CardOps.openApp(this))
  }

  /** 2x4 列窄：每列只显示前 3 块，多余以提示行收尾 */
  private visibleTasks(day: Record<string, Object>): Record<string, Object>[] {
    const all = day['tasks'] as Record<string, Object>[];
    if (all.length <= 4) {
      return all;
    }
    const head = all.slice(0, 3);
    const more: Record<string, Object> = {
      'time': '',
      'title': '…' + (all.length - 3) + '项',
      'location': '',
      'color': '#00000000',
      'done': false
    };
    head.push(more);
    return head;
  }

  @Builder
  DayColumn(day: Record<string, Object>) {
    Column({ space: 2 }) {
      Column({ space: 1 }) {
        Text(day['weekday'] as string)
          . fontSize(15)
          . fontWeight(day['isToday'] === true ? FontWeight.Bold : FontWeight.Normal)
          . fontColor(day['isToday'] === true ? '#4A3B8C' : '#000000')
        Text(day['dateText'] as string)
          . fontSize(13)
          . fontColor('#444444')
      }
      . width('100%')
      . padding(2)
      . borderRadius(6)
      . backgroundColor(day['isToday'] === true ? '#E8DEF8' : '#00000000')

      ForEach(this.visibleTasks(day), (t: Record<string, Object>) => {
        Column({ space: 1 }) {
          if ((t['time'] as string).length > 0) {
            Text(t['time'] as string)
              . fontSize(12)
              . fontColor('#444444')
              . maxLines(1)
          }
          Text(t['title'] as string)
            . fontSize(14)
            . fontColor(t['done'] === true ? '#9E9E9E' : '#000000')
            . maxLines(2)
            . textOverflow({ overflow: TextOverflow.Ellipsis })
          if ((t['location'] as string).length > 0) {
            Text(t['location'] as string)
              . fontSize(12)
              . fontColor('#444444')
              . maxLines(1)
              . textOverflow({ overflow: TextOverflow.Ellipsis })
          }
        }
        . width('100%')
        . padding(3)
        . borderRadius(8)
        . backgroundColor(t['color'] as string)
        . alignItems(HorizontalAlign.Start)
      }, (t: Record<string, Object>, idx: number) => `${this.rev}-t-${idx}-${t['title']}`)
    }
    . layoutWeight(1)
    . width('100%')
    . alignItems(HorizontalAlign.Start)
  }
}
