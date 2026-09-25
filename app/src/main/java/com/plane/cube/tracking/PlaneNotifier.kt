package com.plane.cube.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.media.AudioAttributes
import android.net.Uri
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.plane.cube.R
import com.plane.cube.domain.entity.Plane
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaneNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(MONITOR_CHANNEL_ID) == null) {
            // Low importance: the ongoing "watching" notice should sit quietly
            // in the shade, not buzz. Only real alerts use the loud channel.
            manager.createNotificationChannel(
                NotificationChannel(
                    MONITOR_CHANNEL_ID,
                    "Area monitoring",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Shown while PlaneCube watches your tracking area in the background"
                },
            )
        }
        // A channel's sound can't change once created, so each new alert sound
        // gets a new channel and the earlier ones are removed.
        LEGACY_CHANNEL_IDS.forEach(manager::deleteNotificationChannel)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Plane alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alerts when a plane enters your tracking area"
            enableLights(true)
            enableVibration(true)
            setSound(
                Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.plane_alert}"),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        manager.createNotificationChannel(channel)
    }

    fun notifyPlanes(planes: List<Plane>) {
        if (planes.isEmpty()) return
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }
        ensureChannel()

        val first = planes.first()
        val title = "Plane in your area"
        val text = buildString {
            append(first.callsign ?: first.icao24)
            first.typeDesignator?.let { append(" · $it") }
            first.altitudeMeters?.let { append(" · ${it.toInt()} m") }
            if (planes.size > 1) append(" (+${planes.size - 1} more)")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(openAppIntent())
            .build()
            .apply {
                // Loop the sound until the user opens or dismisses the alert
                // (or pulls down the shade).
                flags = flags or Notification.FLAG_INSISTENT
            }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /** The ongoing notification a foreground service must show while it runs. */
    fun monitoringNotification(): Notification {
        ensureChannel()
        return NotificationCompat.Builder(context, MONITOR_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Watching your area")
            .setContentText("You'll be alerted when a plane enters it")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent())
            .addAction(0, "End tracking", AreaMonitorService.endTrackingIntent(context))
            // Android 14+ lets users swipe away even ongoing service
            // notifications; if that happens, put it straight back.
            .setDeleteIntent(AreaMonitorService.repostNotificationIntent(context))
            .build()
    }

    private fun openAppIntent(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "plane_alerts_v3"
        private val LEGACY_CHANNEL_IDS = listOf("plane_alerts", "plane_alerts_v2")
        const val MONITOR_CHANNEL_ID = "area_monitoring"
        const val MONITOR_NOTIFICATION_ID = 1002
        private const val NOTIFICATION_ID = 1001
    }
}
