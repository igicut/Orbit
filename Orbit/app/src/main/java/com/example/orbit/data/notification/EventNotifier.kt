package com.example.orbit.data.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.orbit.MainActivity
import com.example.orbit.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

const val REMINDER_CHANNEL_ID = "orbit_reminders"
const val SERVICE_CHANNEL_ID = "orbit_service"
const val EXTRA_EVENT_ID = "eventId"

/**
 * F-26 - building and posting notifications.
 *
 * The channel creation and NotificationCompat.Builder usage follow slide 28 of
 * the course material. Two channels rather than one, because they are different
 * kinds of message: a reminder should make a sound, while the notice that a
 * service is running should be silent - it exists only because the system
 * requires a foreground service to show one.
 */
@Singleton
class EventNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /**
     * Slide 28 creates the channel inside onStartCommand. Doing it here instead
     * means every entry point gets the same channels, and re-creating a channel
     * that already exists is a no-op, so calling it repeatedly is safe.
     */
    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                REMINDER_CHANNEL_ID,
                context.getString(R.string.notification_channel_reminders),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_reminders_desc)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                SERVICE_CHANNEL_ID,
                context.getString(R.string.notification_channel_service),
                // LOW: no sound. The user did not ask to be told a check is running.
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    /** The permanent notice a foreground service is required to display. */
    fun buildServiceNotification(): Notification =
        NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_service_title))
            .setContentText(context.getString(R.string.notification_service_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()

    /**
     * One reminder for one event.
     *
     * Returns false when the user has not granted POST_NOTIFICATIONS, which is
     * required from Android 13 and is not covered by the material at all - it
     * post-dates it. Posting without it throws nothing and does nothing, so the
     * check has to be explicit.
     */
    fun notifyEventSoon(eventId: String, title: String, minutesUntil: Long): Boolean {
        if (!hasPermission()) return false

        // Opens the app when tapped. FLAG_IMMUTABLE is mandatory from Android 12
        // and the material predates it.
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_EVENT_ID, eventId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(
                context.getString(R.string.notification_event_soon, minutesUntil)
            )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // A stable id per event, so a second reminder replaces the first rather
        // than stacking duplicates.
        NotificationManagerCompat.from(context).notify(eventId.hashCode(), notification)
        return true
    }

    fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
