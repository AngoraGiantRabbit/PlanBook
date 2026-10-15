import { AppColors } from '../common/Theme';
import { ReviewSetting, ReviewType, TaskTypeLabels } from '../model/Models';
import { SettingsVM } from '../viewmodel/ViewModels';
import { DatePickerOverlay, TimePickerOverlay } from '../components/Pickers';
import { DateUtils } from '../common/DateUtils';
import { parseTime } from '../components/Pickers';
import { router } from '@kit.ArkUI';

/**
 * 设置页（对应 Android SettingsScreen）：
 * 复盘设置（每类开关 + 时段，TimePicker）+ 计划本管理入口。
 */
@ComponentV2
export struct SettingsPage {
  @Local vm: SettingsVM = new SettingsVM();
  @Local pickerFor: ReviewType | null = null;

  aboutToAppear(): void {
    this.vm.attach();
  }

  aboutToDisappear(): void {
    this.vm.detach();
  }

  private settingOf(type: ReviewType): ReviewSetting | null {
    for (const s of this.vm.reviewSettings) {
      if (s.reviewType === type) {
        return s;
      }
    }
    return null;
  }

  @Local showTermPicker: boolean = false;

  build() {
    Stack() {
      Scroll() {
        Column({ space: 10 }) {
          Text('复盘设置')
            . fontSize(16)
            . fontWeight(FontWeight.Bold)
            . fontColor(AppColors.onSurface)
            . width('100%')
          Text('控制每日/每周/每月复盘待办是否自动生成，以及生成时段。')
            . fontSize(12)
            . fontColor(AppColors.onSurfaceVariant)
            . width('100%')

          ForEach(this.vm.reviewSettings, (setting: ReviewSetting) => {
            this.ReviewSettingItem(setting)
          }, (setting: ReviewSetting) => `${setting.reviewType}-${setting.enabled}-${setting.startTime}-${setting.endTime}`)

          Blank()
            . height(20)
          Text('学期')
            . fontSize(16)
            . fontWeight(FontWeight.Bold)
            . fontColor(AppColors.onSurface)
            . width('100%')
          Text('配置学期第一周的周一后，周视图标题与桌面卡片会显示教学周数（第 N 周）。')
            . fontSize(12)
            . fontColor(AppColors.onSurfaceVariant)
            . width('100%')
          Row({ space: 12 }) {
            Text(this.vm.termStart.length > 0 ? `第一周：${this.vm.termStart} 起` : '未设置')
              . fontSize(14)
              . fontColor(this.vm.termStart.length > 0 ? AppColors.onSurface : AppColors.onSurfaceVariant)
              . layoutWeight(1)
            Text('选择')
              . fontSize(13)
              . fontColor(AppColors.primary)
              . padding({ left: 10, right: 10, top: 6, bottom: 6 })
              . borderRadius(10)
              . border({ width: 1, color: AppColors.outline })
              . onClick(() => this.showTermPicker = true)
            if (this.vm.termStart.length > 0) {
              Text('清除')
                . fontSize(13)
                . fontColor(AppColors.error)
                . padding({ left: 10, right: 10, top: 6, bottom: 6 })
                . borderRadius(10)
                . border({ width: 1, color: AppColors.outline })
                . onClick(() => this.vm.setTermStart(null))
            }
          }
          . width('100%')

          Blank()
            . height(20)
          Text('计划本')
            . fontSize(16)
            . fontWeight(FontWeight.Bold)
            . fontColor(AppColors.onSurface)
            . width('100%')
          Button('计划本管理（子计划本：新建 / 显示 / 活动 / 颜色）')
            . width('100%')
            . backgroundColor(AppColors.surface)
            . fontColor(AppColors.primary)
            . border({ width: 1, color: AppColors.outline })
            . onClick(() => router.pushUrl({ url: 'pages/NotebookManagerPage' }))
        }
        . width('100%')
        . padding(16)
        . alignItems(HorizontalAlign.Start)
      }
      . align(Alignment.TopStart)

      if (this.showTermPicker) {
        DatePickerOverlay({
          initialDate: this.vm.termStart.length > 0 ? this.vm.termStart : DateUtils.today(),
          onConfirm: (d: string) => {
            this.vm.setTermStart(d);
            this.showTermPicker = false;
          },
          onDismiss: () => this.showTermPicker = false
        })
      }

      if (this.pickerFor !== null) {
        this.TimePickOverlay()
      }
    }
    . width('100%')
    . height('100%')
    . backgroundColor(AppColors.surface)
  }

  @Builder
  ReviewSettingItem(setting: ReviewSetting) {
    Column({ space: 8 }) {
      Row() {
        Text(TaskTypeLabels.reviewTypeLabel(setting.reviewType))
          . fontSize(15)
          . fontColor(AppColors.onSurface)
          . layoutWeight(1)
        Toggle({ type: ToggleType.Switch, isOn: setting.enabled })
          . selectedColor(AppColors.primary)
          . onChange((on: boolean) => this.vm.toggleReview(setting.reviewType, on))
      }
      . width('100%')

      // #6：时段行始终显示（关闭开关时也能查看/修改时间，重开时不用重设）
      Row({ space: 10 }) {
        Text('时段')
          . fontSize(14)
          . fontColor(AppColors.onSurface)
        this.TimeChip(setting.startTime, true, setting.reviewType)
        Text('~')
          . fontSize(14)
          . fontColor(AppColors.onSurfaceVariant)
        this.TimeChip(setting.endTime, false, setting.reviewType)
      }
      . width('100%')
    }
    . width('100%')
    . padding({ top: 8, bottom: 8 })
    . border({ width: { bottom: 1 }, color: AppColors.surfaceVariant })
  }

  /** 是否正在编辑起点（TimeChip 点击时记录，TimePickOverlay 确认时使用） */
  private pickerIsStart: boolean = true;

  @Builder
  TimeChip(time: string, isStart: boolean, type: ReviewType) {
    Text(time)
      . fontSize(13)
      . fontColor(AppColors.primary)
      . padding({ left: 12, right: 12, top: 6, bottom: 6 })
      . borderRadius(10)
      . border({ width: 1, color: AppColors.outline })
      . onClick(() => {
        this.pickerIsStart = isStart;
        this.pickerFor = type;
      })
  }


  @Builder
  TimePickOverlay() {
    TimePickerOverlay({
      initialHour: this.pickerInitHour(),
      initialMinute: this.pickerInitMinute(),
      onConfirm: (t: string) => {
        const type = this.pickerFor;
        if (type !== null) {
          const setting = this.settingOf(type);
          if (setting !== null) {
            if (this.pickerIsStart) {
              this.vm.setReviewTime(type, t, setting.endTime);
            } else {
              this.vm.setReviewTime(type, setting.startTime, t);
            }
          }
        }
        this.pickerFor = null;
      },
      onDismiss: () => this.pickerFor = null
    })
  }

  private pickerInitHour(): number {
    const setting = this.settingOf(this.pickerFor ?? ReviewType.DAILY);
    const t = parseTime(this.pickerIsStart ? (setting?.startTime ?? '21:00') : (setting?.endTime ?? '21:30'));
    return t === null ? 21 : t[0];
  }

  private pickerInitMinute(): number {
    const setting = this.settingOf(this.pickerFor ?? ReviewType.DAILY);
    const t = parseTime(this.pickerIsStart ? (setting?.startTime ?? '21:00') : (setting?.endTime ?? '21:30'));
    return t === null ? 0 : t[1];
  }
}
