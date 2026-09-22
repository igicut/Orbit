# Orbit — project memory

Student project defended as coursework. Two modules in one repository:

- `Orbit/` — Android client: Kotlin, Jetpack Compose, Material 3, MVVM, Hilt, Room, Retrofit, Coil 3, Mapbox Maps SDK v11.
- `OrbitKtorServer/` — Ktor 3.5 REST server: Exposed 1.3 over R2DBC to MySQL 9, JWT auth, Gemini for AI suggestions and search embeddings.

Dependency direction in the app: `ui → data → domain`. `domain/model` imports nothing from `data` or `ui`.

## Code complexity — the top priority

This project is defended live by a student who must understand and explain every line. Code
that cannot be explained is worse than no code, however elegant it is.

- Write **junior-friendly, basic, straightforward** logic. Readability beats cleverness every time.
- No complex architectural patterns, no senior-level abstractions, no over-engineered workarounds.
- Prefer a longer obvious function over a shorter clever one. Prefer an `if` over a strategy object.
- Follow the patterns already in the codebase: `Route → Service → Table` on the server,
  `Api → Repository → ViewModel → Screen` in the app. Do not invent a new layer.
- Reuse what exists before adding anything. Check for the existing function, table or route first.
- If a solution cannot be explained to an examiner in two sentences, it is the wrong solution.

## Language

- Conversational responses, explanations and planning: **English**, to keep token use down.
- Code comments: **one short Serbian sentence, no diacritics** (`c` not `ć`, `z` not `ž`). Comment only non-obvious intent, constraints, workarounds or regression context — never restate the code.
- Repo documentation (`FEATURES.md`, READMEs, this file): English.
- User-facing strings: English in `values/strings.xml`, Serbian latinica in `values-sr/strings.xml`.
  Android needs the default file, so every new string still needs both — the Serbian file is the
  one users read.
- Server-side user-facing text (error messages returned to the app): Serbian latinica, no diacritics.
- Avoid gendered forms for people in every language.

## Secrets — never commit, never print

| Secret | Where it lives |
|---|---|
| MySQL password | environment only (`DB_PASSWORD`); `application.yaml` keeps an empty default and is tracked |
| `JWT_SECRET` | environment only |
| `GEMINI_API_KEY` | environment only |
| Mapbox public token | `mapbox.accessToken` in `Orbit/local.properties` (gitignored); injected as `R.string.mapbox_access_token` via `resValue` |

Set the DB password once as a user environment variable; the server and the API tests read it from there:

```powershell
setx DB_PASSWORD "<password>"
```

The API test runner needs `MYSQL_PWD`; in bash use `export MYSQL_PWD=$DB_PASSWORD`.

Nothing is ever committed by the assistant — the user commits. Suggest a message instead.

## Commands

Windows `PATH` contains stray quotes that crash the Gradle test worker. Always strip them:

```powershell
$env:Path = $env:Path -replace '"', ''
```

Run Gradle with `--no-daemon`.

| Task | Command (from the module directory) |
|---|---|
| Build + unit test the app | `./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest` |
| Install to a connected phone | `./gradlew --no-daemon :app:installDebug` |
| Build + test the server | `./gradlew --no-daemon compileKotlin test` |
| Run the server | `./gradlew --no-daemon run` (needs MySQL up, `JWT_SECRET`, `GEMINI_API_KEY`) |
| API tests | `cd OrbitKtorServer/scripts/api-tests && python run_all.py` (needs the server running and `MYSQL_PWD`) |

Database load order: `db/schema.sql` → `db/seed.sql` → optionally `db/demo.sql` (recording fixtures, ids `d3m0`) → optionally `db/events_catalog.sql` (70-event search corpus, ids `ca7a`).

Every seeded event references photos from `OrbitKtorServer/db/seed_images/`. The server serves only from `uploads/` (gitignored), so after loading the SQL copy them there, or event photos show as empty tiles:

```bash
cd OrbitKtorServer && cp db/seed_images/*.jpg db/seed_images/*.png db/seed_images/*.webp uploads/
```

