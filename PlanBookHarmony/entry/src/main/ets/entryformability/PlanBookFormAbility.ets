import { FormExtensionAbility, formBindingData, formInfo, formProvider } from '@kit.FormKit';
import { Want } from '@kit.AbilityKit';
import { CardDataBuilder } from '../widget/CardData';
import { WidgetSync } from '../widget/WidgetSync';
import { DateUtils } from '../common/DateUtils';
import { Repository } from '../data/Repository';
import { hilog } from '@kit.PerformanceAnalysisKit';

const TAG = 'PlanBook.FormAbility';

/**
 * 桌面卡片 FormExtensionAbility：
 * - onAddForm / onUpdateForm：登记 formId，构建 per-form 数据（配置 + 翻周状态）推给卡片；
 * - onFormEvent：卡内 < > 翻周与 ↻ 刷新（message = postCardAction params 的 JSON 串，含 op）；
 * - onRemoveForm：注销 formId 并清理 card_prefs。
 */
export default class PlanBookFormAbility extends FormExtensionAbility {
  onAddForm(want: Want): formBindingData.FormBindingData {
    Repository.init(this.context);
    const formId = want.parameters?.[formInfo.FormParam.IDENTITY_KEY] as string;
    if (formId !== undefined && formId.length > 0) {
      WidgetSync.trackFormId(this.context, formId);
      this.pushData(formId);
      hilog.info(0, TAG, 'card added: %{public}s', formId);
    }
    return formBindingData.createFormBindingData({});
  }

  onUpdateForm(formId: string): void {
    this.pushData(formId);
  }

  onFormEvent(formId: string, message: string): void {
    // 卡内按钮：postCardAction message → params 的 JSON 串（{op: prevWeek | nextWeek | refresh}）
    hilog.info(0, TAG, 'onFormEvent %{public}s: %{public}s', formId, message);
    let op = '';
    try {
      const data = JSON.parse(message) as Record<string, Object>;
      if (data['op'] !== undefined) {
        op = `${data['op']}`;
      } else if (data['message'] !== undefined) {
        op = `${data['message']}`;
      }
    } catch (e) {
      hilog.warn(0, TAG, 'parse message failed: %{public}s', `${e}`);
    }
    Repository.init(this.context);
    if (op === 'prevWeek' || op === 'nextWeek') {
      const delta = op === 'prevWeek' ? -1 : 1;
      Repository.shiftCardWeek(formId, delta).then(() => {
        this.pushData(formId);
      });
    } else if (op === 'refresh') {
      Repository.getMasterNotebookOnce().then((master) => {
        if (master === null) {
          this.pushData(formId);
          return;
        }
        Repository.ensureDerivedData(master.id, DateUtils.weekStartOf(DateUtils.today())).then(() => {
          this.pushData(formId);
        });
      });
    } else {
      // 未知 op 仍刷新一次，保证卡内任何 message 按钮有反馈
      this.pushData(formId);
    }
  }

  onRemoveForm(formId: string): void {
    WidgetSync.untrackFormId(this.context, formId).then(() => {
      Repository.init(this.context);
      Repository.deleteCardPref(formId);
    });
  }

  private pushData(formId: string): void {
    CardDataBuilder.build(this.context, formId).then((data: Record<string, Object>) => {
      formProvider.updateForm(formId, formBindingData.createFormBindingData(data)).catch(() => {
      });
    });
  }
}
