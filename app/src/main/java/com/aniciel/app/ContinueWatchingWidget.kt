package com.aniciel.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

/**
 * 4×2 Continue Watching widget. Text-only (no poster bitmaps) to keep source small.
 * Tap opens AniCiel on the Library screen.
 */
class ContinueWatchingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetUpdater.init(context)
        LibraryStore.init(context)
        ids.forEach { id -> manager.updateAppWidget(id, build(context)) }
    }

    companion object {
        private val ROW_IDS = intArrayOf(
            R.id.widget_cw_row0, R.id.widget_cw_row1, R.id.widget_cw_row2, R.id.widget_cw_row3
        )
        private val TITLE_IDS = intArrayOf(
            R.id.widget_cw_title0, R.id.widget_cw_title1, R.id.widget_cw_title2, R.id.widget_cw_title3
        )
        private val META_IDS = intArrayOf(
            R.id.widget_cw_meta0, R.id.widget_cw_meta1, R.id.widget_cw_meta2, R.id.widget_cw_meta3
        )

        fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_continue)
            val open = PendingIntent.getActivity(
                context, 11,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_LIBRARY)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_cw_root, open)

            val items = LibraryStore.continueWatching(4)
            views.setViewVisibility(R.id.widget_cw_empty, if (items.isEmpty()) View.VISIBLE else View.GONE)
            ROW_IDS.forEachIndexed { i, rowId ->
                val item = items.getOrNull(i)
                if (item == null) {
                    views.setViewVisibility(rowId, View.GONE)
                } else {
                    views.setViewVisibility(rowId, View.VISIBLE)
                    views.setTextViewText(TITLE_IDS[i], item.title)
                    val ep = when {
                        item.lastSeason > 1 -> "S${item.lastSeason}E${item.lastEpisode}"
                        item.lastEpisode > 0 -> "Episode ${item.lastEpisode}"
                        else -> item.libraryCategory
                    }
                    val pct = if (item.progressPercent > 0) " · ${item.progressPercent.toInt()}%" else ""
                    views.setTextViewText(META_IDS[i], ep + pct)
                }
            }
            return views
        }
    }
}
