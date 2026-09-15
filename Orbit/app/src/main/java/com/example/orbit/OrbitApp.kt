package com.example.orbit

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.orbit.ui.navigation.OrbitBottomBar
import com.example.orbit.ui.navigation.OrbitDestinations
import com.example.orbit.ui.navigation.OrbitNavHost

/** Okvir aplikacije: donja navigacija i NavHost */
@Composable
fun OrbitApp(
    pendingEventId: String? = null,
    onPendingEventHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // F-26: klik na podsetnik otvara njegov dogadjaj
    LaunchedEffect(pendingEventId) {
        pendingEventId?.let { id ->
            navController.navigate(OrbitDestinations.eventDetail(id))
            onPendingEventHandled()
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in OrbitDestinations.bottomBarRoutes) {
                OrbitBottomBar(
                    currentRoute = currentRoute,
                    onTabSelected = { tab ->
                        navController.navigate(tab.route) {
                            // Tabovi se ne gomilaju i pamte svoje stanje
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        OrbitNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
