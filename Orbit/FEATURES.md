# Feature backlog — Event discovery app

Single backlog for Orbit: what is done, how it works, and what is still open. It replaces the
old `TODO.md` (merged here on 2026-09-15). Each entry is scoped small enough to hand to Claude
Code as one task. Keep this file accurate — Claude Code works better against a backlog that
reflects the real state.

Status markers: ✅ done · 🟡 done with gaps · ⏳ open · ⏸ on hold

---

## Data model reference

**Event** (server `events`, Room `events`)
`id: String (UUID)`, `ownerId: String`, `title`, `description`, `imageUris: List<String>`,
`latitude`, `longitude`, `address: String?`, `startTime: Long`, `durationMinutes: Int?`,
`category`, `capacity: Int?` (null = unlimited spots), `price: Double?`,
`registeredCount: Int` (server-maintained), `visibility: PUBLIC | PRIVATE`, `accessCode: String?`,
`avgRating: Float`, `ratingCount: Int`, `createdAt: Long`, `syncedToBackend: Boolean`
(app only), `ownerName: String?` (server response only)

**User** (`users`) — public profile: `id`, `displayName`, `interests: List<String>`

**Credentials** (`user_credentials`, server only) — `userId`, `email` (unique, lowercase),
`passwordHash` (bcrypt), `createdAt`

**Registration** (`registrations`) — `eventId`, `userId`, `registeredAt`; PK (eventId, userId).
The app keeps only its own rows (`eventId`, `registeredAt`).

**Attendance** (`attendances`) — `eventId`, `userId`, `checkedInAt`; PK (eventId, userId).
Every attendance has a registration row; a walk-in creates both in one transaction.

**Rating** (`ratings`) — `id`, `eventId`, `userId`, `value: 1–5`, `comment?`, `createdAt`;
unique (eventId, userId). Only users with an attendance may rate.

**BlockedUser** (`blocked_users`) — `blockerId`, `blockedId`, `createdAt`

**EventMember** (`event_members`) — `eventId`, `userId`, `joinedAt`; who opened a private
event with its access code.

No foreign keys on the server (older tables had a different collation); cascades are done in
`ExposedEventService.delete`. SQL scripts: `OrbitKtorServer/db/schema.sql`, `seed.sql`
(demo accounts `*@orbit.test`, password `orbit123`, dates relative to `NOW()`).

---

## Project structure

```
com.example.orbit
├── data/
│   ├── camera/           CameraX capture
│   ├── local/            Room: entity/, dao/, AppDatabase (v4), CurrentUser (session)
│   ├── location/         LocationProvider (fused location, precise fix for check-in)
│   ├── notification/     ReminderWorker, EventReminderService, ReminderChecker, EventNotifier
│   ├── remote/           Retrofit service + DTOs
│   └── repository/       EventRepository, AuthRepository + result types
├── di/                   Hilt modules: Database, Network, Repository, CoroutineScopes
├── domain/model/         Pure Kotlin models and rules (EventFilters, EventEditRules, AttendanceRules)
└── ui/
    ├── common/ components/ navigation/ screens/ stateholders/ theme/ util/
```

**The one rule that matters:** dependencies point inward — `ui` → `data` → `domain`.
`domain/model` imports nothing from the other two, which is why the rule objects are unit
tested on the JVM.

Server (`OrbitKtorServer`): `routes/` → `service/` → `db/` (Exposed R2DBC, MySQL), `plugins/`
for security, serialization, status pages and database wiring.

---

## Module 0 — Project setup

### F-01 — Project scaffolding ✅
Compose + Navigation-Compose + Hilt, `data/domain/ui` packages.

### F-02 — App theme ✅
Material 3 light/dark theme.

### F-03 — UI state pattern ✅
Shared `UiState<T>` (Loading / Success / Error with a string resource).

---

## Module 1 — Data layer / Room

### F-04 — Event Room entity + DAO ✅
`fallbackToDestructiveMigration(dropAllTables = true)`: every schema change wipes the local
cache, which is refilled from the server. Unsent offline events are lost on such an update —
check `syncedToBackend = 0` before installing a build with a new Room version.

