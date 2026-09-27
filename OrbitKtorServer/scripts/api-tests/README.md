# API tests

End-to-end checks against a running Orbit server and its MySQL database: registration (F-33),
attendance check-in and rating rules (F-34, F-27), event duration (F-35), image upload and photo access (F-37),
server-side validation of event fields and the access code,
natural-language search (F-32), password reset with an emailed code and the login rate limit (F-13). Plain Python 3, no packages to
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

4. `test_tokens.py` signs its own JWTs, so `JWT_SECRET` must be set in the terminal to the same
   value the server runs with. `test_upload_failures.py` counts files in the server's `uploads/`
   folder, so it must run on the same computer as the server (`ORBIT_UPLOAD_DIR` if the folder
   is not `OrbitKtorServer/uploads`).

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

`run_all.py` also writes `results/test-results.md`: a summary per suite and, for every check, the
scenario, the expected result, the actual result and PASS/FAIL. The expected result is the
`expected=` argument of `check()`, or the text after `->` in the check's name; a name without
an arrow is a statement that must hold. `test_access.py` also writes `results/access-matrix.md`.
Running a single file does not write the table.

## What the tests touch

- They log in as the demo accounts (`*@orbit.test`, password `orbit123`).
- They create events titled `REGTEST…`, `ATTTEST…`, `DURTEST…`, `IMGTEST…` and `VALTEST…` and delete
  them at the end with SQL. SQL is needed because started events cannot be deleted through the API.
- The server accepts only events that start in the future. A test that needs an event which already
  started creates it for tomorrow and then moves `start_time` back with SQL (`common.move_start`,
  `shift_start` in the attendance test).
- Every event needs at least one photo the caller uploaded, so each test event uploads a 1×1 PNG
  (`common.photo`). Events removed with SQL leave those files in `uploads/`; they are a few bytes
  each and safe to delete.
- `test_images.py` writes a few 1×1 PNG files into the server's `uploads/` folder and has the
  server delete them again (by removing the image from the event and then deleting the event).
- `test_search.py` creates one `SRCHTEST…` event, waits for its embedding, edits it to check the
  vector is refreshed, then deletes it. It reads `event_embeddings` over SQL and spends a few
  Gemini embedding calls.
- `test_checkin_qr.py` (F-41) creates `QRTEST…` events that already started, asks for their entry
  code as the organiser, checks guests in with it and deletes the events at the end. It needs the
  `events.check_in_code` column (see `db/schema.sql`).
- `test_validation.py` checks that every rule of the event form is also enforced by the server,
  on create and on edit, and that the server, not the client, makes the private event access code.
- `test_password_reset.py` resets Stefan's password with a one-time code. The server stores only a
  bcrypt hash of the code, so the test overwrites that hash with the hash of a known code
  (`482913`) over SQL. At the end it puts back Stefan's password hash from `seed.sql` and sets
  `password_changed_at` to 0, also when a check fails. It makes about 20 `/auth` requests, so it
  waits for the rate limit twice. It works without SMTP settings; the server then writes the
  codes to its log instead of sending them.
- `test_access.py` checks every protected route against five callers (owner, stranger, member of
  the private event, non-member, a user who blocked the owner). The expected statuses come from
  the project's rules: no access looks like 404, an owner-only action is 403 for someone who can
  see the event. It creates two `ACCTEST…` events and deletes them with SQL.
- `test_concurrency.py` repeats each race many times (20 rounds for registrations, 10 for walk-in
  check-ins) with requests released at the same instant, and after every round compares the
  counter with the rows actually stored. It creates about 70 `CONCTEST…` events.
- `test_tokens.py` sends expired, forged, unsigned and malformed JWTs and expects 401 for each,
  after two control checks that a correctly signed token is accepted.
- `test_upload_failures.py` cuts an upload off halfway with a raw socket and checks that no
  partial file and no `images` row is left; the same for oversize, wrong-type and empty files.
- `test_profile.py` writes nothing. It reads organiser profiles from `seed.sql` and checks that
  past public events are listed while private ones and blocked users (both directions) are not.
- `test_search_parse.py` (F-43) writes nothing. It checks only the contract of `POST /search/parse`
  — status codes and that every returned filter is an allowed name — and prints what the model
  read, because which filters the model picks for a sentence is not deterministic. Without
  `GEMINI_API_KEY` it runs only the three checks that do not need the model.
- `test_attendance.py` changes Ana's rating of the quiz and sets it back to 4 (the rating time
  changes). Rerun `seed.sql` if you need the demo data byte-for-byte.
