import { FormExtensionAbility, formBindingData, formInfo, formProvider } from '@kit.FormKit';
import { Want } from '@kit.AbilityKit';
import { CardDataBuilder } from '../widget/CardData';
import { WidgetSync } from '../widget/WidgetSync';
import { Repository } from '../data/Repository';

/**
 * 桌面卡片的 FormExtensionAbility（对应 Android PlanBookWidgetReceiver）。
 * - onAddForm / onUpdateForm：构建周网格数据推给卡片；登记 formId（App 内手动刷新用）。
 * - onRemoveForm：注销 formId。
 * - 卡片为只读版（PRD §5）：刷新由 App 内刷新按钮触发（WidgetSync）。
 */
export default class PlanBookFormAbility extends FormExtensionAbility {
  onAddForm(want: Want): formBindingData.FormBindingData {
    Repository.init(this.context);
    const formId = want.parameters?.[formInfo.FormParam.IDENTITY_KEY] as string;
    if (formId !== undefined && formId.length > 0) {
      WidgetSync.loadFormIds(this.context).then((ids: string[]): void => {
        const next = ids.indexOf(formId) < 0 ? ids.concat([formId]) : ids;
        WidgetSync.saveFormIds(this.context, next);
      });
      this.pushData(formId);
    }
    return formBindingData.createFormBindingData({});
  }

  onUpdateForm(formId: string): void {
    this.pushData(formId);
  }

  onRemoveForm(formId: string): void {
    WidgetSync.loadFormIds(this.context).then((ids: string[]): void => {
      const next = ids.filter((id: string): boolean => id !== formId);
      WidgetSync.saveFormIds(this.context, next);
    });
  }

  private pushData(formId: string): void {
    CardDataBuilder.build(this.context).then((data: Record<string, Object>) => {
      formProvider.updateForm(formId, formBindingData.createFormBindingData(data)).catch(() => {
      });
    });
  }
}
