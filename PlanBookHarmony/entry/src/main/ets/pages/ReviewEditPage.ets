import { AppColors } from '../common/Theme';
import { DateUtils } from '../common/DateUtils';
import { Task, TaskType } from '../model/Models';
import { ReviewEditVM } from '../viewmodel/ViewModels';
import { TaskEditSheet } from '../components/TaskEditSheet';
import { DialogShell, TextButton } from '../components/Ui';
import { router } from '@kit.ArkUI';

/**
 * 复盘编辑页（对应 Android ReviewEditScreen，独立路由独立 VM）：
 * 顶部日期 + 当日已完成 + 复盘文本（停止输入 1 秒自动保存）+ 待细化长期任务（拆分）。
 */
@Entry
@ComponentV2
struct ReviewEditPage {
  @Local vm: ReviewEditVM = new ReviewEditVM();
  /** 路由参数：复盘日期 yyyy-MM-dd */
  date: string = DateUtils.today();
  @Local text: string = '';
  private saveTimer: number = -1;
  /** 拆分中的长期任务 + 拆分类型弹层状态 */
  @Local splitTask: Task | null = null;
  @Local splitTypePick: boolean = false;
  @Local splitType: TaskType | null = null;

  aboutToAppear(): void {
    const params = router.getParams() as Record<string, Object>;
    if (params !== undefined && params !== null && params['date'] !== undefined) {
      this.date = params['date'] as string;
    }
    this.vm.attach();
    this.vm.openReviewEdit(this.date);
  }

  aboutToDisappear(): void {
    this.vm.detach();
    if (this.saveTimer >= 0) {
      clearTimeout(this.saveTimer);
    }
  }

  private onTextChanged(v: string): void {
    this.text = v;
    // 停止输入 1 秒后自动保存（PRD 4.4.4）
    if (this.saveTimer >= 0) {
      clearTimeout(this.saveTimer);
    }
    this.saveTimer = setTimeout(() => {
      this.saveTimer = -1;
      this.vm.saveReviewContent(this.text);
    }, 1000);
  }

  private splitTitle(): string {
    return this.splitTask === null ? '' : this.splitTask.title;
  }

