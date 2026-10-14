import { formBindingData, formProvider } from '@kit.FormKit';
import { common } from '@kit.AbilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { preferences } from '@kit.ArkData';
import { CardDataBuilder } from './CardData';

const TAG = 'PlanBook.WidgetSync';

/**
 * 手动刷新入口（对应 Android WidgetSync）：App 内点刷新按钮时同步所有已安装卡片。
 * formIds 由 PlanBookFormAbility 在 onAddForm/onUpdateForm 时写入 preferences。
 * 异常全部吞掉，确保卡片侧问题不拖垮 App（PRD：手动刷新机制）。
 */
export class WidgetSync {
  static async updateAll(context: common.Context): Promise<void> {
    try {
      const ids = await WidgetSync.loadFormIds(context);
      for (const id of ids) {
        try {
          const data = await CardDataBuilder.build(context);
          await formProvider.updateForm(id, formBindingData.createFormBindingData(data));
        } catch (e) {
          hilog.warn(0, TAG, 'updateForm %{public}s failed: %{public}s', `${id}`, `${e}`);
        }
      }
    } catch (e) {
      hilog.warn(0, TAG, 'updateAll failed: %{public}s', `${e}`);
    }
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
