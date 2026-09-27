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
    private val cipher = TokenCipher()

    /** Desifrovan token u memoriji; Keystore se ne pita pri svakom zahtevu */
    @Volatile
    private var cachedToken: String? = readToken()

    /** null kad niko nije prijavljen */
    val token: String?
        get() = cachedToken

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
            // Na disk ide samo sifrat
            putString(KEY_TOKEN, cipher.encrypt(token))
            putBoolean(KEY_REGISTERED, true)
        }
        cachedToken = token
        _isLoggedIn.value = true
    }

    /** I za 401 i za odjavu: token i prikazani podsetnici; lokalne podatke brise AuthRepository */
    fun endSession() {
        prefs.edit {
            remove(KEY_TOKEN)
            remove(KEY_PLAIN_TOKEN)
        }
        cachedToken = null
        notifier.cancelAll()
        _isLoggedIn.value = false
    }

    /** Token sa diska; null ako ga nema ili ne moze da se desifruje */
    private fun readToken(): String? {
        // Ranija verzija je cuvala token kao obican tekst; sifruje se jednom, a obican se brise
        val plain = prefs.getString(KEY_PLAIN_TOKEN, null)
        if (plain != null) {
            prefs.edit {
                putString(KEY_TOKEN, cipher.encrypt(plain))
                remove(KEY_PLAIN_TOKEN)
            }
            return plain
        }
        return prefs.getString(KEY_TOKEN, null)?.let { cipher.decrypt(it) }
    }

    private companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_TOKEN = "token_encrypted"
        /** Kljuc pod kojim je ranija verzija cuvala token bez sifrovanja */
        const val KEY_PLAIN_TOKEN = "token"
        const val KEY_REGISTERED = "registered"
    }
}
