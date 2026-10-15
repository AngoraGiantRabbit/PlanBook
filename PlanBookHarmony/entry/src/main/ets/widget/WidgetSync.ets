import { formBindingData, formProvider } from '@kit.FormKit';
import { common } from '@kit.AbilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { preferences } from '@kit.ArkData';
import { CardDataBuilder } from './CardData';
import { Repository } from '../data/Repository';

const TAG = 'PlanBook.WidgetSync';

/**
 * 卡片同步（App 侧）：
 * - formId 登记：FormAbility onAddForm/onRemoveForm 经 trackFormId/untrackFormId 维护 preferences；
 * - updateAll / refreshOne：构建 per-form 数据推送（配置 + 翻周状态在 card_prefs 表）；
 * - installAutoSync：App 内任何写操作后自动同步全部卡片（2s 节流，避免连续勾选时频繁推送）。
 *   卡片上的 ↻ 按钮保留作兜底。
 */
export class WidgetSync {
  private static timer: number = -1;

  /** EntryAbility onCreate 调用：写操作 → 节流推送全部卡片 */
  static installAutoSync(context: common.Context): void {
    Repository.subscribe(() => {
      if (WidgetSync.timer >= 0) {
        clearTimeout(WidgetSync.timer);
      }
      WidgetSync.timer = setTimeout(() => {
        WidgetSync.timer = -1;
        WidgetSync.updateAll(context);
      }, 2000);
    });
  }

  static async updateAll(context: common.Context): Promise<void> {
    try {
      const ids = await WidgetSync.loadFormIds(context);
      for (const id of ids) {
        WidgetSync.refreshOne(context, id);
      }
    } catch (e) {
      hilog.warn(0, TAG, 'updateAll failed: %{public}s', `${e}`);
    }
  }

  static async refreshOne(context: common.Context, formId: string): Promise<void> {
    try {
      const data = await CardDataBuilder.build(context, formId);
      await formProvider.updateForm(formId, formBindingData.createFormBindingData(data));
    } catch (e) {
      const msg = `${e}`;
      hilog.warn(0, TAG, 'refreshOne %{public}s failed: %{public}s', formId, msg);
      // 卡片已被删除（ID 不存在）：自动摘除，避免每次同步反复报错
      if (msg.includes('does not exist')) {
        WidgetSync.untrackFormId(context, formId);
      }
    }
  }

  /** 登记新卡片 formId（幂等） */
  static trackFormId(context: common.Context, formId: string): void {
    WidgetSync.loadFormIds(context).then((ids: string[]): void => {
      const next = ids.indexOf(formId) < 0 ? ids.concat([formId]) : ids;
      WidgetSync.saveFormIds(context, next);
    }).catch((e: Error) => hilog.warn(0, TAG, 'track failed: %{public}s', `${e}`));
  }

  /** 注销 formId；返回是否原本存在（供 FormAbility 决定清理 card_prefs） */
  static async untrackFormId(context: common.Context, formId: string): Promise<boolean> {
    const ids = await WidgetSync.loadFormIds(context);
    const next = ids.filter((id: string): boolean => id !== formId);
    await WidgetSync.saveFormIds(context, next);
    return next.length !== ids.length;
  }

  static async loadFormIds(context: common.Context): Promise<string[]> {
    const prefs = await preferences.getPreferences(context, 'planbook_forms');
    const raw = await prefs.get('formIds', '') as string;
    if (raw.length === 0) {
      return [];
    }
    return raw.split(',').filter((s: string): boolean => s.length > 0);
  }

  static async saveFormIds(context: common.Context, ids: string[]): Promise<void> {
    const prefs = await preferences.getPreferences(context, 'planbook_forms');
    await prefs.put('formIds', ids.join(','));
    await prefs.flush();
  }
}
