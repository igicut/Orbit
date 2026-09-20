package com.example.orbit.data.repository

import com.example.orbit.data.local.AppDatabase
import com.example.orbit.data.local.CurrentUser
import com.example.orbit.data.local.dao.UserDao
import com.example.orbit.data.local.entity.UserEntity
import com.example.orbit.data.notification.ReminderHistory
import com.example.orbit.data.remote.OrbitApiService
import com.example.orbit.data.remote.dto.AuthResponseDto
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

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_NOT_FOUND = 404
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

    override suspend fun resetPassword(email: String, newPassword: String): AuthResult =
        authenticate(email) { api.resetPassword(ResetPasswordRequestDto(email, newPassword)) }

    override suspend fun logOut() {
        // Samo neposlati dogadjaji ne postoje na serveru
        eventRepository.pushPendingEvents()
        clearLocalData()
        currentUser.endSession()
    }

    /** Zajednicki deo prijave i registracije */
    private suspend fun authenticate(
        email: String,
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
                HTTP_UNAUTHORIZED -> AuthResult.WrongCredentials
                HTTP_NOT_FOUND -> AuthResult.UnknownEmail
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
