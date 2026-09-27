# Handoff — review points 8, 9, 10, 12

Written 2026-09-27 at the end of a session. Read `CLAUDE.md` first (project rules), then this
file, then `CHANGES.md` (one section per finished step; written for the thesis).

## Status at a glance

| Point | What the reviewer asked | Status | Left to do |
|---|---|---|---|
| 8 | Integration and negative tests + summary table (scenario / expected / actual / PASS-FAIL); especially the race for the last seat, other users' and private resources, token expiry, offline/online sync, failed photo uploads | **Done** (`CHANGES.md` steps 6, 7, 11) | Nothing; 347 API checks and 10 instrumented tests pass |
| 9 | Semantic search: separate tuning and independent test set; precision + recall/F1/lost | **Done** (`CHANGES.md` step 9) | Nothing; the results are uncommitted |
| 10 | Sentence → filters: % correct for category, price, time, distance, on varied and ambiguous sentences | **Done** (`CHANGES.md` step 10) | Nothing; the results are uncommitted |
| 12 | GPS / QR / geofencing: separate "implemented" from "verified in real conditions"; device tests for geofence entry, background location off, early arrival, phone reboot | **In progress** | Protocol written (`DEVICE_TESTS.md`); the user walks it, then the two fixes |

## Point 8: what is left

Done (see `CHANGES.md` steps 6 and 7):
- 16 API test suites, **347 checks**, all passing
- `run_all.py` writes `OrbitKtorServer/scripts/api-tests/results/test-results.md`, the summary
  table the reviewer asked for, and `test_access.py` writes `results/access-matrix.md`
- new suites: `test_access.py`, `test_tokens.py`, `test_concurrency.py`, `test_upload_failures.py`

**Done on 2026-09-27** (SM-G973F, Android 12, API 31): 10/10 instrumented tests pass, table in
`CHANGES.md` step 11. Gradle uninstalled the app afterwards; it was reinstalled, and the account
needs to log in again.

**Historical note, kept for the method:**
1. **Run the instrumented tests on the physical phone:**
   `cd Orbit && ./gradlew --no-daemon :app:connectedDebugAndroidTest`
   - `TokenCipherTest` (5 tests): the JWT is encrypted with an Android Keystore key
   - `EventSyncTest` (4 tests): offline→online sync, lost reply (409), rejected photo, event the
     server rejects (400). This last test posts a real "Sync test" notification on the phone,
     which is expected.

   They run inside the installed app's process. They must never start a session, write the
   token or cause a 401: that would log out the real account on the phone.
2. **Put the instrumented results into the summary table.** `test-results.md` currently has only
   API rows. Add a section with the 9 instrumented tests (scenario / expected / actual /
   PASS-FAIL), either by hand from the Gradle report
   (`Orbit/app/build/reports/androidTests/connected/`) or with a small script. Record the result in
   `CHANGES.md` steps 6 and 7 ("Not run yet" → actual result).
3. Known, documented gap: the app's reaction to 401 (automatic logout) is not tested
   automatically, for the reason in 1. The server side of token expiry is fully covered by
   `test_tokens.py`.

## Points 9 and 10: done, only commit left

- Labels frozen in commit `31cf624`; results in `OrbitKtorServer/scripts/eval/results/`.
- Corpus: database `orbit_eval` = `db/schema.sql` + `seed.sql` **from commit `27ca613`** +
  `events_catalog.sql` from `31cf624`. See `OrbitKtorServer/scripts/eval/README.md`.
- **Do not change thresholds (`SemanticRanking.kt`) or the search prompt (`AiSuggestService.SEARCH_PROMPT`)
  on the basis of these results.** The test set would stop being independent. Any re-tuning
  needs new test queries, labelled and committed before running.

## Point 12: the full plan (not started)

### Part A: "implemented" vs "verified" table

Add to `Orbit/FEATURES.md` (F-34 GPS check-in, F-41 QR check-in, F-42 geofencing; F-42 is
still marked ⏳) and to `CHANGES.md` a table with these columns:

*Feature | Implemented | Automated test | Verified on a real device (date, phone model, Android
version, conditions, result)*

**Ask the user** what has already been tried on the phone and when. The last session did not
know. Only fill the last column with what was really done.

What exists as automated tests: `test_attendance.py` and `test_concurrency.py` cover GPS check-in
rules on the server (time window, 200 m radius, capacity, walk-ins); `test_checkin_qr.py`
covers QR; `AttendanceRulesTest` (JVM) covers the app's rules. Nothing covers geofencing.

### Part B: device test protocol, carried out by the user

Every row records expected result, actual result, PASS/FAIL, and a screenshot or logcat excerpt.
Mock locations are rejected on purpose (`location.isMock` in `GeofenceReceiver.kt`), so the
geofence tests need real walking.

