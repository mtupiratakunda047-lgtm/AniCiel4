package com.aniciel.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Pushes library/download changes to home-screen widgets. Tiny, no Glance. */
object WidgetUpdater {
    @Volatile private var app: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    fun refresh(context: Context? = app) {
        val ctx = context?.applicationContext ?: return
        app = ctx
        val mgr = AppWidgetManager.getInstance(ctx)
        fun notify(cls: Class<*>) {
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, cls))
            if (ids.isEmpty()) return
            val intent = Intent(ctx, cls).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            ctx.sendBroadcast(intent)
        }
        notify(ContinueWatchingWidget::class.java)
        notify(DownloadsStatusWidget::class.java)
        notify(LaunchShortcutWidget::class.java)
    }
}
