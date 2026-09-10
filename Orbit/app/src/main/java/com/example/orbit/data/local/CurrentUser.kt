package com.example.orbit.data.local

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F-13 (client half) - "who am I?".
 *
 * There is no login. On first run a random UUID is generated and kept in
 * SharedPreferences; every event created here is stamped with it, and it is what
 * the app sends in the X-User-Id header.
 *
 * The display name exists so other devices can show "Organised by <name>" rather
 * than a UUID. Nothing in the app asks for one yet, so a readable placeholder is
 * derived from the id - see DEFAULT_NAME_PREFIX.
 */
@Singleton
class CurrentUser @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("orbit_user", Context.MODE_PRIVATE)

    val id: String
        get() {
            val existing = prefs.getString(KEY_USER_ID, null)
            if (existing != null) return existing

            val generated = UUID.randomUUID().toString()
            prefs.edit { putString(KEY_USER_ID, generated) }
            return generated
        }

    /**
     * Placeholder until there is a profile screen: "User a3f9c1".
     *
     * Short enough to read aloud, and derived from the id so two devices never
     * collide. Deliberately not translated - it is stored on the server and
     * shown to other people, whose language is not known here.
     */
    var displayName: String
        get() {
            val existing = prefs.getString(KEY_DISPLAY_NAME, null)
            if (existing != null) return existing

            val generated = DEFAULT_NAME_PREFIX + id.take(6)
            prefs.edit { putString(KEY_DISPLAY_NAME, generated) }
            return generated
        }
        set(value) {
            prefs.edit {
                putString(KEY_DISPLAY_NAME, value)
                // The server holds a stale name now, so register again.
                putBoolean(KEY_REGISTERED, false)
            }
        }

    /** Whether the server has this device's profile. */
    var isRegistered: Boolean
        get() = prefs.getBoolean(KEY_REGISTERED, false)
        set(value) = prefs.edit { putBoolean(KEY_REGISTERED, value) }

    private companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_REGISTERED = "registered"
        const val DEFAULT_NAME_PREFIX = "User "
    }
}
