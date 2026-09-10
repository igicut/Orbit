package com.example.orbit.ui.common

import androidx.annotation.StringRes

/**
 * F-03 - the state every screen can be in.
 *
 * Error carries a string RESOURCE, not a String. An exception's message is
 * developer-facing English from the framework; showing it to a user is neither
 * translatable nor understandable. The ViewModel decides which message applies
 * and the screen resolves it in the current locale.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(@StringRes val messageRes: Int) : UiState<Nothing>
}
