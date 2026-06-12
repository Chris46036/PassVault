package com.passvault.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.passvault.app.MainActivity
import com.passvault.app.R

/** Widget de acceso rápido: abrir la bóveda o el generador. No muestra secretos. */
class QuickWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_quick)

            val openIntent = Intent(context, MainActivity::class.java)
            views.setOnClickPendingIntent(
                R.id.widget_open,
                PendingIntent.getActivity(
                    context, 4001, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            val genIntent = Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_GENERATOR)
            views.setOnClickPendingIntent(
                R.id.widget_generator,
                PendingIntent.getActivity(
                    context, 4002, genIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            manager.updateAppWidget(id, views)
        }
    }
}
