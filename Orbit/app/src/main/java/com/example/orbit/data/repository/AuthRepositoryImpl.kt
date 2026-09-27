package com.example.orbit.data.repository

import com.example.orbit.data.local.AppDatabase
import com.example.orbit.data.local.CurrentUser
import com.example.orbit.data.local.dao.UserDao
import com.example.orbit.data.local.entity.UserEntity
import com.example.orbit.data.notification.ReminderHistory
import com.example.orbit.data.remote.OrbitApiService
import com.example.orbit.data.remote.dto.AuthResponseDto
import com.example.orbit.data.remote.dto.ForgotPasswordRequestDto
import com.example.orbit.data.remote.dto.LoginRequestDto
import com.example.orbit.data.remote.dto.ResetPasswordRequestDto
import com.example.orbit.data.remote.dto.SignUpRequestDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val HTTP_BAD_REQUEST = 400
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_CONFLICT = 409
private const val HTTP_TOO_MANY_REQUESTS = 429

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: OrbitApiService,
    private val currentUser: CurrentUser,
    private val database: AppDatabase,
    private val userDao: UserDao,
    private val eventRepository: EventRepository,
    private val reminderHistory: ReminderHistory,
) : AuthRepository {

    override suspend fun logIn(email: String, password: String): AuthResult =
        authenticate(email) { api.logIn(LoginRequestDto(email, password)) }

    override suspend fun signUp(displayName: String, email: String, password: String): AuthResult =
        authenticate(email) { api.signUp(SignUpRequestDto(email, password, displayName)) }

    override suspend fun requestPasswordReset(email: String): AuthResult {
        val response = try {
            api.forgotPassword(ForgotPasswordRequestDto(email))
        } catch (e: IOException) {
            return AuthResult.NoConnection
        }
        return when {
            response.isSuccessful -> AuthResult.Success
            response.code() == HTTP_TOO_MANY_REQUESTS -> AuthResult.TooManyAttempts
            else -> AuthResult.Failed
        }
    }

    override suspend fun resetPassword(email: String, code: String, newPassword: String): AuthResult =
        // Kod je jedino sto ovde server moze da odbije; lozinku i email je forma vec proverila
        authenticate(email, badRequest = AuthResult.InvalidCode) {
            api.resetPassword(ResetPasswordRequestDto(email, code, newPassword))
        }

    override suspend fun logOut() {
        // Samo neposlati dogadjaji ne postoje na serveru
        eventRepository.pushPendingEvents()
        clearLocalData()
        currentUser.endSession()
    }

    /**
     * Zajednicki deo prijave, registracije i zamene lozinke.
     * @param badRequest sta znaci 400; kod zamene lozinke to je pogresan kod
     */
    private suspend fun authenticate(
        email: String,
        badRequest: AuthResult = AuthResult.Failed,
        request: suspend () -> Response<AuthResponseDto>,
    ): AuthResult {
        val response = try {
            request()
        } catch (e: IOException) {
            return AuthResult.NoConnection
        } catch (e: SerializationException) {
            return AuthResult.Failed
        }

        val body = response.body()
        if (!response.isSuccessful || body == null) {
            return when (response.code()) {
                HTTP_BAD_REQUEST -> badRequest
                HTTP_UNAUTHORIZED -> AuthResult.WrongCredentials
                HTTP_CONFLICT -> AuthResult.EmailTaken
                HTTP_TOO_MANY_REQUESTS -> AuthResult.TooManyAttempts
                else -> AuthResult.Failed
            }
        }

        // Drugi nalog na istom telefonu ne vidi tudje lokalne podatke
        if (body.user.id != currentUser.id) clearLocalData()

        currentUser.startSession(
            userId = body.user.id,
            email = email.trim().lowercase(),
            displayName = body.user.displayName,
            token = body.token,
        )
        userDao.upsert(
            UserEntity(
                id = body.user.id,
                displayName = body.user.displayName,
                interests = body.user.interests,
            )
        )
        return AuthResult.Success
    }

    /** clearAllTables blokira nit, zato IO */
    private suspend fun clearLocalData() = withContext(Dispatchers.IO) {
        database.clearAllTables()
        reminderHistory.clear()
    }
}
