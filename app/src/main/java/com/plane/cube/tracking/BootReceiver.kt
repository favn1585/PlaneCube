package com.plane.cube.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.plane.cube.domain.repository.TrackingPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Resumes area monitoring after a reboot if a tracking area is saved. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var trackingRepository: TrackingPreferencesRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (trackingRepository.observePreferences().first() != null) {
                    AreaMonitorService.start(context.applicationContext)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
