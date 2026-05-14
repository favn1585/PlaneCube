package com.plane.cube.navigation

import androidx.navigation.NavGraphBuilder
import com.plane.cube.features.map.mapDestination

fun NavGraphBuilder.planeCubeNavGraph() {
    mapDestination()
}
