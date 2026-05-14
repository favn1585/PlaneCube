package com.plane.cube.features.map

import androidx.navigation.NavGraphBuilder
import com.plane.cube.navigation.NavigationCommand

fun NavGraphBuilder.mapDestination() {
    NavigationCommand.Map.configure(this) {
        MapScreen()
    }
}
