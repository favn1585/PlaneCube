package com.plane.cube.features.map

import androidx.navigation.NavGraphBuilder
import com.plane.cube.navigation.NavigationCommand

fun NavGraphBuilder.mapDestination(onOpenAreaSelection: () -> Unit) {
    NavigationCommand.Map.configure(this) {
        MapScreen(onOpenAreaSelection = onOpenAreaSelection)
    }
}
