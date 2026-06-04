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
            // The repo no longer filters by area (the map wants every plane),
            // so the worker has to do its own in-polygon + altitude check.
            val planes = planeRepository.fetchPlanes()
            val inCube = planes.filter { plane ->
                val altitude = plane.altitudeMeters
                altitude != null &&
                    altitude <= preferences.maxAltitudeMeters &&
                    preferences.area.contains(plane.position)
            }
            if (inCube.isNotEmpty()) notifier.notifyPlanes(inCube)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        const val UNIQUE_NAME = "plane_check_worker"
    }
}
