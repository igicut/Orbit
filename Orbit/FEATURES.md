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

**Rating** (`ratings`) — `id`, `eventId`, `userId`, `value: 1–5`, `comment?`, `imagePath?`,
`createdAt`; unique (eventId, userId). Only users with an attendance may rate. A rating with a
comment and a photo is what the app shows as a review (F-40).

**BlockedUser** (`blocked_users`) — `blockerId`, `blockedId`, `createdAt`

**Event image** — no table of its own: `imageUris` holds server paths (`/images/<uuid>.jpg`,
F-37) and, until the event is pushed, the local `content://` / `file://` URI. The bytes are
files in the server's `uploads/` folder.

**Event embedding** (`event_embeddings`, server only) — `eventId`, `vector: List<Float>` (768,
JSON), `updatedAt`. One row per public event that Gemini has processed; a missing row only
means that event cannot be ranked semantically (F-32).

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
│   ├── local/            Room: entity/, dao/, AppDatabase (v5), CurrentUser (session)
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
**Explore** is the only list of public events, without tabs. The user's own plan moved to the
**Plans** tab (F-38) with two tabs, **Upcoming** and **Attended** (F-36). Upcoming shows only what
is still ahead: events that have not ended (`AttendanceRules.hasEnded`) and are not attended yet;
a one-minute clock removes an event that ends while the list is open.

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
- `PUT /events/{id}` — edit, owner only. The app waits for the answer: Room changes only after
  the server accepts, and a rejected or offline edit keeps the form open with a message
  (`EditResult`). Like delete and cancel, an edit needs the network.
- `DELETE /events/{id}` — owner only and only before the start (409 after), so attendees keep
  their history and ratings; cascades ratings, attendances, registrations, members

**Edit limits** (server, mirrored in `EventEditRules.kt`): started events are frozen; the new
start must be in the future; ±14 days maximum reschedule; inside 24 h an event may only be
postponed; ≤50 km relocation; capacity ≥ 1 and not below `registeredCount`; price ≥ 0.
`visibility`, `accessCode`, `ownerId`, `createdAt`, ratings and `registeredCount` are not
client-writable.

### F-13 — Login (JWT) ✅
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

**Conscious simplifications** (no identity check on password reset, no refresh token, and so on)
are listed in Open work → Conscious simplifications.

---

## Module 4 — Networking client

### F-14 — Retrofit service + DI ✅
Base URL from `BuildConfig`; short timeouts so the app falls back to the cache quickly.

