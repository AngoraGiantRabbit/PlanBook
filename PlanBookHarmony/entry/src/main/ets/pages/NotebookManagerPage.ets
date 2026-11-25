import { AppColors, ColorUtil } from '../common/Theme';
import { Notebook, PeriodConfig } from '../model/Models';
import { ColorPickerSheet } from '../components/ColorPickerSheet';
import { PeriodEditor } from '../components/PeriodEditor';
import { NotebookManagerVM } from '../viewmodel/ViewModels';
import { IcsExporter } from '../data/IcsExporter';
import { Repository } from '../data/Repository';
import { DatePickerOverlay, TimePickerOverlay, parseTime } from '../components/Pickers';
import { DialogShell, TextButton } from '../components/Ui';
import { router } from '@kit.ArkUI';
import { common } from '@kit.AbilityKit';
import { picker } from '@kit.CoreFileKit';
import fs from '@ohos.file.fs';
import { BusinessError } from '@kit.BasicServicesKit';
import util from '@ohos.util';

/**
 * 子计划本管理页（对应 Android NotebookManagerScreen，设置页入口，ADR-0004）：
 * 列表 + 新建 + 重命名 + 删除 + 活动切换 + 显示开关 + 颜色（预设 + HSV 自定义）+ ICS 导入。
 */
@Entry
@ComponentV2
struct NotebookManagerPage {
  @Local vm: NotebookManagerVM = new NotebookManagerVM();
  @Local showCreate: boolean = false;
  @Local createName: string = '';
  @Local renaming: Notebook | null = null;
  @Local renameText: string = '';
  @Local deleting: Notebook | null = null;
  @Local coloring: Notebook | null = null;
  /** 待导入的 ICS（选完文件后先选导入方式） */
  @Local showImportKind: boolean = false;
  @Local pendingImportName: string = '';
  @Local pendingImportContent: string = '';
  /** 课表节次时间编辑（「改时间」按钮） */
  @Local editingPeriodsOf: Notebook | null = null;
  @Local periodsConfig: PeriodConfig = PeriodConfig.default();
  /** 节次小块编辑链：先开始后结束（选择器在页面根部渲染） */
  @Local editingPeriodN: number = -1;
  @Local pickPeriodStart: boolean = false;
  @Local pickPeriodEnd: boolean = false;


  aboutToAppear(): void {
    this.vm.attach();
  }

  aboutToDisappear(): void {
    this.vm.detach();
  }

  build() {
    Stack() {
      Column() {
        Row({ space: 4 }) {
          SymbolGlyph($r('sys.symbol.chevron_left'))
            . fontSize(26)
            . fontColor([AppColors.onSurface])
            . onClick(() => router.back())
          Text('子计划本管理')
            . fontSize(19)
            . fontWeight(FontWeight.Bold)
            . fontColor(AppColors.onSurface)
        }
        . width('100%')
        . padding({ left: 8, right: 16, top: 8, bottom: 4 })

        Text('活动子计划本是首页新建任务/课程的落点；显示开关只控制是否在计划本页聚合显示。课表设为活动后，点击课程即可编辑。')
          . fontSize(12)
          . fontColor(AppColors.onSurfaceVariant)
          . width('100%')
          . padding({ left: 16, right: 16, top: 4, bottom: 8 })

        Scroll() {
          Column() {
            Text(`子计划本 (${this.normalSubs().length})`)
              . fontSize(15)
              . fontWeight(FontWeight.Bold)
              . fontColor(AppColors.onSurface)
              . width('100%')
              . padding({ top: 8, bottom: 4 })
            ForEach(this.normalSubs(), (sub: Notebook) => {
              this.SubNotebookRow(sub)
            }, (sub: Notebook) => `${sub.id}-${sub.name}-${sub.isVisible}-${sub.isActive}-${this.vm.activeSubId}`)

            Text(`课程表 (${this.timetableSubs().length})`)
              . fontSize(15)
              . fontWeight(FontWeight.Bold)
              . fontColor(AppColors.onSurface)
              . width('100%')
              . padding({ top: 16, bottom: 4 })
            Text('课表（新建或 ICS 导入）：课程不是任务，不进待办列表；设为活动后首页可点课编辑、加号加课；每行「现有课程」管理课程颜色。')
              . fontSize(11)
              . fontColor(AppColors.onSurfaceVariant)
              . width('100%')
              . padding({ bottom: 4 })
            ForEach(this.timetableSubs(), (sub: Notebook) => {
              this.SubNotebookRow(sub)
            }, (sub: Notebook) => `${sub.id}-${sub.name}-${sub.isVisible}-${sub.isActive}-${this.vm.activeSubId}`)
          }
          . width('100%')
          . padding({ left: 16, right: 16 })
        }
        . scrollBar(BarState.Off)
        . layoutWeight(1)
        . align(Alignment.TopStart)


        Row({ space: 8 }) {
          Button('新建子本')
            . layoutWeight(1)
            . fontSize(13)
            . backgroundColor(AppColors.primary)
            . onClick(() => {
              this.createName = this.defaultSubName();
              this.showCreate = true;
            })
          Button('新建课表')
            . layoutWeight(1)
            . fontSize(13)
            . backgroundColor(AppColors.primary)
            . onClick(() => router.pushUrl({ url: 'pages/CourseBuilderPage' }))
          Button('导入 ICS')
            . layoutWeight(1)
            . fontSize(13)
            . backgroundColor(AppColors.surface)
            . fontColor(AppColors.primary)
            . border({ width: 1, color: AppColors.outline })
            . onClick(() => this.pickIcs())
        }
        . width('100%')
        . padding(16)
      }
      . width('100%')
      . height('100%')

      this.Dialogs()
    }
    . width('100%')
    . height('100%')
    . backgroundColor(AppColors.surface)
  }

