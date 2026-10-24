package com.bruma.app.download

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import com.bruma.app.R
import com.bruma.app.data.db.DownloadEntity
import com.bruma.app.ui.MainActivity

object Notifications {
    const val CH_PROGRESS = "downloads_progress"
    const val CH_DONE = "downloads_done"
    const val CH_PROBLEM = "downloads_problem"
    const val EXTRA_ROUTE = "route"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_PROGRESS, "Descargas en curso", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Progreso de las descargas"
                    setShowBadge(false)
                },
                NotificationChannel(CH_DONE, "Descargas terminadas", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Aviso cuando un archivo queda listo"
                },
                NotificationChannel(CH_PROBLEM, "Problemas", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Errores que necesitan tu atención, como falta de espacio"
                },
            ),
        )
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(
        context: Context,
        route: String,
        code: Int,
    ): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_ROUTE, route)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun action(
        context: Context,
        action: String,
        id: Long,
    ): PendingIntent {
        val intent = Intent(context, DownloadActionReceiver::class.java).setAction(action).putExtra("id", id)
        val code = (id * 10 + if (action == DownloadActionReceiver.ACTION_PAUSE) 1 else 2).toInt()
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun progressId(id: Long) = (1000 + id).toInt()

    fun foregroundInfo(
        context: Context,
        d: DownloadEntity,
        fraction: Float,
        label: String,
    ): ForegroundInfo {
        val pct = (fraction * 100).toInt().coerceIn(0, 100)
        val n =
            NotificationCompat
                .Builder(context, CH_PROGRESS)
                .setSmallIcon(R.drawable.ic_stat_bruma)
                .setContentTitle(d.title)
                .setContentText(label)
                .setProgress(100, pct, fraction <= 0f)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setGroup("bruma_downloads")
                .setContentIntent(openApp(context, "downloads", 1))
                .addAction(0, "Pausar", action(context, DownloadActionReceiver.ACTION_PAUSE, d.id))
                .addAction(0, "Cancelar", action(context, DownloadActionReceiver.ACTION_CANCEL, d.id))
                .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(progressId(d.id), n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(progressId(d.id), n)
        }
    }

    @SuppressLint("MissingPermission")
    fun done(
        context: Context,
        id: Long,
        title: String,
    ) {
        if (!canNotify(context)) return
        val n =
            NotificationCompat
                .Builder(context, CH_DONE)
                .setSmallIcon(R.drawable.ic_stat_bruma)
                .setContentTitle("Listo")
                .setContentText(title)
                .setAutoCancel(true)
                .setGroup("bruma_done")
                .setContentIntent(openApp(context, "library", 2))
                .build()
        NotificationManagerCompat.from(context).notify((5000 + id).toInt(), n)
    }

    @SuppressLint("MissingPermission")
    fun problem(
        context: Context,
        id: Long,
        title: String,
        text: String,
    ) {
        if (!canNotify(context)) return
        val n =
            NotificationCompat
                .Builder(context, CH_PROBLEM)
                .setSmallIcon(R.drawable.ic_stat_bruma)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(openApp(context, "downloads", 3))
                .build()
        NotificationManagerCompat.from(context).notify((9000 + id).toInt(), n)
    }
}
