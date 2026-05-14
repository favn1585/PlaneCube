package com.plane.cube.features.area

import androidx.navigation.NavGraphBuilder
import com.plane.cube.navigation.NavigationCommand

fun NavGraphBuilder.areaDestination(onSaved: () -> Unit) {
    NavigationCommand.AreaSelection.configure(this) {
        AreaScreen(onSaved = onSaved)
    }
}
