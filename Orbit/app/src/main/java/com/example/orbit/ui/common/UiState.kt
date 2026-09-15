package com.example.orbit.ui.common

import androidx.annotation.StringRes

/** F-03: stanja ekrana; Error nosi string resurs */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(@StringRes val messageRes: Int) : UiState<Nothing>
}
