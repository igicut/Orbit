package com.example.orbit.data.local

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton


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

    private companion object {
        const val KEY_USER_ID = "user_id"
    }
}
