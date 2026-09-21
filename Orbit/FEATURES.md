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
- **History / Posećeni** tab next to Registered.
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
- **App:** badge on `EventPreviewCard` and at the top of the detail; Register button disabled;
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

### F-41 — QR check-in ⏳
`Difficulty: Medium-High` · `Effort: 1–2 sessions` · new dependency plus camera work
Requested: the organiser generates a QR code, visitors scan it at the entrance, and it must work
when location is unavailable. Also downloadable as an image so it can be printed.

- **Schema:** `events.check_in_code CHAR(8)`, random, written when the event is created
  (manual `ALTER TABLE`; existing rows need a backfill `UPDATE`).
- **Organiser:** "QR za ulaz" on the detail renders a QR from `orbit:<eventId>:<code>`, plus
  **Save as image** → PNG into the gallery via `MediaStore`, so it can be printed and taped to the door.
- **Visitor:** scanner screen, decode, then `PUT /events/{id}/attendance {code}` — **no coordinates**.
- **Server:** the same endpoint now accepts either `{latitude, longitude}` or `{code}`. Everything
  else is untouched: time window, free spot, 15-minute walk-in, one row per person. A wrong code is 403.
- **Photo-of-the-QR problem:** the code is regenerated every time the organiser opens the QR
  screen, so a screenshot taken yesterday no longer works. One `UPDATE`, and it is honest to
  explain at the defense.
- **Open decision — library:** ML Kit barcode scanning (bigger, needs Play Services, works well
  with the CameraX code already in `data/camera/`) or ZXing (smaller, older). Generating the
  bitmap is `zxing-core` either way.

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
  cannot invent a value. `keywords` is the only free-text field.
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
- The field is replaced with `keywords` (often empty); the caption keeps the sentence visible.
- Chips live in a new `ui/components/ActiveFilterChips.kt`; `SearchScreen` only places them.
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
10. [ ] **Rating formatting** (S). Show the average with one decimal. The dead rating code
        this item also listed was resolved by F-40: `getRatings` is used, the rest was removed.

**Conscious simplifications (document for the defense rather than build)**
- [ ] Token stored in app-private `SharedPreferences`, not encrypted
- [ ] No refresh token; the app learns about an expired token only at the next request
- [ ] No password change / reset and no account deletion
- [ ] GPS check-in can be spoofed by a modified client (QR from the organiser would fix it)
- [ ] `JWT_SECRET` must be set in the server run configuration, otherwise every restart logs
      everyone out
- [ ] Event rows and the map card do not show spots ("12/30") — belongs to the UI pass
- [ ] Uploaded images are trusted on their `Content-Type` alone; the server never looks at the
      bytes, so a signed-in user could store any file as "image/jpeg"
- [ ] Photos are uploaded in full resolution (no downscaling) to keep the EXIF orientation;
      8 MB is the ceiling and a rejected photo is silently dropped from the event
- [ ] If the connection drops between `POST /images` and `POST /events`, the uploaded file stays
      on the server with nothing pointing at it; nothing cleans up such orphans

### Small leftovers
- [ ] Detail top bar title falls back to a hard-coded "Event" string

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
- **History is a third tab**, not a section in Account — it sits next to Registered, which it
  complements.
- **Guest list is organiser-only** — names of guests at a private event are not for everyone.
