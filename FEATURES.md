# Feature backlog — Event discovery app

How to use this with Claude Code: drop this file into your repo root (or reference it from a `CLAUDE.md`). Each entry is scoped small enough to hand to Claude Code as one task — paste the "Claude Code prompt" line, adjust anything project-specific (package names, exact file paths once they exist), and let it work. Entries are ordered by dependency, roughly matching the week-by-week plan from the workplan doc. Checkboxes are for you to track progress; keep this file updated as you go so Claude Code always has current context on what already exists.

---

## Data model reference

Keep this section accurate as the schema evolves — most features below refer back to it.

**Event**
`id: String (UUID)`, `ownerId: String`, `title: String`, `description: String`, `imageUris: List<String>`, `latitude: Double`, `longitude: Double`, `address: String?`, `startTime: Long`, `durationMinutes: Int?`, `category: String`, `capacity: Int?`, `price: Double?`, `requiresReservation: Boolean`, `visibility: PUBLIC | PRIVATE`, `accessCode: String?`, `avgRating: Float`, `ratingCount: Int`, `createdAt: Long`, `syncedToBackend: Boolean`

**User** (no full auth for MVP — a generated device/user id is enough)
`id: String`, `displayName: String`, `interests: List<String>`

**Rating**
`id: String`, `eventId: String`, `userId: String`, `value: Int (1–5)`, `comment: String?`, `createdAt: Long`

**BlockedUser**
`blockerId: String`, `blockedId: String`, `createdAt: Long`

---

## Project structure

Settled in week 1. Differs from the original F-01 suggestion in two places: `ui/stateholders`
is kept (it is Android's own term for ViewModels, and separating them from screens keeps the
screen package purely composables), and `ui/elements/*` was flattened away.

```
com.example.orbit
├── data/
│   ├── local/            Room: entity/, dao/, AppDatabase, Converters, EventMappers
│   ├── remote/           Retrofit service + DTOs          (week 2, F-14)
│   └── repository/       EventRepository + Impl           (F-06)
├── di/                   Hilt modules: DatabaseModule, RepositoryModule
├── domain/model/         Pure Kotlin models — no Android/Room/Retrofit imports
└── ui/
    ├── common/           UiState<T>                       (F-03)
    ├── components/       Reusable composables (osmdroid MapView wrapper)
    ├── navigation/       OrbitDestinations + OrbitNavHost  (F-01)
    ├── screens/          One file per screen
    ├── stateholders/     ViewModels
    └── theme/            Color / Type / Theme             (F-02)
```

**The one rule that matters:** dependencies point inward — `ui` → `data` → `domain`.
`domain/model` imports nothing from the other two. That is what makes the week-2 backend
work (F-14/F-15) a change confined to `data/`, and it is the claim your thesis
architecture chapter will rest on.

---

## Module 0 — Project setup (week 1)

### F-01 — Project scaffolding ✅ done
**Priority:** MVP · **Depends on:** —
Compose + Navigation-Compose + Hilt wired up, standard `data/domain/ui` package structure.
**Acceptance criteria**
- [x] App builds and launches into a working NavHost (list / create / detail / map)
- [x] Hilt `Application` class + `@HiltAndroidApp` in place  *(manifest `android:name` wired, DI graph verified)*
- [x] Package structure scaffolded — see **Project structure** below (deviates slightly from the original suggestion)
**Claude Code prompt:** "Set up a new Jetpack Compose Android project with Hilt DI and Navigation-Compose. Create the package structure data/local, data/remote, data/repository, ui/screens, ui/components, ui/theme, domain/model. Add a NavHost with one placeholder screen."

### F-02 — App theme ✅ done
**Priority:** MVP · **Depends on:** F-01
Material 3 theme (light/dark), consistent with Compose theming lecture.
**Claude Code prompt:** "Create a Material 3 Compose theme (Color.kt, Type.kt, Theme.kt) supporting light and dark mode, following current Material 3 conventions."

