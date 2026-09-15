package com.example.orbit.ui.stateholders

import android.util.Patterns
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbit.R
import com.example.orbit.data.repository.AuthRepository
import com.example.orbit.data.repository.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthMode { LOG_IN, SIGN_UP }

data class AuthFormState(
    val mode: AuthMode = AuthMode.LOG_IN,
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",

    // Popunjava validate(); null = polje je ispravno
    @StringRes val displayNameError: Int? = null,
    @StringRes val emailError: Int? = null,
    @StringRes val passwordError: Int? = null,
    @StringRes val confirmPasswordError: Int? = null,

    /** Greska servera ili mreze, ispod polja */
    @StringRes val error: Int? = null,
    val isSubmitting: Boolean = false,
)

/** F-13: forma za prijavu i registraciju */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthFormState())
    val state: StateFlow<AuthFormState> = _state.asStateFlow()

    fun onDisplayNameChange(value: String) = _state.update {
        it.copy(displayName = value.take(MAX_NAME_LENGTH), displayNameError = null, error = null)
    }

    fun onEmailChange(value: String) =
        _state.update { it.copy(email = value, emailError = null, error = null) }

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, passwordError = null, error = null) }

    fun onConfirmPasswordChange(value: String) =
        _state.update { it.copy(confirmPassword = value, confirmPasswordError = null, error = null) }

    /** Prelaz izmedju prijave i registracije, email ostaje */
    fun toggleMode() = _state.update {
        AuthFormState(
            mode = if (it.mode == AuthMode.LOG_IN) AuthMode.SIGN_UP else AuthMode.LOG_IN,
            email = it.email,
        )
    }

    fun submit() {
        if (_state.value.isSubmitting || !validate()) return
        val form = _state.value

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }

            val result = when (form.mode) {
                AuthMode.LOG_IN -> repository.logIn(form.email.trim(), form.password)
                AuthMode.SIGN_UP -> repository.signUp(
                    displayName = form.displayName.trim(),
                    email = form.email.trim(),
                    password = form.password,
                )
            }

            _state.update {
                when (result) {
                    // Lozinka se brise iz forme, posle odjave je nema
                    AuthResult.Success -> AuthFormState(email = it.email)
                    AuthResult.WrongCredentials ->
                        it.copy(isSubmitting = false, error = R.string.auth_error_wrong_credentials)
                    AuthResult.EmailTaken ->
                        it.copy(isSubmitting = false, emailError = R.string.auth_error_email_taken)
                    AuthResult.TooManyAttempts ->
                        it.copy(isSubmitting = false, error = R.string.auth_error_too_many_attempts)
                    AuthResult.NoConnection ->
                        it.copy(isSubmitting = false, error = R.string.auth_error_network)
                    AuthResult.Failed ->
                        it.copy(isSubmitting = false, error = R.string.auth_error_failed)
                }
            }
        }
    }

    private fun validate(): Boolean {
        val form = _state.value
        val signingUp = form.mode == AuthMode.SIGN_UP

        val displayNameError =
            if (signingUp && form.displayName.isBlank()) R.string.account_name_required else null

        val emailError =
            if (!Patterns.EMAIL_ADDRESS.matcher(form.email.trim()).matches()) {
                R.string.auth_error_email_invalid
            } else {
                null
            }

        // Duzina se proverava samo pri registraciji, stare lozinke vaze
        val passwordError = when {
            form.password.isEmpty() -> R.string.auth_error_password_required
            signingUp && form.password.length < MIN_PASSWORD_LENGTH -> R.string.auth_error_password_short
            else -> null
        }

        val confirmPasswordError =
            if (signingUp && form.confirmPassword != form.password) {
                R.string.auth_error_password_mismatch
            } else {
                null
            }

        _state.update {
            it.copy(
                displayNameError = displayNameError,
                emailError = emailError,
                passwordError = passwordError,
                confirmPasswordError = confirmPasswordError,
            )
        }
        return listOf(displayNameError, emailError, passwordError, confirmPasswordError).all { it == null }
    }

    private companion object {
        /** Isto kao na serveru */
        const val MIN_PASSWORD_LENGTH = 8

        /** Isto kao ime na ekranu naloga */
        const val MAX_NAME_LENGTH = 40
    }
}
