package dev.pinganator.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val ids = AppWidgetManager.getInstance(ctx)
            .getAppWidgetIds(ComponentName(ctx, PingWidgetProvider::class.java))
        if (ids.isNotEmpty()) {
            PingWidgetProvider.schedulePings(ctx)
            PingWidgetProvider.triggerPing(ctx)
        }
    }
}
