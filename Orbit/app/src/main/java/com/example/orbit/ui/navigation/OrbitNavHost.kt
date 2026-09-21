package com.example.orbit.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.orbit.ui.screens.AccountScreen
import com.example.orbit.ui.screens.BlockedUsersScreen
import com.example.orbit.ui.screens.CreateEventScreen
import com.example.orbit.ui.screens.EventDetailScreen
import com.example.orbit.ui.screens.JoinedEventsScreen
import com.example.orbit.ui.screens.MapScreen
import com.example.orbit.ui.screens.MyEventsScreen
import com.example.orbit.ui.screens.ProfileScreen
import com.example.orbit.ui.screens.PlansScreen
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

        composable(OrbitDestinations.PLANS) {
            PlansScreen(
                onEventClick = { id -> navController.navigate(OrbitDestinations.eventDetail(id)) },
            )
        }

        composable(OrbitDestinations.ACCOUNT) {
            AccountScreen(
                onMyEventsClick = { navController.navigate(OrbitDestinations.MY_EVENTS) },
                onJoinedEventsClick = { navController.navigate(OrbitDestinations.JOINED_EVENTS) },
                onBlockedUsersClick = { navController.navigate(OrbitDestinations.BLOCKED_USERS) },
            )
        }

        composable(OrbitDestinations.MY_EVENTS) {
            MyEventsScreen(
                onBack = { navController.popBackStack() },
                onEventClick = { id -> navController.navigate(OrbitDestinations.eventDetail(id)) },
            )
        }

        composable(OrbitDestinations.JOINED_EVENTS) {
            JoinedEventsScreen(
                onBack = { navController.popBackStack() },
                onEventClick = { id -> navController.navigate(OrbitDestinations.eventDetail(id)) },
            )
        }

        composable(OrbitDestinations.BLOCKED_USERS) {
            BlockedUsersScreen(onBack = { navController.popBackStack() })
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
                onOrganiserClick = { id -> navController.navigate(OrbitDestinations.userProfile(id)) },
            )
        }

        composable(
            route = OrbitDestinations.USER_PROFILE,
            arguments = listOf(
                navArgument(OrbitDestinations.USER_ID_ARG) { type = NavType.StringType }
            ),
        ) {
            // Id cita ProfileViewModel iz SavedStateHandle
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onEventClick = { id -> navController.navigate(OrbitDestinations.eventDetail(id)) },
            )
        }
    }
}
