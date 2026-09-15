package com.example.orbit.data.local

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** F-13: identitet uredjaja, UUID bez logovanja */
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

    /** Privremeno ime iz id-a, npr. User a3f9c1 */
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
                // Server ima staro ime, registruj ponovo
                putBoolean(KEY_REGISTERED, false)
            }
        }

    /** Da li server ima profil ovog uredjaja */
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
