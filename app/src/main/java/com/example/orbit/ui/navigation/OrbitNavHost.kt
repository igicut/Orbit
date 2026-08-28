package com.example.orbit.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.orbit.ui.screens.CreateEventScreen
import com.example.orbit.ui.screens.EventDetailScreen
import com.example.orbit.ui.screens.EventListScreen
import com.example.orbit.ui.screens.MapScreen

/**
 * F-01 - the app's single map of "route name -> screen".
 *
 * Screens never receive the NavController itself. They get plain lambdas like
 * `onEventClick: (String) -> Unit`. That keeps each screen independent of navigation,
 * so it can be previewed and tested on its own.
 */
@Composable
fun OrbitNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = OrbitDestinations.EVENT_LIST,
    ) {

        composable(OrbitDestinations.EVENT_LIST) {
            EventListScreen(
                onEventClick = { eventId ->
                    navController.navigate(OrbitDestinations.eventDetail(eventId))
                },
                onCreateClick = { navController.navigate(OrbitDestinations.CREATE_EVENT) },
                onMapClick = { navController.navigate(OrbitDestinations.MAP) },
            )
        }

        composable(OrbitDestinations.CREATE_EVENT) {
            CreateEventScreen(
                onSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }

        composable(
            route = OrbitDestinations.EVENT_DETAIL,
            arguments = listOf(
                navArgument(OrbitDestinations.EVENT_ID_ARG) { type = NavType.StringType }
            ),
        ) {
            // We do not read the id here - EventDetailViewModel pulls it straight out of
            // SavedStateHandle, which Hilt hands it automatically.
            EventDetailScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(OrbitDestinations.MAP) {
            MapScreen(
                onEventClick = { eventId ->
                    navController.navigate(OrbitDestinations.eventDetail(eventId))
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
