package com.example.orbit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.orbit.data.notification.EXTRA_EVENT_ID
import com.example.orbit.ui.theme.OrbitTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** F-26: id dogadjaja iz kliknutog obavestenja */
    private var pendingEventId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingEventId = intent?.getStringExtra(EXTRA_EVENT_ID)

        setContent {
            OrbitTheme {
                OrbitApp(
                    pendingEventId = pendingEventId,
                    // Brise se posle navigacije da se skok ne ponovi
                    onPendingEventHandled = { pendingEventId = null },
                )
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
