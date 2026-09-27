# Changes after the review

This file tracks the changes made in response to the review comments, one section per
change, so they can be described in the thesis. Each section states the review point,
the problem found in the code, the solution, the files touched and how it was verified.

| Step | Review point | Status |
|---|---|---|
| 1 | 7 — access control for photos; 6 — photo ownership checked on the server | done |
| 2 | 6 — remaining server-side validation of event fields, access code generated on the server | done |
| 3 | 5 — secure storage of the JWT on the device | done; on-device tests still to run |
| 4 | 5 — password reset confirmed with a one-time code sent by email | done; not yet tried on the phone or with a real mailbox |
| 5 | 6 — display name length enforced by the server as in the app | done |
| 6 | 8 — integration and negative tests, summary table; 5 server defects they found, fixed | done; the instrumented sync tests still to run on the phone |
| 7 | 8 (follow-up) — an event the server rejects no longer waits on the phone forever | done; still to run on the phone |
| 8 | 9, 10 — evaluation labels and scripts | done; labels frozen in commit `31cf624` |
| 9 | 9 — semantic search evaluation results | done |
| 10 | 10 — sentence-to-filters evaluation results | done |

---

## Step 1 — Photo access control and photo ownership

### Review points

> 7. Check access control for photos of private events. A valid JWT proves who the user is,
> but not that they may access every photo; the server should check ownership of, or
> membership in, the specific private event.

> 6. (part) Every rule that decides whether data is valid must be repeated on the server.
> A modified client or a direct API call must not be able to bypass the system's rules.

### Problem

**Photo download.** `GET /images/{name}` only required *some* valid token. Any logged-in
user who obtained a photo's file name (a random UUID) received the file, including photos
of private events they were not a member of, and photos of an organiser who had blocked
them. The response was also marked cacheable for 30 days without restriction, so an
intermediate cache could keep a private photo.

**Photo ownership.** `POST /events`, `PUT /events/{id}` and `PATCH /events/{id}/rating`
only checked that a photo path had the right *form* (`/images/<uuid>.<ext>`), not *who
uploaded it*. That enabled a destructive attack, not just a leak:

1. Mallory creates an event whose `imageUris` contains the path of Ana's photo.
2. Mallory edits the event and removes that photo.
3. The server deletes files that were removed from an event, so **Ana's photo file is
   deleted from disk**, and Ana's event shows an empty tile.

Deleting the event, or replacing the photo on a rating, had the same effect.

### Solution

The server now records who uploaded each photo, and uses that plus the existing event
access rules for every photo decision.

```
POST /images                    → saves the file, writes (name, uploader) to the images table

GET /images/{name}              → allowed if ANY of:
                                    • the caller uploaded it (not attached to anything yet)
                                    • it is on an event the caller may see
                                    • it is on a rating of an event the caller may see
                                  otherwise 404, same as a missing file

POST /events, PUT /events/{id},  → every photo must be uploaded by the caller,
PATCH /events/{id}/rating          or already be on this event / this rating
```

"An event the caller may see" is the existing `ExposedUserDataService.canAccess`, the same
check used by `GET /events/{id}`: the owner always, nobody when a block exists in either
direction, everyone for a public event, and only members for a private event. Photo access
therefore follows the same rules as the event itself, including blocking.

Photos from the seed data have no uploader row. They are still served, because the lookup
finds the event they are on, and an organiser can still edit a seed event, because photos
already on that event are always allowed.

Finding the events that contain a given photo uses MySQL's `JSON_CONTAINS` over the
`image_uris` JSON column, through Exposed's `contains` function from the `exposed-json` module:

```kotlin
eventsWithOwner.selectAll()
    .where { Events.imageUris.contains("\"$path\"") }
```

The response header changed from `Cache-Control: max-age=2592000` to
`Cache-Control: max-age=2592000, private`, so only the phone may cache the photo.

### Behaviour before and after

| Request | Before | After |
|---|---|---|
| Non-member downloads a photo of a private event | 200 | 404 |
| Member (joined with the access code) downloads it | 200 | 200 |
| User who blocked the organiser downloads a photo | 200 | 404 |
| Another user downloads a photo not attached to anything | 200 | 404 |
| Uploader downloads their own unattached photo | 200 | 200 |
| Anyone who sees a public event downloads its photo | 200 | 200 |
| Create or edit an event with someone else's photo | 201 / 200, file later deletable | 400 |
| Rate an event with someone else's photo | 200 | 400 |
| Download without a token | 401 | 401 |

The new error message is `Moguce je dodati samo sopstvene slike`.

### Files

| File | Change |
|---|---|
| `OrbitKtorServer/src/main/kotlin/com/example/orbit/db/ImageTable.kt` | new: `images` table (name, uploader_id, created_at) |
| `OrbitKtorServer/src/main/kotlin/com/example/orbit/db/DatabaseSchema.kt` | creates the new table on start |
| `OrbitKtorServer/db/schema.sql` | `CREATE TABLE images`, and the commented reset line |
| `OrbitKtorServer/src/main/kotlin/com/example/orbit/service/ExposedImageService.kt` | new: `recordUpload`, `uploaderOf` |
| `OrbitKtorServer/src/main/kotlin/com/example/orbit/service/ExposedEventService.kt` | `findByImage` (JSON_CONTAINS) |
| `OrbitKtorServer/src/main/kotlin/com/example/orbit/service/ExposedRatingService.kt` | `eventIdsWithImage` |
| `OrbitKtorServer/src/main/kotlin/com/example/orbit/routes/ImageRoutes.kt` | upload records the uploader; download checks `canView`; private cache |
| `OrbitKtorServer/src/main/kotlin/com/example/orbit/routes/EventRoutes.kt` | `hasForeignImage` check on create and edit; shared `FOREIGN_IMAGE_MESSAGE` |
| `OrbitKtorServer/src/main/kotlin/com/example/orbit/routes/RatingRoutes.kt` | same check for the rating photo |
| `OrbitKtorServer/src/main/kotlin/com/example/orbit/plugins/Databases.kt` | image routes registered here, since they now need the database |
| `OrbitKtorServer/src/main/kotlin/com/example/orbit/plugins/Routing.kt` | image routes removed from the database-free routing |
| `OrbitKtorServer/scripts/api-tests/test_images.py` | 18 → 33 checks |

The Android app did not change: it only shows photos of events the user may already see.

### Database migration

A new table only, no change to existing tables. The server creates it on start
(`SchemaUtils.create`); for a fresh database it is also in `schema.sql`. Photos uploaded
before this change have no uploader row, so they behave like seed photos: visible through
the events they are on, and not attachable to a new event.

### Verification

- `./gradlew compileKotlin test`: 11/11 JVM tests pass.
- API tests on a database loaded with only `seed.sql`: all 10 suites pass, 184 checks.
  New checks in `test_images.py`:
  - uploader downloads the unattached photo, another user gets 404, `Cache-Control` contains `private`
  - once on a public event, another user downloads it; the user who blocked the organiser gets 404
  - creating an event with someone else's photo → 400; editing to add one → 400; the other organiser's file is still on disk
  - non-member gets 404 for a private event's photo, 200 after joining with the access code
  - rating with someone else's photo → 400, with one's own → 200, and a third user who sees the event downloads it
- Manual check on seed data: a photo of a public seed event → 200; a photo that is only on
  a private seed event, requested by a non-member → 404.

### Known limitations

- A photo already cached on the phone (Coil disk cache) stays visible after the user leaves
  a private event or is blocked, until the cache evicts it. The server no longer serves it.
- Each photo request makes one to three small database queries. Coil caches photos on the
  device, so the cost is paid once per photo, not on every scroll.
- `images` rows are not deleted together with the file. The name is a random UUID that is
  never reused, so a leftover row has no effect.

---

## Step 2 — Server-side validation of events and a server-made access code

### Review point

> 6. Every rule that decides whether data is valid must be repeated on the server. Client-side
> checks of the date, the number of photos and other limits are useful for the user interface,
> but a modified client or a direct API call must not be able to bypass the system's rules.

### Problem

The event form in the app (`CreateEventViewModel`) checks every field before saving, but the
server repeated only some of those checks. Comparing the two:

| Rule | App | Server, create (before) | Server, edit (before) |
|---|---|---|---|
| Title not blank | yes | yes | **no** |
| Description not blank | yes | **no** | **no** |
| Title ≤ 200, address ≤ 300 characters (column size) | no | **no, database error → 500** | **no** |
| Start time in the future | yes | **no** | yes |
| Latitude in [-90, 90], longitude in [-180, 180] | yes | **no** | **no** |
| At least one photo | yes | **no** | **no** |
| At most five photos | yes | yes | yes |
| Duration, capacity, price | yes | yes | yes |

