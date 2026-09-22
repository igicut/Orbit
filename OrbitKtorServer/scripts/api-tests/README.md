# API tests

End-to-end checks against a running Orbit server and its MySQL database: registration (F-33),
attendance check-in and rating rules (F-34, F-27), event duration (F-35), image upload (F-37),
natural-language search (F-32) and the login rate limit (F-13). Plain Python 3, no packages to
install.

`test_search.py` needs the server to have `GEMINI_API_KEY` set; without it the script checks
the keyword fallback and skips the semantic assertions instead of failing.

`/auth/*` allows 10 requests per minute per IP address. `common.login` waits for `Retry-After`
when it gets 429, so a full run takes a few minutes; `test_auth_rate_limit.py` runs last
because it uses up the limit on purpose.

## Before running

1. Load fresh demo data — the tests expect `db/seed.sql` exactly (dates are relative to the
   moment the seed ran, so rerun it if it is days old):
   ```
   mysql -u root orbit_database < db/seed.sql
   ```
2. Start the server (`./gradlew run` or the IDE run configuration) and wait for
   `GET http://localhost:8080/health`.
3. Put the database password in the environment for this terminal only. It is never written
   into the scripts. PowerShell:
   ```
   $env:MYSQL_PWD = "<your database password>"
   ```
   Bash: `export MYSQL_PWD=...`

Optional overrides: `ORBIT_API` (default `http://localhost:8080`), `ORBIT_DB`
(`orbit_database`), `ORBIT_DB_USER` (`root`), `MYSQL_BIN` (path to `mysql`, default is the
MySQL 9.1 install path on Windows).

## Running

From `OrbitKtorServer/scripts/api-tests`:
```
python run_all.py
```
or one file, e.g. `python test_attendance.py`. Each script prints `PASS`/`FAIL` per check and
exits with code 1 if anything failed.

## What the tests touch

- They log in as the demo accounts (`*@orbit.test`, password `orbit123`).
- They create events titled `REGTEST…`, `ATTTEST…`, `DURTEST…` and `IMGTEST…` and delete them at
  the end with SQL. SQL is needed because started events cannot be deleted through the API, and the
  attendance test moves start times of its own events to simulate "the event started 30 minutes ago".
- `test_images.py` writes a few 1×1 PNG files into the server's `uploads/` folder and has the
  server delete them again (by removing the image from the event and then deleting the event).
- `test_search.py` creates one `SRCHTEST…` event, waits for its embedding, edits it to check the
  vector is refreshed, then deletes it. It reads `event_embeddings` over SQL and spends a few
  Gemini embedding calls.
- `test_checkin_qr.py` (F-41) creates `QRTEST…` events that already started, asks for their entry
  code as the organiser, checks guests in with it and deletes the events at the end. It needs the
  `events.check_in_code` column (see `db/schema.sql`).
- `test_profile.py` writes nothing. It reads organiser profiles from `seed.sql` and checks that
  past public events are listed while private ones and blocked users (both directions) are not.
- `test_search_parse.py` (F-43) writes nothing. It checks only the contract of `POST /search/parse`
  — status codes and that every returned filter is an allowed name — and prints what the model
  read, because which filters the model picks for a sentence is not deterministic. Without
  `GEMINI_API_KEY` it runs only the three checks that do not need the model.
- `test_attendance.py` changes Ana's rating of the quiz and sets it back to 4 (the rating time
  changes). Rerun `seed.sql` if you need the demo data byte-for-byte.
