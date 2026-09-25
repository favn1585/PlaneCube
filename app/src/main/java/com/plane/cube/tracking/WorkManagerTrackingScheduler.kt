package com.plane.cube.tracking

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.plane.cube.domain.TrackingScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerTrackingScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : TrackingScheduler {

    /**
     * Starts the foreground [AreaMonitorService] for fast alerts, plus a
     * 15-minute WorkManager check as a fallback in case the service is ever
     * stopped (e.g. Android refused to start it from the background).
     */
    override fun schedule() {
        AreaMonitorService.start(context)
        val request = PeriodicWorkRequestBuilder<PlaneCheckWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PlaneCheckWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    override fun cancel() {
        AreaMonitorService.stop(context)
        WorkManager.getInstance(context).cancelUniqueWork(PlaneCheckWorker.UNIQUE_NAME)
    }
}