### F-05 — User / Rating / BlockedUser entities + DAOs ✅

### F-06 — EventRepository ✅
Single source of truth; the UI never talks to DAOs directly.

---

## Module 2 — Event CRUD UI

### F-07 — Event list screen ✅
Tabs **All**, **Registered** and **History** (F-36). Registered shows only what is still ahead:
events that have not ended (`AttendanceRules.hasEnded`) and are not attended yet; a one-minute
clock removes an event that ends while the list is open.

### F-08 — Create/edit event form ✅
Title, description, category, start time, optional duration (F-35), map location picker,
address, photos, capacity (empty = unlimited), price, visibility. Validation: required fields,
future start, capacity ≥ 1, price ≥ 0, duration 1 min – 7 days; editing also applies
`EventEditRules`. Editing keeps duration, rating summary and `createdAt` of the original.

### F-09 — Event detail screen ✅
Edit/delete only for the owner, both hidden once the event has started.

### F-10 — Manual category selection ✅

---

## Module 3 — Backend / Ktor

### F-11 — Ktor server scaffold ✅
Netty, kotlinx.serialization, Exposed R2DBC on MySQL, `GET /health`.

### F-12 — Events REST endpoints ✅
- `POST /events` — create (owner taken from the token)
- `GET /events?lat=&lng=&radiusKm=&category=&q=` — public search
- `GET /events/{id}` — detail (private only for owner and members)
- `PUT /events/{id}` — edit, owner only
- `DELETE /events/{id}` — owner only and only before the start (409 after), so attendees keep
  their history and ratings; cascades ratings, attendances, registrations, members

**Edit limits** (server, mirrored in `EventEditRules.kt`): started events are frozen; the new
start must be in the future; ±14 days maximum reschedule; inside 24 h an event may only be
postponed; ≤50 km relocation; capacity ≥ 1 and not below `registeredCount`; price ≥ 0.
`visibility`, `accessCode`, `ownerId`, `createdAt`, ratings and `registeredCount` are not
client-writable.

### F-13 — Login (JWT) 🟡
Replaced the old `X-User-Id` header identity.
- **Server:** `POST /auth/signup` and `POST /auth/login` (bcrypt cost 12, email lowercased,
  same 401 for unknown email and wrong password). JWT HMAC256, issuer/audience checked,
  30-day expiry, secret from `JWT_SECRET` (random key with a warning when missing). Every
  route except `/health` and `/auth/*` is inside `authenticate(JWT_AUTH)`; identity comes only
  from `call.userIdOrNull()` (the token subject). `/auth/*` is rate limited: 10 requests per
  minute per IP address → 429 with `Retry-After` (`AuthResult.TooManyAttempts` in the app).
- **App:** `AuthScreen` (log in / sign up), `CurrentUser` stores token and profile, OkHttp
  interceptor adds `Authorization: Bearer`, the `Authorization` header is redacted and
  `/auth/*` bodies are never logged. A 401 ends the session → login screen.
- **Account data lives on the server:** `GET /users/me/sync` restores own events, joined
  events, registrations, attendances, blocked users and ratings into Room after login.
  `PATCH /users/me` changes only the display name.
- **Session end:** logout wipes Room and reminder history; a 401 keeps local data (so unsent
  offline events survive) but `endSession()` removes shown reminders and `ReminderChecker`
  returns `NoSession`. Logging in with a different account wipes the previous account's data.

**Gaps:** see Open work → Login.

---

## Module 4 — Networking client

### F-14 — Retrofit service + DI ✅
Base URL from `BuildConfig`; short timeouts so the app falls back to the cache quickly.

### F-15 — Repository sync logic ✅
Pending events are pushed before every public sync; list falls back to Room when offline.

---

## Module 5 — Location

### F-16 — Location permission flow ✅
Activity Result API, shared `rememberLocationPermissionState` (with `onGranted`/`onDenied`).

### F-17 — Current location + radius search ✅
The radius is applied **twice on purpose**: sent to the server so the bounding-box search
reduces what is downloaded, and applied again locally so changing a chip re-filters instantly
and works offline. With no location, distance filtering switches itself off.

