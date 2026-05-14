package com.plane.cube.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

sealed class NavigationCommand<T>(
    private val destination: String,
    private val serializer: KSerializer<T>,
) {

    fun path(argument: T? = null): String =
        buildString {
            append(destination)
            if (this@NavigationCommand !is NoArgNavigationCommand) {
                append("/")
                append(argument?.let { encodeArgument(it) } ?: "{$ARG_KEY}")
            }
        }

    private fun encodeArgument(argument: T): String =
        URLEncoder.encode(
            NavigatorJson.encodeToString(serializer, argument),
            Charsets.UTF_8.name(),
        )

    fun configure(builder: NavGraphBuilder, ui: @Composable () -> Unit) {
        builder.composable(route = path()) { ui() }
    }

    fun configure(
        builder: NavGraphBuilder,
        argumentSerializer: KSerializer<T>,
        ui: @Composable (T) -> Unit,
    ) {
        builder.composable(route = path()) { entry ->
            val raw = requireNotNull(entry.arguments?.getString(ARG_KEY))
            val decoded = URLDecoder.decode(raw, Charsets.UTF_8.name())
            val argument = NavigatorJson.decodeFromString(argumentSerializer, decoded)
            ui(argument)
        }
    }

    abstract class NoArgNavigationCommand(destination: String) :
        NavigationCommand<Unit>(destination, Unit.serializer())

    object Map : NoArgNavigationCommand("map")
    object AreaSelection : NoArgNavigationCommand("area-selection")

    companion object {
        private const val ARG_KEY = "arg"
        private val NavigatorJson = Json { isLenient = true }
    }
}