  /** 默认名：子计划本 N（N 取未被占用的最小序号，避免与现存重名） */
  private defaultSubName(): string {
    const names = new Set<string>();
    for (const s of this.vm.subs) {
      names.add(s.name);
    }
    let n = this.vm.subs.length + 1;
    while (names.has(`子计划本 ${n}`)) {
      n++;
    }
    return `子计划本 ${n}`;
  }

  private normalSubs(): Notebook[] {
    return this.vm.subs.filter((s: Notebook): boolean => !s.isTimetable);
  }

  private timetableSubs(): Notebook[] {
    return this.vm.subs.filter((s: Notebook): boolean => s.isTimetable);
  }

  /** 导出该子计划本为 .ics（用户选保存位置） */
  private exportIcs(sub: Notebook): void {
    Repository.getTasksOfSubNotebook(sub.id).then((tasks) => {
      const content = IcsExporter.buildContent(tasks, sub.name);
      const ctx = this.getUIContext().getHostContext() as common.UIAbilityContext;
      IcsExporter.saveToFile(ctx, `${sub.name}.ics`, content).then((ok: boolean) => {
        this.vm.importMessage = ok ? `已导出「${sub.name}」${tasks.length} 条日程` : '导出已取消';
      }).catch((e: Error) => {
        this.vm.importMessage = `导出失败：${e.message ?? e}`;
      });
    });
  }

  /** #11：DocumentViewPicker 选 .ics → 读文本 → 选导入方式（课表 / 子计划本）→ 建导入子本 */
  private pickIcs(): void {
    try {
      const docPicker = new picker.DocumentViewPicker();
      docPicker.select({
        fileSuffixFilters: ['.ics'],
        maxSelectNumber: 1
      }, (err: BusinessError, result: Array<string>) => {
        if (err || result.length === 0) {
          return;
        }
        if (result.length === 0 || result[0].length === 0) {
          return;
        }
        const uri = result[0];
        const name = this.fileNameOf(uri);
        const content = this.readTextFromUri(uri);
        this.pendingImportName = name;
        this.pendingImportContent = content ?? '';
        this.showImportKind = true;
      });
    } catch (e) {
      console.error(`open picker failed: ${e}`);
    }
  }

  private runImport(asTimetable: boolean): void {
    this.showImportKind = false;
    this.vm.importIcs(this.pendingImportName, this.pendingImportContent, asTimetable);
    this.pendingImportName = '';
    this.pendingImportContent = '';
  }

