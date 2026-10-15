import { CardOps } from './CardOps';

let storage = new LocalStorage();

/**
 * 整页周课表卡（4x4/6x4）：与 App 首页同构的时间网格——
 * 08:00-24:00 逐小时格线，课程块按实际时刻定位（早八在顶、10:20 在对应高度），
 * 重叠课程横向并列；底部今日待办条 + 翻周/刷新。只读。
 */
@Entry(storage)
@Component
struct LargeCard {
  @LocalStorageProp('rev') rev: string = '';
  @LocalStorageProp('masterName') masterName: string = '计划本';
  @LocalStorageProp('selectedNames') selectedNames: string = '';
  @LocalStorageProp('weekRange') weekRange: string = '';
  @LocalStorageProp('weekNo') weekNo: string = '';
  @LocalStorageProp('todayText') todayText: string = '';
  @LocalStorageProp('days') days: Record<string, Object>[] = [];
  @LocalStorageProp('todos') todos: Record<string, Object>[] = [];
  @LocalStorageProp('todoCount') todoCount: number = 0;
  @LocalStorageProp('hasData') hasData: boolean = false;
  /** 网格范围：08:00-24:00（16 小时） */
  private readonly gridStartMin: number = 8 * 60;
  private readonly gridEndMin: number = 24 * 60;
  private readonly totalMin: number = 16 * 60;

