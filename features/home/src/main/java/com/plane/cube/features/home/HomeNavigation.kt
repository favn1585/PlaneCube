package com.plane.cube.features.home

import androidx.navigation.NavGraphBuilder
import com.plane.cube.navigation.NavigationCommand

fun NavGraphBuilder.homeDestination() {
    NavigationCommand.Home.configure(this) { HomeScreen() }
}
