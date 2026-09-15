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

/** Ruta -> ekran; ekrani dobijaju lambde, ne NavController */
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

        // F-12: isti ekran u rezimu izmene
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
            // Id cita EventDetailViewModel iz SavedStateHandle
            EventDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(OrbitDestinations.editEvent(id)) },
            )
        }
    }
}
