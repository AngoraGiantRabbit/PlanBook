package com.example.planbook.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.planbook.MainActivity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 桌面小部件（PRD §5 / Issue #13）—— 可安装骨架，暂无课表数据。
 *
 * 本阶段行为：
 * - 显示标题「计划本」+ 当前周区间（周一开始）+ 提示「在 App 内点刷新同步」。
 * - 整个表面可点击，点击任意区域打开 App 主页面。
 * - 刷新为手动机制：App 内点刷新按钮后由 [WidgetSync.updateAll] 同步更新。
 *
 * 下一阶段（接入真实数据）：
 * - 周视图网格 + 底部灵活待办的简化布局。
 * - 复选框点击用 actionStartActivity + 携带 taskId 的 intent 处理完成切换。
 */
class PlanBookWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        // 周一为一周起始（ISO）：dayOfWeek.value 周一=1..周日=7
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val weekEnd = weekStart.plusDays(6)
        val fmt = DateTimeFormatter.ofPattern("MM/dd", Locale.CHINA)

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .clickable(actionStartActivity<MainActivity>())
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "计划本",
                    style = TextStyle(
                        color = ColorProvider(Color.Black),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "${weekStart.format(fmt)} - ${weekEnd.format(fmt)}",
                    modifier = GlanceModifier.padding(top = 4.dp),
                    style = TextStyle(
                        color = ColorProvider(Color.DarkGray),
                        fontSize = 14.sp
                    )
                )
                Text(
                    text = "在 App 内点刷新同步",
                    modifier = GlanceModifier.padding(top = 4.dp),
                    style = TextStyle(
                        color = ColorProvider(Color.Gray),
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}

class PlanBookWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlanBookWidget()
}

/**
 * 手动刷新入口：App 内点刷新按钮时同步所有已安装的小部件。
 * runCatching 包裹，确保小部件侧异常不会拖垮 App（PRD：手动刷新机制）。
 */
object WidgetSync {
    suspend fun updateAll(context: Context) {
        runCatching { PlanBookWidget().updateAll(context) }
    }
}