---

## Module 6 — Map & discovery

### F-18 — Interactive map screen ✅
osmdroid map; tapping a marker opens a non-modal preview card (a bottom sheet would dim the
map). The map recentres once per reason, not on every redraw.

### F-19 — Navigate to event ✅
`geo:` intent to the device's maps app.

---

## Module 7 — Private events

### F-20 — Access code generation ✅
6 characters, alphabet without `I`/`O` so codes read aloud are not confused with `1`/`0`.

### F-21 — Join by code ✅
`POST /events/join {accessCode}` returns the event and records membership in
`event_members`, so the private event is visible, registrable and rateable for that account.

---

## Module 8 — Private events, P2P ⏸ on hold

The assistant allowed putting Bluetooth/Nearby sharing on hold; it may be skipped entirely.

### F-22 — Nearby Connections advertise/discover ⏸
### F-23 — Send/receive event payload ⏸
### F-24 — Received-event confirmation UI ⏸

If resumed: needs two physical devices (not the emulator); the Nearby permission set changes
across API levels (Bluetooth split at 31, `NEARBY_WIFI_DEVICES` at 33) for `minSdk 24`;
JSON payload via kotlinx.serialization.

---

## Module 9 — Notifications

### F-25 — WorkManager periodic check ✅
`ReminderChecker` decides which **registered** events start within 24 h
(`REMINDER_WINDOW_HOURS`); both callers use it:
- **`ReminderWorker`** — hourly `PeriodicWorkRequest`, `KEEP` on launch; posts notifications
  directly (a background app cannot start a foreground service since Android 12).
- **`EventReminderService`** — foreground service for the manual "Check for reminders now" button.

`OrbitApplication` implements `Configuration.Provider` so the worker gets injected dependencies.

### F-26 — Local notifications ✅
Two channels, Android 13+ permission (permanent refusal sends the user to Settings), a tapped
reminder opens its event. Every check reports a `ReminderOutcome` (`Posted` /
`AlreadyNotified` / `NothingSoon` / `NoRegistrations` / `NoSession` / `PermissionMissing` /
`Failed`).

---

## Module 10 — Ratings

### F-27 — Rating submission UI ✅
5 stars on the detail screen, `PATCH /events/{id}/rating`, the server recomputes the average.
**Rule:** only users who confirmed attendance (F-34) can rate — the server returns 403
otherwise, and the app shows the stars only after check-in. The organiser cannot rate their
own event because organisers do not check in.

---

## Module 11 — Moderation

### F-28 — Block user ✅
Server-side per account (`PUT/DELETE /users/me/blocked/{id}`); blocked organisers' events are
hidden from lists.

---

## Module 12 — Search & filters

### F-29 — Filter bar ✅
Distance (1/5/25/100 km/Anywhere), category, when (any/today/week/month), sort
(soonest/nearest/top rated); text also matches the organiser name. Pure function
`applyFilters()` in `EventFilters.kt`, 23 JVM tests.

---

## Module 13 — AI features

### F-30 — AI description/classification (backend) ✅
`POST /events/ai-suggest`, Gemini called server-side, key from `GEMINI_API_KEY`.

### F-31 — AI suggestions in the create flow ✅
"Suggest with AI" pre-fills category and description.

### F-32 — AI search / personalised ranking ⏳
Consultation asked for natural-language search ("I want to go to something with …"). Planned:
embeddings stored per event, `POST /events/ai-search` → cosine similarity in Kotlin over the
public events in range → top 5. No vector database (small data set).

---

## Module 14 — Registration and attendance (after consultation)

### F-33 — Registration for events ✅
One action for every event (it replaced both "save" and the reservation flag).
- `PUT` / `DELETE /events/{id}/registration`; registering is idempotent.
- A spot is taken with a conditional `UPDATE … WHERE registered_count < capacity` in the same
  transaction as the insert, so two people never get the last spot.
- Registration closes at start; cancelling is allowed until start (after that the registration
  is needed for check-in). Organisers cannot register; private events need access.