A direct API call could therefore create an event without a description or photos, one
that had already started, or one located off the globe.

The **access code** of a private event was made up by the app, with a non-cryptographic
random generator, and the server stored whatever it received. A modified client could send
the code of someone else's private event. `findByAccessCode` then found two rows,
`singleOrNull()` returned nothing, and **nobody could join either event any more**. The client
could also send a `CANCELLED` status on a new event. The database ignored it, but the response
echoed it back.

### Solution

**One set of rules for create and edit.** The separate `if` checks and the older
`imagesProblem` were merged into one function, `ExposedEvent.fieldsProblem()` in
`EventRoutes.kt`. Both `POST /events` and `PUT /events/{id}` call it, so a rule cannot be
added to one and forgotten in the other:

```kotlin
private fun ExposedEvent.fieldsProblem(): String? = when {
    title.isBlank() -> "Naslov je obavezan"
    title.length > MAX_TITLE_LENGTH -> "Naslov moze imati najvise $MAX_TITLE_LENGTH znakova"
    description.isBlank() -> "Opis je obavezan"
    description.length > MAX_DESCRIPTION_LENGTH -> "Opis moze imati najvise ... znakova"
    (address?.length ?: 0) > MAX_ADDRESS_LENGTH -> "Adresa moze imati najvise ... znakova"
    latitude !in -90.0..90.0 || longitude !in -180.0..180.0 -> "Lokacija nije ispravna"
    capacity != null && capacity < 1 -> "Capacity must be at least 1"
    price != null && price < 0.0 -> "Price cannot be negative"
    durationMinutes != null && durationMinutes !in 1..MAX_DURATION_MINUTES -> "..."
    imageUris.isEmpty() -> "Potrebna je bar jedna fotografija"
    imageUris.size > MAX_IMAGES -> "An event can have at most $MAX_IMAGES images"
    imageUris.any { !isStoredPath(it) } -> "Images must first be uploaded with POST /images"
    else -> null
}
```

`POST /events` also rejects a start time that is not in the future (`PUT` already did).
On edit, `fieldsProblem` runs before the rescheduling and relocation checks, because the
distance from the old location is meaningless for invalid coordinates. The description limit
(10,000 characters) exists because the `TEXT` column holds 65,535 bytes and one character takes
up to 4 bytes. Without a limit a very long text reached the database and came back as a 500.

**The server makes the access code.** For a new private event the server now ignores what the
client sent and generates the code itself, the same way it already generated the QR check-in
code: `SecureRandom` over an alphabet without the easily confused `I`, `O`, `0` and `1`. It
retries until the code is not used by another private event. A public event never carries a
code, and a new event is always stored and returned as `ACTIVE`.

```kotlin
suspend fun newAccessCode(): String {
    while (true) {
        val code = randomCode(ACCESS_CODE_LENGTH)
        if (findByAccessCode(code) == null) return code
    }
}
```

The code stays six characters long, because the app's join field accepts exactly six. The
existing check-in generator was renamed from `newCheckInCode` to `randomCode(length)` so both
codes use it.

**The app shows the server's code.** The app is offline-first: it saves a new event locally,
showed its own code in a dialog right away, and sent the event to the server in the background.
Now that the server makes the code, that dialog would have shown a code that does not work.
So for a **private** event the app now waits for the server's answer and shows the code from
it. A public event is still sent in the background as before.

```
private event, online   → save locally → wait for POST /events → dialog shows the server's code
private event, offline  → save locally → dialog: "the code will appear in the event details
                          once you are back online"; the next sync stores the server's version
public event            → save locally → send in the background → screen closes (unchanged)
```

### Behaviour before and after

| Request | Before | After |
|---|---|---|
| Create with a blank description | 201 | 400 `Opis je obavezan` |
| Create without photos | 201 | 400 `Potrebna je bar jedna fotografija` |
| Create with a start time in the past | 201 | 400 `Pocetak mora biti u buducnosti` |
| Create with latitude 95 | 201 | 400 `Lokacija nije ispravna` |
| Create with a 201-character title or a 301-character address | 500 | 400 |
| Edit with a blank title or description | 200 | 400 |
| Edit with latitude 95 | 400 (misleading "moved more than 50 km") | 400 `Lokacija nije ispravna` |
| Edit removing every photo | 200 | 400 |
| Private event created with the code `AAAAAA` | stored as `AAAAAA` | server's own random code |
| Public event created with an access code | stored | ignored, `null` |
| New event sent with status `CANCELLED` | response said `CANCELLED` | `ACTIVE` |

### Files

| File | Change |
|---|---|
| `OrbitKtorServer/src/main/kotlin/com/example/orbit/routes/EventRoutes.kt` | `fieldsProblem()` replaces the separate checks and `imagesProblem`; future start on create; server-made access code; new event always `ACTIVE` |
| `OrbitKtorServer/src/main/kotlin/com/example/orbit/service/ExposedEventService.kt` | `newAccessCode()`; `newCheckInCode()` generalised to `randomCode(length)` |
| `Orbit/app/src/main/java/com/example/orbit/data/repository/EventRepository.kt` | `pushEvent` returns the server's version of the event |
| `Orbit/app/src/main/java/com/example/orbit/data/repository/EventRepositoryImpl.kt` | returns that version; after a 409 fetches it, since it carries the code |
| `Orbit/app/src/main/java/com/example/orbit/ui/stateholders/CreateEventViewModel.kt` | no local code generator; a private event waits for the server; `isAccessCodePending` state |
| `Orbit/app/src/main/java/com/example/orbit/ui/screens/CreateEventScreen.kt` | dialog shows the server's code, or the "code comes later" text when offline |
| `Orbit/app/src/main/res/values/strings.xml`, `values-sr/strings.xml` | `create_code_pending_text` |
| `OrbitKtorServer/scripts/api-tests/test_validation.py` | new, 21 checks |
| `OrbitKtorServer/scripts/api-tests/common.py` | `photo(token)` and `move_start(...)` helpers, shared 1×1 PNG |
| `test_attendance.py`, `test_checkin_qr.py`, `test_registration.py`, `test_duration.py`, `test_search.py`, `test_blocking.py`, `test_images.py`, `run_all.py`, `README.md` in the same folder | test events carry a photo; started events are created for tomorrow and moved back with SQL; private codes are read from the response |

### Database migration

None. No table or column changed.

### Verification

- App: `./gradlew :app:assembleDebug :app:testDebugUnitTest` — builds, 66/66 unit tests pass.
- Server: `./gradlew compileKotlin test` — 11/11 JVM tests pass.
- API tests on a database loaded with only `seed.sql`: all 11 suites pass, **205 checks**.
  `test_validation.py` (new) covers, on create: blank description, no photo, past start,
  latitude 95, longitude -181, a title over 200, an address over 300 and a description over
  10,000 characters. Also: the private code format, that a client code is ignored, that two
  private events get different codes, that another user joins with the server's code, that a
  public event never has a code, and that a new event is always active. On edit: blank title,
  blank description, latitude 95 reported as a location error, removing every photo, that the
  access code cannot be changed, and that a valid edit still works.
- Existing suites were adapted, not weakened: they used to create events without photos, or
  events that had already started. The API no longer allows either, so each test event now
  uploads its own photo, and "already started" events are created for tomorrow and moved back
  in the database, the same way `test_attendance` already moved start times.
- Not yet checked on the phone: the dialog with the server's code, and the offline text.

### Known limitations

- **An event created offline can miss its start.** If the phone stays offline until after the
  start time, the server rejects the event (start in the past) when it finally arrives. The app
  keeps it as unsent and retries at every sync. The organiser cannot remove it either, because
  the delete action is hidden once an event has started (`EventDetailScreen.kt:233`). Fixing
  this means the app has to drop or flag an event the server answered with 400. That is not done yet.
- **Title and address limits are server-only.** The app does not stop typing at 200 / 300
  characters. A longer text is rejected on save with the generic "the server did not accept
  the change" message instead of a field error.
- **No rate limit on joining.** Six characters from a 32-letter alphabet give about 10⁹
  combinations, and `POST /events/join` is not rate-limited the way `/auth/*` is. Adding the
  same limiter there would be a one-line change.

---

## Step 3 — Encrypted JWT on the device

### Review point

> 5. (part) In the same part, consider storing the JWT token on the device more securely.

### Problem

After login the app kept the JWT in `SharedPreferences` (`orbit_user.xml`) as **plain text**:

```xml
<string name="token">eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI1ZWVk...</string>
```

`MODE_PRIVATE` stops other apps from reading the file, but not every copy of it:

- The manifest has `android:allowBackup="true"`, and both backup rule files were the empty
  project templates, so **the file with the token went into the Google cloud backup** and into
  device-to-device transfer.
