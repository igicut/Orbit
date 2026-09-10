package com.example.orbit.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.orbit.ui.screens.AccountScreen
import com.example.orbit.ui.screens.CreateEventScreen
import com.example.orbit.ui.screens.EventDetailScreen
import com.example.orbit.ui.screens.MapScreen
import com.example.orbit.ui.screens.SearchScreen

/**
 * Route name -> screen.
 *
 * Screens never receive the NavController. They get plain lambdas such as
 * onEventClick: (String) -> Unit, which keeps each one previewable and testable
 * on its own.
 */
@Composable
fun OrbitNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = OrbitDestinations.SEARCH,
        modifier = modifier,
    ) {

        composable(OrbitDestinations.SEARCH) {
            SearchScreen(
                onEventClick = { id -> navController.navigate(OrbitDestinations.eventDetail(id)) },
            )
        }

        composable(OrbitDestinations.MAP) {
            MapScreen(
                onEventClick = { id -> navController.navigate(OrbitDestinations.eventDetail(id)) },
            )
        }

        composable(OrbitDestinations.ACCOUNT) {
            AccountScreen(
                onEventClick = { id -> navController.navigate(OrbitDestinations.eventDetail(id)) },
                onCreateClick = { navController.navigate(OrbitDestinations.CREATE_EVENT) },
            )
        }

        composable(OrbitDestinations.CREATE_EVENT) {
            CreateEventScreen(
                onSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }

        // F-12 - the same screen in edit mode. CreateEventViewModel reads the id
        // from SavedStateHandle and loads the event; with no id it creates one.
        composable(
            route = OrbitDestinations.EDIT_EVENT,
            arguments = listOf(
                navArgument(OrbitDestinations.EVENT_ID_ARG) { type = NavType.StringType }
            ),
        ) {
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
            // The id is not read here - EventDetailViewModel pulls it out of
            // SavedStateHandle, which Hilt supplies automatically.
            EventDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(OrbitDestinations.editEvent(id)) },
            )
        }
    }
}