  /** file:// URI → 文本；失败返回 null */
  private readTextFromUri(uri: string): string | null {
    try {
      const file = fs.openSync(uri, fs.OpenMode.READ_ONLY);
      try {
        const stat = fs.statSync(file.fd);
        const buf = new ArrayBuffer(stat.size);
        fs.readSync(file.fd, buf);
        const decoder = util.TextDecoder.create('utf-8');
        return decoder.decodeToString(new Uint8Array(buf));
      } finally {
        fs.closeSync(file);
      }
    } catch (e) {
      console.error(`read ics failed: ${e}`);
      return null;
    }
  }

  /** uri → 源文件名（去 .ics 后缀；取不到回退「导入课程表」） */
  private fileNameOf(uri: string): string {
    const seg = decodeURIComponent(uri).split('/');
    const file = seg[seg.length - 1];
    if (file.length === 0) {
      return '导入课程表';
    }
    return file.endsWith('.ics') ? file.substring(0, file.length - 4) : file;
  }

  @Builder
  SubNotebookRow(sub: Notebook) {
    Column({ space: 4 }) {
      Row({ space: 8 }) {
        Circle({ width: 16, height: 16 })
          . fill(ColorUtil.safe(sub.color))
        Text(sub.name)
          . fontSize(15)
          . fontWeight(sub.id === this.vm.activeSubId ? FontWeight.Bold : FontWeight.Normal)
          . fontColor(AppColors.onSurface)
          . layoutWeight(1)
          . maxLines(1)
        if (sub.isImported) {
          // 导入课表标识（同源重导整本替换）
          Text('导入')
            . fontSize(10)
            . fontColor(AppColors.onTertiaryContainer)
            . padding({ left: 4, right: 4, top: 2, bottom: 2 })
            . borderRadius(4)
            . backgroundColor(AppColors.tertiaryContainer)
        }
        if (sub.id === this.vm.activeSubId) {
          Text('活动')
            . fontSize(10)
            . fontColor(AppColors.onPrimaryContainer)
            . padding({ left: 4, right: 4, top: 2, bottom: 2 })
            . borderRadius(4)
            . backgroundColor(AppColors.primaryContainer)
        }
      }
      . width('100%')

      Text(sub.id === this.vm.activeSubId ? (sub.isTimetable ? '首页点课编辑、加号加课的落点' : '首页新建任务的落点') : '点击设为活动')
        . fontSize(12)
        . fontColor(AppColors.onSurfaceVariant)
        . width('100%')

      Row({ space: 2 }) {
        SymbolGlyph($r('sys.symbol.share'))
          . fontSize(20)
          . fontColor([AppColors.onSurfaceVariant])
          . padding(4)
          . onClick(() => this.exportIcs(sub))
        if (sub.isTimetable) {
          // 课表节次时间（改时间）：节数 + 每节起止
          SymbolGlyph($r('sys.symbol.clock'))
            . fontSize(20)
            . fontColor([AppColors.onSurfaceVariant])
            . padding(4)
            . onClick(() => this.openPeriods(sub))
        }
        if (sub.timetableJson !== null) {
          SymbolGlyph($r('sys.symbol.calendar'))
            . fontSize(20)
            . fontColor([AppColors.primary])
            . padding(4)
            . onClick(() => {
              router.pushUrl({ url: 'pages/CourseBuilderPage', params: { subId: sub.id } });
            })
        }
        if (sub.isTimetable) {
          // 现有课程：色块管理该课表的课程颜色，可新建课程
          SymbolGlyph($r('sys.symbol.list_bullet'))
            . fontSize(20)
            . fontColor([AppColors.onSurfaceVariant])
            . padding(4)
            . onClick(() => {
              router.pushUrl({ url: 'pages/CourseColorsPage', params: { subId: sub.id } });
            })
        }
        SymbolGlyph($r('sys.symbol.paintpalette'))
          . fontSize(20)
          . fontColor([AppColors.onSurfaceVariant])
          . padding(4)
          . onClick(() => {
            this.coloring = sub;
          })
        SymbolGlyph($r('sys.symbol.square_and_pencil'))
          . fontSize(20)
          . fontColor([AppColors.onSurfaceVariant])
          . padding(4)
          . onClick(() => {
            this.renaming = sub;
            this.renameText = sub.name;
          })
        SymbolGlyph($r('sys.symbol.trash'))
          . fontSize(20)
          . fontColor([this.vm.canDelete(sub) ? AppColors.onSurfaceVariant : AppColors.outline])
          . padding(4)
          . onClick(() => {
            if (this.vm.canDelete(sub)) {
              this.deleting = sub;
            }
          })
        Blank()
        Toggle({ type: ToggleType.Switch, isOn: sub.isVisible })
          . selectedColor(AppColors.primary)
          . onChange((on: boolean) => this.vm.setSubNotebookVisible(sub.id, on))
      }
      . width('100%')
    }
    . width('100%')
    . padding({ top: 8, bottom: 8 })
    . border({ width: { bottom: 1 }, color: AppColors.surfaceVariant })
    . onClick(() => this.vm.setActiveSubNotebook(sub.id))
  }

