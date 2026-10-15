import { CardOps } from './CardOps';
let storage = new LocalStorage();

/**
 * 今日待办卡（1x2 / 2x2）：今天 FLEX + 无时段 ONE_OFF + 长期任务，未完成在前；
 * 行首按子计划本着色。只读；点卡片打开 App。
 */
@Entry(storage)
@Component
struct TodoCard {
  @LocalStorageProp('rev') rev: string = '';
  @LocalStorageProp('masterName') masterName: string = '计划本';
  @LocalStorageProp('selectedNames') selectedNames: string = '';
  @LocalStorageProp('todayText') todayText: string = '';
  @LocalStorageProp('todos') todos: Record<string, Object>[] = [];
  @LocalStorageProp('todoCount') todoCount: number = 0;
  @LocalStorageProp('hasData') hasData: boolean = false;

  build() {
    Column({ space: 4 }) {
      Row() {
        Text('今日待办')
          . fontSize(16)
          . fontWeight(FontWeight.Bold)
          . fontColor('#000000')
        Blank()
        Text(this.todayText)
          . fontSize(12)
          . fontColor('#666666')
      }
      . width('100%')

      if (this.todoCount === 0) {
        Text(this.hasData ? '今天没有待办' : '打开 App 初始化')
          . fontSize(14)
          . fontColor('#9E9E9E')
          . layoutWeight(1)
          . width('100%')
          . textAlign(TextAlign.Center)
      } else {
        Column({ space: 3 }) {
          ForEach(this.todos.slice(0, 8), (todo: Record<string, Object>) => {
            Row({ space: 5 }) {
              Circle({ width: 6, height: 6 })
                . fill(todo['color'] as string)
                . align(Alignment.Center)
              Text(todo['text'] as string)
                . fontSize(13)
                . fontColor(todo['done'] === true ? '#9E9E9E' : '#000000')
                . maxLines(1)
                . textOverflow({ overflow: TextOverflow.Ellipsis })
                . layoutWeight(1)
            }
            . width('100%')
          }, (todo: Record<string, Object>, idx: number) => `${this.rev}-${idx}-${todo['title']}`)
          if (this.todoCount > 8) {
            Text(`…还有 ${this.todoCount - 8} 条`)
              . fontSize(11)
              . fontColor('#9E9E9E')
          }
        }
        . width('100%')
        . layoutWeight(1)
        . alignItems(HorizontalAlign.Start)
      }

      if (this.selectedNames.length > 0) {
        Text(this.selectedNames)
          . fontSize(10)
          . fontColor('#9E9E9E')
          . maxLines(1)
          . textOverflow({ overflow: TextOverflow.Ellipsis })
          . width('100%')
      }
    }
    . width('100%')
    . height('100%')
    . padding(8)
    . backgroundColor('#FFFFFF')
    . borderRadius(16)
    . onClick(() => CardOps.openApp(this))
  }
}
