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

    /**
     * F-26 - the event a notification was tapped for, if any.
     *
     * The reminder always carried EXTRA_EVENT_ID but nothing ever read it, so
     * tapping a reminder opened the app on whatever screen it was last on
     * instead of the event it was telling you about.
     *
     * Held as Compose state rather than read once, because the activity may
     * already be running when a notification is tapped - in that case the id
     * arrives through onNewIntent, long after onCreate.
     */
    private var pendingEventId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingEventId = intent?.getStringExtra(EXTRA_EVENT_ID)

        setContent {
            OrbitTheme {
                OrbitApp(
                    pendingEventId = pendingEventId,
                    // Cleared once navigation has happened, so returning to the
                    // app later does not jump back to the same event.
                    onPendingEventHandled = { pendingEventId = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Keeps getIntent() consistent with what was just delivered.
        setIntent(intent)
        pendingEventId = intent.getStringExtra(EXTRA_EVENT_ID)
    }
}
