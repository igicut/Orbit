package com.example.orbit.data.notification

import android.annotation.SuppressLint
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

/** F-26: pravljenje i slanje obavestenja (slajd 28) */
@Singleton
class EventNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /** Kanali na jednom mestu, ponovno pravljenje je bezbedno */
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
                // LOW: bez zvuka za obavestenje servisa
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    /** Stalno obavestenje koje foreground servis mora da ima */
    fun buildServiceNotification(): Notification =
        NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_service_title))
            .setContentText(context.getString(R.string.notification_service_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()

    /** Jedan podsetnik; false ako nema POST_NOTIFICATIONS dozvole */
    @SuppressLint("MissingPermission") // provereno u redu ispod
    fun notifyEventSoon(eventId: String, title: String, minutesUntil: Long): Boolean {
        if (!hasPermission()) return false

        // Otvara aplikaciju; FLAG_IMMUTABLE obavezan od Android 12
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

        // Isti id po dogadjaju, novi podsetnik zamenjuje stari
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
