package com.plane.cube.tracking

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
    private val notifier: PlaneNotifier,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val preferences = trackingRepository.observePreferences().first() ?: return Result.success()
        return runCatching {
            val planes = planeRepository.fetchPlanes(preferences.area)
            val inCube = planes.filter { plane ->
                val altitude = plane.altitudeMeters
                altitude != null && altitude <= preferences.maxAltitudeMeters
            }
            if (inCube.isNotEmpty()) notifier.notifyPlanes(inCube)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        const val UNIQUE_NAME = "plane_check_worker"
    }
}