- On a rooted phone, or through `adb backup` on older Android versions, the file can be read
  directly.

Whoever has the file has the token, and the token is valid for 30 days, so reading the file
is enough to act as the user.

### Solution

**The token is encrypted with a key that never leaves the Android Keystore.** The new class
`TokenCipher` asks the Keystore to create an AES-256 key in GCM mode on first use. The key is
generated inside the Keystore (in secure hardware where the phone has it). The app gets back
only a reference to the key, never the key bytes. The app then asks the Keystore to encrypt or
decrypt, and only the result is written to disk:

```
login   → token → Keystore encrypts (AES-256-GCM, new random IV) → Base64(IV + ciphertext)
                                                                   → orbit_user.xml
start   → orbit_user.xml → Keystore decrypts → token kept in memory for the session
logout  → the encrypted value is removed from the file and from memory
```

```kotlin
fun encrypt(plain: String): String {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key())          // the Keystore picks a new IV
    val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
    return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
}
```

GCM is authenticated encryption: a changed byte in the stored value makes decryption fail
instead of producing a different token. When decryption fails (the value was changed, or the
file was copied to another phone where the key does not exist), `decrypt` returns `null` and
the user is simply asked to log in again.

The file on disk now looks like this:

```xml
<string name="token_encrypted">q3V0e1d8...(Base64 of IV + ciphertext)...</string>
```

**The token is decrypted once per app start, not per request.** The network interceptor reads
the token for every request. `CurrentUser` therefore decrypts it once when it is created and
keeps it in a `@Volatile` field for the rest of the session. `startSession` and `endSession`
update the field together with the file.

**Existing logins keep working.** A phone updated from the previous version still has the
plain token under the old key `token`. On the first start after the update, `CurrentUser`
encrypts it, stores it under `token_encrypted` and deletes the plain value, so the user stays
logged in and the plain text is gone.

**The file is excluded from backup.** `data_extraction_rules.xml` (Android 12 and newer, cloud
backup and device transfer) and `backup_rules.xml` (Android 11 and older) now exclude
`orbit_user.xml`. The Keystore key is never backed up either, so a backed-up token could not
have been decrypted on another phone anyway. The exclusion means it no longer leaves the phone
at all. After restoring onto a new phone the user logs in again. Events cached in Room are
still backed up as before.

`androidx.security:security-crypto` (`EncryptedSharedPreferences`) was not used. Since version
1.1.0-alpha07 (April 2025) all of its APIs are deprecated "in favour of existing platform APIs
and direct use of Android Keystore", which is what `TokenCipher` does
(source: developer.android.com/jetpack/androidx/releases/security).

### Behaviour before and after

| Situation | Before | After |
|---|---|---|
| Content of `orbit_user.xml` | the JWT in plain text | Base64 of IV + AES-GCM ciphertext |
| Where the key is | — | Android Keystore, not readable by the app |
| Google cloud backup / device transfer | includes the token | `orbit_user.xml` excluded |
| File copied to another phone | token usable | cannot be decrypted, login required |
| Stored value changed by someone | changed token sent | decryption fails, login required |
| Update from the previous version while logged in | — | stays logged in; plain token encrypted and removed |
| Decryptions per request | — | none, one per app start |

### Files

| File | Change |
|---|---|
| `Orbit/app/src/main/java/com/example/orbit/data/local/TokenCipher.kt` | new: Keystore key, `encrypt`, `decrypt` |
| `Orbit/app/src/main/java/com/example/orbit/data/local/CurrentUser.kt` | stores only the encrypted token; token kept in memory; one-time migration of the plain token |
| `Orbit/app/src/main/res/xml/data_extraction_rules.xml` | excludes `orbit_user.xml` from cloud backup and device transfer |
| `Orbit/app/src/main/res/xml/backup_rules.xml` | excludes `orbit_user.xml` from backup on Android 11 and older |
| `Orbit/app/src/androidTest/java/com/example/orbit/data/local/TokenCipherTest.kt` | new instrumented test, 5 cases |

No server change; the server never sees how the app stores the token.

### Verification

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest`: the app
  and the test APK build, 66/66 JVM unit tests pass. Lint reports nothing in the new code.
  The 4 lint errors it does report (`NonObservableLocale`) are older, in files this step did
  not touch.
- `TokenCipherTest` checks that decryption returns the original token, that the stored text
  does not contain the token, that the same token encrypts differently each time (new IV),
  that one changed byte is rejected, and that text that is not Base64 is rejected. It needs
  the Keystore, which exists only on a device, so it runs with
  `./gradlew --no-daemon :app:connectedDebugAndroidTest` with the phone connected.
  **Not run yet:** no device was connected when this step was made.
- To see the file on a debug build, after logging in:
  `adb shell run-as io.github.igicut.orbit cat shared_prefs/orbit_user.xml` shows
  `token_encrypted` with a Base64 value and no `token` entry.

### Known limitations

- **The token is in memory while the app runs.** Encryption protects the file, not a running
  process. Reading another app's memory already requires root.
- **Root plus the Keystore.** On a rooted phone an attacker running code as the app can ask
  the Keystore to decrypt, just as the app does. Hardware-backed keys prevent copying the key
  off the phone, not using it on the phone.
- **The token is still valid for 30 days.** Encryption makes it harder to steal, not
  shorter-lived. Step 4 makes a password change revoke every older token.

---

## Step 4 — Password reset with a one-time code sent by email

### Review point

> 5. Changing the password must include verifying the user's identity. It is not acceptable to
> allow setting a new password based only on knowing the email address; implement a one-time
> code or a time-limited link sent to the registered address.

### Problem

`POST /auth/reset-password` took an email and a new password and set it. Anyone who knew
someone's email could take over the account. The route even returned a token, so the attacker
was logged in immediately. Two more weaknesses came with it:

- **It revealed which emails have accounts.** An unknown email got `404 "No account uses this
  email"`, a known one `200`.
- **Changing the password did not lock anyone out.** JWTs are valid for 30 days and are not
  stored on the server. An attacker who already had a token kept access even after the real
  owner changed the password.

### Solution

A code instead of a link, because a code needs only one more field on the existing screen. A
link needs a deep link with App Links domain verification, or a web page served by the server.

The reset now takes two requests. The code proves that the person can read the mailbox of the
account.

```
POST /auth/forgot-password {email}
    → always 200 with the same text, whether the account exists or not
    → in the background, only if the account exists:
         6-digit code from SecureRandom
         bcrypt hash of the code → password_reset_codes (valid 15 min, 0 attempts)
         email with the code over SMTP (or, without SMTP settings, the server log)

POST /auth/reset-password {email, code, password}
    → wrong format / wrong code / expired / 5 attempts used → 400, one message for all
    → correct code → new password hash, code deleted,
                     password_changed_at = now → every older token stops working
                   → 200 with a new token, as before
```

**The code is stored like a password.** Only its bcrypt hash is in the database, so a database
leak does not reveal valid codes.

**Five attempts, counted atomically.** Six digits are 10⁶ possibilities. With five attempts per
code, a guess succeeds with probability 5 / 10⁶. An attempt is counted *before* the code is
checked, in the same `UPDATE` that checks the limit and the expiry. Concurrent requests
therefore cannot get more than five checks between them:

```kotlin
val counted = PasswordResetCodes.update({
    (PasswordResetCodes.email eq email) and
        (PasswordResetCodes.attempts less MAX_RESET_ATTEMPTS) and
        (PasswordResetCodes.expiresAt greater System.currentTimeMillis())
}) {
    it[attempts] = attempts + 1
}
if (counted == 0) return null   // no code, expired, or no attempts left
```

Mistakes that are not a guess do not use up an attempt: a code that is not six digits, or a
new password that is too short, is rejected before the counting step. After a correct code the
row is deleted before the password is written, so the same code cannot succeed twice.

**Nothing reveals whether an account exists.** Both steps answer the same way for known and
unknown emails. The first step also answers *before* doing any work, because bcrypt (cost 12)
and sending an email take hundreds of milliseconds. Doing them first would make an existing
account answer measurably slower. Measured on the test server:

| Email | Status | Response time |
|---|---|---|
| existing account | 200 | 3.9 ms |
| no account | 200 | 3.8 ms |

**A new password revokes old tokens.** `user_credentials` has a new column
`password_changed_at`. The JWT check (`validate` in `Security.kt`) now rejects a token whose
issue time (`iat`) is earlier than the last password change. The comparison is in whole
seconds, because `iat` has no milliseconds. Comparing milliseconds would reject the fresh
token issued by the reset itself.

