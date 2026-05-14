package com.plane.cube.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination

class Navigator {

    private var navController: NavController? = null

    fun bindNavController(navController: NavController) {
        this.navController = navController
    }

    fun navigate(
        command: NavigationCommand.NoArgNavigationCommand,
        popBackStack: Boolean = false,
        clearBackStack: Boolean = false,
    ) = navigateInternal(command, argument = null, popBackStack, clearBackStack)

    fun <T> navigate(
        command: NavigationCommand<T>,
        argument: T,
        popBackStack: Boolean = false,
        clearBackStack: Boolean = false,
    ) = navigateInternal(command, argument, popBackStack, clearBackStack)

    fun goBack() {
        navController?.popBackStack()
    }

    private fun <T> navigateInternal(
        command: NavigationCommand<T>,
        argument: T?,
        popBackStack: Boolean,
        clearBackStack: Boolean,
    ) {
        if (popBackStack) navController?.popBackStack()
        val controller = navController ?: return
        controller.navigate(route = command.path(argument)) {
            if (clearBackStack) {
                controller.graph.findStartDestination().id.let {
                    popUpTo(it) { inclusive = true }
                }
            }
        }
    }
}
