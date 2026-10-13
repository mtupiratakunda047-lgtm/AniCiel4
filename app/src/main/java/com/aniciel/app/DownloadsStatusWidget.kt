package com.aniciel.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** 2×1 downloads status. Tap opens the Downloads screen. */
class DownloadsStatusWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetUpdater.init(context)
        DownloadsStore.init(context)
        ids.forEach { id -> manager.updateAppWidget(id, build(context)) }
    }

    companion object {
        fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_downloads)
            val open = PendingIntent.getActivity(
                context, 12,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_DOWNLOADS)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_dl_root, open)

            val pending = DownloadsStore.pendingCount()
            val completed = DownloadsStore.items.count { it.state == "completed" }
            views.setTextViewText(R.id.widget_dl_pending, pending.toString())
            views.setTextViewText(R.id.widget_dl_done, completed.toString())
            views.setTextViewText(
                R.id.widget_dl_status,
                when {
                    pending == 0 && completed == 0 -> "No downloads yet"
                    pending == 0 -> "All caught up"
                    else -> "$pending in progress"
                }
            )
            return views
        }
    }
}
