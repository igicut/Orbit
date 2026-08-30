# TODO — deferred work

Everything here was skipped on purpose while implementing Phase 1. Each item says **why**
it was skipped, because "needs a decision from you" and "needs infrastructure that does not
exist yet" are very different problems.

Ordered roughly by when you should tackle them.

---

## Blocked: the backend does not exist yet

These cannot be written until Module 3 exists. Nothing about them is hard — they just have
no API to talk to.

### F-11 / F-12 / F-13 — Ktor server
**Why skipped:** this is a whole second program, not a feature. It needs its own Gradle
module (or its own project), its own dependency set, its own DB schema. Decisions you need
to make first:

- [ ] Separate Gradle module inside Orbit, or a completely separate project? *(A module is
      easier to run from one IDE window; a separate project is easier to explain as "the
      backend" in your thesis.)*
- [ ] H2 or SQLite for the server DB
- [ ] Exposed DSL or Exposed DAO API
- [ ] How the phone reaches your laptop during the demo — laptop IP over shared Wi-Fi is
      normal, but note that `localhost` on an emulator means the emulator itself; you need
      `10.0.2.2` to reach the host machine

### F-14 — Retrofit client
- [ ] Write `EventApiService` to match whatever F-12 ends up exposing
- [ ] Hilt module providing Retrofit + OkHttp
- [ ] **Decision:** where does the base URL live? A `buildConfigField` is the usual answer,
      so debug builds point at your laptop and you never ship a hardcoded IP

### F-15 — Repository sync
The seam already exists: `syncPublicEvents()` and `pushEvent()` are declared in
`EventRepository` and are empty (not throwing) in `EventRepositoryImpl`.

- [ ] Implement both against `EventApiService`
- [ ] Call `pushEvent` after save in `CreateEventViewModel` — the call site is already
      marked with a TODO
- [ ] **Decision:** retry policy. What happens to an event stuck at
      `syncedToBackend = false`? Retry on next app open, on a timer, or manually?

### F-21 — Join by access code
- [ ] Needs `GET /events/by-code/{code}` on the server first
- [ ] Generation side is **done** (F-20) — creating a private event already produces and
      displays a 6-character code

---

## Blocked: needs real hardware or a permission flow

### F-16 / F-17 — Location
- [ ] Add `play-services-location` dependency
- [ ] Runtime permission flow — manifest permissions are already declared, but asking for
      them at runtime is not written
- [ ] **Decision:** Accompanist Permissions or the plain Activity Result API? Accompanist
      is less code; the Activity Result API is one less dependency and is what the official
      docs now use
- [ ] **Decision:** what happens when the user says no? The create form currently requires
      typed coordinates, which is a workable fallback — decide if that stays
- [ ] Once done: prefill lat/lng in `CreateEventScreen` (TODO marked in the file) and centre
      `MapScreen` on the user instead of the first event

### F-22 / F-23 / F-24 — Nearby Connections (P2P)
**Why skipped:** this is the single riskiest part of the project and it cannot be developed
against an emulator. Your own workplan flags it. Do the spike early even though the feature
is week 3.

- [ ] Add `play-services-nearby`
- [ ] Get two physical devices to discover and connect — nothing else, no payload
- [ ] Only then: serialise an `Event` as a BYTES payload
- [ ] Confirmation dialog on receive, saving via `EventRepository`
- [ ] **Decision:** the runtime permission set for Nearby changes significantly across API
      levels (Bluetooth permissions split at API 31, `NEARBY_WIFI_DEVICES` at 33). Work out
      the matrix for your `minSdk 24` / `targetSdk 37` range before writing the flow
- [ ] **Decision:** payload format. JSON is easiest to debug; you would add
      `kotlinx-serialization` for it

### F-25 / F-26 — WorkManager + notifications
- [ ] Add `work-runtime-ktx` and `hilt-work` (Hilt cannot inject a plain Worker without it)
- [ ] `POST_NOTIFICATIONS` runtime permission for API 33+
- [ ] **Decision:** "new nearby event" needs a definition — new since when? You need to
      store a last-checked timestamp somewhere
- [ ] Deep link from notification into `EventDetailScreen`

---

## Routine, but pointless until other people exist

Right now every event in the database was created by you, on this device. These features
are not hard — they just have nothing to act on until the backend (F-15) or P2P (F-23)
brings in events owned by somebody else.

### F-27 — Ratings
- [ ] `RatingDao` is **done** and includes `getByUserAndEvent` to enforce one rating per
      person per event
- [ ] 5-star input on `EventDetailScreen` — placement is marked with a TODO
- [ ] **Decision:** `avgRating` / `ratingCount` are computed from *everyone's* ratings, so
      the real recalculation belongs on the server (F-12's `PATCH /events/{id}/rating`).
      Decide whether local ratings show optimistically before the server confirms

### F-28 — Block user
- [ ] `BlockedUserDao` is **done**, including `observeBlockedIds` as a Flow so the list can
      filter reactively
- [ ] Action on the owner row in `EventDetailScreen` — placement is marked with a TODO
- [ ] Filter blocked owners out of `EventListViewModel`

### F-29 — Filter bar
- [ ] Category filter is genuinely routine and could be done now
- [ ] Radius filter needs F-17 (location) to mean anything
- [ ] Date range filter is routine

---

## Small things left inside finished features

- [ ] **F-18** — tapping a map marker jumps straight to the detail screen. The spec asks for
      a preview card first. Marked with a TODO in `MapScreen.kt`
- [ ] **F-08** — the form creates events but cannot edit them. `CreateEventViewModel` was
      built around a single form-state object specifically so it can be reused for editing;
      it needs an optional `eventId` and a "load existing" path
- [ ] **F-09** — there is no edit button yet, for the same reason
- [ ] Room uses `fallbackToDestructiveMigration(dropAllTables = true)`. Fine now; it wipes
      all data whenever you change an entity. Worth a sentence in your thesis about why
      real migrations matter
- [ ] No tests yet. Your `EventRepositoryImpl` and `CreateEventViewModel.validate()` are the
      two things most worth testing and the easiest to test

---

## Decisions I made for you (change them if you disagree)

- **Category list** — I picked MUSIC, SPORT, FOOD, ART, TECH, OUTDOOR, SOCIAL, OTHER.
  Entirely arbitrary; edit `EventCategory`.
- **Access code alphabet** — excludes `I` and `O` so they cannot be confused with `1` and
  `0` when someone reads a code aloud.
- **Image list storage** — joined with newlines rather than commas, because a content URI
  may legally contain a comma but never a newline.
- **`SimpleDateFormat` over `java.time`** — `java.time` needs API 26 or core library
  desugaring, and your `minSdk` is 24. Switch if you enable desugaring.
- **Coordinates are typed by hand** in the create form. That is a placeholder for F-17, but
  it also doubles as the permission-denied fallback.
