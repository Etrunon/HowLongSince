package eu.etrunon.howlongsince

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Home-screen widget: the counters most in need of a reset, each with a reset button. */
class Widget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, m: AppWidgetManager, ids: IntArray) {
        val done = goAsync()
        CoroutineScope(Dispatchers.IO).launch { try { updateWidgets(ctx) } finally { done.finish() } }
    }
}

// ponytail: times refresh on every change and every 30 min (system minimum); an alarm per minute isn't worth the battery.
suspend fun updateWidgets(ctx: Context) {
    val m = AppWidgetManager.getInstance(ctx)
    val ids = m.getAppWidgetIds(ComponentName(ctx, Widget::class.java))
    if (ids.isEmpty()) return
    val now = System.currentTimeMillis()
    val list = Db.get(ctx).dao().counters().first().filter { !it.counter.archived }.sortedByDescending { it.overdue(now) }.take(6)
    val v = RemoteViews(ctx.packageName, R.layout.widget)
    v.setViewVisibility(R.id.empty, if (list.isEmpty()) View.VISIBLE else View.GONE)
    v.setOnClickPendingIntent(R.id.title, open(ctx, 0))
    v.removeAllViews(R.id.rows)
    list.forEach { c ->
        val id = c.counter.id
        v.addView(R.id.rows, RemoteViews(ctx.packageName, R.layout.widget_row).apply {
            setTextViewText(R.id.label, c.counter.label)
            setTextViewText(R.id.since, since(now - c.lastAt))
            setTextColor(R.id.since, if (c.overdue(now)) 0xFFFF6B6B.toInt() else 0xFF8AB4F8.toInt())
            setOnClickPendingIntent(R.id.label, open(ctx, id))
            setOnClickPendingIntent(R.id.reset, action(ctx, id, "reset"))
        })
    }
    m.updateAppWidget(ids, v)
}

/** Same intent as a notification tap: opens the app on that counter (0 = the list). */
private fun open(ctx: Context, id: Long) = PendingIntent.getActivity(ctx, id.toInt(),
    Intent(ctx, MainActivity::class.java).putExtra("id", id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