### F-03 — UI state pattern ✅ done
**Priority:** MVP · **Depends on:** F-01
Shared `UiState<T>` sealed class (Loading/Success/Error) so every ViewModel follows the same pattern.
**Claude Code prompt:** "Create a generic sealed class UiState<T> with Loading, Success(data: T), and Error(message: String) variants, plus a base ViewModel extension for collecting StateFlow in Compose."

---

## Module 1 — Data layer / Room (week 1)

### F-04 — Event Room entity + DAO ✅ done
**Priority:** MVP · **Depends on:** F-01
**Acceptance criteria**
- [x] `EventEntity` matches the data model reference above
- [x] `EventDao` supports insert/update/delete, get-by-id, get-all (Flow), get-by-owner
- [x] Room database class with `fallbackToDestructiveMigration(dropAllTables = true)`
**Claude Code prompt:** "Create a Room EventEntity matching this schema: [paste Event fields from data model reference]. Add EventDao with CRUD operations and Flow-returning queries for get-all and get-by-owner. Add the AppDatabase class."

### F-05 — User / Rating / BlockedUser entities + DAOs ✅ done
**Priority:** MVP · **Depends on:** F-04
**Claude Code prompt:** "Add Room entities and DAOs for User, Rating, and BlockedUser matching these schemas: [paste from data model reference]. Wire them into the existing AppDatabase."

### F-06 — EventRepository ✅ done (remote stubs empty, see TODO.md)
**Priority:** MVP · **Depends on:** F-04, F-05
Single source of truth; UI only talks to this, never to DAOs directly.
**Acceptance criteria**
- [x] Exposes `Flow<List<Event>>` for local events
- [x] Has stub methods for remote sync (`syncPublicEvents()`, `pushEvent()`) — empty, not throwing
**Claude Code prompt:** "Create an EventRepository interface and Hilt-injected implementation that wraps EventDao. Expose events as Flow<List<Event>> mapped from EventEntity to a domain Event model. Add stub suspend functions syncPublicEvents() and pushEvent(event: Event) that will later call a remote API."

---

## Module 2 — Event CRUD UI (week 1–2)

### F-07 — Event list screen ✅ done
**Priority:** MVP · **Depends on:** F-06
**Acceptance criteria**
- [x] LazyColumn of events with loading/empty/error states
- [x] Tapping an item navigates to detail
**Claude Code prompt:** "Create an EventListScreen composable + EventListViewModel that collects events from EventRepository and displays them in a LazyColumn with loading, empty, and error states. Each row navigates to event detail on click."

### F-08 — Create/edit event form 🟡 create done, edit pending
**Priority:** MVP · **Depends on:** F-06
**Acceptance criteria**
- [x] Form covers all required fields + optional capacity/price/reservation toggle
- [x] Image picker uses the Android Photo Picker API (no storage permission needed)
- [x] Client-side validation (required fields, start time not in the past)
**Claude Code prompt:** "Create a CreateEventScreen composable with a form for all Event fields. Use the Android Photo Picker API (PickVisualMedia) for image selection, storing content URIs. Add validation for required fields and a future start time. Submit calls EventRepository."

### F-09 — Event detail screen ✅ done (no edit button yet)
**Priority:** MVP · **Depends on:** F-06
**Claude Code prompt:** "Create an EventDetailScreen showing all Event fields, with edit/delete actions visible only when the current user is the owner."

### F-10 — Manual category selection ✅ done
**Priority:** MVP · **Depends on:** F-08
Chip-based category picker (since AI classification is deferred).
**Claude Code prompt:** "Add a category chip selector (FilterChip row) to the create/edit event form, backed by a fixed enum or string list of event categories."

---

## Module 3 — Backend / Ktor (week 2)

### F-11 — Ktor server scaffold
**Priority:** MVP · **Depends on:** —
Separate Gradle module or separate project — routing + content negotiation (kotlinx.serialization) + a lightweight DB (SQLite or H2 via Exposed).
**Claude Code prompt:** "Set up a Ktor server project with Netty engine, kotlinx.serialization content negotiation, and Exposed ORM backed by a local SQLite/H2 database. Add a health-check route at GET /health."