`seed.sql` deletes `5eed%`, `demo.sql` deletes `d3m0%`, `events_catalog.sql` deletes `ca7a%`. The three prefixes must stay distinct or one script wipes another's data.

## Device setup

Testing happens on a **physical phone over USB**, not the emulator.

- `BASE_URL` comes from `orbit.baseUrl` in `Orbit/local.properties` (gitignored). Default when absent is `http://10.0.2.2:8080/` for the emulator; the phone uses `http://127.0.0.1:8080/`.
- The `adbReverse` Gradle task maps `tcp:8080` from phone to computer and is wired to every `install*` task, so no manual `adb reverse` is needed from the terminal. Android Studio's Run button may bypass it — if so, add it as a Before-launch external tool.
- `network_security_config.xml` permits cleartext only for `10.0.2.2`, `127.0.0.1` and `localhost`.

## Tests

- App: **65 JVM tests** — `EventFiltersTest` 36, `AttendanceRulesTest` 7, `EventDurationTest` 5, `CheckInQrTest` 5, `ParsedSearchTest` 4, `OrganiserRatingTest` 4, `ImageUrlsTest` 3, template 1.
- Server: **11 JVM tests** — `SemanticRankingTest` 10, `ServerTest` 1 (needs MySQL).
- API: **167 checks** across `test_registration`, `test_attendance`, `test_checkin_qr`, `test_duration`, `test_images`, `test_search`, `test_search_parse`, `test_profile`, `test_blocking`, `test_auth_rate_limit`. `test_search_parse` runs only 3 of its 15 without `GEMINI_API_KEY`.

API tests assume a database loaded with **only** `seed.sql`. With `demo.sql` or `events_catalog.sql` on top, the checks that enumerate exact seed registrations and attendances fail, and `test_search` fails on the larger corpus. Those failures are environmental, not regressions.

`seed.sql` ships a demo block "Ana blocked Marko". Since F-28 is enforced server-side in both directions, API tests must not use that pair as organiser and participant — `test_registration` and `test_attendance` use Jelena as organiser for this reason.

## Conventions that have bitten before

- Never invent hex colors. The palette lives in `ui/theme/Color.kt`, roles in `Theme.kt`, non-Material roles in `OrbitAccents.kt` (category colors, registration status, shadow). Every light value needs a dark counterpart.
- Shadows go through `Modifier.warmShadow(elevation, shape)` in `ui/theme/Shadow.kt` — never a plain black `Modifier.shadow` on the warm palette.
- A custom bottom bar built on `Surface` does **not** apply system insets the way `NavigationBar` does. Edge-to-edge is on, so add `windowInsetsPadding(WindowInsets.navigationBars)` or the bar lands under the system navigation.
- `OrbitApp.kt` wraps the NavHost in a `Scaffold` whose `innerPadding` already reserves the status bar. A `TopAppBar` inside a screen must pass `windowInsets = WindowInsets(0)`, otherwise the status-bar inset is counted twice and the screen top sits ~36 dp too low. Screens without a top bar (Explore, Map, Plans) never showed this, which is what made the app look inconsistent.
- `SchemaUtils.create` creates missing tables but never alters existing ones. New columns and indexes on existing tables need a manual `ALTER TABLE`.
- Every event is shown through `ui/components/EventCard.kt` (lists, plans, profile and the map preview). Never add a second event card; extend this one with an optional parameter instead.
- Mapbox needs a bitmap for annotation icons; `ui/components/MapView.kt` renders the existing vector pin drawables into one.
- Design work follows the `orbit-design` skill. It covers palette, typography, spacing, shadows and component styling — it does not license navigation or layout restructuring, which must be requested explicitly.

## Where the documentation lives

- `Orbit/FEATURES.md` — the only backlog: features F-01…F-52 with status, data model, open work, development notes.
- `C:\Users\Igor\Desktop\propratno\CODE_MAP_sr.md` — code map in Serbian, one line per declaration, outside the repository. Keep it in sync after feature work.
- `OrbitKtorServer/README.md` is still the project-generator template and is out of date.
