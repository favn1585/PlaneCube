package com.plane.cube.tracking

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.plane.cube.domain.PlaneAlerts
import com.plane.cube.domain.TrackingScheduler
import com.plane.cube.domain.repository.PlaneRepository
import com.plane.cube.domain.repository.TrackingPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Watches the saved tracking area while the app is closed: polls the feed
 * every [POLL_INTERVAL_MS] and hands each snapshot to [PlaneAlerts], which
 * notifies when a plane enters the box.
 *
 * Runs as a foreground service (with its ongoing notification) because
 * nothing else can poll this often in the background — WorkManager's floor
 * is 15 minutes, far longer than a plane spends crossing a small area.
 * Stops itself when the tracking area is cleared.
 */
@AndroidEntryPoint
class AreaMonitorService : Service() {

    @Inject lateinit var planeRepository: PlaneRepository
    @Inject lateinit var trackingRepository: TrackingPreferencesRepository
    @Inject lateinit var planeAlerts: PlaneAlerts
    @Inject lateinit var notifier: PlaneNotifier
    @Inject lateinit var scheduler: TrackingScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_END_TRACKING) {
            endTracking()
            return START_NOT_STICKY
        }
        // Must be called promptly after every startForegroundService(). For
        // ACTION_REPOST this is also what brings a swiped-away notification back.
        ServiceCompat.startForeground(
            this,
            PlaneNotifier.MONITOR_NOTIFICATION_ID,
            notifier.monitoringNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        if (!started) {
            started = true
            scope.launch { monitor() }
        }
        // If the system kills the service, restart it; monitor() re-reads the area.
        return START_STICKY
    }

    private suspend fun monitor() {
        trackingRepository.observePreferences().collectLatest { preferences ->
            if (preferences == null) {
                Log.d(TAG, "Tracking area cleared; stopping")
                stopSelf()
                return@collectLatest
            }
            val area = preferences.area
            while (true) {
                try {
                    val planes = planeRepository.fetchPlanes(area.center, area.radiusNm)
                    planeAlerts.onPlanesUpdated(preferences, planes)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    // Offline or feed hiccup: keep watching, try again next tick.
                    Log.w(TAG, "Area check failed", error)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * "End tracking" from the notification: forget the area, which stops the
     * periodic check and this service. Nothing restarts until the user saves a
     * new area.
     */
    private fun endTracking() {
        scope.launch {
            trackingRepository.clear()
            scheduler.cancel()
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AreaMonitorService"
        private const val POLL_INTERVAL_MS = 5_000L
        private const val ACTION_END_TRACKING = "com.plane.cube.action.END_TRACKING"
        private const val ACTION_REPOST = "com.plane.cube.action.REPOST_NOTIFICATION"

        fun endTrackingIntent(context: Context): PendingIntent = servicePendingIntent(
            context,
            ACTION_END_TRACKING,
            requestCode = 1,
        )

        fun repostNotificationIntent(context: Context): PendingIntent = servicePendingIntent(
            context,
            ACTION_REPOST,
            requestCode = 2,
        )

        // Plain getService (not getForegroundService): the service is already
        // running in the foreground whenever its notification is on screen.
        private fun servicePendingIntent(context: Context, action: String, requestCode: Int) =
            PendingIntent.getService(
                context,
                requestCode,
                Intent(context, AreaMonitorService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        /**
         * Starts (or keeps running) the monitor. Android only allows this
         * while the app is in the foreground or from a boot broadcast, so a
         * refusal is logged rather than thrown.
         */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AreaMonitorService::class.java),
                )
            } catch (error: IllegalStateException) {
                Log.w(TAG, "Not allowed to start area monitoring right now", error)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AreaMonitorService::class.java))
        }
    }
}
