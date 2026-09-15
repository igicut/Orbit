package com.example.orbit.data.local

import android.content.Context
import androidx.core.content.edit
import com.example.orbit.data.notification.EventNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** F-13: prijavljeni korisnik i njegov JWT token */
@Singleton
class CurrentUser @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notifier: EventNotifier,
) {
    private val prefs = context.getSharedPreferences("orbit_user", Context.MODE_PRIVATE)

    /** null kad niko nije prijavljen */
    val token: String?
        get() = prefs.getString(KEY_TOKEN, null)

    /** MainActivity po ovome bira prijavu ili aplikaciju */
    private val _isLoggedIn = MutableStateFlow(token != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    /** Ostaje posle odjave, da se prepozna drugi nalog */
    val id: String
        get() = prefs.getString(KEY_USER_ID, null).orEmpty()

    val email: String
        get() = prefs.getString(KEY_EMAIL, null).orEmpty()

    var displayName: String
        get() = prefs.getString(KEY_DISPLAY_NAME, null).orEmpty()
        set(value) {
            prefs.edit {
                putString(KEY_DISPLAY_NAME, value)
                // Server ima staro ime, registruj ponovo
                putBoolean(KEY_REGISTERED, false)
            }
        }

    /** Da li server ima poslednje ime */
    var isRegistered: Boolean
        get() = prefs.getBoolean(KEY_REGISTERED, false)
        set(value) = prefs.edit { putBoolean(KEY_REGISTERED, value) }

    fun startSession(userId: String, email: String, displayName: String, token: String) {
        prefs.edit {
            putString(KEY_USER_ID, userId)
            putString(KEY_EMAIL, email)
            putString(KEY_DISPLAY_NAME, displayName)
            putString(KEY_TOKEN, token)
            putBoolean(KEY_REGISTERED, true)
        }
        _isLoggedIn.value = true
    }

    /** I za 401 i za odjavu: token i prikazani podsetnici; lokalne podatke brise AuthRepository */
    fun endSession() {
        prefs.edit { remove(KEY_TOKEN) }
        notifier.cancelAll()
        _isLoggedIn.value = false
    }

    private companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_TOKEN = "token"
        const val KEY_REGISTERED = "registered"
    }
}