- App: detail shows "3 of 25 spots taken" / "N people registered", Register / Cancel / No spots
  left; **Registered** tab; reminders follow registrations. Requires a connection.

### F-34 — Attendance check-in with location ✅
Rules live in `domain/model/AttendanceRules.kt` and again in `RegistrationRoutes.kt`
(server decides, using its own clock):

| Who | When | Condition |
|---|---|---|
| Registered | start → end (`startTime + durationMinutes`, or 3 h when missing) | within 200 m |
| Walk-in (not registered) | first 15 minutes after start | within 200 m and a free spot; registers them in the same transaction |

- `PUT /events/{id}/attendance {latitude, longitude}` → 200 event, 403 too far, 409 outside the
  window or full. Repeated check-in keeps the first time.
- App checks first for a clear message: permission, fresh high-accuracy fix (never cached),
  not a mock location, accuracy ≤ 100 m, distance ≤ 200 m.
- Organiser: **Guest list (N)** button opens a `ModalBottomSheet` from
  `GET /events/{id}/attendees` (owner only): name, registered / attended / walk-in with time.
- Verified by API tests (window edges, 150/211/1100 m, 5 concurrent walk-ins for 1 spot,
  started event cannot be deleted) and 7 `AttendanceRulesTest` JVM tests.

**API tests:** `OrbitKtorServer/scripts/api-tests/` — `test_registration.py` (25),
`test_attendance.py` (30), `test_duration.py` (9); `python run_all.py`, see the README there.

**Known limit:** GPS can be spoofed by a modified client; a stronger proof would be a code or QR
shown by the organiser at the venue.

### F-35 — Event duration ✅
- Form: optional **Duration (hours)** + **Minutes** fields; both empty = no duration (check-in
  then uses the 3 h default, shown in the hint).
- Parsing and limits in `domain/model/EventDuration.kt` (1 min – 7 days, minutes 0–59),
  5 JVM tests in `EventDurationTest`; the server rejects values outside 1..10080 on
  `POST` and `PUT /events` with 400.
- Detail shows "Until 23:15", or the full date when the event ends on another day.
- Fixed the edit bug: `CreateEventViewModel.save()` now sends the duration (it used to send
  null and erase it) and keeps `avgRating`, `ratingCount` and `createdAt` in the local copy.

### F-36 — Attended events history ✅
- **History / Posećeni** tab next to Registered.
- `AttendanceDao.observeAttendedEvents(userId)`: attendances joined with events and my rating,
  newest check-in first; rows show "Attended <time> · your rating 4/5" or "not rated yet".
- Data comes from Room (filled by `/users/me/sync` and by a successful check-in), so the list
  works offline. Events from blocked organisers stay in the history — it is the user's own record.

---

## Open work

### Finishing Login, Registration and Attendance (ordered)

Feature work for these three is done (F-13, F-33–F-36). What is left before they can be
called complete, most important first. Size: S ≈ under an hour, M ≈ half a day.

**Needed for a correct, defensible result**
1. [x] **Keep attendees' history when an event is deleted** — the server refuses to delete a
       started event (409) and the detail hides the delete icon after start.
2. [x] **Registered tab shows only what is still ahead** — not ended and not attended.
3. [x] **Limit login attempts** — Ktor `RateLimit` on `/auth/*`: 10 requests per minute per IP,
       then 429 with `Retry-After`; the app shows "Too many attempts. Wait a minute and try again."
4. [x] **API test scripts in the repository** — `OrbitKtorServer/scripts/api-tests/`, 71 checks
       (including `test_auth_rate_limit.py`), shared helpers in `common.py`, `run_all.py`, README
       with setup (`MYSQL_PWD` from the environment only).
5. [x] **Demo data for check-in** — seed event "Okupljanje na Studentskom trgu" starts 5 minutes
       after `seed.sql` runs and lasts 4 h at 44.8189, 20.4587; Milica is registered, Ana can walk
       in during the first 15 minutes, Stefan is the organiser (guest list).
6. [x] **Code map** — `propratno/CODE_MAP_sr.md` is now the only code map (Serbian, English one
       deleted) and describes login, registrations, attendance, duration, history, the rate limit
       and the API tests.

