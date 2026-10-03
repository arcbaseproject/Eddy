package app.eddy.browser.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.eddy.browser.MainActivity
import app.eddy.browser.R

/** Home-screen search bar: tap the field to type, the mic to dictate, or the mask for an incognito tab. */
class SearchWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_search).apply {
            setOnClickPendingIntent(R.id.widget_search, open(context, ACTION_SEARCH, 0))
            setOnClickPendingIntent(R.id.widget_voice, open(context, ACTION_VOICE, 1))
            setOnClickPendingIntent(R.id.widget_incognito, open(context, ACTION_INCOGNITO, 2))
        }
        manager.updateAppWidget(ids, views)
    }

    private fun open(context: Context, action: String, code: Int) = PendingIntent.getActivity(
        context, code, Intent(context, MainActivity::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_SEARCH = "app.eddy.browser.WIDGET_SEARCH"
        const val ACTION_VOICE = "app.eddy.browser.WIDGET_VOICE"
        const val ACTION_INCOGNITO = "app.eddy.browser.WIDGET_INCOGNITO"
    }
}
