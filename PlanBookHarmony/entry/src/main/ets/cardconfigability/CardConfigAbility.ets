import { AbilityConstant, UIAbility, Want } from '@kit.AbilityKit';
import { window } from '@kit.ArkUI';
import { formInfo } from '@kit.FormKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { Repository } from '../data/Repository';

const TAG = 'PlanBook.CardConfigAbility';

/**
 * 卡片配置页宿主（formConfigAbility 声明的目标）：
 * 桌面上长按卡片 →「编辑卡片显示内容」拉起本 Ability，携带 formId；
 * 打开 pages/CardConfigPage 供用户勾选该卡片显示哪些子计划本。
 * 兼容多个候选参数键（不同桌面版本携带的 key 不一）。
 */
export default class CardConfigAbility extends UIAbility {
  /** 当前正在配置的卡片 formId（配置页读取） */
  static formId: string = '';

  onCreate(want: Want, _launchParam: AbilityConstant.LaunchParam): void {
    Repository.init(this.context);
    CardConfigAbility.formId = CardConfigAbility.extractFormId(want);
    hilog.info(0, TAG, 'want params: %{public}s', JSON.stringify(want.parameters));
    hilog.info(0, TAG, 'config for card: %{public}s', CardConfigAbility.formId);
  }

  onNewWant(want: Want, _launchParam: AbilityConstant.LaunchParam): void {
    CardConfigAbility.formId = CardConfigAbility.extractFormId(want);
    hilog.info(0, TAG, 'newWant, card: %{public}s', CardConfigAbility.formId);
  }

  /** 从 want.parameters 里尽力提取 formId（数字串），打日志定位实际 key */
  private static extractFormId(want: Want): string {
    const params = want.parameters;
    if (params === undefined || params === null) {
      return '';
    }
    // 候选键逐个试
    const keys: string[] = [
      formInfo.FormParam.IDENTITY_KEY,
      'formId',
      'formIdentity',
      'ohos.extra.param.key.form_identity'
    ];
    for (const k of keys) {
      const v = params[k];
      if (v !== undefined && v !== null && `${v}`.length > 0) {
        return `${v}`;
      }
    }
    // 兜底：遍历找值形如纯数字串的参数
    const all = Object.keys(params);
    for (const k of all) {
      const v = `${params[k]}`;
      if (/^\d{5,}$/.test(v)) {
        hilog.info(0, TAG, 'formId fallback key: %{public}s', k);
        return v;
      }
    }
    return '';
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    windowStage.loadContent('pages/CardConfigPage', (err) => {
      if (err.code) {
        hilog.error(0, TAG, 'load failed: %{public}s', JSON.stringify(err));
      }
    });
  }
}