**Makes the features work well in real use**
7. [ ] **Reminder "The event started — confirm attendance"** (S–M). Second window in
       `ReminderChecker`: registered, started in the last 30 min, not attended; separate history
       key so it does not collide with the 24 h reminder. Without it people forget to check in
       and then cannot rate.
8. [ ] **Tell registered users about changes** (M). No push exists; during `syncAccountData()`
       compare the cached start time/location of registered events with the fresh copy and post
       a local notification when an event moved or disappeared.
9. [ ] **Organiser manual check-in from the guest list** (M). `PUT /events/{id}/attendees/{userId}`
       (owner only, during the window) as a fallback when GPS fails indoors.
10. [ ] **Rating formatting and dead rating code** (S). Show the average with one decimal;
        either use `GET /events/{id}/ratings` (comments from attendees on the detail) or remove
        it together with `getRatings`, `summaryForEvent`, `RatingDao.observeForEvent` and
        `getByUserAndEvent`.

**Conscious simplifications (document for the defense rather than build)**
- [ ] Token stored in app-private `SharedPreferences`, not encrypted
- [ ] No refresh token; the app learns about an expired token only at the next request
- [ ] No password change / reset and no account deletion
- [ ] GPS check-in can be spoofed by a modified client (QR from the organiser would fix it)
- [ ] `JWT_SECRET` must be set in the server run configuration, otherwise every restart logs
      everyone out
- [ ] Event rows and the map card do not show spots ("12/30") — belongs to the UI pass

### Small leftovers
- [ ] Detail top bar title falls back to a hard-coded "Event" string

### Later steps from the consultation plan
- [ ] AI natural-language search (F-32)
- [ ] Google Maps instead of osmdroid (needs a Google Cloud project with billing)
- [ ] Image upload to the server — photos are local `content://`/`file://` URIs, other devices
      cannot see them
- [ ] UI pass (palette, event cards with image/category/spots, detail layout)

### Development notes
- **Unit tests on this machine:** the Windows `PATH` contains stray quotes
  (`"C:\Java\jdk1.8.0\bin` … `C:\Windows\System32\Wbem"`). Gradle passes it to test workers
  as `java.library.path` and they fail with `Could not find or load main class Files\dotnet…`.
  Fix the PATH, or run from PowerShell:
  `$env:Path = $env:Path -replace '"', ''; .\gradlew.bat --no-daemon :app:testDebugUnitTest`
- New server tables are created by `SchemaUtils.create`, but new **columns** on existing tables
  need a manual `ALTER TABLE` (keep `schema.sql` in sync).
- Emulator reaches the host at `10.0.2.2`, not `localhost`.
- **Check-in demo:** rerun `seed.sql` about 5 minutes before the demo, set the emulator location
  to 44.8189, 20.4587 (Extended Controls → Location, or `adb emu geo fix 20.4587 44.8189`),
  log in as `ana@orbit.test` (walk-in, first 15 minutes) or `milica@orbit.test` (registered).

---

## Decisions made along the way (change them if you disagree)

- **Category list** — MUSIC, SPORT, FOOD, ART, TECH, OUTDOOR, SOCIAL, OTHER (`EventCategory`).
- **Image list storage in Room** — joined with newlines, because a content URI may contain a
  comma but never a newline. The server stores a JSON array.
- **`SimpleDateFormat` over `java.time`** — `java.time` needs API 26 or desugaring; `minSdk` is 24.
- **Own Ktor + JWT instead of Firebase** — no second identity system, and the server verifies
  every token itself.
- **One registration action for all events** — capacity only limits spots; saving and reserving
  meant the same thing.
- **Check-in radius 200 m, walk-in window 15 min, default duration 3 h** — constants in
  `AttendanceRules` and `RegistrationRoutes`, change both together.
- **Duration as hours + minutes, max 7 days** — `EventDuration` and `EventRoutes`; clearing both
  fields removes the duration.
- **History is a third tab**, not a section in Account — it sits next to Registered, which it
  complements.
- **Guest list is organiser-only** — names of guests at a private event are not for everyone.
