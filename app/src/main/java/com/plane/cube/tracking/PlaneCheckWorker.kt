package com.plane.cube.tracking

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.plane.cube.domain.PlaneAlerts
import com.plane.cube.domain.repository.PlaneRepository
import com.plane.cube.domain.repository.TrackingPreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class PlaneCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val planeRepository: PlaneRepository,
    private val trackingRepository: TrackingPreferencesRepository,
    private val planeAlerts: PlaneAlerts,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val preferences = trackingRepository.observePreferences().first() ?: return Result.success()
        return runCatching {
            // Shares PlaneAlerts with the foreground monitor, so a plane that
            // one of them already announced isn't announced twice.
            val area = preferences.area
            val planes = planeRepository.fetchPlanes(area.center, area.radiusNm)
            planeAlerts.onPlanesUpdated(preferences, planes)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        const val UNIQUE_NAME = "plane_check_worker"
    }
}
