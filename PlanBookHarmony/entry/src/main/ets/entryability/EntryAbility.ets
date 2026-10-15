import { AbilityConstant, UIAbility, Want } from '@kit.AbilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { window } from '@kit.ArkUI';
import { WidgetSync } from '../widget/WidgetSync';
import { Repository } from '../data/Repository';

const TAG: string = 'PlanBook.EntryAbility';

export default class EntryAbility extends UIAbility {
  onCreate(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    // 数据门面初始化：App 全局唯一（对应 Android 侧 Hilt Singleton）
    Repository.init(this.context);
    // App 内任何写操作后自动同步桌面卡片（2s 节流；卡片 ↻ 为兜底）
    WidgetSync.installAutoSync(this.context);
  }

  onDestroy(): void {
    hilog.info(0x0000, TAG, 'Ability onDestroy');
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    windowStage.loadContent('pages/Index', (err) => {
      if (err.code) {
        hilog.error(0x0000, TAG, 'Failed to load content. Cause: %{public}s', JSON.stringify(err));
        return;
      }
      hilog.info(0x0000, TAG, 'Succeeded in loading content.');
    });
  }

  onWindowStageDestroy(): void {
    hilog.info(0x0000, TAG, 'WindowStage destroy');
  }

  onForeground(): void {
    hilog.info(0x0000, TAG, 'App foreground');
  }

  onBackground(): void {
    hilog.info(0x0000, TAG, 'App background');
  }
}