| # | Scenario | Setup | Expected | Prediction from the code |
|---|---|---|---|---|
| 1 | Entering the geofence | registered event that has already started; walk in from > 500 m, app in the background | "Checked in" notification within a few minutes | should pass |
| 2 | Same, app killed from recents | as 1 | as 1 | should pass |
| 3 | Background location off | location permission set to "only while using" | no automatic check-in; the manual button works; the account screen shows automatic check-in as off | should pass |
| 4 | **Early arrival** | arrive 20 min before the start and stay | check-in once the event starts | **fails** |
| 5 | **Phone reboot** | register, reboot, do not open the app, walk in | check-in | **fails** |
| 6 | Manual GPS check-in at 150 m and 250 m | outdoors | 150 m accepted, 250 m refused | should pass |
| 7 | Poor accuracy (indoors, over 100 m) | inside a building | refused with the accuracy message | should pass |
| 8 | QR: printed code; another event's code; before the start | printed PNG | accepted / refused / refused | should pass |
| 9 | Created offline, then online | airplane mode | syncs; a private event gets its code | should pass |

`db/odbrana.sql` makes an event that starts 10 minutes after it runs, with QR entry code
`ODBRANA7`. It is useful for 1, 2 and 8.

### Part C: the two predicted failures and their fixes

Recommended order, still **to confirm with the user**: run the protocol first, so the failures
are recorded; then fix; then run 4 and 5 again. A "fail → fix → pass" record is stronger in a
thesis.

- **Early arrival (4).** `GeofenceReceiver.kt` reacts only to `GEOFENCE_TRANSITION_ENTER`.
  Check-in opens only at the start (`AttendanceRules.canCheckIn`, and the same rule on the
  server). An entry before the start is refused and only logged (`GeofenceReceiver.kt`, about
  line 66). The person is then inside, so no second ENTER comes.

  **Fix:** when an automatic check-in is refused because it is too early, schedule a one-time
  WorkManager job at the event start that calls `EventGeofences.refresh()`. Re-registering uses
  `GeofencingRequest.INITIAL_TRIGGER_ENTER`, which fires ENTER again if the phone is already
  inside. `ReminderWorker` runs only hourly, which is too coarse for this.
- **Reboot (5).** Android clears registered geofences on reboot. `EventGeofences.refresh()` runs
  only at app start (`OrbitApplication.kt` about line 73), after registration
  (`EventDetailViewModel.kt` about line 234), and from the account screen. There is no
  `BOOT_COMPLETED` receiver.

  **Fix:** a small `BroadcastReceiver` for `android.intent.action.BOOT_COMPLETED` that calls
  `geofences.refresh()` (Hilt `@AndroidEntryPoint`, `goAsync()` as in `GeofenceReceiver`), the
  `RECEIVE_BOOT_COMPLETED` permission, and the receiver registered in `AndroidManifest.xml`.

Relevant code: `Orbit/app/src/main/java/com/example/orbit/data/location/EventGeofences.kt`
(`refresh()`, 24 h window, ENTER only, radius `AttendanceRules.CHECK_IN_RADIUS_METERS` = 200 m,
at most 20 geofences), `GeofenceReceiver.kt`, `domain/model/AttendanceRules.kt`,
`data/notification/ReminderWorker.kt`.

## Other open items

1. **`OrbitKtorServer/db/seed.sql` fixed on 2026-09-27** (the user approved). Event `…011` now
   closes the INSERT, the bare `;` is gone, and the two registration rows that pointed at the
   commented events `…003` and `…012` are commented out. A fresh load gives 6 users, 10 events,
   18 registrations. The API tests still expect the 12-event seed, so they keep loading
   `git show 27ca613:OrbitKtorServer/db/seed.sql`.
2. **The user's `orbit_database` still needs a column** (step 4), or every login fails. Checked
   on 2026-09-27: the column does not exist. The user runs:
   `ALTER TABLE user_credentials ADD COLUMN password_changed_at BIGINT NOT NULL DEFAULT 0;`
3. **Uncommitted:** `CHANGES.md`, `Orbit/FEATURES.md`, `OrbitKtorServer/scripts/eval/README.md`,
   `OrbitKtorServer/scripts/eval/results/`, `HANDOFF.md`, and the `CLAUDE.md` pointer to this
   file. The assistant never commits: suggest a message.

## How to run things (as used in the last session)

Databases created by the assistant: `orbit_test` (API tests, seed only) and `orbit_eval`
(evaluation corpus). The user's own `orbit_database` was never touched.

```powershell
# server on the test database (PowerShell, from OrbitKtorServer/)
$env:Path = $env:Path -replace '"', ''; $env:DB_URL = 'r2dbc:mysql://localhost:3306/orbit_test'; ./gradlew --no-daemon run
```

```bash
# API tests (bash, from OrbitKtorServer/scripts/api-tests); reload the seed first
export MYSQL_PWD=$DB_PASSWORD ORBIT_DB=orbit_test
git show 27ca613:OrbitKtorServer/db/seed.sql | mysql -u root orbit_test
python run_all.py
```

`test_tokens.py` needs `JWT_SECRET` in the terminal (the same value as the server).
`test_upload_failures.py` must run on the server's machine.

## Rules that matter for this work

- Code must be junior-friendly and explainable in two sentences; follow `Route → Service → Table`
  and `Api → Repository → ViewModel → Screen`.
- Code comments: one short Serbian sentence, no diacritics. Server messages returned to the app:
  Serbian latinica, no diacritics. App strings: both `values/strings.xml` and `values-sr/strings.xml`.
- After each finished piece, add a section to `CHANGES.md`: review point, problem, solution,
  files, verification, known limitations. Only write "verified" for what was actually run.
