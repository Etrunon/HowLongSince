package eu.etrunon.howlongsince

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

/** Ms until the reminder is due, or null if none / already past. */
fun notifyDelay(lastAt: Long, afterMs: Long?, now: Long): Long? =
    afterMs?.let { lastAt + it - now }?.takeIf { it >= 0 }

/** Call after any create/reset/edit/delete: replaces the counter's pending reminder. */
suspend fun reschedule(ctx: Context, id: Long) {
    val wm = WorkManager.getInstance(ctx)
    val c = Db.get(ctx).dao().counter(id)
    val delay = c?.let { notifyDelay(it.lastAt, it.counter.notifyAfterMs, System.currentTimeMillis()) }
    if (delay == null) { wm.cancelUniqueWork("notify-$id"); return }
    wm.enqueueUniqueWork("notify-$id", ExistingWorkPolicy.REPLACE,
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
        nm.createNotificationChannel(NotificationChannel("reminders", "Reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(applicationContext, id.toInt(),
            Intent(applicationContext, MainActivity::class.java).putExtra("id", id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(applicationContext, "reminders")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(c.counter.label)
            .setContentText("It's been ${since(System.currentTimeMillis() - c.lastAt)}")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(applicationContext).notify(id.toInt(), n) } catch (_: SecurityException) {} // permission denied
        return Result.success()
    }
}
