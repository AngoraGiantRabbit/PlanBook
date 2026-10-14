let storage = new LocalStorage();

/**
 * 桌面卡片（对应 Android PlanBookWidget 的只读周视图）：
 * 一周 7 列日程网格（按子计划本着色，今天列头高亮）+ 底部今日待办（≤4 条，未完成优先）。
 * 点按任意区域打开 App；刷新为手动机制（App 内点刷新后 WidgetSync 推数据）。
 * 数据由 PlanBookFormAbility / WidgetSync 经 formBindingData 推入 LocalStorage。
 */
@Entry(storage)
@Component
struct PlanBookCard {
  @LocalStorageProp('masterName') masterName: string = '计划本';
  @LocalStorageProp('weekRange') weekRange: string = '';
  @LocalStorageProp('days') days: Record<string, Object>[] = [];
  @LocalStorageProp('todos') todos: Record<string, Object>[] = [];
  @LocalStorageProp('todoCount') todoCount: number = 0;
  @LocalStorageProp('hasData') hasData: boolean = false;

  build() {
    Column({ space: 4 }) {
      // 标题行：主计划本名 + 周范围
      Row({ space: 6 }) {
        Text(this.masterName)
          . fontSize(26)
          . fontWeight(FontWeight.Bold)
          . fontColor('#000000')
          . maxLines(1)
        Text(this.weekRange)
          . fontSize(18)
          . fontColor('#444444')
          . maxLines(1)
      }
      . width('100%')

      if (this.days.length === 0) {
        Text(this.hasData ? '本周暂无时段任务' : '暂无数据，打开 App 刷新')
          . fontSize(18)
          . fontColor('#9E9E9E')
          . layoutWeight(1)
          . width('100%')
          . textAlign(TextAlign.Center)
      } else {
        // 7 列周网格
        Row({ space: 2 }) {
          ForEach(this.days, (day: Record<string, Object>) => {
            this.DayColumn(day)
          }, (day: Record<string, Object>, idx: number) => `${day['weekday']}-${idx}`)
        }
        . width('100%')
        . layoutWeight(1)
        . alignItems(VerticalAlign.Top)
      }

      // 今日待办（只读；与 App 计划本页底部口径一致，最多 4 条）
      if (this.todoCount > 0) {
        Column({ space: 1 }) {
          Text('今日待办')
            . fontSize(16)
            . fontWeight(FontWeight.Bold)
            . fontColor('#4A3B8C')
          ForEach(this.todos.slice(0, 4), (todo: Record<string, Object>) => {
            Text(todo['text'] as string)
              . fontSize(16)
              . fontColor(todo['done'] === true ? '#9E9E9E' : '#000000')
              . maxLines(1)
              . textOverflow({ overflow: TextOverflow.Ellipsis })
          }, (todo: Record<string, Object>, idx: number) => `${todo['title']}-${idx}`)
          if (this.todoCount > 4) {
            Text(`…还有 ${this.todoCount - 4} 条`)
              . fontSize(14)
              . fontColor('#9E9E9E')
          }
        }
        . width('100%')
        . padding(4)
        . borderRadius(12)
        . backgroundColor('#F5F1FA')
        . alignItems(HorizontalAlign.Start)
      }

      Text('在 App 内点刷新同步')
        . fontSize(13)
        . fontColor('#9E9E9E')
        . width('100%')
        . textAlign(TextAlign.Start)
    }
    . width('100%')
    . height('100%')
    . padding(8)
    . backgroundColor('#FFFFFF')
    . borderRadius(20)
    . onClick(() => {
      const act: Record<string, Object | string> = {
        'action': 'router',
        'abilityName': 'EntryAbility'
      };
      postCardAction(this, act);
    })
  }

  @Builder
  DayColumn(day: Record<string, Object>) {
    Column({ space: 2 }) {
      // 列头（今天高亮）
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

      // 任务块（时间 + 标题 + 教室）
      ForEach(day['tasks'] as Record<string, Object>[], (t: Record<string, Object>) => {
        Column({ space: 1 }) {
          if ((t['time'] as string).length > 0) {
            Text(t['time'] as string)
              . fontSize(12)
              . fontColor('#444444')
              . maxLines(1)
          }
          Text(t['title'] as string)
            . fontSize(14)
            . fontColor('#000000')
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
      }, (t: Record<string, Object>, idx: number) => `${t['title']}-${idx}`)
    }
    . layoutWeight(1)
    . width('100%')
    . alignItems(HorizontalAlign.Start)
  }
}
