import { AppColors, ColorUtil } from '../common/Theme';
import { Notebook, SubNotebookPalette } from '../model/Models';
import { NotebookManagerVM } from '../viewmodel/ViewModels';
import { DialogShell, TextButton } from '../components/Ui';
import { router } from '@kit.ArkUI';
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
  @Local showCustomColor: boolean = false;
  @Local hue: number = 0;
  @Local sat: number = 0;
  @Local bri: number = 0;

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

        Text('活动子计划本是首页新建任务的落点；显示开关只控制是否在计划本页聚合显示。')
          . fontSize(12)
          . fontColor(AppColors.onSurfaceVariant)
          . width('100%')
          . padding({ left: 16, right: 16, top: 4, bottom: 8 })

        Scroll() {
          Column() {
            ForEach(this.vm.subs, (sub: Notebook) => {
              this.SubNotebookRow(sub)
            }, (sub: Notebook) => `${sub.id}-${sub.name}-${sub.color}-${sub.isVisible}-${sub.isActive}-${this.vm.activeSubId}`)
          }
          . width('100%')
          . padding({ left: 16, right: 16 })
        }
        . scrollBar(BarState.Off)
        . layoutWeight(1)

        Row({ space: 12 }) {
          Button('新建子计划本')
            . layoutWeight(1)
            . backgroundColor(AppColors.primary)
            . onClick(() => {
              this.createName = this.defaultSubName();
              this.showCreate = true;
            })
          Button('导入 ICS')
            . layoutWeight(1)
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

  /** #11：DocumentViewPicker 选 .ics → 读文本（fd + UTF-8 解码）→ 建导入子计划本 */
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
        if (content === null) {
          this.vm.importIcs(name, ''); // 空内容 → 走「没有解析到事件」提示
        } else {
          this.vm.importIcs(name, content);
        }
      });
    } catch (e) {
      console.error(`open picker failed: ${e}`);
    }
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
          // #11：导入子计划本标识（只读快照，ADR-0005）
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

      Text(sub.id === this.vm.activeSubId ? '首页新建任务的落点' : '点击设为活动')
        . fontSize(12)
        . fontColor(AppColors.onSurfaceVariant)
        . width('100%')

      Row({ space: 2 }) {
        SymbolGlyph($r('sys.symbol.paintpalette'))
          . fontSize(20)
          . fontColor([AppColors.onSurfaceVariant])
          . padding(4)
          . onClick(() => {
            this.coloring = sub;
            this.showCustomColor = false;
            const hsv = ColorUtil.toHsv(sub.color);
            this.hue = hsv[0];
            this.sat = hsv[1];
            this.bri = hsv[2];
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
          . fontColor([this.vm.subs.length > 1 ? AppColors.onSurfaceVariant : AppColors.outline])
          . padding(4)
          . onClick(() => {
            if (this.vm.subs.length > 1) {
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

    // 颜色选择对话框
    if (this.coloring !== null) {
      this.ColorPickerDialog()
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

  /** 颜色选择：预设调色板色圈（4 个一行）+ 自定义圈（HSV 调色盘任选） */
  @Builder
  ColorPickerDialog() {
    if (this.showCustomColor) {
      this.CustomColorPicker()
    } else {
      DialogShell({
        onDismiss: () => this.coloring = null
      }) {
        Column({ space: 12 }) {
          Text('选择颜色')
            . fontSize(17)
            . fontWeight(FontWeight.Bold)
          Flex({ wrap: FlexWrap.Wrap, justifyContent: FlexAlign.Start }) {
            ForEach(SubNotebookPalette.all(), (hex: string) => {
              Stack() {
                Circle({ width: 40, height: 40 })
                  . fill(hex)
                if (this.coloring !== null && this.coloring.color === hex) {
                  Text('✓')
                    . fontSize(18)
                    . fontColor('#FFFFFF')
                }
              }
              . margin(6)
              . borderRadius(20)
              . border({
                width: this.coloring !== null && this.coloring.color === hex ? 3 : 1,
                color: this.coloring !== null && this.coloring.color === hex ? AppColors.primary : AppColors.outline
              })
              . onClick(() => {
                if (this.coloring !== null) {
                  this.vm.setSubNotebookColor(this.coloring, hex);
                }
                this.coloring = null;
              })
            }, (hex: string) => `${hex}-${this.coloring !== null ? this.coloring.color ?? '' : ''}`)
          }
          . width('100%')

          Row({ space: 8 }) {
            Column()
              . width(40)
              . height(40)
              . borderRadius(20)
              . linearGradient({
                angle: 90,
                colors: [['#E57373', 0], ['#FFB74D', 0.17], ['#FFF176', 0.33], ['#81C784', 0.5],
                  ['#4DD0E1', 0.67], ['#7986CB', 0.83], ['#BA68C8', 1]]
              })
              . border({ width: 1, color: AppColors.outline })
              . onClick(() => this.showCustomColor = true)
            Text('自定义颜色')
              . fontSize(14)
              . fontColor(AppColors.onSurface)
          }
          . width('100%')

          TextButton({ label: '取消', onTap: () => this.coloring = null })
        }
        . width('100%')
      }
    }
  }

  private customHex(): string {
    return ColorUtil.hsv(this.hue, this.sat, this.bri);
  }

  /** 自定义调色盘：HSV 三滑块 + 实时预览，确定后回调 "#RRGGBB" */
  @Builder
  CustomColorPicker() {
    DialogShell({
      onDismiss: () => this.showCustomColor = false
    }) {
      Scroll() {
        Column({ space: 10 }) {
          Text('自定义颜色')
            . fontSize(17)
            . fontWeight(FontWeight.Bold)
          Row({ space: 12 }) {
            Circle({ width: 52, height: 52 })
              . fill(this.customHex())
              . border({ width: 1, color: AppColors.outline })
            Text(this.customHex())
              . fontSize(16)
              . fontWeight(FontWeight.Medium)
          }
          . width('100%')

          Text('色相')
            . fontSize(12)
            . fontColor(AppColors.onSurfaceVariant)
            . width('100%')
          Row()
            . height(10)
            . width('100%')
            . borderRadius(5)
            . linearGradient({
              angle: 90,
              colors: [['#FF0000', 0], ['#FFFF00', 0.17], ['#00FF00', 0.33], ['#00FFFF', 0.5],
                ['#0000FF', 0.67], ['#FF00FF', 0.83], ['#FF0000', 1]]
            })
          Slider({ value: this.hue, min: 0, max: 360 })
            . selectedColor(AppColors.primary)
            . onChange((v: number) => this.hue = v)

          Text('饱和度')
            . fontSize(12)
            . fontColor(AppColors.onSurfaceVariant)
            . width('100%')
          Row()
            . height(10)
            . width('100%')
            . borderRadius(5)
            . linearGradient({
              angle: 90,
              colors: [[ColorUtil.hsv(this.hue, 0, 1), 0], [ColorUtil.hsv(this.hue, 1, 1), 1]]
            })
          Slider({ value: this.sat * 100, min: 0, max: 100 })
            . selectedColor(AppColors.primary)
            . onChange((v: number) => this.sat = v / 100)

          Text('明度')
            . fontSize(12)
            . fontColor(AppColors.onSurfaceVariant)
            . width('100%')
          Row()
            . height(10)
            . width('100%')
            . borderRadius(5)
            . linearGradient({
              angle: 90,
              colors: [['#000000', 0], [ColorUtil.hsv(this.hue, this.sat, 1), 1]]
            })
          Slider({ value: this.bri * 100, min: 0, max: 100 })
            . selectedColor(AppColors.primary)
            . onChange((v: number) => this.bri = v / 100)

          Row({ space: 4 }) {
            TextButton({ label: '取消', onTap: () => this.showCustomColor = false })
            Blank()
            TextButton({
              label: '确定',
              onTap: () => {
                if (this.coloring !== null) {
                  this.vm.setSubNotebookColor(this.coloring, this.customHex());
                }
                this.coloring = null;
                this.showCustomColor = false;
              }
            })
          }
          . width('100%')
        }
        . width('100%')
      }
      . scrollBar(BarState.Off)
    }
  }
}
