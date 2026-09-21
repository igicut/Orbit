package com.example.orbit.ui.stateholders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbit.data.repository.EventRepository
import com.example.orbit.domain.model.Event
import com.example.orbit.domain.model.User
import com.example.orbit.ui.navigation.OrbitDestinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Profil organizatora. Kao i ostatak aplikacije cita iz Room-a; server samo dopuni Room
 * njegovim dogadjajima, pa ekran radi i bez veze sa onim sto je vec skinuto.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: EventRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val userId: String = checkNotNull(savedStateHandle[OrbitDestinations.USER_ID_ARG]) {
        "user_profile ruta mora imati userId"
    }

    val user: StateFlow<User?> =
        repository.observeUser(userId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val events: StateFlow<List<Event>> =
        repository.observeEventsByOwner(userId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Ja sam blokirao organizatora; drugi smer server skriva sam (404) */
    val isBlocked: StateFlow<Boolean> =
        repository.observeIsBlocked(userId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Server nije odgovorio; lista moze biti nepotpuna, ali prikazuje ono sto Room ima */
    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    init {
        viewModelScope.launch {
            repository.cacheUser(userId)
            _loadFailed.value = !repository.refreshOrganiserEvents(userId)
        }
    }
}
