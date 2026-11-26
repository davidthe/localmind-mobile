package dev.localmind.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import dev.localmind.app.MainActivity
import dev.localmind.app.R
import dev.localmind.app.search.SearchDeepLink
import dev.localmind.app.search.SearchScope

class LocalMindSearchWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_localmind_search)
            views.setOnClickPendingIntent(
                R.id.widget_search_bar,
                launchPendingIntent(
                    context = context,
                    requestCode = 100 + appWidgetId,
                    scope = SearchScope.ALL,
                ),
            )
            views.setOnClickPendingIntent(
                R.id.widget_opt_filenames,
                launchPendingIntent(
                    context = context,
                    requestCode = 200 + appWidgetId,
                    scope = SearchScope.FILE_NAMES,
                ),
            )
            views.setOnClickPendingIntent(
                R.id.widget_opt_inside_files,
                launchPendingIntent(
                    context = context,
                    requestCode = 300 + appWidgetId,
                    scope = SearchScope.INSIDE_FILES,
                ),
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun launchPendingIntent(
        context: Context,
        requestCode: Int,
        scope: SearchScope,
    ): PendingIntent {
        val uri = Uri.parse(
            SearchDeepLink.buildUriString(
                scope = scope,
                focus = true,
                fromWidget = true,
            ),
        )
        val intent = Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        fun requestPinWidget(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            val manager = context.getSystemService(AppWidgetManager::class.java) ?: return false
            if (!manager.isRequestPinAppWidgetSupported) return false
            val provider = ComponentName(context, LocalMindSearchWidgetProvider::class.java)
            return manager.requestPinAppWidget(provider, null, null)
        }
    }
}