```kotlin
val issuedAtSeconds = (credential.payload.issuedAt?.time ?: 0L) / 1000
val changedAtSeconds = authService.passwordChangedAt(userId) / 1000
if (issuedAtSeconds < changedAtSeconds) null else JWTPrincipal(credential.payload)
```

The price is one primary-key lookup per authenticated request.

**Sending email.** `MailService` uses Jakarta Mail (`org.eclipse.angus:angus-mail` 2.0.5) over
SMTP with STARTTLS required and 10-second timeouts. The account comes only from environment
variables, `SMTP_USER` and `SMTP_PASSWORD` (for Gmail an app password; host and port default to
`smtp.gmail.com:587`), in the same way as `JWT_SECRET` and `GEMINI_API_KEY`. Without them the
server logs a warning at start and writes each code to its log instead. The feature can then be
shown without a mail account; this is the same approach as the random `JWT_SECRET` fallback.
The mail text:

```
Subject: Orbit - kod za novu lozinku

Kod za postavljanje nove lozinke: 482913

Kod vazi 15 minuta. Ako niste trazili novu lozinku, zanemarite ovu poruku; stara lozinka i dalje vazi.
```

**The app.** The "Forgot your password?" screen now has two stages:

```
stage 1:  [Email]                           [Send code]
stage 2:  "If an account uses x@y.z, a 6-digit code is on its way. It is valid for 15 minutes."
          [Email] [Code from the email]  Send a new code
          [New password] [Repeat password]  [Change password]
```

Changing the email in stage 2 returns to stage 1, because the code was sent to the old
address. The code field accepts only digits, at most six. A rejected code shows "The code is
wrong or has expired" under the code field. The old `UnknownEmail` result and its message
("No account uses this email") were removed, because the server no longer tells.

### Behaviour before and after

| Situation | Before | After |
|---|---|---|
| Reset knowing only the email | new password set, attacker logged in | impossible, needs the emailed code |
| Unknown email in the reset | `404 No account uses this email` | same `200` and same timing as a real account |
| Code guessed wrongly 5 times | — | the code is dead, a new one must be requested |
| Code older than 15 minutes | — | rejected |
| Second request for a code | — | replaces the first code |
| Token issued before a password change | valid up to 30 days | `401` on the next request |
| Old app sends a reset without a code | password changed | `400` |
| `/auth/forgot-password` flooding | — | same limit as login, 10 per minute per IP |

### Files

| File | Change |
|---|---|
| `OrbitKtorServer/build.gradle.kts` | `org.eclipse.angus:angus-mail:2.0.5` |
| `OrbitKtorServer/src/main/kotlin/com/example/orbit/db/ResetCodeTable.kt` | new: `password_reset_codes` (email, code_hash, expires_at, attempts) |
| `OrbitKtorServer/src/main/kotlin/com/example/orbit/db/CredentialTable.kt` | `password_changed_at` column |
| `OrbitKtorServer/src/main/kotlin/com/example/orbit/db/DatabaseSchema.kt` | creates the new table on start |
| `OrbitKtorServer/db/schema.sql` | new table, new column, and the `ALTER TABLE` for existing databases |
| `OrbitKtorServer/src/main/kotlin/com/example/orbit/service/MailService.kt` | new: SMTP from environment variables |
| `OrbitKtorServer/src/main/kotlin/com/example/orbit/service/AuthService.kt` | `createResetCode`, `resetPassword` with the code, `passwordChangedAt` |
| `OrbitKtorServer/src/main/kotlin/com/example/orbit/routes/AuthRoutes.kt` | `POST /auth/forgot-password`; `POST /auth/reset-password` requires the code |
| `OrbitKtorServer/src/main/kotlin/com/example/orbit/plugins/Security.kt` | rejects tokens older than the last password change; `AuthServiceKey` |
| `OrbitKtorServer/src/main/kotlin/com/example/orbit/plugins/Databases.kt` | puts `AuthService` into the attributes; creates `MailService` |
| `Orbit/app/src/main/java/com/example/orbit/data/remote/dto/AuthDto.kt` | `ForgotPasswordRequestDto`; `code` in `ResetPasswordRequestDto` |
| `Orbit/app/src/main/java/com/example/orbit/data/remote/OrbitApiService.kt` | `forgotPassword` |
| `Orbit/app/src/main/java/com/example/orbit/data/repository/AuthRepository.kt`, `AuthRepositoryImpl.kt` | `requestPasswordReset`; `resetPassword` with the code; 400 on reset means a wrong code |
| `Orbit/app/src/main/java/com/example/orbit/data/repository/AuthResult.kt` | `UnknownEmail` replaced by `InvalidCode` |
| `Orbit/app/src/main/java/com/example/orbit/ui/stateholders/AuthViewModel.kt` | `code`, `codeSent`, `codeError`; `requestCode()`; code validated before sending |
| `Orbit/app/src/main/java/com/example/orbit/ui/screens/AuthScreen.kt` | the two stages, code field, "Send a new code" |
| `Orbit/app/src/main/res/values/strings.xml`, `values-sr/strings.xml` | 6 new strings, 1 removed, 1 reworded |
| `OrbitKtorServer/scripts/api-tests/test_password_reset.py` | new, 18 checks |
| `OrbitKtorServer/scripts/api-tests/test_auth_rate_limit.py` | `forgot-password` shares the `/auth` limit |
| `OrbitKtorServer/scripts/api-tests/run_all.py`, `README.md` | the new test |
| `Orbit/FEATURES.md` | the two "conscious simplifications" for token storage and password reset marked as resolved |

### Database migration

- New table `password_reset_codes`: created automatically on server start.
- New column on an existing table, which `SchemaUtils.create` does not add. **Run once** on an
  existing database, before starting the new server, or every login fails:

  ```sql
  ALTER TABLE user_credentials ADD COLUMN password_changed_at BIGINT NOT NULL DEFAULT 0;
  ```

  The default 0 means "never changed", so all existing tokens stay valid.

### Verification

- Server: `./gradlew compileKotlin test`: 11/11 JVM tests pass.
- App: `./gradlew :app:assembleDebug :app:testDebugUnitTest`: builds, 66/66 unit tests pass.
  Lint shows the same 4 older errors and 40 warnings as before this step, none in the changed files.
- API tests on a database loaded with only `seed.sql`: all 12 suites pass, **224 checks**
  (205 before this step, plus 18 in `test_password_reset.py` and 1 in `test_auth_rate_limit.py`
  showing that `forgot-password` shares the `/auth` limit).
  `test_password_reset.py` (new, 18 checks):
  - existing and unknown email get the identical answer, and a code is stored only for the
    existing one, as a bcrypt hash
  - a malformed code and a too-short password are rejected without using an attempt
  - a wrong code uses one attempt; the correct code returns a token for the same account; the
    same code a second time is rejected
  - the new password logs in and the old one does not
  - the token from before the reset gets 401, and the token from the reset works
  - after five wrong codes even the correct one is rejected
  - an expired code is rejected
  - a second request replaces the first code
  - a request without a code (the old app) gets 400, not 500
  - Stefan's seed password is restored at the end, in a `finally` block
- Timing: `forgot-password` for an existing and an unknown email both answer in about 3.9 ms
  (table above).
- Without SMTP settings the server log showed one code per request, and only for the existing
  account.
- **Not yet checked:** sending through a real SMTP account, and the two-stage screen on the phone.

### Known limitations

- **A new request resets the attempts.** Every `forgot-password` call replaces the code and
  starts again at five attempts. With the limit of 10 `/auth` requests per minute per IP, one
  address gets about 8 guesses a minute. Each guess succeeds with probability 10⁻⁶, so a hit
  takes on average 10⁶ guesses, about 125,000 minutes (roughly 12 weeks). Many addresses together
  are faster. A per-email limit on new codes would close this.
- **The code in the log.** Without SMTP settings the codes are written to the server log. That
  is meant for development and the demo only. With SMTP configured, nothing is logged.
- **A reset is a login.** As before, a successful reset logs the user in on the phone that did
  it. The code is the proof, and a reset that gave no session would just be followed by a login.
- **Tokens issued in the same second as the reset stay valid.** The comparison is in whole
  seconds, so a stolen token would have to be issued in the exact second of the reset to survive.

---

## Step 5 — Display name length on the server

### Review point

> 6. Every rule that decides whether data is valid must be repeated on the server. (…) a
> modified client or a direct API call must not be able to bypass the system's rules.

### Problem

A check of the remaining client-side rules after step 2 found one more mismatch. The app
limits the display name to **40** characters, both at sign-up (`AuthViewModel`) and on the
account screen (`AccountViewModel`). The server allowed **100**, with two separate constants in
`AuthRoutes.kt` and `MeRoutes.kt`. A direct call to `POST /auth/signup` or `PATCH /users/me`
could save a name the app itself never produces, and that the layout was not designed for.

