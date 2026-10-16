package com.example.shorts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Системные уведомления о новых событиях («Входящие»). */
object Notifier {
    private const val CHANNEL = "shorts_activity"
    private const val WORK = "shorts_notice_check"

    fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Активность", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Лайки, комментарии, подписчики и репосты"
            }
        )
    }

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun show(ctx: Context, id: Int, title: String, text: String) {
        if (!canPost(ctx)) return
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(0xFFFF0050.toInt())
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    /** Фоновая проверка раз в 15 минут (минимум, который разрешает Android). */
    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<NoticeWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}

class NoticeWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val settings = ctx.getSharedPreferences("shorts_settings", Context.MODE_PRIVATE)
        if (!settings.getBoolean("pushEnabled", true)) return Result.success()
        if (!Notifier.canPost(ctx)) return Result.success()

        val api = Api(ctx)
        val uid = api.userId ?: return Result.success()
        if (api.accessToken.isNullOrEmpty() && api.refreshToken.isNullOrEmpty()) return Result.success()

        return try {
            val rows = api.select(
                "notifications",
                "select" to "*,profiles:actor_id(username,display_name,avatar_url)",
                "user_id" to "eq.$uid", "is_read" to "eq.false",
                "order" to "created_at.desc", "limit" to "20"
            )
            val seenPrefs = ctx.getSharedPreferences("shorts_push", Context.MODE_PRIVATE)
            val seen = seenPrefs.getStringSet("seen", emptySet())!!.toMutableSet()
            val fresh = mutableListOf<Pair<String, String>>()
            for (i in 0 until rows.length()) {
                val o = rows.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isEmpty() || id in seen) continue
                val allowed = when (o.optString("type")) {
                    "like" -> settings.getBoolean("notifLikes", true)
                    "comment" -> settings.getBoolean("notifComments", true)
                    "follow" -> settings.getBoolean("notifFollows", true)
                    "repost" -> settings.getBoolean("notifReposts", true)
                    else -> true
                }
                seen.add(id)
                if (!allowed) continue
                val p = o.optJSONObject("profiles")
                val name = p?.optString("display_name", "")?.takeIf { it.isNotBlank() && it != "null" }
                    ?: p?.optString("username", "")?.takeIf { it.isNotBlank() && it != "null" }
                    ?: "Кто-то"
                fresh.add(name to o.optString("message"))
            }
            // Хвост запоминаем ограниченным (чтобы список не рос бесконечно).
            seenPrefs.edit().putStringSet("seen", seen.toList().takeLast(200).toSet()).apply()

            when {
                fresh.isEmpty() -> {}
                fresh.size == 1 -> Notifier.show(ctx, 1001, "Shorts", fresh[0].first + " " + fresh[0].second)
                else -> Notifier.show(
                    ctx, 1001, "Новые уведомления: ${fresh.size}",
                    fresh.take(3).joinToString("\n") { it.first + " " + it.second }
                )
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
