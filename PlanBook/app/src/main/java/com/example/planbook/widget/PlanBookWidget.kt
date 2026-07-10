package com.example.planbook.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.text.Text
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceModifier
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * 桌面小部件（PRD §5）—— 本阶段暂缓实现。
 *
 * 设计目标（来自 PRD）：
 * - 默认占满一页（如 4×6 / 5×7），用户可手动调整大小，最小 4×4。
 * - 显示当前计划本的周视图网格 + 底部灵活待办，与 App 主页面一致。
 * - 点击任务块/时段 → 打开 App 主页面；点灵活任务复选框 → 直接切换完成状态。
 * - 刷新采用手动机制：App 内点刷新按钮后同步更新小部件。
 *
 * 技术选型：Jetpack Glance（依赖已在 libs.versions.toml 引入，版本 1.1.0）。
 *
 * 待办（下一阶段）：
 * 1. 实现 @Composable Glance 内容：复用周视图与灵活待办的简化布局（Glance 组件受限，需另写）。
 * 2. 注册 AppWidgetProviderInfo（res/xml）+ 在 AndroidManifest 声明 PlanBookWidgetReceiver。
 * 3. 通过 GlanceAppWidgetManager.updateAll() 在 ViewModel.refresh() 中触发小部件刷新。
 * 4. 复选框点击用 actionStartActivity + 携带 taskId 的 intent 处理完成切换。
 *
 * 说明：小部件行为在 Android 模拟器上不易验证，且最终通过卓易通在鸿蒙上运行，
 * 需先验证 Glance 小部件在该环境的兼容性，故本阶段先做 App 内功能。
 */
class PlanBookWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 占位实现：下一阶段替换为完整的周视图 + 灵活待办布局
        provideContent {
            Text(
                text = "计划本小部件（开发中）",
                modifier = GlanceModifier,
                style = TextStyle(color = ColorProvider(Color.Black))
            )
        }
    }
}

class PlanBookWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlanBookWidget()
}
