package com.example.orbit.ui.stateholders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbit.data.repository.EventRepository
import com.example.orbit.domain.model.AttendanceRules
import com.example.orbit.domain.model.AttendedEvent
import com.example.orbit.domain.model.Event
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Stanje ekrana Plans. Odvojen od `SearchViewModel`, jer Plans ne trazi javne
 * dogadjaje: dovoljno mu je sto nalog vec ima u Room-u.
 */
@HiltViewModel
class PlansViewModel @Inject constructor(
    private val repository: EventRepository,
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(PlansTab.UPCOMING)
    val selectedTab: StateFlow<PlansTab> = _selectedTab.asStateFlow()

    /** Samo ono sto tek predstoji: nije zavrseno i dolazak nije potvrdjen */
    val upcomingEvents: StateFlow<List<Event>> =
        combine(
            repository.observeRegisteredEvents(),
            repository.observeAttendedEvents(),
            clock(),
        ) { registered, attended, now ->
            val attendedIds = attended.mapTo(HashSet()) { it.event.id }
            registered.filter { it.id !in attendedIds && !AttendanceRules.hasEnded(it, now) }
        }
            .catch { emit(emptyList()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /** F-36: poseceni, sa vremenom dolaska i mojom ocenom */
    val attendedEvents: StateFlow<List<AttendedEvent>> =
        repository.observeAttendedEvents()
            .catch { emit(emptyList()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /** Imena organizatora po id-ju, za redove liste */
    val userNames: StateFlow<Map<String, String>> =
        repository.observeUserNames()
            .catch { emit(emptyMap()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap(),
            )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Povlacenje nadole; sinhronizuje samo podatke naloga */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.syncAccountData()
            } catch (e: Exception) {
                // Lista ostaje na kesu; greska se ne prikazuje jer podaci i dalje stoje
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun onTabSelected(tab: PlansTab) {
        _selectedTab.value = tab
    }

    /** Dogadjaj koji se zavrsi dok je lista otvorena prelazi iz Upcoming bez osvezavanja */
    private fun clock() = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(CLOCK_TICK_MS)
        }
    }

    private companion object {
        const val CLOCK_TICK_MS = 60_000L
    }
}
