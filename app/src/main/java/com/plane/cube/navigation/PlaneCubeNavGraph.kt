package com.plane.cube.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import com.plane.cube.features.area.areaDestination
import com.plane.cube.features.map.mapDestination

fun NavGraphBuilder.planeCubeNavGraph(navController: NavController) {
    mapDestination(onOpenAreaSelection = {
        navController.navigate(NavigationCommand.AreaSelection.path())
    })
    areaDestination(onSaved = { navController.popBackStack() })
}