### Solution

One constant, `MAX_NAME_LENGTH = 40`, now lives in `AuthRoutes.kt` and is used by both
routes, so the two limits cannot drift apart again. The app constants carry a comment pointing
to it.

```kotlin
/** Isto kao MAX_NAME_LENGTH u aplikaciji; vazi i za registraciju i za izmenu imena (MeRoutes) */
internal const val MAX_NAME_LENGTH = 40
```

### Behaviour before and after

| Request | Before | After |
|---|---|---|
| Sign up with a 41-character name | 201, account created | 400 |
| Change the name to 41 characters | 200 | 400 |
| Name of exactly 40 characters | 200 | 200 |

### Files

| File | Change |
|---|---|
| `OrbitKtorServer/src/main/kotlin/com/example/orbit/routes/AuthRoutes.kt` | `MAX_NAME_LENGTH` 100 → 40, shared |
| `OrbitKtorServer/src/main/kotlin/com/example/orbit/routes/MeRoutes.kt` | its own constant removed, uses the shared one |
| `Orbit/app/src/main/java/com/example/orbit/ui/stateholders/AuthViewModel.kt`, `AccountViewModel.kt` | comment pointing to the server constant |
| `OrbitKtorServer/scripts/api-tests/test_validation.py` | 4 new checks |

### Database migration

None. The column stays `VARCHAR(100)`. No existing name in `orbit_database` or in the seed data
is longer than 40 characters (checked with `CHAR_LENGTH`), so no account is affected.

### Verification

- API tests on a database loaded with only `seed.sql`: all 12 suites pass, **228 checks**.
  New in `test_validation.py`: a 41-character profile name → 400, exactly 40 → 200, Marko's
  seed name is restored afterwards (through the API, because it contains `ć`), and sign-up with
  a 41-character name → 400.

### What was checked and deliberately not moved to the server

- **GPS accuracy at check-in.** The app refuses a location fix less accurate than 100 m
  (`AttendanceRules.MAX_ACCURACY_METERS`). The server does not receive the accuracy, and
  repeating the check there would prove nothing, because a modified client can report any
  accuracy it likes. What the server can check, it does: the distance from the event (at most
  200 m) and the check-in time window. GPS spoofing remains a documented limitation.
- **Rating value and comment length, registration capacity.** These were already checked on
  the server. The comment is cut to the same 1,000 characters on both sides.

---

## Step 6 — Integration and negative tests with a summary table

### Review point

> 8. Significantly extend testing with basic integration and negative tests and add a summary
> table with the scenario, the expected result, the actual result and the PASS/FAIL outcome.
> In particular check concurrent registration for the last seat, access to other users' and
> private resources, token expiry, offline/online synchronisation and failed photo uploads.

### Before this step

| Area the review names | Covered before | Gap |
|---|---|---|
| Concurrent registration for the last seat | one race, run once | no repetition, no same-user race |
| Access to other users' and private resources | spread over several suites | no systematic endpoint × caller check |
| Token expiry | not tested | — |
| Offline/online synchronisation | not tested | app-side logic, no test at all |
| Failed photo upload | 413 / 415 / missing part | interrupted upload, empty file, app-side handling |
| Summary table | none; the console printed only failures | — |

### What was added

**Summary table.** `common.check()` now records every check (not only failures), with an
explicit `expected=` value where the check name does not state it. `run_all.py` writes
`OrbitKtorServer/scripts/api-tests/results/test-results.md`: run time, code version, database,
a per-suite summary, then one row per check with *Scenario | Expected | Actual | Result*.
A script that crashes before reporting a failure also gets a FAIL row, so a broken suite
cannot look green.

**Four new API suites (119 checks):**

| Suite | Checks | What it does |
|---|---|---|
| `test_access.py` | 92 | Every protected route × five callers: owner, stranger, member of the private event, non-member, and a user who blocked the owner. Covers view, photo, ratings, guest list, QR code, register, unregister, check in, rate, edit, cancel, delete, organiser profile and join-by-code. Also writes `results/access-matrix.md`. |
| `test_tokens.py` | 17 | Signs its own JWTs with Python's `hmac` and `JWT_SECRET`. Two control checks prove a correctly signed token is accepted, so every 401 below means rejection, not a broken test. Then: expired one hour and one second ago, a token that works and stops working 3 s later, issued in the future, wrong key, payload changed to another user, `alg: none`, a different algorithm, wrong issuer, wrong audience, missing or empty user id, missing `Bearer`, garbage, no header. |
| `test_concurrency.py` | 4 | Each race repeated: 20 rounds of 5 people for 1 or 2 last seats; 20 rounds of one person registering 5 times at once; 10 rounds of 5 walk-ins for the last spot; 20 rounds of one person registering and cancelling 3 + 3 times at once. All threads start from a barrier, and after every round the counter is compared with the rows actually stored. |
| `test_upload_failures.py` | 6 | An upload cut off halfway (a raw socket announces 1 MB, sends 200 KB, hangs up); oversize, text and empty files; no token; an event pointing to a photo that was never uploaded. After each failure it counts the files in `uploads/` and the rows in `images`, which must not change. |

**Instrumented app test `EventSyncTest` (3 tests, on the phone).** It uses the real Room
database (in memory), the real Retrofit client and the real `ImageUploader`. Only the server is
replaced, by OkHttp's `MockWebServer` (`com.squareup.okhttp3:mockwebserver3:5.5.0`, the same
version as the app's OkHttp):

| Test | Scenario | Expected |
|---|---|---|
| `eventCreatedOfflineStaysPendingAndIsSentWhenOnline` | event with a photo saved offline (the server is closed, connections are refused), then the server comes back on the same port | offline: nothing lost, still pending, local photo kept; online: photo uploaded first, then the event with the server path and no `file://` path, stored as synced with the server's access code |
| `lostReplyIsTreatedAsAlreadySent` | the server has the event but the reply was lost (409) | the app fetches the server copy and stores it as synced, with its access code |
| `rejectedPhotoIsLeftOutAndTheEventGoesWithTheOthers` | of two photos, the server refuses one (415) | the event is sent with the other photo only |

These tests run inside the installed app, so they deliberately do not start a session or write
the token. For the same reason the app's reaction to a 401 (log out) is not tested
automatically: it would log out the real account on the phone. The server side of 401 is fully
covered by `test_tokens.py`.

### What the tests found, and the fixes

The first run of the new suites against the unchanged server:

| Suite | First run | Findings |
|---|---|---|
| `test_access.py` | 81 / 92 | 11 mismatched cells, 3 defects |
| `test_upload_failures.py` | 2 / 6 | 2 defects (the other 2 failures were caused by the first leftover file) |
| `test_tokens.py` | 16 / 17 | 1 failure, a timing mistake in the test itself |
| `test_concurrency.py` | 4 / 4 | none |

| # | Defect | Found by | Fix |
|---|---|---|---|
| 1 | Edit, cancel and delete returned **403** to a non-member of a private event and to a blocked user. That tells them the event exists; the rule everywhere else is 404. | 9 cells of the access matrix | `EventRoutes.kt`: the event is loaded with `canAccess` first (404), and only then is ownership checked (403), the same as the registration routes. |
| 2 | A blocked user could still read the other person's name through `GET /users/{id}`. | access matrix | `UserRoutes.kt`: a block in either direction returns 404, as `/users/{id}/events` already did. |
| 3 | A blocked user who knew the access code could **join** the private event and received its details. | access matrix | `EventRoutes.kt`, `POST /events/join`: an event whose owner is in a block with the caller is not found. |
| 4 | An **interrupted upload left a partial file** on disk: 200,000 bytes, with no `images` row and no owner, never deleted. | `test_upload_failures.py` | `ImageStorage.kt`, see below. |
| 5 | An **empty file was accepted** as a photo (201, a 0-byte file and a row). | `test_upload_failures.py` | `ImageStorage.save` returns a new `SaveOutcome.Empty`; the route answers 400 `Slika je prazna`. |

**How defect 4 was found.** It took three attempts, recorded here because the analysis is
part of the result:

1. The first guess was an exception from reading the next multipart part after the file had
   been saved. A route-level catch that deleted the saved file did not help.
2. The second guess was a `CancellationException` from the stream, which `ImageStorage` did not
   catch because it only caught `IOException`. Widening the catch did not help either, and the
   route-level catch from attempt 1 was removed again because it had proven nothing.
3. Temporary logging showed that the handler threw `JobCancellationException: Parent job is
   Cancelling` from inside `save`. When the client hangs up, the copy loop sees a normal end
   of stream and finishes. `withContext(Dispatchers.IO)` then notices the cancelled request
   and throws **on the way out**. The `try` that deleted the file was *inside* `withContext`,
   so it never ran.

