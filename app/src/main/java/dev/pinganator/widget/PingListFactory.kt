package dev.pinganator.widget

import android.content.Context
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import dev.pinganator.R
import dev.pinganator.data.ResultStore
import dev.pinganator.data.model.PingResult

class PingListFactory(private val ctx: Context) : RemoteViewsService.RemoteViewsFactory {

    private var results: List<PingResult> = emptyList()

    override fun onCreate() {}
    override fun onDestroy() {}
    override fun onDataSetChanged() { results = ResultStore(ctx).load() }
    override fun getCount(): Int = results.size
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
    override fun getLoadingView(): RemoteViews? = null

    override fun getViewAt(position: Int): RemoteViews {
        val result = results.getOrNull(position)
            ?: return RemoteViews(ctx.packageName, R.layout.widget_row)

        return RemoteViews(ctx.packageName, R.layout.widget_row).apply {
            setTextViewText(R.id.row_label, result.displayLabel())
            setTextViewText(R.id.row_latency, formatLatency(result))
            setImageViewResource(
                R.id.row_indicator,
                if (result.isReachable) R.drawable.ic_status_ok else R.drawable.ic_status_fail
            )
        }
    }

    private fun formatLatency(r: PingResult): String = when {
        !r.isReachable -> ctx.getString(R.string.status_fail)
        r.latencyMs < 0 -> "—"
        else -> "${r.latencyMs} мс"
    }
}
