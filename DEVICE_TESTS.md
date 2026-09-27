# Device test protocol — GPS, QR and geofence check-in (review point 12)

The reviewer asked that "implemented" and "verified in real conditions" be told apart. Automated
tests cover the rules; they cannot cover a phone walking into a park. This file is the protocol
for the tests that need a real device and a real walk, and the place to write down what happened.

Fill the three right-hand columns while testing. Write only what was actually observed. A row
with no evidence stays empty, not "PASS".

## Rules under test

| Rule | Value | Where |
|---|---|---|
| Check-in radius | 200 m | `AttendanceRules.CHECK_IN_RADIUS_METERS`, same value on the server |
| Worst accepted GPS accuracy | 100 m | `AttendanceRules.MAX_ACCURACY_METERS` |
| Check-in window | from the start to the end of the event | `AttendanceRules.canCheckIn` |
| Walk-in without registration | first 15 minutes only, capacity permitting | `WALK_IN_WINDOW_MINUTES` |
| Geofence per event | events starting within 24 h, at most 20 | `EventGeofences` |
| Geofence trigger | ENTER only | `EventGeofences.geofenceFor` |
| Mock locations | refused | `GeofenceReceiver`, `EventDetailViewModel.attemptCheckIn` |

Because mock locations are refused on purpose, scenarios 1 to 5 cannot be faked with
`adb emu geo fix` or a mock-location app. They need real movement.

## Setup before the first scenario

1. Migration on the database the server uses, once:

   ```sql
   ALTER TABLE user_credentials ADD COLUMN password_changed_at BIGINT NOT NULL DEFAULT 0;
   ```

2. Server running and reachable from the phone (`adb reverse` is wired into every `install*`
   task). A phone away from the computer needs the server on a reachable address instead.
3. `db/odbrana.sql` for scenarios 1, 2 and 8: one event that starts 10 minutes after the script
   runs, entry code `ODBRANA7`, coordinates 44.820194, 20.398972. For a real walk, change those
   coordinates to a place you can walk into from more than 500 m away.
4. App installed and logged in. After any `connectedDebugAndroidTest` run the app is uninstalled,
   so install and log in again.
5. Permissions: location "Allow all the time" through the Account screen card, and notifications
   allowed. Scenario 3 deliberately changes this.
6. Log capture, in a second terminal:

   ```bash
   adb logcat -v time -s EventGeofences:* GeofenceReceiver:* QrScanner:* OrbitSync:*
   ```

7. Screenshot when something is on screen:

   ```bash
   adb exec-out screencap -p > shot.png
   ```

## Scenarios

| # | Scenario | Setup | Expected | Prediction from the code | Actual | PASS/FAIL | Evidence |
|---|---|---|---|---|---|---|---|
| 1 | Entering the geofence | registered event that has already started; walk in from more than 500 m, app in the background | "Checked in" notification within a few minutes; the event moves to visited | should pass | | | |
| 2 | Same, app killed from recents | as 1 | as 1 | should pass | | | |
| 3 | Background location off | permission set to "only while using the app" | no automatic check-in; the manual button still works; the Account card shows automatic check-in as off | should pass | | | |
| 4 | Early arrival | arrive 20 minutes before the start and stay inside | check-in once the event starts | **fails**: ENTER already happened, the check-in was refused as too early, and no second ENTER comes | | | |
| 5 | Phone reboot | register, reboot, do not open the app, walk in | check-in | **fails**: Android drops geofences on reboot and nothing re-registers them | | | |
| 6 | Manual GPS check-in at 150 m and 250 m | outdoors, event started | 150 m accepted, 250 m refused with the distance message | should pass | | | |
| 7 | Poor accuracy indoors | inside a building, accuracy worse than 100 m | refused with the accuracy message | should pass | | | |
| 8 | QR: printed code, another event's code, before the start | printed PNG from the organiser sheet | accepted / refused / refused | should pass | | | |
| 9 | Created offline, then online | airplane mode while creating, then network | the event syncs; a private event gets its access code from the server | should pass | | | |

## What to write in "Actual"

- Scenarios 1 to 5: the logcat lines with their time, and whether the notification appeared.
  `GeofenceReceiver` logs `Automatic check-in refused for <id>` when the server says no.
- Scenario 6: the two distances the app showed, and the message for 250 m.
- Scenario 7: the accuracy the app reported.
- Scenario 8: which of the three attempts were accepted, and the message for the refused ones.
- Scenario 9: whether the event appeared on the server after the network came back, and whether
  the access code was filled in.

## After the walk

Scenarios 4 and 5 are expected to fail. Their fixes are described in `HANDOFF.md`:
a one-time WorkManager job at the event start for the early arrival, and a `BOOT_COMPLETED`
receiver for the reboot. Record the failure first, then fix, then repeat 4 and 5 only.