```kotlin
// before: the try is inside withContext and misses the exception thrown on exit
val written = withContext(Dispatchers.IO) {
    try { source.use { input -> copyLimited(input, file) } }
    catch (e: IOException) { file.delete(); throw e }
}

// after: the try wraps withContext, so every failure deletes the file
val written = try {
    withContext(Dispatchers.IO) { source.use { input -> copyLimited(input, file) } }
} catch (e: Exception) {
    file.delete()
    throw e
}
```

The temporary logging was removed. After the fix the upload suite passed 5 runs out of 5 with
no leftover file. The leftover files from the failed runs were deleted by hand.

### Files

| File | Change |
|---|---|
| `OrbitKtorServer/src/main/kotlin/com/example/orbit/routes/EventRoutes.kt` | edit / cancel / delete check access before ownership; join refuses a blocked owner |
| `OrbitKtorServer/src/main/kotlin/com/example/orbit/routes/UserRoutes.kt` | `GET /users/{id}` returns 404 across a block |
| `OrbitKtorServer/src/main/kotlin/com/example/orbit/service/ImageStorage.kt` | partial file deleted on any failure; empty file refused |
| `OrbitKtorServer/src/main/kotlin/com/example/orbit/routes/ImageRoutes.kt` | 400 for an empty file |
| `OrbitKtorServer/scripts/api-tests/common.py` | every check recorded with scenario, expected and actual |
| `OrbitKtorServer/scripts/api-tests/run_all.py` | writes `results/test-results.md`; 4 new suites in the run |
| `OrbitKtorServer/scripts/api-tests/test_access.py`, `test_tokens.py`, `test_concurrency.py`, `test_upload_failures.py` | new |
| `OrbitKtorServer/scripts/api-tests/README.md` | the new suites, the table, `JWT_SECRET` and same-machine requirements |
| `Orbit/app/src/androidTest/java/com/example/orbit/data/repository/EventSyncTest.kt` | new, 3 instrumented tests (a 4th added in step 7) |
| `Orbit/gradle/libs.versions.toml`, `Orbit/app/build.gradle.kts` | `mockwebserver3` for instrumented tests only |
| `.gitignore` | the raw `results/results.jsonl`; the Markdown tables stay committable |

### Verification

- API tests on a database loaded with only `seed.sql`: all 16 suites pass, **347 checks** (228
  before this step). The table is in `OrbitKtorServer/scripts/api-tests/results/test-results.md`
  and the access matrix in `results/access-matrix.md`.
- Server: `./gradlew compileKotlin test` passes, 11/11 JVM tests.
- App: `./gradlew :app:assembleDebugAndroidTest` builds the test APK with `EventSyncTest`.
  **Run on the phone in step 11** (2026-09-27, SM-G973F, Android 12): 10/10 instrumented tests
  pass; the table is in step 11.

### Known limitations

- **A race test that passes is evidence, not proof.** 20 rounds make a lucky pass unlikely, but
  thread timing on one computer is not every possible interleaving.
- **An event the server rejects stayed pending on the phone.** Fixed in step 7.

---

## Step 7 — An event the server rejects no longer waits forever

### Problem

The app is offline-first: a new event is saved on the phone and sent when there is a
connection. Since step 2 the server refuses some events that the app could still produce:

- one created offline whose start time passed before the phone got back online
- one whose only photo was refused during upload

Such an event got `400` at every sync, stayed in the pending queue forever, and was sent again
each time. Once its start time had passed, the organiser could not even delete it, because the
delete action is hidden after the start (`EventDetailScreen.kt:233`).

### Decision

Two options were weighed:

| Option | Work | What the user sees |
|---|---|---|
| **Delete it locally and show a notification with the server's reason (chosen)** | small, no database change | the event disappears, with an explanation |
| Keep it, marked as rejected, for the user to edit or delete | Room migration and new UI | the event stays, with a warning |

The first was chosen: the stored event cannot become valid by being sent again, and the
notification tells the user what to do.

### Solution

`pushEvent` now returns a `PushResult` instead of `Event?`, so every caller can tell the three
outcomes apart:

```kotlin
sealed interface PushResult {
    data class Sent(val event: Event) : PushResult       // server has it, with its access code
    data object Pending : PushResult                     // no connection or a temporary error
    data class Rejected(val reason: String) : PushResult // permanent: deleted locally, user notified
}
```

Only **400** counts as permanent. No connection, `401` (session) and `5xx` (server trouble) can
succeed later and stay `Pending`; `409` still means "already there" and becomes `Sent`. On 400,
the repository deletes the local copy and shows a notification with the server's message:

> **Sync test** — The server did not accept this event, so it was removed from your phone.
> Reason: Pocetak mora biti u buducnosti

When this happens right while creating a private event online, the create form also stays
open with "The server did not accept the event. The notification says why; fix it and save
again.", so the input is not lost.

### Files

| File | Change |
|---|---|
| `Orbit/app/src/main/java/com/example/orbit/data/repository/PushResult.kt` | new |
| `Orbit/app/src/main/java/com/example/orbit/data/repository/EventRepository.kt`, `EventRepositoryImpl.kt` | `pushEvent` returns `PushResult`; on 400 deletes and notifies; `EventNotifier` injected |
| `Orbit/app/src/main/java/com/example/orbit/data/notification/EventNotifier.kt` | `notifyEventRejected` |
| `Orbit/app/src/main/java/com/example/orbit/ui/stateholders/CreateEventViewModel.kt` | handles the three outcomes for a private event |
| `Orbit/app/src/main/res/values/strings.xml`, `values-sr/strings.xml` | `notification_event_rejected`, `create_error_rejected` |
| `Orbit/app/src/androidTest/java/com/example/orbit/data/repository/EventSyncTest.kt` | new test `eventTheServerRejectsIsRemovedInsteadOfWaitingForever`; the other three use `PushResult` |