### F-15 — Repository sync logic ✅
Pending events are pushed before every public sync; list falls back to Room when offline.
Only newly created events can be pending, because an edit is never stored before the server
accepts it. A `409` on a retried `POST /events` means an earlier attempt already reached the
server, so the event is marked as sent. `/users/me/sync` also removes own events that the server
no longer has (deleted from another phone); unsent ones stay.

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
Mapbox map (osmdroid until the migration); tapping a marker opens a non-modal preview card (a bottom sheet would dim the
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
(soonest/nearest; "top rated" removed in F-46); text also matches the organiser name. Pure function
`applyFilters()` in `EventFilters.kt`, 23 JVM tests.

---

## Module 13 — AI features

### F-30 — AI description/classification (backend) ✅
`POST /events/ai-suggest`, Gemini called server-side, key from `GEMINI_API_KEY`.

### F-31 — AI suggestions in the create flow ✅
"Suggest with AI" pre-fills category and description.

### F-32 — AI natural-language search ✅
A layer on top of the existing search, not a replacement: `GET /events?q=` still does the same
`LIKE` on title and description, and semantic ranking is added over the result.

- **Embeddings:** `gemini-embedding-001`, `output_dimensionality=768`, `RETRIEVAL_DOCUMENT` for
  the event (title + description) and `RETRIEVAL_QUERY` for the search text. Stored as a JSON
  array in `event_embeddings` — its own table, so event queries never drag 768 numbers around
  and the vector cannot leak into `ExposedEvent`. No vector database, no MySQL `VECTOR`
  functions: the comparison is `SemanticRanking.cosineSimilarity` in Kotlin (dot product over
  both magnitudes).
- **Generation is async** (`application.launch` after the response): Gemini takes 300–800 ms and
  an outage must never block creating or editing an event. An edit re-embeds only when the title
  or description actually changed. Only public events are embedded — private ones are never
  searchable.
- **Missing vector is not an error:** the event still competes through the keyword match and is
  appended after the ranked ones. Events without embeddings at startup (seed data, or a failed
  call) are filled in by one batched background call, logged as "Embedded N of M events".
- **The threshold is relative, not absolute** — this is the one thing that had to bend. Measured
  over the seed data, this model returns similarities in a narrow band, so an absolute cutoff
  admits everything:

  | Query | Best | Field average | Lead |
  |---|---|---|---|
  | `zzznepostojecirec` (nonsense) | 0.628 | 0.610 | **0.018** |
  | "gde mogu da probam vina iz Srbije" | 0.742 | 0.613 | **0.129** |
  | "trčanje" | 0.737 | 0.635 | **0.102** |
  | "nešto sa decom" (no such event) | 0.650 | 0.613 | **0.037** |

  So the rule is: the best score must beat the field average by `MIN_LEAD` (0.05), otherwise the
  query is treated as carrying no signal and the response is exactly the old keyword result —
  with no scores at all. When it does lead, everything within `RELATIVE_MARGIN` (0.03) of the
  best is kept, at most `MAX_RELATED` (10). Measured on the 70-event catalog, this raised
  precision from 50% (0.05 / 20) to 71%.
- **App:** `EventDto.relevance` is a server-only field, like `ownerName`. `SearchViewModel` asks
  the server 400 ms after typing stops and only for queries of 3+ characters, then keeps a
  `Map<id, score>`; `applyFilters` includes an event when the text matches **or** it has a
  score, and orders by score while the sort is the default "Soonest" — an explicitly chosen sort
  still wins. Results are upserted into Room (never `replacePublicCache`), so searching cannot
  shrink the offline cache.
- Verified by `SemanticRankingTest` (10 JVM tests, including the flat-field regression),
  6 new `EventFiltersTest` cases and `test_search.py` (23 checks).

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
- **Attended / Posećeno** tab next to Upcoming on the Plans screen (it was a "History" tab on
  the old event list). The empty Upcoming list names this tab through the tab's own string, since
  the survey showed that a second name for the same tab confuses people.
- `AttendanceDao.observeAttendedEvents(userId)`: attendances joined with events and my rating,
  newest check-in first; rows show "Attended <time> · your rating 4/5" or "not rated yet".
- Data comes from Room (filled by `/users/me/sync` and by a successful check-in), so the list
  works offline. Events from blocked organisers stay in the history — it is the user's own record.

---

## Module 15 — Event photos

### F-37 — Image upload to the server ✅
Photos used to stay on the device that took them: `image_uris` held `content://` / `file://`
strings, which mean nothing on another phone.

- **Upload:** `POST /images`, `multipart/form-data`, one file part, JWT required. The server
  names the file itself (`UUID` + extension from the `Content-Type`), so a client cannot choose
  a path; the body is streamed to disk in 8 KB chunks. 201 `{"path": "/images/<uuid>.jpg"}`,
  415 for anything but JPEG/PNG/WEBP or a non-multipart body, 413 over 8 MB, 400 with no file part.
- **Download:** `GET /images/{name}`, JWT required (photos of private events are not public);
  the name must match `^[0-9a-f-]{36}\.(jpg|png|webp)$`, everything else is 404. `Cache-Control`
  is 30 days because the name never points at different bytes.
- **Storage:** files live in `uploads/` next to the server (`ORBIT_UPLOAD_DIR` overrides it),
  the directory is git-ignored. The database keeps only the relative path in the existing
  `image_uris` JSON column — no schema change.
- **Events carry only stored paths:** `POST` / `PUT /events` return 400 for a local URI or for
  more than 5 images (`MAX_IMAGES`, was 10). Deleting an event deletes its files, and an edit deletes the photos that
  were removed from the list.
- **App:** `ImageUploader` reads the bytes through the `ContentResolver` and posts them;
  `EventRepositoryImpl.withUploadedImages()` swaps local URIs for server paths before
  `pushEvent` / `updateEvent`. No connection → the whole event waits for the next sync; a 4xx
  (file gone, too large) → that one photo is dropped and the event still goes out.
  `ImageUrls.model()` turns `/images/…` into `BASE_URL + path` for Coil.
- **Coil needs a network fetcher:** `coil-compose` alone cannot load `http` URLs.
  `coil-network-okhttp` plus `OrbitApplication : SingletonImageLoader.Factory` building the
  loader from the app's own `OkHttpClient`, which is what attaches the `Authorization` header.
- Verified by `test_images.py` (18 checks) and `ImageUrlsTest` (3 JVM tests).

This is also the app's clearest **file work**: reading a picked/captured image on the client and
writing and deleting files on the server.


### Photos everywhere (2026-09-21) ✅
- **Create form requires 1–5 photos.** `MAX_EVENT_PHOTOS = 5` in `Event.kt` mirrors `MAX_IMAGES`
  on the server (lowered from 10). The photo step shows a counter ("2/5") and the rule under the
  title; the error replaces the rule in red. At 5 the add/take buttons are disabled.
  `onImagesPicked` cuts at 5, because several picker rounds can add up past the picker's own cap.
- **The minimum is enforced in the app only.** The server still accepts events without photos:
  the API tests create such events, and an older client should not start failing. Editing an
  older event that has no photo now asks for one before saving.
- **Detail photos** (`ui/components/EventPhotos.kt`): one photo spans the full width; several
  sit in a `LazyRow` of fixed 240×180 dp tiles with the chips' rounded shape. Fixed tiles keep a
  portrait poster and a wide panorama the same size, so the row never jumps while loading.
- **Full photo on tap**, in the detail and in reviews: a plain Compose `Dialog` with
  `ContentScale.Fit`, a dark scrim and a close button; a tap anywhere closes it. No navigation
  route, no gallery library, no zoom.
- **Seed photos:** 14 photos in `OrbitKtorServer/db/seed_images/` (tracked; `uploads/` is not),
  renamed to the server's `^[0-9a-f-]{36}\.(jpg|png|webp)$` rule. All 74 seeded events have at
  least one photo, 33 have two or three. `seed_images/README.md` maps each file to its original.
- **Known limit — shared files:** seed events reuse the same files. Deleting a seeded event, or
  removing a photo from one while editing, deletes the file from `uploads/` and the other events
  using it show an empty tile (no crash). Copy `seed_images` into `uploads/` again to restore.
- **Known limit — rejected upload:** if the server rejects a photo (too large, wrong type),
  `withUploadedImages` still drops it, so an event could in theory be saved with none.

---

## Module 16 — Second consultation (required for the thesis)

Every item here was requested by the mentor and is a precondition for defending, not a
suggestion. Ordered by dependency and by how much of it already exists.

**The one idea that holds this module together:** F-34 already has one endpoint and one rules
object for attendance. F-41 and F-42 do not add new attendance systems — they add two more ways
to reach the same endpoint. Three kinds of proof, one rule, one row in `attendances`.

```
                  ┌─ manual button ──► proof: GPS coordinates   (F-34, done)
                  │
   PUT /events/{id}/attendance ──► AttendanceRules ──► attendances
                  │                (window, capacity,
                  ├─ geofence ──────►  200 m, 15 min walk-in)
                  │   automatic         UNCHANGED
                  │   proof: GPS
                  │
                  └─ QR scan ───────► proof: code from the event
                                       (no GPS needed)
```

**Remaining work at a glance.** Difficulty is how likely the feature is to fight back;
effort assumes the assistant writes the code, and counts only what a person must still do
separately. Calibration: F-38 and F-39 were both **Medium / 1 session**.

| | Feature | Difficulty | Effort | Riskiest part | Needs you |
|---|---|---|---|---|---|
| F-40 | Comments and photos | ✅ done | 1 session | — | — |
| F-43 | AI search filters | ✅ done | 1 session | — | — |
| F-41 | QR check-in | **Medium-High** | 1–2 sessions | new scanner dependency, camera permission, printable PNG | pick the library, test the scan on the phone |
| F-42 | Geofencing | **High** | 1–2 sessions | `ACCESS_BACKGROUND_LOCATION`; may simply not fire | grant "Allow all the time", walk to a real location |

Build them in that order. F-40 is nearly free because the server side already exists, and
finishing the cheap ones first means a partial Module 16 still demonstrates something.

F-42 is the only one that can fail for reasons outside the code — if the permission is refused
the geofence never fires, and no amount of implementation fixes that. Survey questions 10 and
11 measure how common that refusal is.

### F-38 — App Bar redesign ✅
Requested: the bar takes too much space and does not look the same across the app.

**Two causes, neither of them height.** Every bar was already the same 64 dp.

*Cause 1 — a doubled inset.* `OrbitApp.kt:34` wraps the NavHost in a `Scaffold` whose
`innerPadding` already reserves the status bar, and each screen's `TopAppBar` reserved it again.
That put roughly 36 dp of dead space above the title on every screen that had a bar, and on no
screen that lacked one. Fixed with `windowInsets = WindowInsets(0)` inside `OrbitTopBar`.

*Cause 2 — colour.* What made the screens differ is that `surface` (`#FFFDF8`) is lighter than `background` (`#F7F0E3`), so any
surface-coloured bar drew a pale band across the top. Explore and Map had no such band, every
pushed screen did, and so did both `TabRow`s. That band is the "upper edge" that looked
inconsistent.

**The rule, now explicit:** nothing draws a coloured band at the top. Screens are one cream
surface from the status bar down. A pushed screen shows only a back arrow and its title on that
same background; the four tab destinations show no bar at all.

- `ui/components/OrbitTopBar.kt` — wraps Material `TopAppBar` with `expandedHeight = 48.dp`,
  `containerColor = Color.Transparent` and `windowInsets = WindowInsets(0)`. Used by `EventDetailScreen`, `CreateEventScreen`,
  `MyEventsScreen`, `BlockedUsersScreen`, `JoinedEventsScreen`.
- `OrbitFormTopBar` is the same bar with a close icon: a form is cancelled, not navigated back.
  It replaced the wide `TextButton("Cancel")`, which was the other thing that made the create
  screen look unlike the rest.
- **Both `TabRow`s** (`MyEventsScreen`, `PlansScreen`) also got `containerColor = Color.Transparent`.
  Fixing only the top bar would have left the same pale band behind the tabs.
- **Detail screen category colour moved.** It used to be a tint on the app bar; with the bar
  transparent it would have been lost, so it is now a `DetailBadge` next to price and access
  code, using the same 0.38 tint (`CATEGORY_TINT`) that kept text contrast at 6.7:1.
- `BackButton` moved out of `MyEventsScreen.kt` and is now the default navigation icon of
  `OrbitTopBar`.
- **Why wrap `TopAppBar` instead of building a `Surface` row:** the Material bar keeps its own
  status-bar inset handling. A custom surface would have repeated the bottom-bar inset bug.
- No `TopAppBarScrollBehavior`, no collapsing animation. The request was less space, not motion.
- Verified: `:app:assembleDebug` passes, 46 JVM tests pass. **Screens still need one pass on the
  phone** — the `TabRow` divider line and the new category badge have not been seen on a device.

### F-39 — Event cancellation ✅
Requested: cancel an event when something goes wrong (bad weather).

Decisions: a cancelled event **stays visible** with a badge, cancelling is **one way** (no
reactivation), and it is allowed **until the event ends**.

- **Schema:** `events.status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'` and
  `events.cancel_reason VARCHAR(255) NULL`. `DEFAULT 'ACTIVE'` is not optional — without it
  `seed.sql`, `demo.sql` and `events_catalog.sql` all break at once.
- **Manual `ALTER TABLE`** in `db/schema.sql` and against the local database in the same step.
  `SchemaUtils.create` never alters an existing table; a column added only in the Kotlin `Table`
  lets the server start and then fails every `SELECT`.
- **Server:** `POST /events/{id}/cancel {reason}` — owner only (403), 409 if already cancelled.
  Allowed until the event ends. A cancelled event refuses new registrations and check-in.
- The row is never deleted. This is the same reasoning that already makes `DELETE` refuse a
  started event with 409: attendance history has to survive.
- **App:** badge on the event card (`EventCard`, which replaced `EventPreviewCard`) and at the top of the detail; Register button disabled;
  the event stays in Plans with the badge so a registered user sees what happened.
- **Kept simple on purpose:** cancelled events stay in the list with a badge instead of being
  filtered out. Hiding them would mean two different queries for two kinds of user; one list with
  one badge is one code path.
- **Telling registered users:** `syncAccountData()` already refetches registered events. When one
  comes back `CANCELLED` and the local copy was `ACTIVE`, post a local notification. This also
  closes Open work item 8.

**Built:**
- `EventStatus` enum (`ACTIVE`, `CANCELLED`), `events.status` + `events.cancel_reason`,
  index on status. `DEFAULT 'ACTIVE'` so the three SQL scripts keep working.
- `POST /events/{id}/cancel` — owner only (403), 409 when already cancelled or already ended,
  200 with the updated event. `ExposedEventService.cancel` updates
  `WHERE id AND status = ACTIVE`, so a second call changes nothing and the route answers 409.
- Cancelled blocks **registration**, **check-in** and **editing** (all 409). Delete stays
  allowed before the start, as before.
- App: terracotta banner with the reason on the detail, register and check-in hidden, owner
  gets a cancel button with a confirm dialog and an optional reason. "Otkazano" badge lives in
  `EventMetaBadges`, so the list row and the map card both get it from one place.
- **Cancellation notification.** `ReminderChecker` posts "Ovaj dogadjaj je otkazan" for a
  planned event that is cancelled and has not ended yet. It does **not** wait for the 1 h
  reminder window — a cancellation matters as soon as it happens. Staying silent was the first
  attempt and was wrong: an expected reminder that never arrives reads as a late or broken
  notification, not as "the event is gone".
  - Same notification id as the reminder (`eventId.hashCode()`), so the cancel notice
    **replaces** an already-shown "starts in X minutes" instead of sitting next to it.
  - Own history key (`cancelled_event_ids`) so it fires once and does not consume the
    reminder's own key. `retainOnly` and `clear` cover both sets.
- Room 4 → 5. Destructive migration wipes the local cache, as F-04 documents.

**Not covered by tests:** no `test_cancel.py`. The 409-on-second-cancel path is only reachable
through the API or two devices racing — the app hides the button after the first cancel.

### F-40 — Comments and photos on the event detail ✅
`Difficulty: Low` · `Effort: 1 session` (estimate was half — the UI took the other half)

**Built — a review is one rating:** stars, an optional comment and one optional photo, sent
together with one **Objavi / Izmeni** button.

- **Why one button instead of tap-to-rate:** `upsert` on the server writes every field at once.
  The old instant submit on a star tap sent `comment = null`, so once comments existed, changing
  your stars would have silently wiped your comment.
- **Server:** `ratings.image_path`. `PATCH /events/{id}/rating` accepts `imagePath`, rejects
  anything that is not a stored `/images/...` path (400), trims the comment and caps it at 1000
  characters, and deletes the old file when a photo is replaced or removed.
  `GET /events/{id}/ratings` now joins `users` for `authorName` and drops reviews from anyone
  blocked in either direction (`hiddenOwnerIds`, the same filter events use).
- **App:** `ui/components/EventReviews.kt` holds `ReviewForm` and `ReviewList`; the detail screen
  only places them. The section appears once the event has started — before that no review can
  exist. Your own review pre-fills the form and is left out of the list below it. Three reviews
  show inline, the rest open in a bottom sheet, the same pattern as the guest list.
- The photo goes up through the existing `ImageUploader` before the rating is sent. Unlike event
  photos, a rejected review photo fails the whole submit instead of being dropped silently —
  the person picked it on purpose.
- Reviews are fetched from the server, not stored in Room, so no Room version bump.
- **Dead code removed:** `ExposedRatingService.summaryForEvent`, `RatingDao.observeForEvent`,
  `RatingDao.getByUserAndEvent`. `getRatings` in the API is now used.

**Edge cases checked:** offline (list keeps its last value, submit shows the error), organiser
(reads only), cancelled mid-event (people who checked in can still review), blocked users
(hidden both ways), private events (`canAccess`), deleting an event (impossible once it started,
and reviews only exist after the start, so no photo is orphaned by it).

**Known limits:** one generic error message covers both "no connection" and "photo rejected";
no progress indicator while a full-resolution photo uploads; no `test_reviews.py`.
Requested: leave comments and add pictures inside the event detail.

Almost all of this already exists and is unused — `ratings` has a `comment` column,
`GET /events/{id}/ratings` works, and `RatingDao.observeForEvent` / `getRatings` are dead code
listed in Open work item 10. This feature turns that dead code on instead of adding a new table.

- **No `comments` table.** A comment is the text of a rating: name, stars, text, date.
- **Photo:** `ratings.image_path VARCHAR(255) NULL`, one optional picture per comment, uploaded
  through the existing `POST /images`. No gallery table, no new upload path (manual `ALTER TABLE`
  again).
- **Who may comment:** only users with an attendance row, once each — the rule that already
  governs ratings. Being present is what a comment is worth.
- **Blocking:** comments from blocked users are filtered the same way events already are (F-28).
- Removes `summaryForEvent` if it stays unused after this.

Not built on purpose: a shared event gallery separate from reviews. It would need its own table
and its own moderation.

### F-41 — QR check-in ✅
`Difficulty: Medium-High` · `Effort: 2 sessions`
Requested: the organiser shows a QR code, visitors scan it at the entrance, and it must work
when location is unavailable. Also downloadable as an image so it can be printed.

**Decisions (ADR-5):**
- **Direction:** the organiser shows the QR, the guest scans it. The guest's own phone sends the
  check-in with their own token, the same as GPS check-in. The reverse (a ticket the organiser
  scans) would need a signed ticket, because user ids are not secret.
- **QR text:** `orbit:<eventId>:<code>` — `CheckInQr.text` / `CheckInQr.codeFor` in
  `domain/model/CheckInQr.kt`. The prefix rejects other QR codes; the id rejects a QR from
  another event before anything is sent.
- **One fixed code per event.** Created by the server the first time the organiser opens the QR
  (`ExposedEventService.getOrCreateCheckInCode`) and never changed, so a printed QR works until
  the event ends. Created lazily, so seed, demo and catalog rows need no backfill.
- **The code is not part of `ExposedEvent`.** Every guest receives the event JSON; if the code
  were in it, nobody would need to scan. It has its own owner-only route instead.
- **Libraries:** `com.google.zxing:core` 3.5.4 draws the QR (`qrBitmap`: text → grid → pixels).
  Scanning (session 2) uses the Google code scanner (`play-services-code-scanner`): no camera
  permission, the scanner screen comes from Play services.

**Done in session 1:**
- **Schema:** `events.check_in_code VARCHAR(8) NULL`. Needs a manual migration on an existing
  database — without it every query on `events` fails with "Unknown column":
  `ALTER TABLE events ADD COLUMN check_in_code VARCHAR(8) NULL AFTER cancel_reason;`
- **Server:** `GET /events/{id}/check-in-code` (organiser only, 403 for everyone else).
  `PUT /events/{id}/attendance` now takes either `{latitude, longitude}` or `{code}`. A wrong
  code is 403; the time window, free spot, 15-minute walk-in and one row per person are the same
  checks as for GPS. `scripts/api-tests/test_checkin_qr.py`.
- **Organiser:** "Entry QR code" card on the Overview tab (same shape as the organiser card,
  shown while the event is active) → bottom sheet with the QR, the event title and **Save to
  gallery** (PNG in Pictures/Orbit via `MediaStore`, `data/image/GallerySaver.kt`).
- **Tests:** `CheckInQrTest` (5).

**Done in session 2 — guest side:**
- **Scan entrance QR** under "Confirm attendance", only while check-in is open; same white pill
  as Navigate (`WhitePillButton`). `ui/components/QrScanner.kt` → `scanQrCode` opens the Google
  code scanner (`play-services-code-scanner` 16.1.0, QR only). No camera permission and no camera
  code in the app. The manifest `meta-data` `barcode_ui` asks Play services to download the
  scanner module at install time.
- `EventDetailViewModel.checkInWithQr` → `CheckInQr.codeFor` rejects a QR from another event (or
  not from Orbit) on the phone, before anything is sent → `EventRepository.checkInWithCode`.
- GPS and QR share one private `sendCheckIn` in `EventRepositoryImpl`; the only difference is
  that 403 means "too far" for GPS and "wrong code" for QR. `CheckInRequestDto` fields are
  nullable, and `explicitNulls = false` leaves the unused ones out of the JSON.
- New `CheckInResult` cases with their own messages: `WrongCode`, `ScannerUnavailable`.
- **Verified on the phone:** the button, the scanner opening, and the "scanner not ready" message
  (the first tap right after install, while the module was still downloading).

**Known limitations:**
- A photo of the QR sent to someone at home works for them too — the QR path gives up location
  on purpose. The time window and capacity still apply. F-42 is the location-proof path.
- Saving to the gallery is Android 10+ only; below that `MediaStore` needs a storage permission,
  so the button is hidden and the QR can only be shown on screen.

### F-42 — Geofencing, automatic check-in ⏳
`Difficulty: High` · `Effort: 1–2 sessions` · can fail on permission alone, not on code
Requested explicitly by the mentor: walk into the event with the phone in your pocket and be
checked in automatically.

- `GeofencingClient`, one geofence per registered event that starts within the next 24 h, radius
  200 m — the same constant as `AttendanceRules`, not a second number.
- On `GEOFENCE_TRANSITION_ENTER` the receiver calls the existing check-in endpoint with the GPS
  proof. No new server code at all.
- A local notification confirms it, because a silent check-in looks like nothing happened.
- Re-registered when the app starts; geofences are lost on reboot and do not come back by themselves.
- Android allows 100 geofences per app; cap the list defensively even though this app will have a handful.

**Main risk — `ACCESS_BACKGROUND_LOCATION`.** On Android 10+ "Allow all the time" is a separate
system screen, and without it a geofence does not fire while the app is closed. Fallback: with
"while using the app" the geofence still fires in the foreground, which is enough to demo with
the phone unlocked. Survey questions 10 and 11 measure how many real users would grant it — that
measurement belongs in the results chapter, and the limitation in the conclusion.

### F-43 — AI natural-language search filters ✅
`Difficulty: Medium` · `Effort: 1 session`, as estimated

**Built as planned below**, with these differences from the plan:
- A failed AI search shows a short toast ("VI pretraga trenutno nije dostupna…") instead of
  doing nothing. Same lesson as F-39's cancellation notice: a tap that silently does nothing
  reads as a bug, not as "fell back to normal search".
- Location permission now has **one rule for both paths**: a `LaunchedEffect` on
  `filters.needsLocation` asks when any filter needs location. The check that used to sit inside
  the filter sheet's callback was removed, so manual and AI filters cannot behave differently.
- "Večeras" maps to `TODAY` in the prompt. The app still has no time-of-day filter.
- Typing in the field after an AI search clears the caption and **Poništi**, because the caption
  would otherwise describe a query that is no longer there.
- Gemini runs at `temperature(0f)` so the same sentence gives the same filters.
- `toFilters()` uses `entries.firstOrNull { it.name == … }` rather than `runCatching { valueOf }`
  — the same result and easier to read.

**Verified:** 34 `EventFiltersTest` (the original 30 plus 4 `WEEKEND` cases, written first) and
4 `ParsedSearchTest` pass; app JVM tests 46 → 54. Server compiles. `test_search_parse.py` adds 15
API checks (3 without a Gemini key). **Not yet run against a live server, not yet seen on the
phone.**

Requested: the user types "želim da slušam muziku blizu mene krajem nedelje" and the filters set
themselves.

**Gemini classifies, it never searches and never does date math.** It turns one sentence into
filter values the app already understands; `applyFilters()` is not modified.

```
"želim da slušam muziku blizu mene krajem nedelje"
        │  ✨ button or keyboard Search
        ▼
POST /search/parse {text}             one Gemini call, schema-locked answer
        ▼
{ keywords: "", category: "MUSIC", radius: "NEARBY", dateWindow: "WEEKEND" }
        ▼
ParsedSearch.toFilters()              domain, pure Kotlin, JVM-tested
        ▼
existing onFiltersChange → applyFilters() + F-32 semantic ranking on keywords
```

**Decisions (2026-09-21):**
- An AI search **resets** existing filters, then applies its own. The sentence is the full intent.
  **Poništi** restores the previous filters and the sentence.
- **Chips for every active filter**, manual or AI, each removable with `✕`. They are the AI's
  visible "reply", and they also fix today's gap where active filters hide inside the sheet.
- Triggered by the **✨ button and the keyboard Search action** only. Never on typing — filters
  would jump mid-sentence and every pause would cost a Gemini call.
- **Only `WEEKEND`** is added as a time window. No "sutra"/"večeras".
- **No chat screen.** The caption plus chips give the same result without a screen, a message
  history and another ViewModel.

**Server**
- `AiSuggestService.parseSearch(text)` — second method on the existing service, same Gemini
  client, its own `GenerateContentConfig`. The response schema already used by F-30 is the key:
  `category`, `radius`, `dateWindow` and `sort` are `enum_` lists of the app's names, so the model
  cannot invent a value. `keywords` is the only free-text field. Since 2026-09-22 the schema also
  has `price` (FREE, UP_TO_1000, UP_TO_5000), the same three limits as the manual filter (F-45);
  before that "besplatno" stayed in `keywords` and never became a filter.
- Prompt: set a field only when the text clearly implies it; remaining topic words go to
  `keywords`; 3–4 examples in Serbian and English.
- `POST /search/parse` in `AiRoutes.kt` — 400 blank or >200 chars, 503 without key, 502 when
  Gemini fails. Same contract as `/events/ai-suggest`.
- The model is never told today's date. "Krajem nedelje" becomes `WEEKEND`, and the phone's
  `Calendar` computes Saturday–Sunday.

**App**
1. `DateWindow.WEEKEND` in `EventFilters.kt`: on Saturday or Sunday it runs from now to Sunday
   23:59, otherwise from the coming Saturday 00:00 to Sunday 23:59. `Labels.kt` has an exhaustive
   `when`, so the compiler demands the new label. The filter sheet lists `DateWindow.entries`,
   so "Vikend" appears there for manual use too.
2. `domain/model/ParsedSearch.kt` with `toFilters()`. Unknown enum names are ignored via
   `runCatching { valueOf(...) }`, so a server and app out of step cannot crash anything.
3. `EventRepository.parseSearch(text): ParsedSearch?` — `null` on any failure, like `getReviews`.
4. `SearchViewModel.askAi()` — keeps `previousFilters` for undo, shows `isAiPending`, sends the
   result through the existing `onFiltersChange`. **On failure it does nothing**: the sentence
   stays in the field and the normal F-32 search is already running on it.
5. "Blizu mene" sets `NEARBY`. `SearchScreen` already asks for location when a distance filter
   is chosen, and the AI result goes through that same path, so there is no new permission code.

**UI**
```
┌──────────────────────────────────────┐
│ 🔍 želim da slušam muziku bl…  ✨ ✕ │  ✨ only with text; spinner while waiting
└──────────────────────────────────────┘                          ≡
 Razumem: „želim da slušam muziku…"   Poništi   ← only after an AI search
 [Muzika ✕] [Do 5 km ✕] [Vikend ✕]              ← every active filter
```
- The field keeps the typed sentence; only the filter carries `keywords` (`SearchViewModel.searchText`
  is separate from `filters.query`). Until 2026-09-22 the field was overwritten with `keywords`,
  so "muzika dans sa besplatnim ulazom" became "sa besplatnim ulazom" and looked cut off.
- Chips live in a new `ui/components/ActiveFilterChips.kt`; `SearchScreen` only places them.
- **Two searches, two buttons, side by side** in the field: the magnifier runs the ordinary search
  (words plus the F-32 semantic layer) and ✨ runs the AI search that sets filters. The keyboard's
  Search key runs the ordinary one, so the default costs no Gemini call. Running the ordinary
  search clears the AI caption, because the sentence no longer describes the active filters.
- The list keeps showing current results while Gemini answers instead of blanking.

**Tests**
- `WEEKEND` bounds, written **before** changing `dateWindowBounds`: Wednesday, Friday 23:00,
  Saturday, Sunday 22:00, all at fixed timestamps.
- `ParsedSearchTest`: full parse, empty parse, unknown enum ignored, keywords trimmed.
- The existing 30 `EventFiltersTest` cases must stay green — they are the regression guard.
- `test_search_parse.py`: structure and status codes only. The model's choices are
  nondeterministic, so asserting "MUSIC" would make a flaky test.
- Expected: app JVM tests go from 46 to about 54.

**Risks**
1. `dateWindowBounds` is shared by every date filter, so `WEEKEND` can break TODAY/WEEK/MONTH.
   Mitigation: tests first, fixed timestamps, existing tests green before anything else.
2. The model over-interprets ("koncert u subotu" also read as `NEARBY`). Mitigation: the prompt
   demands clear evidence, and every guess is a visible chip removable in one tap.
3. The server schema repeats the app's enum names as strings and can drift. Mitigation:
   `toFilters` ignores unknown names, and a comment on each copy points at the other.
4. Gemini takes 0.3–1 s. Mitigation: spinner on ✨, results stay on screen meanwhile.

**Not covered, on purpose:** price ("jeftino", "besplatno") has no filter in the app. The schema
does not offer one, so those words fall into `keywords` and go through semantic search instead.

**Files:** server `AiSuggestService.kt`, `AiRoutes.kt`. App `EventFilters.kt`, `Labels.kt`,
new `ParsedSearch.kt`, `OrbitApiService.kt`, a DTO, `EventRepository(Impl).kt`,
`SearchViewModel.kt`, `SearchScreen.kt`, new `ActiveFilterChips.kt`, strings in both locales,
two test files. No `ALTER TABLE`, no Room version bump.


### F-44 — Organiser profile ✅
Not requested by the mentor. Built because **F-40 barely worked without it**: reviews exist once
an event has started, but finished events are dropped from Explore
(`!AttendanceRules.hasEnded` in `applyFilters`) and History holds only events *you* attended.
So the people reviews are meant for — someone deciding whether to go to this organiser's next
event — could almost never reach them. Note that *reading* reviews was never limited to
attendees; only writing them is.

- **Entry:** the "Organizuje" row on the detail is now clickable, with a chevron. It is shown only
  for other people's events, so your own profile is never opened from here.
- **Server:** `GET /users/{id}/events` in `UserRoutes.kt` — that organiser's **public** events,
  past and future. `findByOwner` also returns private ones, so they are filtered out. A block in
  either direction answers 404 (`hiddenOwnerIds`, same as events and reviews).
- **App reads from Room, like everywhere else.** `refreshOrganiserEvents` upserts the list into
  Room and the screen observes the existing `observeEventsByOwner`. This is also what makes a past
  event openable: the detail screen reads only from Room, and without the upsert a finished event
  of someone else would show "Događaj nije pronađen". Explore is not affected, because it already
  hides finished events.
- **Screen** (`ProfileScreen.kt`, `ProfileViewModel.kt`): name in the top bar, overall rating,
  number of public events, and two tabs reusing `MyEventsTab` ("U toku" / "Završeni"); upcoming
  soonest first, finished newest first. Offline it shows what Room has plus a short notice. A user
  you blocked shows an empty state instead of the lists.
- **Overall rating is weighted** (`domain/model/OrganiserRating.kt`): every event counts in
  proportion to its number of ratings. A plain average of averages would let one 5★ rating
  outweigh forty 3★ ratings.
- **Verified:** `OrganiserRatingTest` (4 JVM tests), app 54 → 58. `test_profile.py` adds 11 API
  checks: past events listed, private ones hidden, blocking in both directions, unknown user.
  **Not yet run against a live server, not yet seen on the phone.**
- **Not built on purpose:** avatar, bio, following, and opening a profile from a review's author
  name. Each is small, but none was asked for.

### F-45 — Price filter ✅
A fourth group, "Cena", in the shared filter sheet: **Bilo koja · Besplatno · Do 1.000 RSD ·
Do 5.000 RSD**. The map opens the same `EventFilterSheet`, so the filter works there too with no
extra code.

- `PriceLimit` enum in `EventFilters.kt` with `maxRsd` (null = no limit); one extra line in
  `applyFilters`: `event.price ?: 0.0 <= maxRsd`. An event without a price counts as free, the
  same rule the detail and the card already use. The limit is inclusive and free events always
  pass a limit.
- Counted in `activeCount` (badge on the filter button) and shown as a removable chip in
  `ActiveFilterChips` on Search.
- Client-only: the server already sends `price` with every event.
- AI search (F-43) does not set it; asking the AI resets it like every other filter.
- **Verified:** 3 new tests in `EventFiltersTest`, app 58 → 61. **Not yet seen on the phone.**

### F-46 — "Top rated" sort removed ✅
The sort could never change the order. Search hides finished events (F-36), and ratings exist
only after an event starts (F-35), so almost every listed event had `avgRating` 0. Sorting by
the organiser's rating was considered: it needs a server aggregate, a new DTO field and a Room
column, and on the seed data most organisers would tie. Not worth it before the deadline.

- Removed `EventSort.TOP_RATED`, its label and strings, and its branch in `applyFilters`.
- Removed from the AI search schema and prompt (`SORT_NAMES` in `AiSuggestService.kt`) and from
  `test_search_parse.py`. An older server that still sends `TOP_RATED` falls back to Soonest in
  `ParsedSearch.toFilters`, so nothing breaks.
- Tests: the top-rated test is gone; "an explicitly chosen sort wins over the score" now uses
  Nearest. App 61 → 60.

### F-47 — Vibrant event cards ✅
User feedback: the screens felt "dead" and the colors too muted. Every event in the app is now
shown through **one** component, `ui/components/EventCard.kt`, which replaces `EventRow` and the
map's `EventPreviewCard` (both deleted).

- **Photo header** (144 dp): the first event photo, with a date tile (day + three-letter month)
  top-left and a solid category pill bottom-left. Without a photo, or while it loads, a category
  gradient with the large category icon fills the space, so the list never jumps.
- **Gradient background:** the whole card is a diagonal gradient of the category color, 8 % at
  the top right to 30 % at the bottom left, so the color is strongest under the text rather than
  behind the photo. The card also casts a shadow tinted with its category color.
- **Brighter palette:** the category colors in `Color.kt` moved back toward the logo's vivid hues.
  They also drive map pins, category chips and the detail badge, so those brightened too.
  Checked contrast: white on every light category ≥ 5.07:1, charcoal at 80 % on the strongest
  tint ≥ 4.74:1, every dark category ≥ 8:1 against the dark surface.
- **States on the card:** "U toku" (in progress, green dot), "Otkazan" (cancelled: grayscale
  photo, neutral card, red badge), full (red spots), free (green), private (lock), and distance on
  the map. Badges are near-white pills, readable on every category tint.
- **Used on:** Explore, Map (with ✕ on the photo; tapping the card replaces the old "View
  details" button), Plans (Upcoming, Attended with the attendance note), My events, Joined
  private events, organiser profile. List gaps are 16 dp everywhere (were 8 or 12).
- Small follow-ups found on the phone: the map's filter button hides while the card is open (it
  peeked out from under it), and the detail's category badge uses the same solid color as the
  card pill. Coil now crossfades photos in.
- **Verified on the phone** (light theme): every screen above, plus the cancelled state through
  a temporary local change that was reverted. **Dark theme not seen on the phone**, only checked
  by contrast math.
- Conflicts with the `orbit-design` skill, which asks for muted categories and soft tints only.
  The user's request is newer; the skill is not updated yet.

### F-48 — Event detail with Overview and Reviews tabs ✅
The detail was one long scroll: description, metadata, badges, rating, capacity, three stacked
full-width buttons for the owner, organiser, and the reviews about 1.5 screens down. It now
follows the Google Maps place sheet and continues the look of the F-47 card.

- **Hero** (scrolls away): the same category gradient as the card; a photo pager with the date
  tile, category pill and a "1/3" counter (tap opens the full photo); title; cancelled banner;
  rating line "4.7 ★★★★★ (3) ›" that opens the Reviews tab; the card's badges; and one action
  row: the state button (Register / Cancel registration / Confirm attendance / Guest list) next
  to a white Navigate pill. States without a button give Navigate the full width. The state is
  one small `RegistrationStep` enum, computed with the same checks in the same order as before.
- **Tabs:** `DetailTabRow`, a pill whose colored indicator slides between "Pregled" and
  "Utisci (n)". It is a `stickyHeader` in a single `LazyColumn`, so it stays at the top while
  the content scrolls. Switching tabs while they are stuck shows the new tab from its start.
  The content fades and slides; `rememberSaveableStateHolder` keeps a half-written review when
  the tab is hidden.
- **Overview** (`EventOverview.kt`): a facts card (When, Where, Price, Spots with the capacity
  bar, Access code) with icons in category-tinted circles, then the description, then an
  organiser card (initial avatar, name and › open the profile; Block is separate below a line).
  The owner's "Otkaži događaj" moved here, to the bottom; it is rare and destructive.
- **Reviews** (`EventReviews.kt`): a summary card (big average, stars, count, 5→1 bars built from
  the loaded reviews), the own review form in a card, then all other reviews. The "show all"
  bottom sheet is gone, since the tab is now the place for the list. Before the event starts
  the tab explains when reviews open.
- Price reads the same as on the card ("Besplatno" / "700 RSD"), no longer "Cena: 700".
  Serbian "Spisak prijavljenih (n)" became "Prijavljeni (n)", the term the guest list sheet
  already uses, so it fits the half-width button.
- The ViewModel is unchanged. New composables only take values and callbacks.
- **Verified on the phone** (English, light theme): attendee with a confirmed attendance and
  own review, open registration, owner, before-start Reviews tab, keyboard over the comment
  field, and the cancelled state through a temporary local change that was reverted.
  **Not seen:** Serbian texts in the half-width buttons, dark theme.

### F-49 — Account and organiser profile headers ✅
The Account tab read as a settings list (small avatar, one flat card, plain gray numbers), and
the organiser profile had its name only in the top bar, with the rating as fine print. Both now
share one header, so a person looks the same everywhere.

- `ui/components/UserHeader.kt`: `UserAvatar` (initial on a green→blue gradient with a white
  ring), `UserHeaderCard` (avatar, name, optional subtitle and edit button, a row of stats on a
  green→blue tinted gradient) and `StatTile` / `RatingStatTile` (value large, label small).
- Brand colors are two new roles in `OrbitAccents`, `brandStart` / `brandEnd`: the logo's land
  and blue pin, the same values as the Outdoor and Sport categories. No new hex values.
- **Account:** header with email and a pencil that opens the name sheet (the old › chevron looked
  like navigation). Stats: rating as organiser (public events only, the same number others see
  on the profile), organised, attended (`AccountViewModel.attendedCount`, one new flow from the
  existing `observeAttendedEvents()`). Menu items are separate cards with the icon in a colored
  circle and the count in a pill; Log out stays in its own card.
- **Organiser profile:** the top bar keeps only the back arrow; the header card shows the
  avatar, name, rating and number of public events. Tabs and cards are unchanged.
- The organiser card on the event detail uses the same `UserAvatar`.
- **Verified on the phone** (English, light theme): Account, organiser profile, organiser card.
  **Not seen:** Serbian texts, dark theme.

### F-50 — Event form redesign ✅
The three-step form was outlined fields on beige: a plain AI button, a sideways-scrolling
category row, raw coordinates, "Remove" text under each photo, and a map picker with empty
strips. The ViewModel and its validation are unchanged; only the UI moved.

- **Sections as cards** (`ui/components/EventFormParts.kt`, `FormSection`): white card, icon in
  a brand-green circle, title, fields. The accent is the brand green, not the category color:
  the default category (Other) is brown and made the whole form look beige until one was picked.
- **Step indicator:** 6 dp segments whose color animates; the step name is the page heading
  (titleLarge). Step content slides in from the side it comes from (`AnimatedContent`), and
  each step has its own scroll.
- **Step 1:** name and description card; the AI suggestion is its own card on the brand
  gradient with a "Predloži" button that shows a spinner while working; the error sits under it.
  `CategoryPicker` (replaces `CategoryChipRow`) wraps all eight categories, the selected one is
  the solid category pill from the card.
- **Step 2:** the start is a `PickerRow` (icon, "Početak", value, ›) so it does not look like a
  text field; duration has 1 h / 2 h / 3 h chips that fill the same fields as typing. The
  location card says "Lokacija izabrana ✓" with the coordinates as small secondary text.
- **Step 3:** `PhotoStrip` — thumbnails with a ✕ badge (44 dp touch target) and a "Naslovna"
  badge on the first photo, the one the card shows; Gallery and camera tiles at the end while
  there is room. Capacity and price have icons, price has an "RSD" suffix and "Prazno =
  besplatno". Visibility is two option cards with a globe and a lock.
- **Bottom bar:** Back with ←, Next with →, Save with ✓ and a spinner while saving.
- **Map picker:** the screen applied the status and navigation bar insets a second time
  (`safeDrawing`), which left empty strips above and below the map; removed, since
  `OrbitApp` already reserves both for this route. The hint is a compact pill.
- **Camera:** the shutter is a white ring with a white circle; ✕ has a dark circle behind it.
- **Verified on the phone** (English, light theme): all three steps, edit mode with a seeded
  photo, the map picker and the camera. Nothing was saved. **Not seen:** Serbian, dark theme.

### F-51 — Search bar, filters and bottom bar polish ✅
The structure stayed; only the parts still in the old flat style changed, so they match the
cards, profiles and form.

- **Search bar (Explore):** white pill with a warm shadow and no outline, like the address
  search on the map picker. The filter button sits in the same white circle as on the Map.
- **Filter sheet:** chips are pills; the selected one has a 15 % brand-green fill and a green
  border, the same as the form's chips (Material would pick dusty blue). Category chips carry
  their icon, and the selected one is the solid category color, as in `CategoryPicker`. Group
  headings are bold in the text color; the sheet title is larger than them.
- **Active filter chips** under the search bar use the same green pill.
- **Bottom bar:** the selected tab gets a small green pill behind its icon, and the tab color
  fades instead of jumping. Shape, height and the pin are unchanged.
- **Verified on the phone** (English, light theme): Explore, the sheet on Explore and Map, a
  selected category, the search field with text. **Not seen:** dark theme.

### F-52 — Auth screen redesign ✅
Log in, sign up and new password are one screen (`AuthScreen.kt`) in three modes, so one change
covers all three. No ViewModel or string changes.

- **Hero centered:** the logo sits in the middle of two thin "orbit" rings with three colored
  dots (planets); the scattered pins were removed. "Orbit" in brand green, subtitle in the text color.
- **Background:** vertical gradient, blue (`brandEnd`) at the top, green (`brandStart`) in the
  middle, fading to the plain background under the form. The tints stay at 20 % and 12 %, because
  more pushes the green title under 3:1.
- **Fields:** one private `AuthField` for all of them: white `OutlinedTextField` with an icon,
  a thin border that turns green on focus, red on error. The old filled field showed a cut
  underline under rounded corners.
- **Buttons:** main button is a 56 dp green pill with a tinted shadow, as in the event form.
  "Forgot your password?" moved under the password field, aligned right.
- **Not verified on the phone by the assistant:** it needs a logout, and logging back in
  requires the user's password.

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
7.–9. Moved to **Future work** below: they were never planned for the thesis.
10. [x] **Rating formatting** — the average shows one decimal on the detail, the Reviews tab and
        the profile header. The dead rating code was resolved by F-40.

**Bugs fixed (2026-09-22)**
- [x] A rejected edit looked saved: `CreateEventViewModel.save` ignored the server's answer. The
      form now waits and stays open with the reason (`EditResult`: `NoConnection`, `Rejected`).
- [x] An offline edit was stored as unsent and later re-sent as a new event (`POST`), which the
      server refused with 409 forever. Edits are no longer stored before the server accepts them,
      and the 409 branch in `pushEvent` now reads the response code — before, it waited for an
      `HttpException` that a `Response<…>` call never throws.
- [x] Own events deleted from another phone stayed in the local cache
      (`EventDao.deleteOwnMissingOnServer` during `syncAccountData`).
- [x] The spots badge showed a bare "3" for unlimited events, which survey respondents read as
      "3 spots left"; it now shows "3/∞", the same taken/total shape as "3/25".
- [x] `ExampleInstrumentedTest` checked the namespace instead of the applicationId
      (`io.github.igicut.orbit`) and failed on a device.
- [x] The empty Plans list pointed to a "History" tab that does not exist.

**Conscious simplifications (document for the defense rather than build)**
- [ ] Token stored in app-private `SharedPreferences`, not encrypted
- [ ] No refresh token; the app learns about an expired token only at the next request
- [ ] Password reset (F-13) does not check identity: whoever knows the email can set a new
      password. The real fix is a one-time code sent by email
- [ ] GPS check-in can be spoofed by a modified client; the QR check-in (F-41) does not prove
      presence either, because a photo of the QR works from home
- [ ] `JWT_SECRET` must be set in the server run configuration, otherwise every restart logs
      everyone out
- [ ] Uploaded images are trusted on their `Content-Type` alone; the server never looks at the
      bytes, so a signed-in user could store any file as "image/jpeg"
- [ ] Photos are uploaded in full resolution (no downscaling) to keep the EXIF orientation;
      8 MB is the ceiling and a rejected photo is silently dropped from the event
- [ ] If the connection drops between `POST /images` and `POST /events`, the uploaded file stays
      on the server with nothing pointing at it; nothing cleans up such orphans

### Future work (not planned for the thesis)
Ideas that came up during development and in the survey. None of them is part of the thesis
scope; they belong in the conclusion as possible extensions.

- **Reminder "The event started — confirm attendance"** — a second window in `ReminderChecker`
  (registered, started in the last 30 min, not attended). People who forget to check in cannot rate.
- **Tell registered users about changes** — compare the cached start time and place with the
  fresh copy during `syncAccountData()` and notify when an event moved or disappeared (today only
  cancellation notifies).
- **Notifications about new events nearby** — the survey's most common problem was hearing about
  an event too late (51.2 %).
- **Recommendations based on interests** — the survey's highest-rated wish (*M* = 4.43); the
  `interests` field exists but nothing fills it.
- **Organiser manual check-in from the guest list** — less needed since the QR check-in (F-41).
- **Account management** — leave a joined private event, change the password while logged in,
  delete the account.

### Later steps from the consultation plan
- [x] AI natural-language search (F-32)
- [x] Map replaced — Mapbox Maps SDK v11, not Google Maps (no billing account needed)
- [x] Image upload to the server (F-37)
- [x] UI pass (palette, event cards, floating bottom bar; App Bar is F-38)

### From the second consultation
All of Module 16 (F-38…F-43) is required for the thesis, not optional.

Bluetooth/Nearby P2P sharing (old F-22…F-24) was dropped on 2026-09-21 and its module removed
from this file. Private events are shared by access code only (F-20/F-21).

### Development notes
- **Unit tests on this machine:** the Windows `PATH` contains stray quotes
  (`"C:\Java\jdk1.8.0\bin` … `C:\Windows\System32\Wbem"`). Gradle passes it to test workers
  as `java.library.path` and they fail with `Could not find or load main class Files\dotnet…`.
  Fix the PATH, or run from PowerShell:
  `$env:Path = $env:Path -replace '"', ''; .\gradlew.bat --no-daemon :app:testDebugUnitTest`
- New server tables are created by `SchemaUtils.create`, but new **columns** on existing tables
  need a manual `ALTER TABLE` (keep `schema.sql` in sync).
- Emulator reaches the host at `10.0.2.2`, not `localhost`.
- Uploaded photos land in `OrbitKtorServer/uploads/` (git-ignored). Deleting the folder loses
  the photos of existing events; the rows then point at files that answer 404.
- **Check-in demo:** rerun `seed.sql` about 5 minutes before the demo, set the emulator location
  to 44.8189, 20.4587 (Extended Controls → Location, or `adb emu geo fix 20.4587 44.8189`),
  log in as `ana@orbit.test` (walk-in, first 15 minutes) or `milica@orbit.test` (registered).

---

## Decisions made along the way (change them if you disagree)

- **Category list** — MUSIC, SPORT, FOOD, ART, TECH, OUTDOOR, SOCIAL, OTHER (`EventCategory`).
- **Image list storage in Room** — joined with newlines, because a content URI may contain a
  comma but never a newline. The server stores a JSON array.
- **Images are stored as relative paths** (`/images/<uuid>.jpg`), not absolute URLs — the server
  cannot know whether the phone reaches it as `10.0.2.2` or a LAN address, and a stored absolute
  URL would break the moment `BASE_URL` changes. `ImageUrls.model()` joins the two.
- **Photos go up in their original bytes** — downscaling would need `ExifInterface` and a bitmap
  rotation, otherwise portrait photos would upload sideways.
- **Images stay behind the token** like every other route, which is why Coil has to use the
  app's `OkHttpClient`; the cost is that images do not load after logging out.
- **Semantic search decides by relative lead, not an absolute similarity cutoff** — the model's
  scores sit too close together for a fixed threshold to separate signal from noise (F-32 has
  the measurements).
- **The score travels to the app as data, not as row order** — the list is rendered from Room
  and re-sorted locally, so a ranked response would lose its order on the way to the screen.
- **`SimpleDateFormat` over `java.time`** — `java.time` needs API 26 or desugaring; `minSdk` is 24.
- **Own Ktor + JWT instead of Firebase** — no second identity system, and the server verifies
  every token itself.
- **One registration action for all events** — capacity only limits spots; saving and reserving
  meant the same thing.
- **Check-in radius 200 m, walk-in window 15 min, default duration 3 h** — constants in
  `AttendanceRules` and `RegistrationRoutes`, change both together.
- **Duration as hours + minutes, max 7 days** — `EventDuration` and `EventRoutes`; clearing both
  fields removes the duration.
- **Attended events are a tab on Plans**, not a section in Account — they sit next to Upcoming,
  which they complement.
- **Guest list is organiser-only** — names of guests at a private event are not for everyone.
