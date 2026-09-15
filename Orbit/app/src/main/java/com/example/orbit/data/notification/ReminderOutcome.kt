package com.example.orbit.data.notification

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** F-25/F-26: ishod provere podsetnika, umesto tihog kraja */
sealed interface ReminderOutcome {

    data class Posted(val count: Int) : ReminderOutcome

    /** Ima prijava, ali nijedan dogadjaj ne pocinje uskoro */
    data object NothingSoon : ReminderOutcome

    /** Vec je poslat podsetnik za taj dogadjaj */
    data object AlreadyNotified : ReminderOutcome

    data object NoRegistrations : ReminderOutcome

    /** Token je istekao ili je korisnik odjavljen */
    data object NoSession : ReminderOutcome

    /** Android 13+, nije data POST_NOTIFICATIONS dozvola */
    data object PermissionMissing : ReminderOutcome

    /** Pukla baza ili repozitorijum */
    data object Failed : ReminderOutcome
}

/** Prenosi ishod do ekrana, SharedFlow da se ne ponavlja */
@Singleton
class ReminderResults @Inject constructor() {

    private val _outcomes = MutableSharedFlow<ReminderOutcome>(
        replay = 0,
        extraBufferCapacity = 4,
    )
    val outcomes: SharedFlow<ReminderOutcome> = _outcomes.asSharedFlow()

    /** tryEmit da prijava nikad ne blokira */
    fun report(outcome: ReminderOutcome) {
        _outcomes.tryEmit(outcome)
    }
}