### Verification

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest`: builds,
  66/66 unit tests pass, the test APK builds with 4 sync tests.
- `eventTheServerRejectsIsRemovedInsteadOfWaitingForever`: the server answers 400; expected
  `Rejected` with the server's message, no local copy left, nothing pending.
- **Run on the phone in step 11** (2026-09-27, SM-G973F, Android 12): passes; the "Sync test"
  notification appeared as expected.

### Known limitations

- **Uploaded photos are left behind.** Photos already uploaded for a rejected event stay on the
  server without an event. They are served only to their uploader (step 1), so this is a
  question of disk space, not of privacy.

---

## Step 8 — Evaluation labels and scripts for semantic search and sentence parsing

### Review points

> 9. Split the evaluation of semantic search into a set for tuning the thresholds and an
> independent set for the final check. Currently the threshold and the reported 71% precision
> come from the same catalog of 70 events and the same 10 queries, so the result is not an
> independent check of the final algorithm. Besides precision, show recall, F1, or at least the
> number of relevant results that were lost.

> 10. Separately check whether filters are correctly extracted from a natural-language
> sentence. Structured output ensures the model returns allowed values, but does not prove it
> understood the user's intent. Build a set of varied and ambiguous queries and show the
> percentage of correctly recognised categories, prices, time windows and distances.

### Problem

There was no evaluation code in the repository at all. The 71% precision came from a one-off
measurement in a working session, on the same 10 queries the thresholds were tuned on. The
relevance of each result was judged *after* seeing it. `test_search_parse.py` checks only the
contract (allowed values, status codes) and prints what the model chose, without judging it.

### Solution

**Labels written before any run** (`OrbitKtorServer/scripts/eval/`):

- `semantic_labels.json`, 34 queries over a fixed corpus of 61 public events:
  - **tuning set:** the original 10 queries, recovered from the session transcript
  - **test set:** 24 new queries: synonyms, concept queries, English queries over Serbian,
    German and Russian events, a typo, a long sentence, and queries with no matching event

  For every query, every event is marked relevant, borderline or not relevant, which recall
  needs. The thresholds are recorded as frozen.
- `parse_labels.json`, 60 sentences, each with the acceptable answer(s) for category, price,
  time window, distance and sort. Among them:
  - "nedelja" as week versus Sunday
  - "sledećeg meseca", where no filter exists
  - a budget above the highest limit
  - negation, Cyrillic, English and typos

**Who wrote them.** Claude drafted both files from the event texts and the prompt's value
definitions only, without running a search or sending a sentence to the model. The author then
reviewed them. The semantic labels were corrected in seven queries, for example "hiking, cycling
and kayaking are sports"; the parse labels were accepted unchanged. The corrections are
recorded in the notes of each query.

**Scripts:**

| Script | What it does |
|---|---|
| `semantic_eval.py` | For every query compares the server's answer and the keyword-only match with the labels, and reports precision, recall, F1, lost and false hits (per query, macro and micro, strict and lenient) separately for tuning and test. Refuses to run if the thresholds in `SemanticRanking.kt` changed; waits until every event has an embedding. |
| `parse_eval.py` | Sends every sentence 3 times. Per field it reports correct, recognised when asked for, correctly left out, wrong value, missed, set without being asked, and consistency across runs; then all wrong answers. Saves every answer as it arrives, so an interrupted run continues. |
| `make_review_sheet.py` | Rebuilds `LABELS_REVIEW.md`, the readable version of the labels. |
| `eval_common.py` | Shared helpers; every report prints the code version and the labels' last commit, and marks the run as a trial when the labels are not committed. |

The corpus lives in a separate database, `orbit_eval` (schema, seed, catalog), so the API tests
and the author's own database are not affected. Setup and commands are in
`OrbitKtorServer/scripts/eval/README.md`.

### Verification so far

- `--self-test` of both scripts checks the metric code on made-up data with known answers, and
  both pass. The first version of the parse self-test expected the wrong number of wrong
  answers; the scoring code was right.
- A plumbing check against the server on `orbit_eval` used only a query and a sentence that are
  in neither label file:
  - all 61 embeddings were created
  - the keyword SQL, the search call and the parse call all answered
- **The real runs have not been made on purpose.** They happen only after the label files are
  committed, so the commit date shows the expected answers were fixed before any result was seen.

### Open

- Settled: the author's note "only ca7a-021 is off" referred to test query 18 ("aktivnosti na
  reci"). The cycling tour was removed there, and the acoustic evening by the Sava and the run
  along the Danube became relevant.
- The old 71% cannot be repeated exactly: the catalog has changed since (70 → 61 public events).

---

## Step 9 — Semantic search evaluation: results (review point 9)

### How it was run

- Labels frozen in commit `31cf624` (2026-09-27 19:54); evaluation run at 19:56 on the same
  commit. The script checked that the label file had no uncommitted changes and that the
  thresholds in `SemanticRanking.kt` were still the frozen ones.
- Corpus: 61 public events in the `orbit_eval` database. `seed.sql` is from commit `27ca613`,
  because the later version comments out an event the labels use; `events_catalog.sql` is from
  `31cf624`. All 61 events had an embedding.
- Thresholds: `MIN_LEAD = 0.05`, `RELATIVE_MARGIN = 0.03`, `MAX_RELATED = 10`.
- Two systems: **keyword**, the `LIKE` match alone, as the search worked before semantic
  search, and **final**, what the server returns: keyword hits plus semantically related events.
- Full per-query results: `OrbitKtorServer/scripts/eval/results/semantic-eval.md`; raw data in
  `semantic-eval.json`.

### Results, strict mode (borderline events counted as not relevant)

| Set | System | Queries | Macro P | Macro R | Macro F1 | Micro P | Micro R | Micro F1 | Relevant | Found | Lost | False hits |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Tuning | keyword | 9 | 44.4% | 9.4% | 15.2% | 80.0% | 5.3% | 10.0% | 75 | 4 | 71 | 1 |
| Tuning | final | 9 | 78.5% | 36.4% | 45.9% | 73.0% | 36.0% | 48.2% | 75 | 27 | 48 | 10 |
| **Test** | keyword | 20 | 12.5% | 11.0% | 10.0% | 66.7% | 7.0% | 12.7% | 57 | 4 | 53 | 2 |
| **Test** | **final** | 20 | **83.9%** | **76.6%** | **71.0%** | **61.7%** | **64.9%** | **63.2%** | 57 | 37 | 20 | 23 |

Macro averages every query equally; micro sums over all queries, so broad queries with many
relevant events weigh more. A query that returns nothing has precision 0 in the macro average.
Lenient mode, where borderline events count as relevant, is in the full report. It changes the
picture little: test micro F1 is 56.8% instead of 63.2%, because the borderline events add more
relevant events than they add hits.

Queries with no relevant event, where the right answer is an empty list:

| Query | Set | Returned | Correct |
|---|---|---|---|
| xyzzy qwerty | tuning | 0 | yes |
| joga | test | 0 | yes |
| pozoriste | test | 0 | yes |
| koncert klasicne muzike | test | 5 | no |

### What the numbers say

1. **Semantic search does most of the work.** Keywords alone found 4 of 57 relevant test events
   (recall 7%); with the semantic layer, 37 (65%). Most test queries use words that no event
   contains ("kosarka" vs. "basket", "vestacka inteligencija" vs. "AI"), which is exactly the
   case semantic search is for.
2. **Precision holds on the independent set; recall differs by query type.** Micro precision
   is 73% on the tuning set and 62% on the test set. Recall is 36% on tuning and 65% on test,
   because the tuning set has broad queries with many relevant events ("Zelim da idem na
   dogadjaj gde se druzim sa ljudima": 15 relevant, 2 found). The relative margin keeps only
   events very close to the best score, and `MAX_RELATED` caps the list at 10. That favours
   precision over recall by design, and broad sentences pay for it.
3. **Events in other languages are found less often.** Most lost test events are the German and
   Russian ones:
   - bread, pelmeni: lost for "kuvanje"
   - the German and Russian games evenings: lost for "drustvene igre"
   - the Russian space lecture: lost for "svemir i zvezde"
   - three language clubs: lost for "upoznavanje novih ljudi"

   The English query "board game night", on the other hand, found all three game evenings.
4. **Precision drops when no event stands out.** "pisanje prica" returned 9 events for 1 relevant
   one, and "ucenje stranih jezika" hit the cap of 10 with 3 relevant. When many events score
   about the same, the relative margin lets all of them through.
5. **One false answer on a query with no match.** "koncert klasicne muzike" opened the semantic
   layer and returned 5 events, although there is no classical concert. The other three such
   queries correctly returned nothing.

### About the old 71%

The earlier figure came from the tuning queries only, with relevance judged after seeing the
results, on a 70-event version of the catalog. On the current 61-event corpus, with labels
written beforehand, the tuning set gives **73.0%** micro precision. The number an independent
check supports is the test set's: **61.7% micro / 83.9% macro precision at 64.9% / 76.6%
recall (strict)**.

### What was deliberately not done

The thresholds were not re-tuned after this run. The results suggest that recall on broad
sentences could rise with a wider margin or a higher cap, but choosing new thresholds from these
numbers would turn the test set into a second tuning set. Any new tuning needs new test
queries, labelled before running.

---

## Step 10 — Filters from a sentence: evaluation results (review point 10)

### How it was run

- Labels frozen in commit `31cf624`; 60 sentences, each sent 3 times to `POST /search/parse`
  (model `gemini-3.5-flash-lite`), 180 answers, all received on the first try.
- The code under test is exactly commit `31cf624`. The report's "with uncommitted changes" refers
  only to documentation and result files written after the commit.
- A field counts as correct when the answer is one of the acceptable values in
  `parse_labels.json`. Ambiguous sentences accept several answers.
- Full report with every answer: `OrbitKtorServer/scripts/eval/results/parse-eval.md`; raw
  answers in `parse-raw.jsonl`.

### Results

| Field | Correct (all answers) | Recognised when the sentence asked for it | Correctly left out | Wrong value | Missed | Set without being asked | Same in all 3 runs |
|---|---|---|---|---|---|---|---|
| Category | 99.4% (179/180) | 98.8% (83/84) | 100% (42/42) | 0 | 1 | 0 | 93.3% |
| Price | 99.4% (179/180) | 100% (36/36) | 100% (138/138) | 1 | 0 | 0 | 98.3% |
| Time window | 92.8% (167/180) | 86.1% (62/72) | 97.1% (99/102) | 0 | 10 | 3 | 93.3% |
| Distance | 98.3% (177/180) | 92.9% (39/42) | 100% (138/138) | 0 | 3 | 0 | 98.3% |
| Sort | 96.7% (174/180) | 100% (9/9) | 96.5% (165/171) | 0 | 0 | 6 | 100% |

**All five fields right in one answer: 90.0% (162 of 180).**

### What the errors show

All 18 wrong fields come from 9 sentences. Five are repeatable patterns:

| Pattern | Sentences | Effect in the app |
|---|---|---|
| **"blizu" turned into "closest first"** as well as the distance filter | "koncert veceras u blizini", "koncrt blizu mene za vkend" (3 of 3 runs each) | The list is sorted by distance instead of by date. Harmless, but not asked for. |
| **"veceras" lost next to other filters** | "koncert veceras u blizini" (3/3); "sve besplatno danas, najblize prvo" (1/3) | The time filter is missing, so the list shows more than tonight. The Cyrillic "концерт вечерас" was read correctly every time. |
| **"sledeceg meseca" read as "this month"** | "koncert sledeceg meseca" (3/3) | A real error: the filter hides exactly the concerts asked for. There is no "next month" value, and the model picks the nearest one instead of none. |
| **Typos** | "koncrt blizu mene za vkend": "vkend" not recognised as weekend (3/3) | The time filter is missing. |
| **"bilo gde u Beogradu" gives no distance** | 3/3 | No limit instead of "city". Arguably defensible: "anywhere" can mean no limit. |

The rest are single runs out of three: "cheap food" read once as free, once the category was
left out for Cyrillic "друштвене игре", and "danas ili sutra" twice gave no time window.

**What worked every time, including the deliberately tricky sentences:**
- both negations ("ne zanima me sport", "besplatno, ali ne sport"): SPORT was never chosen
- "nedelja" as week versus Sunday
- budgets above the highest limit (10,000 dinars) and "skupo", where any price filter would be wrong
- 1500 dinars rounded up to the 5000 limit
- all price sentences
- English sentences

### Conclusion for the thesis

Structured output guarantees an *allowed* value; this measurement shows how often the value is
the *intended* one. Category and price are recognised almost perfectly: 98.8% and 100% when
asked for, and never set without being asked. The time window is the weakest field, at 86.1%
recognised: it gets lost when a sentence carries several filters, and "next month" has no
matching value. The model is consistent across repeated runs (93–100% identical answers).
A filter the model sets wrongly shows up as a removable chip (`ui/components/ActiveFilterChips.kt`),
so the user sees it and removes it with one tap. A filter the model misses has no chip; the
list is then wider than asked for, and the user can set that filter by hand.

### What was deliberately not done

The search prompt was not changed after this run. Some errors look fixable with a sentence in
the prompt, for example "'blizu' is a distance, not a sort order" or "there is no next month:
leave the time out". But the same 60 sentences could then no longer measure the changed prompt
independently. A prompt change needs new test sentences, labelled before running.

---

## Step 11 — Instrumented tests run on the phone (review point 8)

### What was missing

Steps 6 and 7 wrote the app-side tests but could not run them: no phone was connected, and
`TokenCipherTest` needs a real Android Keystore while `EventSyncTest` needs a real Room database
and a real Retrofit client. Both are therefore instrumented tests, not JVM tests.

### How it was run

```bash
cd Orbit && ./gradlew --no-daemon :app:connectedDebugAndroidTest
```

- Phone: Samsung SM-G973F (Galaxy S10), Android 12, API 31, connected over USB.
- Date: 2026-09-27. Code: commit `153cb17` plus the `seed.sql` fix of this step.
- **10 tests, 10 passed, 0 failed**, 6.7 s total. Report:
  `Orbit/app/build/reports/androidTests/connected/`, raw XML in
  `app/build/outputs/androidTest-results/connected/debug/`.
- Only the server is replaced, by `MockWebServer`. Room, Retrofit, the JSON serialiser, the
  image uploader and the Keystore are the real ones.

### Summary table

| # | Test | Scenario | Expected | Actual | Result |
|---|---|---|---|---|---|
| 1 | `TokenCipherTest.decryptReturnsTheOriginalToken` | encrypt then decrypt a JWT with the Keystore key | the original token | the original token | PASS |
| 2 | `TokenCipherTest.storedTextDoesNotContainTheToken` | read the stored text | neither the token nor the `eyJ` header appears in it | no match | PASS |
| 3 | `TokenCipherTest.sameTokenEncryptsDifferentlyEachTime` | encrypt the same token twice | different ciphertexts, because the IV is new each time | different | PASS |
| 4 | `TokenCipherTest.changedCiphertextIsRejected` | flip one bit of the ciphertext | `null`, not a wrong token: GCM checks the tag | `null` | PASS |
| 5 | `TokenCipherTest.textThatIsNotBase64IsRejected` | decrypt "not base64 at all!" | `null`, no crash | `null` | PASS |
| 6 | `EventSyncTest.eventCreatedOfflineStaysPendingAndIsSentWhenOnline` | save an event with no server listening, then bring the server back | `Pending` first, the local photo kept, the event in the pending queue; then `Sent` with the server's copy | as expected | PASS |
| 7 | `EventSyncTest.lostReplyIsTreatedAsAlreadySent` | the server answers 409 because an earlier attempt arrived but its reply was lost | `Sent`, the event is read back from the server, not duplicated | as expected | PASS |
| 8 | `EventSyncTest.rejectedPhotoIsLeftOutAndTheEventGoesWithTheOthers` | one photo gets 415, the other 201 | `Sent` with only the accepted photo | as expected | PASS |
| 9 | `EventSyncTest.eventTheServerRejectsIsRemovedInsteadOfWaitingForever` | the server answers 400 "Pocetak mora biti u buducnosti" | `Rejected` with that message, the local copy deleted, nothing left to retry | as expected | PASS |
| 10 | `ExampleInstrumentedTest.useAppContext` | project template test | the package name | the package name | PASS |

Test 9 posts a real "Sync test" notification on the phone, which is expected and was seen.

### Side effect worth knowing

Gradle uninstalls the app when the run finishes, which wipes its data: the account is logged out
and Play services drop the optional barcode-scanner module. This is what broke the QR scanner at
the first defence. After the run the app was installed again with
`./gradlew --no-daemon :app:installDebug`; the account has to log in once more, and the scanner
module is requested again at the next app start (`MainActivity.onCreate`).

### `seed.sql` loaded again

An earlier edit commented out events `…003` and `…012`, which left event `…011` ending with `),`
and a bare `;` on the next line, so the whole events INSERT failed and a fresh database got no
seed events. Two registration rows also pointed at the commented events. Fixed: `…011` now ends
the statement, the bare `;` is gone, and the two orphan registration rows are commented out with
their reason. Verified by loading `schema.sql` and `seed.sql` into an empty database: 6 users,
10 events, 18 registrations, 8 attendances, 8 ratings, 2 memberships, 1 block.

The API tests still expect the full seed from commit `27ca613` (12 events), so they keep loading
that version; see `HANDOFF.md`.

### Known limitations

- The app's reaction to a 401 (automatic logout) is still not tested automatically. The
  instrumented tests run inside the installed app's process, so a test that provoked a 401 would
  log the real account out. The server side of token expiry is covered by `test_tokens.py`.
- One test per run posts a notification; the run is not silent.

---

## Step 12 — Implemented versus verified, and a device test protocol (review point 12)

### Review point

> 12. For GPS, QR and geofencing, separate what is implemented from what is verified in real
> conditions, and describe device tests: entering the geofence, background location switched off,
> arriving before the start, and a phone reboot.

### What was missing

The backlog said "✅ done" for a feature as soon as the code existed and the tests passed. For
three features that is not enough, because their behaviour depends on the phone, the operating
system and the person carrying it: a geofence can simply never fire, and no test on a build server
notices.

### What was added

**A three-column distinction** in `Orbit/FEATURES.md` (section "Implemented versus verified"):
implemented, automated test, verified on a real device with a date and the conditions. Filled
honestly: F-34 has no recorded walk, F-41 has one dated on-screen scan and no printed-QR scan,
F-42 has nothing.

**A protocol**, `DEVICE_TESTS.md`: nine scenarios with setup, expected result, and the prediction
that follows from reading the code, plus empty columns for the actual result, PASS/FAIL and the
evidence. It also lists the rules under test with their constants and the log commands, so the
same walk can be repeated later.

### Why the two failures are predicted rather than fixed

Reading the code gives two failures before anyone walks anywhere:

- **Arriving before the start.** `GeofenceReceiver` reacts only to `GEOFENCE_TRANSITION_ENTER`,
  and a check-in before the start is refused by `AttendanceRules.canCheckIn` and by the server.
  The person is then already inside the circle, so no second ENTER arrives and the check-in never
  happens.
- **A reboot.** Android drops registered geofences on reboot. `EventGeofences.refresh()` runs at
  app start, after a registration change and from the Account screen, and there is no
  `BOOT_COMPLETED` receiver.

The fixes are described in `HANDOFF.md` (a one-time WorkManager job at the event start, and a boot
receiver). They are deliberately not written yet: a recorded failure, then the fix, then the same
scenario passing is a stronger argument in the thesis than a feature that was always green.

### Known limitations

- Mock locations are refused on purpose, so scenarios 1 to 5 cannot be simulated with
  `adb emu geo fix` or a mock-location app. They need real movement, which is why they are a
  protocol for a person and not a test suite.
- Geofence delivery is a Play services service. Its delay is not under the app's control, so the
  protocol says "within a few minutes" rather than a fixed number.
