package com.plane.cube

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.plane.cube.navigation.NavigationCommand
import com.plane.cube.navigation.Navigator
import com.plane.cube.navigation.planeCubeNavGraph
import com.plane.cube.ui.theme.PlaneCubeTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var navigator: Navigator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlaneCubeTheme {
                val navController = rememberNavController()
                LaunchedEffect(navController) { navigator.bindNavController(navController) }

                NavHost(
                    navController = navController,
                    startDestination = NavigationCommand.Map.path(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    planeCubeNavGraph(navController)
                }
            }
        }
    }
}