### F-12 — Events REST endpoints
**Priority:** MVP · **Depends on:** F-11
**Acceptance criteria**
- [ ] `POST /events` — create
- [ ] `GET /events?lat=&lng=&radiusKm=&category=` — list/search public events
- [ ] `GET /events/{id}` — detail
- [ ] `PATCH /events/{id}/rating` — submit rating, recompute average
**Claude Code prompt:** "Add Ktor routes for events: POST /events (create), GET /events with lat/lng/radiusKm/category query params (search, use a simple bounding-box or haversine filter), GET /events/{id}, and PATCH /events/{id}/rating. Use the Event schema: [paste from data model reference]."

### F-13 — Minimal identity
**Priority:** MVP · **Depends on:** F-11
No full auth system needed for a course project — a generated device/user id sent as a header is enough.
**Claude Code prompt:** "Add a simple X-User-Id header check to the Ktor routes that require ownership (edit/delete/rating), no full auth system — just validate the header is present and use it as ownerId/userId."

---

## Module 4 — Networking client (week 2)

### F-14 — Retrofit service + DI
**Priority:** MVP · **Depends on:** F-12
**Claude Code prompt:** "Create a Retrofit EventApiService interface matching the Ktor endpoints from Module 3, plus a Hilt module providing Retrofit/OkHttp instances with a configurable base URL (for local network testing)."

### F-15 — Repository sync logic
**Priority:** MVP · **Depends on:** F-06, F-14
**Acceptance criteria**
- [ ] Creating a public event pushes it to the backend after local save
- [ ] Public event list pulls from backend, falls back to local cache if the request fails
**Claude Code prompt:** "Implement syncPublicEvents() and pushEvent() in EventRepository using the Retrofit EventApiService. On push failure, keep syncedToBackend=false and retry later. On list fetch failure, fall back to the local Room cache."

---

## Module 5 — Location (week 2)

### F-16 — Location permission flow
**Priority:** MVP · **Depends on:** F-01
**Claude Code prompt:** "Add a runtime location permission request flow using Accompanist Permissions or the Activity Result API, with a rationale UI shown before the system prompt."

### F-17 — Current location + radius search
**Priority:** MVP · **Depends on:** F-16, F-15
**Claude Code prompt:** "Integrate FusedLocationProviderClient to get the user's last known location, and wire it into the event list search as a default lat/lng with an adjustable radius slider, calling EventRepository.syncPublicEvents(lat, lng, radiusKm)."

---

## Module 6 — Map & discovery (week 3)

### F-18 — Interactive map screen 🟡 markers done, preview card pending
**Priority:** MVP · **Depends on:** F-17
**Claude Code prompt:** "Create a MapScreen using osmdroid showing the user's location and event markers from EventListViewModel. Tapping a marker shows a small preview card with a button to open full event detail."

### F-19 — Navigate to event ✅ done
**Priority:** MVP · **Depends on:** F-09
Deep link to the device's maps app rather than building routing yourself.
**Claude Code prompt:** "Add a 'Navigate' button on EventDetailScreen that opens an Intent with a geo: URI (or Google Maps navigation URI) for the event's coordinates."

---

## Module 7 — Private events, access code (week 3)

### F-20 — Access code generation ✅ done
**Priority:** MVP · **Depends on:** F-08
**Claude Code prompt:** "When creating an event with visibility=PRIVATE, generate a short random access code (e.g. 6 alphanumeric chars) and store it on the Event. Show it in a shareable format after creation."

### F-21 — Join by code
**Priority:** MVP · **Depends on:** F-20, F-15
**Claude Code prompt:** "Add a JoinByCodeScreen with a text field for an access code, calling a backend endpoint GET /events/by-code/{code} and saving the result locally on success."

---

## Module 8 — Private events, P2P (week 3)

