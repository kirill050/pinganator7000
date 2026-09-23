package dev.pinganator.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import dev.pinganator.R
import dev.pinganator.data.ResultStore
import dev.pinganator.data.db.AppDatabase
import dev.pinganator.data.model.PingTarget
import dev.pinganator.ping.DefaultTargets
import dev.pinganator.ping.Pinger
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

open class PingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { applyViews(ctx, mgr, it) }
        schedulePings(ctx)
        triggerPing(ctx)
    }

    override fun onEnabled(ctx: Context) {
        schedulePings(ctx)
        triggerPing(ctx)
    }

    override fun onDisabled(ctx: Context) {
        // Cancel schedule only if no other list-widgets remain
        val mgr = AppWidgetManager.getInstance(ctx)
        val mainLeft = mgr.getAppWidgetIds(ComponentName(ctx, PingWidgetProvider::class.java)).size
        val narrowLeft = mgr.getAppWidgetIds(ComponentName(ctx, PingNarrowWidgetProvider::class.java)).size
        val miniLeft = mgr.getAppWidgetIds(ComponentName(ctx, PingMiniWidgetProvider::class.java)).size
        if (mainLeft + narrowLeft + miniLeft == 0) cancelSchedule(ctx)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        when (intent.action) {
            ACTION_TOGGLE_PAUSE -> handleTogglePause(ctx)
            ACTION_PING, ACTION_REFRESH -> {
                if (ResultStore(ctx).isPaused()) return
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    try {
                        withTimeout(8_000) { runPings(ctx) }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    // ── internals ────────────────────────────────────────────────────────────

    private fun handleTogglePause(ctx: Context) {
        val store = ResultStore(ctx)
        val nowPaused = !store.isPaused()
        store.setPaused(nowPaused)
        if (nowPaused) {
            cancelSchedule(ctx)
        } else {
            schedulePings(ctx)
            triggerPing(ctx)
        }
        refreshAllWidgets(ctx)
    }

    private suspend fun runPings(ctx: Context) {
        val store = ResultStore(ctx)
        val pinger = Pinger()
        val disabledIds = store.getDisabledDefaultIds()
        val customHosts = AppDatabase.getInstance(ctx).customHostDao().getAll()

        val targets: List<PingTarget> = DefaultTargets.getEnabled(disabledIds) + customHosts.map { host ->
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

        // Wrap each ping in try-catch so a single failure doesn't discard all results
        val results = coroutineScope {
            targets.map { target ->
                async {
                    try {
                        pinger.ping(target)
                    } catch (e: Exception) {
                        dev.pinganator.data.model.PingResult(
                            target.id, target.label, target.subtitle,
                            false, -1L, System.currentTimeMillis()
                        )
                    }
                }
            }.awaitAll()
        }

        store.save(results)

        val mgr = AppWidgetManager.getInstance(ctx)
        for (cls in listOf(PingWidgetProvider::class.java, PingNarrowWidgetProvider::class.java)) {
            mgr.getAppWidgetIds(ComponentName(ctx, cls)).forEach { id ->
                applyViews(ctx, mgr, id)
                @Suppress("DEPRECATION")
                mgr.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
            }
        }
        PingMiniWidgetProvider.updateAll(ctx, mgr, customHosts)
    }

    companion object {
        const val ACTION_PING = "dev.pinganator.ACTION_PING"
        const val ACTION_REFRESH = "dev.pinganator.ACTION_REFRESH"
        const val ACTION_TOGGLE_PAUSE = "dev.pinganator.ACTION_TOGGLE_PAUSE"

        private const val RC_ALARM = 100
        private const val RC_REFRESH = 101
        private const val RC_SETTINGS = 102
        private const val RC_STOP = 103

        fun refreshAllWidgets(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            for (cls in listOf(PingWidgetProvider::class.java, PingNarrowWidgetProvider::class.java)) {
                mgr.getAppWidgetIds(ComponentName(ctx, cls)).forEach { id ->
                    applyViews(ctx, mgr, id)
                    @Suppress("DEPRECATION")
                    mgr.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
                }
            }
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                val customs = AppDatabase.getInstance(ctx).customHostDao().getAll()
                PingMiniWidgetProvider.updateAll(ctx, mgr, customs)
            }
        }

        fun applyViews(ctx: Context, mgr: AppWidgetManager, widgetId: Int) {
            val paused = ResultStore(ctx).isPaused()
            val views = RemoteViews(ctx.packageName, R.layout.widget_ping)

            // ListView backed by PingListService
            val svcIntent = Intent(ctx, PingListService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            @Suppress("DEPRECATION")
            views.setRemoteAdapter(R.id.widget_list, svcIntent)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            // Refresh button (hidden when paused)
            val refreshPi = PendingIntent.getBroadcast(
                ctx, RC_REFRESH,
                Intent(ctx, PingWidgetProvider::class.java).apply { action = ACTION_REFRESH },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshPi)
            views.setViewVisibility(R.id.widget_refresh,
                if (paused) android.view.View.GONE else android.view.View.VISIBLE)

            // Stop/resume button
            val stopPi = PendingIntent.getBroadcast(
                ctx, RC_STOP,
                Intent(ctx, PingWidgetProvider::class.java).apply { action = ACTION_TOGGLE_PAUSE },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_stop, stopPi)
            views.setTextViewText(R.id.widget_stop, if (paused) "▶" else "⏸")

            // Title tap → settings
            val settingsPi = PendingIntent.getActivity(
                ctx, RC_SETTINGS,
                Intent(ctx, dev.pinganator.ui.SettingsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, settingsPi)

            // Last update timestamp
            val ts = ResultStore(ctx).getLastUpdated()
            val timeStr = if (ts > 0)
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ts)) else "—"
            val statusPrefix = if (paused) ctx.getString(R.string.status_paused) + " · " else ""
            views.setTextViewText(
                R.id.widget_last_update,
                statusPrefix + ctx.getString(R.string.updated_at, timeStr)
            )

            mgr.updateAppWidget(widgetId, views)
        }

        fun schedulePings(ctx: Context) {
            val store = ResultStore(ctx)
            if (store.isPaused()) return
            val intervalMs = store.getIntervalMs()
            if (intervalMs <= 0L) return
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + intervalMs,
                intervalMs,
                alarmPi(ctx)
            )
        }

        fun cancelSchedule(ctx: Context) {
            (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(alarmPi(ctx))
        }

        fun triggerPing(ctx: Context) {
            ctx.sendBroadcast(
                Intent(ctx, PingWidgetProvider::class.java).apply { action = ACTION_PING }
            )
        }

        private fun alarmPi(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
            ctx, RC_ALARM,
            Intent(ctx, PingWidgetProvider::class.java).apply { action = ACTION_PING },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
