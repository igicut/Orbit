package com.example.orbit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.orbit.data.local.CurrentUser
import com.example.orbit.data.notification.EXTRA_EVENT_ID
import com.example.orbit.ui.screens.AuthScreen
import com.example.orbit.ui.components.requestScannerModule
import com.example.orbit.ui.theme.OrbitTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var currentUser: CurrentUser

    /** F-26: id dogadjaja iz kliknutog obavestenja */
    private var pendingEventId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // F-41: skener je zaseban modul Play services-a; trazi se odmah, da ne fali na ulazu
        requestScannerModule(this)

        pendingEventId = intent?.getStringExtra(EXTRA_EVENT_ID)

        setContent {
            OrbitTheme {
                val isLoggedIn by currentUser.isLoggedIn.collectAsStateWithLifecycle()

                // F-13: bez naloga se vidi samo prijava
                if (isLoggedIn) {
                    OrbitApp(
                        pendingEventId = pendingEventId,
                        // Brise se posle navigacije da se skok ne ponovi
                        onPendingEventHandled = { pendingEventId = null },
                    )
                } else {
                    AuthScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Da getIntent() vrati novi intent
        setIntent(intent)
        pendingEventId = intent.getStringExtra(EXTRA_EVENT_ID)
    }
}