### F-22 — Nearby Connections advertise/discover
**Priority:** MVP · **Depends on:** F-01
This is the riskiest module — build and test it isolated before wiring into the event flow.
**Claude Code prompt:** "Set up the Nearby Connections API (Google Play Services) with both advertising and discovery, using P2P_CLUSTER strategy. Add basic connection lifecycle callbacks with logging, no payload logic yet — just get two devices to discover and connect."

### F-23 — Send/receive event payload
**Priority:** MVP · **Depends on:** F-22
**Claude Code prompt:** "Extend the Nearby Connections setup to send a serialized Event as a BYTES payload on connection, and deserialize it on the receiving end."

### F-24 — Received-event confirmation UI
**Priority:** MVP · **Depends on:** F-23, F-06
**Claude Code prompt:** "Add a confirmation dialog shown when an Event is received via Nearby Connections, with accept (save to Room via EventRepository) and reject actions."

---

## Module 9 — Notifications (week 3)

### F-25 — WorkManager periodic check
**Priority:** MVP · **Depends on:** F-15
**Claude Code prompt:** "Create a WorkManager PeriodicWorkRequest (minimum 15 min interval) that calls EventRepository.syncPublicEvents() with the last known location and checks for new nearby events since the last run."

### F-26 — Local notifications
**Priority:** MVP · **Depends on:** F-25
**Claude Code prompt:** "Add a notification channel and helper to show a local notification when the WorkManager job finds new nearby events or a capacity change, tapping the notification opens EventDetailScreen via deep link."

---

## Module 10 — Ratings (week 4)

### F-27 — Rating submission UI
**Priority:** MVP · **Depends on:** F-05, F-12
**Claude Code prompt:** "Add a 5-star rating input on EventDetailScreen, saving locally via RatingDao and calling PATCH /events/{id}/rating on the backend."

---

## Module 11 — Moderation (week 4)

### F-28 — Block user
**Priority:** MVP · **Depends on:** F-05
**Claude Code prompt:** "Add a 'block user' action on event owner info, storing a BlockedUser row locally, and filter events by blocked owners out of EventListViewModel's results."

---

## Module 12 — Search & filters (week 4)

### F-29 — Filter bar
**Priority:** MVP · **Depends on:** F-10, F-17, F-18
**Claude Code prompt:** "Add a filter bar above the event list (and reflected on the map) for category, radius, and date range, wired into the existing search query parameters."

---

## Module 13 — AI features (weeks 5–6, thesis phase)

### F-30 — AI description/classification (backend)
**Priority:** Thesis · **Depends on:** F-11, F-10
Call the LLM from the server, not the client — keeps API keys off the device.
**Claude Code prompt:** "Add a Ktor endpoint POST /events/ai-suggest that takes a partial event description and returns a suggested category and an expanded description, calling [your chosen LLM API] server-side with the key read from environment config."

### F-31 — AI suggestions in the create flow
**Priority:** Thesis · **Depends on:** F-30, F-08
**Claude Code prompt:** "Add an 'AI suggest' button on the create event form that calls the ai-suggest endpoint and pre-fills the category chip and description field, editable before submit."

### F-32 — AI-personalized ranking
**Priority:** Thesis · **Depends on:** F-30, F-27, F-05
**Claude Code prompt:** "Add a backend ranking step to GET /events that reorders results using user interests, past ratings, and location proximity — start with a simple weighted score, note in code comments where an LLM-based re-ranking could replace it."

---

## Deferred work

Everything not implemented in Phase 1 lives in **[TODO.md](TODO.md)**, grouped by *why* it
was deferred (blocked on the backend / blocked on hardware / routine but not yet useful).

---

## Keeping this file useful

- Check items off as you go — Claude Code reads better against a backlog that reflects actual state.
- If you deviate from an approach here (e.g. raw Bluetooth instead of Nearby Connections), edit the relevant entry rather than leaving it stale.
- Consider a short `CLAUDE.md` at the repo root with the architecture summary from the workplan doc (backend vs BaaS decision, P2P approach, deferred AI) so Claude Code has that context without you repeating it every session.
