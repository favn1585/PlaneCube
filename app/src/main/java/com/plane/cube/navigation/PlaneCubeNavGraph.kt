package com.plane.cube.navigation

import androidx.navigation.NavGraphBuilder
import com.plane.cube.features.home.homeDestination

object PlaneCubeNavGraph : (NavGraphBuilder) -> Unit {
    override fun invoke(builder: NavGraphBuilder) {
        builder.homeDestination()
    }
}