  build() {
    Stack() {
      Column() {
        // 顶栏：返回 + 日期 + 保存
        Row({ space: 4 }) {
          SymbolGlyph($r('sys.symbol.chevron_left'))
            . fontSize(26)
            . fontColor([AppColors.onSurface])
            . onClick(() => router.back())
          Text(DateUtils.cnFull(this.date))
            . fontSize(17)
            . fontWeight(FontWeight.Bold)
            . fontColor(AppColors.onSurface)
            . layoutWeight(1)
            . maxLines(1)
          TextButton({
            label: '保存',
            onTap: () => {
              this.vm.saveReviewContent(this.text);
              router.back();
            }
          })
        }
        . width('100%')
        . padding({ left: 8, right: 12, top: 8, bottom: 4 })

        Scroll() {
          Column({ space: 10 }) {
            // 当日已完成任务区（PRD 4.4.4）
            Text('今日完成')
              . fontSize(14)
              . fontWeight(FontWeight.Bold)
              . fontColor(AppColors.onSurface)
              . width('100%')
            if (this.vm.completedTasks.length === 0) {
              Text('暂无已完成任务')
                . fontSize(12)
                . fontColor(AppColors.onSurfaceVariant)
            } else {
              ForEach(this.vm.completedTasks, (task: Task) => {
                Text(`✓ ${task.title}`)
                  . fontSize(14)
                  . fontColor(AppColors.onSurface)
                  . width('100%')
                  . padding({ left: 8, right: 8, top: 5, bottom: 5 })
                  . borderRadius(8)
                  . backgroundColor(AppColors.surfaceVariant)
              }, (task: Task) => `${task.id}-${task.isCompleted}-${task.title}`)
            }

            // 复盘文本
            Text('复盘')
              . fontSize(14)
              . fontWeight(FontWeight.Bold)
              . fontColor(AppColors.onSurface)
              . width('100%')
            TextArea({ placeholder: '写下今天的复盘…', text: this.text })
              . onChange((v: string) => this.onTextChanged(v))
              . constraintSize({ minHeight: 160 })
              . width('100%')

            // 待细化长期任务区（PRD 4.4.4）
            Text('待细化长期任务')
              . fontSize(14)
              . fontWeight(FontWeight.Bold)
              . fontColor(AppColors.onSurface)
              . width('100%')
            if (this.vm.longTermTasks.length === 0) {
              Text('暂无临近 DDL 的长期任务')
                . fontSize(12)
                . fontColor(AppColors.onSurfaceVariant)
            } else {
              ForEach(this.vm.longTermTasks, (task: Task) => {
                Row({ space: 8 }) {
                  Column({ space: 2 }) {
                    Text(task.title)
                      . fontSize(14)
                      . fontWeight(FontWeight.Medium)
                      . fontColor(AppColors.onSurface)
                      . maxLines(1)
                    Text(`DDL: ${task.endDate}`)
                      . fontSize(11)
                      . fontColor(AppColors.onSurfaceVariant)
                  }
                  . alignItems(HorizontalAlign.Start)
                  . layoutWeight(1)
                  SymbolGlyph($r('sys.symbol.plus'))
                    . fontSize(20)
                    . fontColor([AppColors.onTertiaryContainer])
                }
                . width('100%')
                . padding(8)
                . borderRadius(8)
                . backgroundColor(AppColors.tertiaryContainer)
                . onClick(() => {
                  this.splitTask = task;
                  this.splitTypePick = true;
                })
              }, (task: Task) => `${task.id}-${task.title}-${task.endDate}`)
            }
          }
          . width('100%')
          . padding(16)
        }
        . scrollBar(BarState.Off)
        . layoutWeight(1)
        . align(Alignment.TopStart)
      }
      . width('100%')
      . height('100%')

      // 拆分类型选择
      if (this.splitTypePick && this.splitTask !== null) {
        DialogShell({ onDismiss: () => this.splitTypePick = false }) {
          Column({ space: 6 }) {
            Text(`拆分「${this.splitTitle()}」`)
              . fontSize(17)
              . fontWeight(FontWeight.Bold)
            Text('拆分为哪种任务？可设置名称和时间，原长期任务会保留。')
              . fontSize(13)
              . fontColor(AppColors.onSurfaceVariant)
            TextButton({
              label: '灵活任务',
              onTap: () => {
                this.splitType = TaskType.FLEX;
                this.splitTypePick = false;
              }
            })
            TextButton({
              label: '临时任务',
              onTap: () => {
                this.splitType = TaskType.ONE_OFF;
                this.splitTypePick = false;
              }
            })
            TextButton({
              label: '每日任务',
              onTap: () => {
                this.splitType = TaskType.DAILY;
                this.splitTypePick = false;
              }
            })
            TextButton({ label: '取消', onTap: () => this.splitTypePick = false })
          }
          . width('100%')
        }
      }

      // 选好类型后，打开完整编辑表单（预填标题，原长期保留）
      if (this.splitType !== null && this.splitTask !== null) {
        TaskEditSheet({
          notebookId: this.vm.activeSubId,
          existing: this.splitPrefill(),
          onDismiss: () => {
            this.splitType = null;
          },
          onConfirm: (subTask: Task) => {
            this.vm.splitLongTermTask(subTask);
            this.splitType = null;
          }
        })
      }
    }
    . width('100%')
    . height('100%')
    . backgroundColor(AppColors.surface)
  }

  /** 拆分预填：标题沿用长期任务、类型为所选（对应 task.copy(id=0, title, type)） */
  private splitPrefill(): Task | null {
    if (this.splitTask === null) {
      return null;
    }
    const t = Task.copy(this.splitTask);
    t.id = 0;
    t.title = this.splitTask.title;
    t.type = this.splitType ?? TaskType.FLEX;
    t.isCompleted = false;
    t.completedAt = null;
    t.isAutoReview = false;
    t.reviewType = null;
    return t;
  }
}