  build() {
    Column({ space: 6 }) {
      // 标题行
      Row({ space: 8 }) {
        Text(this.masterName)
          . fontSize(22)
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
            . constraintSize({ maxWidth: '50%' })
        }
      }
      . width('100%')

      // 翻周控制行
      Row({ space: 12 }) {
        Text('<')
          . fontSize(26)
          . fontColor('#4A3B8C')
          . padding({ left: 14, right: 14, top: 2, bottom: 2 })
          . onClick(() => CardOps.send(this, 'prevWeek'))
        Row({ space: 10 }) {
          if (this.weekNo.length > 0) {
            Text(this.weekNo)
              . fontSize(18)
              . fontWeight(FontWeight.Bold)
              . fontColor('#4A3B8C')
          }
          Text(this.weekRange)
            . fontSize(16)
            . fontColor('#444444')
        }
        . layoutWeight(1)
        . justifyContent(FlexAlign.Center)
        Text('>')
          . fontSize(26)
          . fontColor('#4A3B8C')
          . padding({ left: 14, right: 14, top: 2, bottom: 2 })
          . onClick(() => CardOps.send(this, 'nextWeek'))
        Text('↻')
          . fontSize(22)
          . fontColor('#4A3B8C')
          . padding(6)
          . onClick(() => CardOps.send(this, 'refresh'))
      }
      . width('100%')

      // 时间网格：左时间轴 + 7 天列
      if (this.days.length === 0) {
        Text(this.hasData ? '本周暂无时段任务' : '打开 App 刷新')
          . fontSize(22)
          . fontColor('#9E9E9E')
          . layoutWeight(1)
          . width('100%')
          . textAlign(TextAlign.Center)
      } else {
        // 列头行（周几+日期，今天高亮）与网格对齐
        Row({ space: 1 }) {
          Column()
            . width(24)
          ForEach(this.days, (day: Record<string, Object>) => {
            Column({ space: 1 }) {
              Text(day['weekday'] as string)
                . fontSize(14)
                . fontWeight(day['isToday'] === true ? FontWeight.Bold : FontWeight.Normal)
                . fontColor(day['isToday'] === true ? '#4A3B8C' : '#000000')
              Text(day['dateText'] as string)
                . fontSize(12)
                . fontColor('#444444')
            }
            . layoutWeight(1)
            . padding(2)
            . borderRadius(6)
            . backgroundColor(day['isToday'] === true ? '#E8DEF8' : '#00000000')
          }, (day: Record<string, Object>, idx: number) => `${this.rev}-h-${idx}`)
        }
        . width('100%')
        Row({ space: 1 }) {
          this.TimeAxis()
          ForEach(this.days, (day: Record<string, Object>) => {
            this.DayColumn(day)
          }, (day: Record<string, Object>, idx: number) => `${this.rev}-${idx}-${day['weekday']}`)
        }
        . width('100%')
        . layoutWeight(1)
        . alignItems(VerticalAlign.Top)
      }

      // 底部今日待办条（最多 4 条）
      if (this.todoCount > 0) {
        Column({ space: 2 }) {
          Text(`今日待办 · ${this.todayText}`)
            . fontSize(16)
            . fontWeight(FontWeight.Bold)
            . fontColor('#4A3B8C')
          ForEach(this.todos.slice(0, 4), (todo: Record<string, Object>) => {
            Row({ space: 6 }) {
              Circle({ width: 7, height: 7 })
                . fill(todo['color'] as string)
              Text(todo['text'] as string)
                . fontSize(15)
                . fontColor(todo['done'] === true ? '#9E9E9E' : '#000000')
                . maxLines(1)
                . textOverflow({ overflow: TextOverflow.Ellipsis })
                . layoutWeight(1)
            }
            . width('100%')
          }, (todo: Record<string, Object>, idx: number) => `${this.rev}-d-${idx}-${todo['title']}`)
          if (this.todoCount > 4) {
            Text(`…还有 ${this.todoCount - 4} 条`)
              . fontSize(14)
              . fontColor('#9E9E9E')
          }
        }
        . width('100%')
        . padding(8)
        . borderRadius(14)
        . backgroundColor('#F5F1FA')
        . alignItems(HorizontalAlign.Start)
      }
    }
    . width('100%')
    . height('100%')
    . padding(12)
    . backgroundColor('#FFFFFF')
    . borderRadius(24)
    . onClick(() => CardOps.openApp(this))
  }

  /** 左侧时间轴：8 个偶数小时标签，每项占 2 小时高（12.5%），标签贴格线 */
  @Builder
  TimeAxis() {
    Column() {
      ForEach(this.evenLabels(), (item: string) => {
        Column() {
          Text(item)
            . fontSize(11)
            . lineHeight(12)
            . fontColor('#888888')
        }
        . justifyContent(FlexAlign.Start)
        . height('12.5%')
        . width(24)
      }, (item: string) => `${this.rev}-${item}`)
    }
    . width(24)
    . height('100%')
    . alignItems(HorizontalAlign.Start)
  }

  private evenLabels(): string[] {
    const out: string[] = [];
    for (let h = 8; h <= 22; h += 2) {
      out.push(`${h}`.padStart(2, '0'));
    }
    return out;
  }

  /** 某日任务（网格内定位用，过滤出起止分钟有效且在网格范围内的） */
  private gridTasks(day: Record<string, Object>): Record<string, Object>[] {
    const all = day['tasks'] as Record<string, Object>[];
    const out: Record<string, Object>[] = [];
    for (const t of all) {
      const s = t['startMin'] as number;
      const e = t['endMin'] as number;
      if (s === undefined || e === undefined || e <= this.gridStartMin || s >= this.gridEndMin) {
        continue;
      }
      out.push(t);
    }
    return out;
  }

  private min(v: number): number {
    return Math.max(this.gridStartMin, Math.min(this.gridEndMin, v));
  }

  private yPct(t: Record<string, Object>): string {
    return `${(this.min(t['startMin'] as number) - this.gridStartMin) / this.totalMin * 100}%`;
  }

  private hPct(t: Record<string, Object>): string {
    const s = this.min(t['startMin'] as number);
    const e = this.min(t['endMin'] as number);
    return `${Math.max(1.5, (e - s) / this.totalMin * 100)}%`;
  }

  /** 与该块时间重叠的任务数与自身序号（横向并列分列，同 App 算法） */
  private laneOf(t: Record<string, Object>, day: Record<string, Object>): string[] {
    const tasks = this.gridTasks(day);
    const s0 = this.min(t['startMin'] as number);
    const e0 = this.min(t['endMin'] as number);
    const overlap: Record<string, Object>[] = [];
    for (const o of tasks) {
      const os = this.min(o['startMin'] as number);
      const oe = this.min(o['endMin'] as number);
      if (os < e0 && s0 < oe) {
        overlap.push(o);
      }
    }
    let idx = 0;
    for (let i = 0; i < overlap.length; i++) {
      if (overlap[i]['title'] === t['title'] && overlap[i]['startMin'] === t['startMin']
        && overlap[i]['time'] === t['time']) {
        idx = i;
        break;
      }
    }
    return [`${(idx * 100) / overlap.length}%`, `${100 / overlap.length}%`];
  }

  /** 逐小时（格线用，16 行） */
  private hourLabels(): string[] {
    const out: string[] = [];
    for (let h = 8; h <= 23; h++) {
      out.push(`${h}`);
    }
    return out;
  }

  @Builder
  DayColumn(day: Record<string, Object>) {
    Stack({ alignContent: Alignment.TopStart }) {
      // 背景格线（16 行，逐小时）
      Column() {
        ForEach(this.hourLabels(), (label: string, idx: number) => {
          Row()
            . width('100%')
            . height(`${100 / this.totalMin * 60}%`)
            . border({ width: { bottom: 0.5 }, color: '#33999999' })
        }, (label: string, idx: number) => `${this.rev}-${idx}`)
      }
      . width('100%')
      . height('100%')

      // 课程块：按时刻定位，重叠并列
      ForEach(this.gridTasks(day), (t: Record<string, Object>) => {
        Column() {
          Text(t['title'] as string)
            . fontSize(11)
            . lineHeight(12)
            . maxLines(2)
            . fontColor(t['done'] === true ? '#9E9E9E' : '#000000')
            . textOverflow({ overflow: TextOverflow.Ellipsis })
          if ((t['location'] as string).length > 0) {
            Text(t['location'] as string)
              . fontSize(10)
              . lineHeight(11)
              . maxLines(1)
              . fontColor('#444444')
              . textOverflow({ overflow: TextOverflow.Ellipsis })
          }
        }
        . position({ x: this.laneOf(t, day)[0], y: this.yPct(t) })
        . width(this.laneOf(t, day)[1])
        . height(this.hPct(t))
        . padding({ top: 2, bottom: 1, left: 2, right: 2 })
        . clip(true)
        . borderRadius(6)
        . backgroundColor(t['color'] as string)
        . alignItems(HorizontalAlign.Start)
      }, (t: Record<string, Object>, idx: number) => `${this.rev}-t-${idx}-${t['title']}-${t['time']}`)
    }
    . layoutWeight(1)
    . width('100%')
    . height('100%')
    . clip(true)
  }
}