  /** 「改时间」：打开节次编辑弹层（载入该课表已存配置） */
  private openPeriods(sub: Notebook): void {
    this.editingPeriodsOf = sub;
    Repository.getTimetablePeriods(sub.id).then((p: PeriodConfig) => {
      this.periodsConfig = p;
    });
  }

  private savePeriods(): void {
    const sub = this.editingPeriodsOf;
    if (sub !== null) {
      Repository.setTimetablePeriods(sub.id, this.periodsConfig);
    }
    this.editingPeriodsOf = null;
  }

  @Builder
  Dialogs() {
    // 新建对话框
    if (this.showCreate) {
      DialogShell({
        onDismiss: () => this.showCreate = false
      }) {
        Column({ space: 10 }) {
          Text('新建子计划本')
            . fontSize(17)
            . fontWeight(FontWeight.Bold)
          TextInput({ text: this.createName })
            . onChange((v: string) => this.createName = v)
          Row({ space: 4 }) {
            TextButton({ label: '取消', onTap: () => this.showCreate = false })
            Blank()
            TextButton({
              label: '保存',
              isEnabled: this.createName.trim().length > 0,
              onTap: () => {
                this.vm.createSubNotebook(this.createName.trim());
                this.showCreate = false;
              }
            })
          }
          . width('100%')
        }
        . width('100%')
      }
    }

    // 重命名对话框
    if (this.renaming !== null) {
      DialogShell({
        onDismiss: () => this.renaming = null
      }) {
        Column({ space: 10 }) {
          Text('重命名子计划本')
            . fontSize(17)
            . fontWeight(FontWeight.Bold)
          TextInput({ text: this.renameText })
            . onChange((v: string) => this.renameText = v)
          Row({ space: 4 }) {
            TextButton({ label: '取消', onTap: () => this.renaming = null })
            Blank()
            TextButton({
              label: '保存',
              isEnabled: this.renameText.trim().length > 0,
              onTap: () => {
                if (this.renaming !== null) {
                  this.vm.renameSubNotebook(this.renaming, this.renameText.trim());
                }
                this.renaming = null;
              }
            })
          }
          . width('100%')
        }
        . width('100%')
      }
    }

    // 删除确认对话框
    if (this.deleting !== null) {
      DialogShell({
        onDismiss: () => this.deleting = null
      }) {
        Column({ space: 10 }) {
          Text('删除子计划本')
            . fontSize(17)
            . fontWeight(FontWeight.Bold)
          Text(`确定删除「${this.deleting !== null ? this.deleting.name : ''}」吗？该子计划本的所有任务将一并删除。`)
            . fontSize(13)
            . fontColor(AppColors.onSurfaceVariant)
          Row({ space: 4 }) {
            TextButton({ label: '取消', onTap: () => this.deleting = null })
            Blank()
            TextButton({
              label: '删除',
              color: AppColors.error,
              onTap: () => {
                if (this.deleting !== null) {
                  this.vm.deleteSubNotebook(this.deleting);
                }
                this.deleting = null;
              }
            })
          }
          . width('100%')
        }
        . width('100%')
      }
    }

    // 颜色选择（复用 ColorPickerSheet：预设 + HSV 自定义）
    if (this.coloring !== null) {
      ColorPickerSheet({
        current: this.coloring.color,
        onPick: (c: string) => {
          const nb = this.coloring;
          if (nb !== null) {
            this.vm.setSubNotebookColor(nb, c);
          }
          this.coloring = null;
        },
        onDismiss: () => this.coloring = null
      })
    }

    // 课表节次时间编辑（「改时间」）
    if (this.editingPeriodsOf !== null) {
      DialogShell({
        onDismiss: () => this.editingPeriodsOf = null
      }) {
        Column({ space: 10 }) {
          Text(`节次时间 · ${this.editingPeriodsOf !== null ? this.editingPeriodsOf.name : ''}`)
            . fontSize(17)
            . fontWeight(FontWeight.Bold)
          Text('修改影响此后该课表新建课程/编辑课表时展开的时间，已有节次不自动改。')
            . fontSize(11)
            . fontColor(AppColors.onSurfaceVariant)
          PeriodEditor({
            config: this.periodsConfig,
            compact: true,
            onEditPeriod: (n: number) => {
              this.editingPeriodN = n;
              this.pickPeriodStart = true;
            }
          })
          Row({ space: 4 }) {
            TextButton({ label: '取消', onTap: () => this.editingPeriodsOf = null })
            Blank()
            TextButton({ label: '保存', onTap: () => this.savePeriods() })
          }
          . width('100%')
        }
        . width('100%')
      }
    }

    // 导入方式选择：作为课表 / 作为子计划本
    if (this.showImportKind) {
      DialogShell({
        onDismiss: () => this.showImportKind = false
      }) {
        Column({ space: 10 }) {
          Text('导入方式')
            . fontSize(17)
            . fontWeight(FontWeight.Bold)
          Text(`「${this.pendingImportName}」导入为：`)
            . fontSize(13)
            . fontColor(AppColors.onSurfaceVariant)
          TextButton({
            label: '作为课表（点课编辑 · 课程颜色）',
            onTap: () => this.runImport(true)
          })
          TextButton({
            label: '作为子计划本（课程作为任务，进待办）',
            onTap: () => this.runImport(false)
          })
          TextButton({ label: '取消', onTap: () => this.showImportKind = false })
        }
        . width('100%')
        . alignItems(HorizontalAlign.Start)
      }
    }

    // 节次起止选择器（根部渲染，盖在「改时间」弹层之上）：先开始后结束
    if (this.pickPeriodStart && this.editingPeriodN > 0) {
      TimePickerOverlay({
        initialHour: this.periodInitHour(true),
        initialMinute: this.periodInitMinute(true),
        onConfirm: (t: string) => {
          this.applyPeriodTime(true, t);
          this.pickPeriodStart = false;
          this.pickPeriodEnd = true;
        },
        onDismiss: () => this.pickPeriodStart = false
      })
    }
    if (this.pickPeriodEnd && this.editingPeriodN > 0) {
      TimePickerOverlay({
        initialHour: this.periodInitHour(false),
        initialMinute: this.periodInitMinute(false),
        onConfirm: (t: string) => {
          this.applyPeriodTime(false, t);
          this.pickPeriodEnd = false;
        },
        onDismiss: () => this.pickPeriodEnd = false
      })
    }


    // #11：导入结果提示
    if (this.vm.importMessage !== null) {
      DialogShell({
        onDismiss: () => this.vm.clearImportMessage()
      }) {
        Column({ space: 10 }) {
          Text('导入 ICS')
            . fontSize(17)
            . fontWeight(FontWeight.Bold)
          Text(this.vm.importMessage ?? '')
            . fontSize(13)
            . fontColor(AppColors.onSurfaceVariant)
          TextButton({ label: '知道了', onTap: () => this.vm.clearImportMessage() })
        }
        . width('100%')
        . alignItems(HorizontalAlign.Start)
      }
    }
  }

  private applyPeriodTime(isStart: boolean, t: string): void {
    const n = this.editingPeriodN;
    const cur = this.periodsConfig.time(n);
    const next: string[] = isStart ? [t, cur[1]] : [cur[0], t];
    this.periodsConfig.times[n - 1] = next;
  }

  private periodInitHour(isStart: boolean): number {
    const p = parseTime(this.periodsConfig.time(this.editingPeriodN)[isStart ? 0 : 1]);
    return p === null ? 8 : p[0];
  }

  private periodInitMinute(isStart: boolean): number {
    const p = parseTime(this.periodsConfig.time(this.editingPeriodN)[isStart ? 0 : 1]);
    return p === null ? 0 : p[1];
  }

}
