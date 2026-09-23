package dev.pinganator.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import dev.pinganator.R
import dev.pinganator.data.ResultStore
import dev.pinganator.data.db.CustomHost
import dev.pinganator.data.model.PingTarget
import dev.pinganator.ping.DefaultTargets
import kotlinx.coroutines.*

class PingMiniWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        PingWidgetProvider.schedulePings(ctx)
        PingWidgetProvider.triggerPing(ctx)
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            val customs = dev.pinganator.data.db.AppDatabase.getInstance(ctx).customHostDao().getAll()
            ids.forEach { applyMiniViews(ctx, mgr, it, customs) }
        }
    }

    override fun onEnabled(ctx: Context) {
        PingWidgetProvider.schedulePings(ctx)
        PingWidgetProvider.triggerPing(ctx)
    }

    override fun onAppWidgetOptionsChanged(
        ctx: Context, mgr: AppWidgetManager, widgetId: Int, newOptions: Bundle
    ) {
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            val customs = dev.pinganator.data.db.AppDatabase.getInstance(ctx).customHostDao().getAll()
            applyMiniViews(ctx, mgr, widgetId, customs)
        }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == PingWidgetProvider.ACTION_PING ||
            intent.action == PingWidgetProvider.ACTION_REFRESH
        ) {
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                val customs = dev.pinganator.data.db.AppDatabase.getInstance(ctx).customHostDao().getAll()
                val mgr = AppWidgetManager.getInstance(ctx)
                updateAll(ctx, mgr, customs)
            }
        }
    }

    companion object {
        fun updateAll(ctx: Context, mgr: AppWidgetManager, customs: List<CustomHost>) {
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, PingMiniWidgetProvider::class.java))
            ids.forEach { applyMiniViews(ctx, mgr, it, customs) }
        }

        fun applyMiniViews(
            ctx: Context,
            mgr: AppWidgetManager,
            widgetId: Int,
            customs: List<CustomHost>
        ) {
            val store = ResultStore(ctx)
            val disabled = store.getDisabledDefaultIds()

            val activeHosts: List<PingTarget> =
                DefaultTargets.getEnabled(disabled) + customs.map { host ->
                    val url = if (host.url.startsWith("http")) host.url else "https://${host.url}"
                    PingTarget(
                        id = "custom_${host.id}",
                        label = host.label,
                        subtitle = host.url,
                        address = url,
                        checkMode = if (url.startsWith("http://"))
                            PingTarget.CheckMode.HTTP_HEAD else PingTarget.CheckMode.HTTPS_HEAD,
                        isBuiltIn = false
                    )
                }

            val resultMap = store.load().associateBy { it.targetId }

            // Widget current size → cells
            val opts = mgr.getAppWidgetOptions(widgetId)
            val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
            val minH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 40)
            val cols = maxOf(2, (minW + 30) / 70).coerceAtMost(4)
            val rows = maxOf(1, (minH + 30) / 70).coerceAtMost(2)

            val numHosts = activeHosts.size
            // No gray slots when there are ≥ 2 hosts; always show at least 2
            val numToShow = if (numHosts >= 2) {
                minOf(cols * rows, numHosts).coerceAtMost(8)
            } else {
                2
            }

            val cellIds = intArrayOf(
                R.id.mini_cell_1, R.id.mini_cell_2, R.id.mini_cell_3, R.id.mini_cell_4,
                R.id.mini_cell_5, R.id.mini_cell_6, R.id.mini_cell_7, R.id.mini_cell_8
            )
            val indIds = intArrayOf(
                R.id.mini_ind_1, R.id.mini_ind_2, R.id.mini_ind_3, R.id.mini_ind_4,
                R.id.mini_ind_5, R.id.mini_ind_6, R.id.mini_ind_7, R.id.mini_ind_8
            )
            val lblIds = intArrayOf(
                R.id.mini_lbl_1, R.id.mini_lbl_2, R.id.mini_lbl_3, R.id.mini_lbl_4,
                R.id.mini_lbl_5, R.id.mini_lbl_6, R.id.mini_lbl_7, R.id.mini_lbl_8
            )
            val subIds = intArrayOf(
                R.id.mini_sub_1, R.id.mini_sub_2, R.id.mini_sub_3, R.id.mini_sub_4,
                R.id.mini_sub_5, R.id.mini_sub_6, R.id.mini_sub_7, R.id.mini_sub_8
            )

            val views = RemoteViews(ctx.packageName, R.layout.widget_mini)

            // Each cell occupies grid position (row=i/4, col=i%4).
            // Host index = row*cols + col — so row 1 always holds cols circles,
            // row 2 holds the next cols circles.
            var row2HasContent = false
            for (i in 0 until 8) {
                val row = i / 4       // 0 = top row, 1 = bottom row
                val col = i % 4       // column within the physical 4-cell row
                val hostIdx = row * cols + col

                val shouldShow = col < cols && row < rows && hostIdx < numToShow
                if (shouldShow) {
                    val host = activeHosts.getOrNull(hostIdx)
                    val result = host?.let { resultMap[it.id] }
                    views.setViewVisibility(cellIds[i], View.VISIBLE)
                    views.setImageViewResource(
                        indIds[i], when {
                            host == null -> R.drawable.ic_status_unknown
                            result == null -> R.drawable.ic_status_unknown
                            result.isReachable -> R.drawable.ic_status_ok
                            else -> R.drawable.ic_status_fail
                        }
                    )
                    views.setTextViewText(lblIds[i], host?.label ?: "")
                    views.setTextViewText(subIds[i], host?.subtitle ?: "")
                    if (row == 1) row2HasContent = true
                } else {
                    views.setViewVisibility(cellIds[i], View.GONE)
                }
            }

            views.setViewVisibility(
                R.id.mini_row_2,
                if (row2HasContent) View.VISIBLE else View.GONE
            )

            mgr.updateAppWidget(widgetId, views)
        }
    }
}
