import { AppColors, ColorUtil } from '../common/Theme';
import { Notebook } from '../model/Models';
import { Repository } from '../data/Repository';
import { WidgetSync } from '../widget/WidgetSync';
import CardConfigAbility from '../cardconfigability/CardConfigAbility';
import { common } from '@kit.AbilityKit';
import { router } from '@kit.ArkUI';

/**
 * 卡片配置页：勾选「这张卡片」显示哪些子计划本（多开卡片各配各的）。
 * 都不勾选保存 = 恢复默认（显示全部可见子本）。
 */
@Entry
@ComponentV2
struct CardConfigPage {
  @Local subs: Notebook[] = [];
  @Local selected: Set<number> = new Set<number>();
  @Local configured: boolean = false;
  @Local loaded: boolean = false;
  @Local formId: string = '';

  onPageShow(): void {
    // Ability 复用（onNewWant）时页面不会重建：每次显示都重读 formId 并重载勾选
    this.formId = CardConfigAbility.formId;
    this.reload();
  }

  aboutToAppear(): void {
    this.formId = CardConfigAbility.formId;
    this.reload();
  }

  private async reload(): Promise<void> {
    try {
      const master = await Repository.getMasterNotebookOnce();
      if (master === null) {
        this.loaded = true;
        return;
      }
      const subs = await Repository.getSubNotebooksOnce(master.id);
      const configured = this.formId.length > 0 ? await Repository.getCardNotebookIds(this.formId) : [];
      const sel = new Set<number>();
      if (configured.length > 0) {
        this.configured = true;
        for (const id of configured) {
          sel.add(id);
        }
      } else {
        this.configured = false;
        for (const s of subs) {
          if (s.isVisible) {
            sel.add(s.id);
          }
        }
      }
      this.subs = subs;
      this.selected = sel;
      this.loaded = true;
    } catch (e) {
      console.error(`CardConfigPage.reload: ${e}`);
      this.loaded = true;
    }
  }

  private toggle(id: number): void {
    const next = new Set<number>(this.selected);
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    this.selected = next;
  }

  private save(): void {
    const ids: number[] = [];
    for (const s of this.subs) {
      if (this.selected.has(s.id)) {
        ids.push(s.id);
      }
    }
    Repository.setCardNotebookIds(CardConfigAbility.formId, ids).then(() => {
      const ctx = this.getUIContext().getHostContext() as common.UIAbilityContext;
      WidgetSync.refreshOne(ctx, this.formId);
      router.back();
    });
  }

  build() {
    Column({ space: 12 }) {
      Row({ space: 4 }) {
        SymbolGlyph($r('sys.symbol.chevron_left'))
          . fontSize(26)
          . fontColor([AppColors.onSurface])
          . onClick(() => router.back())
        Text('卡片显示内容')
          . fontSize(19)
          . fontWeight(FontWeight.Bold)
          . fontColor(AppColors.onSurface)
          . layoutWeight(1)
      }
      . width('100%')
      . padding({ left: 8, right: 16, top: 8 })

      Text(this.formId.length > 0 ? '这张卡片显示哪些子计划本？都不勾选保存 = 恢复默认（显示全部）。' : '未获取到卡片标识，请从桌面卡片的设置角标进入。')
        . fontSize(12)
        . fontColor(AppColors.onSurfaceVariant)
        . width('100%')
        . padding({ left: 16, right: 16 })

      Scroll() {
        Column() {
          ForEach(this.subs, (sub: Notebook) => {
            Row({ space: 10 }) {
              Checkbox()
                . select(this.selected.has(sub.id))
                . selectedColor(AppColors.primary)
                . onChange((_v: boolean) => this.toggle(sub.id))
              Circle({ width: 14, height: 14 })
                . fill(ColorUtil.safe(sub.color))
              Text(sub.name)
                . fontSize(15)
                . fontColor(AppColors.onSurface)
                . layoutWeight(1)
                . maxLines(1)
              if (sub.isImported) {
                Text('导入')
                  . fontSize(10)
                  . fontColor(AppColors.onTertiaryContainer)
                  . padding({ left: 4, right: 4, top: 2, bottom: 2 })
                  . borderRadius(4)
                  . backgroundColor(AppColors.tertiaryContainer)
              }
            }
            . width('100%')
            . height(48)
            . padding({ left: 16, right: 16 })
          }, (sub: Notebook) => `${sub.id}-${sub.name}-${this.selected.has(sub.id)}`)
        }
        . width('100%')
      }
      . scrollBar(BarState.Off)
      . layoutWeight(1)
      . align(Alignment.TopStart)

      Button(this.configured ? '保存' : '保存（首次配置）')
        . width('90%')
        . backgroundColor(AppColors.primary)
        . enabled(this.loaded && this.formId.length > 0)
        . onClick(() => this.save())
    }
    . width('100%')
    . height('100%')
    . backgroundColor(AppColors.surface)
  }
}
