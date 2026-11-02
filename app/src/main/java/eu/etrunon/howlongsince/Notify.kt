package eu.etrunon.howlongsince

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Ms until the reminder is due, or null if none / already past. */
fun notifyDelay(lastAt: Long, afterMs: Long?, now: Long): Long? =
    afterMs?.let { lastAt + it - now }?.takeIf { it >= 0 }

/** Call after any create/reset/edit/delete: replaces the counter's pending reminder. */
suspend fun reschedule(ctx: Context, id: Long) {
    val wm = WorkManager.getInstance(ctx)
    val c = Db.get(ctx).dao().counter(id)
    val delay = c?.takeIf { !it.counter.archived }?.let { notifyDelay(it.lastAt, it.counter.notifyAfterMs, System.currentTimeMillis()) }
    if (delay == null) wm.cancelUniqueWork("notify-$id") else remindIn(ctx, id, delay)
    updateWidgets(ctx) // every change goes through here
}

fun remindIn(ctx: Context, id: Long, delay: Long) {
    WorkManager.getInstance(ctx).enqueueUniqueWork("notify-$id", ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<NotifyWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("id" to id))
            .build())
}

class NotifyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong("id", 0)
        val c = Db.get(applicationContext).dao().counter(id) ?: return Result.success()
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("reminders", applicationContext.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(applicationContext, id.toInt(),
            Intent(applicationContext, MainActivity::class.java).putExtra("id", id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(applicationContext, "reminders")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(c.counter.label)
            .setContentText(applicationContext.getString(R.string.its_been, since(System.currentTimeMillis() - c.lastAt)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, applicationContext.getString(R.string.reset_now), action(applicationContext, id, "reset"))
            .addAction(0, applicationContext.getString(R.string.snooze), action(applicationContext, id, "snooze"))
            .build()
        try { NotificationManagerCompat.from(applicationContext).notify(id.toInt(), n) } catch (_: SecurityException) {} // permission denied
        return Result.success()
    }
}

fun action(ctx: Context, id: Long, what: String) = PendingIntent.getBroadcast(ctx, id.toInt() * 2 + (if (what == "reset") 0 else 1),
    Intent(ctx, ReminderAction::class.java).setAction(what).putExtra("id", id),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

/** Notification buttons: reset the counter, or remind again in a day. */
class ReminderAction : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra("id", 0)
        NotificationManagerCompat.from(ctx).cancel(id.toInt())
        val done = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent.action == "reset") { Db.get(ctx).dao().reset(id); reschedule(ctx, id); syncSoon(ctx) }
                else remindIn(ctx, id, TimeUnit.DAYS.toMillis(1))
            } finally { done.finish() }
        }
    }
}
