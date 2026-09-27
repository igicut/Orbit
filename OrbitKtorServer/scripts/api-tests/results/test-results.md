# API test results

- Run: 2026-09-27 18:49
- Code: `27ca613 (with uncommitted changes)`
- Server: `http://localhost:8080`, database `orbit_test`
- **347 / 347 checks passed**

## Summary by suite

| Suite | Checks | PASS | FAIL |
|---|---|---|---|
| `test_registration` | 25 | 25 | 0 |
| `test_attendance` | 30 | 30 | 0 |
| `test_checkin_qr` | 13 | 13 | 0 |
| `test_duration` | 9 | 9 | 0 |
| `test_images` | 33 | 33 | 0 |
| `test_upload_failures` | 6 | 6 | 0 |
| `test_validation` | 25 | 25 | 0 |
| `test_access` | 92 | 92 | 0 |
| `test_concurrency` | 4 | 4 | 0 |
| `test_search` | 23 | 23 | 0 |
| `test_search_parse` | 17 | 17 | 0 |
| `test_profile` | 11 | 11 | 0 |
| `test_blocking` | 16 | 16 | 0 |
| `test_tokens` | 17 | 17 | 0 |
| `test_password_reset` | 18 | 18 | 0 |
| `test_auth_rate_limit` | 8 | 8 | 0 |
| **Total** | **347** | **347** | **0** |

## `test_registration`

| # | Scenario | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | milica sync lists her 6 seed registrations | the statement holds | ["Hakaton na ETF-u", "Izložba mladih ilustratora", "Jutarnje trčanje na Adi", "Okupljanje… | PASS |
| 2 | event JSON has registeredCount 3 | the statement holds | {"id": "5eed0002-0000-4000-8000-000000000007", "ownerId": "5eed0001-0000-4000-8000-000000… | PASS |
| 3 | create with capacity 0 | 400 | 400 | PASS |
| 4 | create with negative price | 400 | 400 | PASS |
| 5 | create capacity-2 event | 201 | 201 | PASS |
| 6 | organiser cannot register for own event | 400 | 400 | PASS |
| 7 | milica registers | 200, count 1 | 200, {"id": "bab9c3da-606b-4570-97e6-76fe54a98b13", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 8 | registering twice is idempotent, count stays 1 | the statement holds | 200, {"id": "bab9c3da-606b-4570-97e6-76fe54a98b13", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 9 | ana registers | count 2 | 200, {"id": "bab9c3da-606b-4570-97e6-76fe54a98b13", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 10 | stefan on a full event | 409 full | 409, The event is full | PASS |
| 11 | ana cancels | count 1 | 200, {"id": "bab9c3da-606b-4570-97e6-76fe54a98b13", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 12 | cancelling twice is idempotent, count stays 1 | the statement holds | 200, {"id": "bab9c3da-606b-4570-97e6-76fe54a98b13", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 13 | freed spot: stefan registers | count 2 | 200, {"id": "bab9c3da-606b-4570-97e6-76fe54a98b13", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 14 | new registration appears in milica's sync | the statement holds | holds | PASS |
| 15 | organiser lowers capacity below registered | 409 | 409, Capacity cannot be lower than the number of people already registered | PASS |
| 16 | organiser raises capacity | 200, count kept | 200, {"id": "bab9c3da-606b-4570-97e6-76fe54a98b13", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 17 | 5 simultaneous registrations for 1 spot: exactly one 200, count 1 | the statement holds | [200, 409, 409, 409, 409], {"id": "aadd5103-26d7-4073-8023-af57126b7509", "ownerId": "5ee… | PASS |
| 18 | event without capacity counts unlimited registrations | the statement holds | {"id": "2f49ea89-6a31-400d-a8b2-83db46a322d3", "ownerId": "5eed0001-0000-4000-8000-000000… | PASS |
| 19 | register after start | 409 closed | 409, Registration closed when the event started | PASS |
| 20 | cancel after start | 409 | 409, The event has started, the registration stays | PASS |
| 21 | organiser cannot delete a started event | 409 | 409, An event that has already started cannot be deleted | PASS |
| 22 | non-member registers for private event | 404 | 404 | PASS |
| 23 | member (already registered) | 200 idempotent | 200, {"id": "5eed0002-0000-4000-8000-000000000009", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 24 | organiser deletes a future event | 204 | 204 | PASS |
| 25 | deleted event is gone from milica's registrations | the statement holds | holds | PASS |

## `test_attendance`

| # | Scenario | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | milica sync has attendances for hakaton and izlozba | the statement holds | [{"eventId": "5eed0002-0000-4000-8000-000000000002", "checkedInAt": 1789839649000}, {"eve… | PASS |
| 2 | organiser sees kviz list: 3 checked in, nikola walk-in, jelena without check-in | the statement holds | {"stefan": [true, false], "ana": [true, false], "jelena": [false, false], "nikola": [true… | PASS |
| 3 | attendee rows carry display names | the statement holds | [{"userId": "5eed0001-0000-4000-8000-000000000002", "displayName": "Stefan Petrović", "re… | PASS |
| 4 | participant cannot see the guest list | 403 | 403 | PASS |
| 5 | private event without access | 404 | 404 | PASS |
| 6 | attendee (ana) can change rating on kviz | 200 | 200, {"id": "5eed0002-0000-4000-8000-000000000001", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 7 | registered without check-in (jelena) cannot rate | 403 | 403, Only people who checked in can rate this event | PASS |
| 8 | organiser cannot rate own event | 403 | 403, Only people who checked in can rate this event | PASS |
| 9 | registered user on a future event | 409 not started | 409, Cannot rate an event that has not started yet | PASS |
| 10 | walk-in within 15 min at the location | 200, count 1 | 200, {"id": "300ecd55-8a9b-4e86-b2c8-b7f03b5934ae", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 11 | repeated check-in is idempotent, count stays 1 | the statement holds | 200, {"id": "300ecd55-8a9b-4e86-b2c8-b7f03b5934ae", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 12 | repeated check-in keeps the first time | the statement holds | 1790527293544, 1790527293544 | PASS |
| 13 | organiser cannot check in | 400 | 400, Organisers do not check in to their own events | PASS |
| 14 | 1.1 km away | 403 with distance | 403, You are 1112 m from the event, check-in works within 200 m | PASS |
| 15 | about 211 m away | 403 | 403, You are 211 m from the event, check-in works within 200 m | PASS |
| 16 | about 150 m away | 200, count 2 | 200, {"id": "300ecd55-8a9b-4e86-b2c8-b7f03b5934ae", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 17 | walk-in on a full event | 409 full | 409, The event is full | PASS |
| 18 | invalid coordinates | 400 | 400, Invalid coordinates | PASS |
| 19 | walk-in appears in sync as registration and attendance | the statement holds | [{"eventId": "300ecd55-8a9b-4e86-b2c8-b7f03b5934ae", "checkedInAt": 1790527293544}, {"eve… | PASS |
| 20 | after check-in milica can rate | 200 | 200, {"id": "300ecd55-8a9b-4e86-b2c8-b7f03b5934ae", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 21 | organiser list: 2 walk-ins, both checked in | the statement holds | [{"userId": "5eed0001-0000-4000-8000-000000000001", "displayName": "Milica Jovanović", "r… | PASS |
| 22 | deleting a started event | 409, attendances and ratings stay | 409, An event that has already started cannot be deleted, 2 1 | PASS |
| 23 | check-in before start | 409 opens at start | 409, Check-in opens when the event starts | PASS |
| 24 | walk-in after 15 min | 409 first 15 minutes | 409, Without registration, check-in is only possible in the first 15 minutes | PASS |
| 25 | registered after 30 min | 200, count stays 1 | 200, {"id": "c47e9db8-e484-4546-b1cf-19c9eae96651", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 26 | after the end (duration 60) | 409 ended | 409, The event has ended | PASS |
| 27 | no duration: 170 min after start is still open | 200 | 200, {"id": "9070f0b4-50e0-44d8-b1e9-8673b6cbae84", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 28 | no duration: 190 min after start is closed | 409 ended | 409, The event has ended | PASS |
| 29 | 5 simultaneous walk-ins for 1 spot: one 200, counter 1, 1 registration, 1 attendance | the statement holds | [200, 409, 409, 409, 409], 1 1 1 | PASS |
| 30 | private event without membership | 404 | 404 | PASS |

## `test_checkin_qr`

| # | Scenario | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | code without a token | 401 | 401 | PASS |
| 2 | organiser gets an 8-character code | the statement holds | 200, {"code": "687GRVYM"} | PASS |
| 3 | the code never changes, so a printed QR keeps working | the statement holds | 200, {"code": "687GRVYM"} | PASS |
| 4 | a guest cannot read the code | 403 | 403 | PASS |
| 5 | the event itself does not carry the code | the statement holds | {"id": "b7fc0a19-b5c0-4b97-8c7b-d09c4ba31cec", "ownerId": "5eed0001-0000-4000-8000-000000… | PASS |
| 6 | wrong code | 403 | 403 | PASS |
| 7 | neither a code nor coordinates | 400 | 400 | PASS |
| 8 | right code without location | 200, count 1 | 200, {"id": "b7fc0a19-b5c0-4b97-8c7b-d09c4ba31cec", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 9 | the organiser still cannot check in | 400 | 400 | PASS |
| 10 | right code before the start | 409 | 409 | PASS |
| 11 | a code from another event | 403 | 403 | PASS |
| 12 | without registration, after 15 minutes | 409 even with the right code | 409 | PASS |
| 13 | an event whose QR was never opened has no code | 403 | 403 | PASS |

## `test_duration`

| # | Scenario | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | create with duration 0 | 400 | 400 | PASS |
| 2 | create with duration -5 | 400 | 400 | PASS |
| 3 | create with duration 10081 | 400 | 400 | PASS |
| 4 | create with exactly 7 days | 201 | 201 | PASS |
| 5 | create with 90 min | 201 and stored | 201, {"id": "73455145-63b1-4b84-bc51-150034c6e7dd", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 6 | edit that sends the duration keeps it | the statement holds | 200, {"id": "73455145-63b1-4b84-bc51-150034c6e7dd", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 7 | edit changes duration to 150 | the statement holds | 200, {"id": "73455145-63b1-4b84-bc51-150034c6e7dd", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 8 | edit with duration 0 | 400 | 400 | PASS |
| 9 | edit clearing duration | 200 null | 200, {"id": "73455145-63b1-4b84-bc51-150034c6e7dd", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |

## `test_images`

| # | Scenario | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | upload png | 201 with a generated path | 201, b'{"path":"/images/95c7e3e1-349e-459b-9d83-6094716ff57c.png"}' | PASS |
| 2 | uploader downloads the image before it is on any event | the statement holds | 200, 67 | PASS |
| 3 | cache is private, only the phone may keep the image | the statement holds | max-age=2592000, private | PASS |
| 4 | another user cannot download an image that is on no event | 404 | 404 | PASS |
| 5 | download without a token | 401 | 401 | PASS |
| 6 | upload text/plain | 415 | 415 | PASS |
| 7 | upload over 8 MB | 413 | 413 | PASS |
| 8 | upload that is not multipart | 415 | 415 | PASS |
| 9 | multipart without a file part | 400 | 400 | PASS |
| 10 | unknown image | 404 | 404 | PASS |
| 11 | path traversal | 404 | 404 | PASS |
| 12 | name that is not a stored image | 404 | 404 | PASS |
| 13 | create event with two uploaded images | 201 | 201, {"id": "71c8b531-670f-47cb-b558-11153584ec3f", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 14 | another user sees the same paths | the statement holds | {"id": "71c8b531-670f-47cb-b558-11153584ec3f", "ownerId": "5eed0001-0000-4000-8000-000000… | PASS |
| 15 | once on a public event, another user downloads the image | the statement holds | 200, 67 | PASS |
| 16 | user who blocked the organiser cannot download the image | 404 | 404 | PASS |
| 17 | create event with a local content URI | 400 | 400, Images must first be uploaded with POST /images | PASS |
| 18 | create event with 6 images | 400 | 400, An event can have at most 5 images | PASS |
| 19 | create event with someone else's image | 400 | 400, Moguce je dodati samo sopstvene slike | PASS |
| 20 | create event with own image | 201 | 201, {"id": "ab6c5ac5-589d-4b33-b494-ae6b9580117d", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 21 | edit that adds someone else's image | 400 | 400, Moguce je dodati samo sopstvene slike | PASS |
| 22 | after the rejected edit the other organiser's image is still on disk | the statement holds | 200 | PASS |
| 23 | create private event | 201 | 201, {"id": "6fa42cd6-416a-4f87-a56a-24911bde1c6d", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 24 | non-member cannot download a private event's image | 404 | 404 | PASS |
| 25 | milica joins with the access code | 200 | 200 | PASS |
| 26 | member downloads the private event's image | the statement holds | 200 | PASS |
| 27 | rating with someone else's image | 400 | 400, Moguce je dodati samo sopstvene slike | PASS |
| 28 | rating with own image | 200 | 200 | PASS |
| 29 | anyone who sees the event downloads the rating image | the statement holds | 200 | PASS |
| 30 | edit keeps the remaining image | the statement holds | 200, {"id": "71c8b531-670f-47cb-b558-11153584ec3f", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 31 | image removed from the event is deleted from disk | 404 | 404 | PASS |
| 32 | organiser deletes the event | 204 | 204 | PASS |
| 33 | images of a deleted event are gone | 404 | 404 | PASS |

## `test_upload_failures`

| # | Scenario | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | upload without a token | 401 | 401 | PASS |
| 2 | upload cut off halfway leaves no file and no images row | unchanged: (266, 257) | files and rows before (266, 257), after (266, 257) | PASS |
| 3 | upload over 8 MB is refused and leaves nothing behind | 413, unchanged | 413; before (266, 257), after (266, 257) | PASS |
| 4 | upload of a text file is refused and leaves nothing behind | 415, unchanged | 415; before (266, 257), after (266, 257) | PASS |
| 5 | empty file is refused and leaves nothing behind | 400, unchanged | 400; before (266, 257), after (266, 257) | PASS |
| 6 | event with a photo path that was never uploaded | 400 | 400, Moguce je dodati samo sopstvene slike | PASS |

## `test_validation`

| # | Scenario | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | valid event | 201 | 201, {"id": "26487d2a-00cc-4380-bc7c-2493e7b736d1", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 2 | blank description | 400 | 400, Opis je obavezan | PASS |
| 3 | no photo | 400 | 400, Potrebna je bar jedna fotografija | PASS |
| 4 | start in the past | 400 | 400, Pocetak mora biti u buducnosti | PASS |
| 5 | latitude 95 | 400 | 400, Lokacija nije ispravna | PASS |
| 6 | longitude -181 | 400 | 400, Lokacija nije ispravna | PASS |
| 7 | title over 200 characters | 400, not 500 | 400, Naslov moze imati najvise 200 znakova | PASS |
| 8 | address over 300 characters | 400, not 500 | 400, Adresa moze imati najvise 300 znakova | PASS |
| 9 | description over 10000 characters | 400 | 400, Opis moze imati najvise 10000 znakova | PASS |
| 10 | private event gets a server code of 6 readable characters | the statement holds | 201, {"id": "77fb0bda-a3e1-40e4-bf8c-1370b8566058", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 11 | the code the client sent is ignored | the statement holds | ZHRUZF | PASS |
| 12 | two private events get different codes | the statement holds | 201, {"id": "b1ea8bbf-0422-4287-810a-1a223270fb07", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 13 | another user joins with the server code | the statement holds | 200, {"id": "77fb0bda-a3e1-40e4-bf8c-1370b8566058", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 14 | public event never carries an access code | the statement holds | 201, {"id": "0093da0e-db86-413c-afd8-f126fe6b3e6a", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 15 | a new event is always active, whatever the client sends | the statement holds | ACTIVE, ACTIVE | PASS |
| 16 | edit with a blank title | 400 | 400, Naslov je obavezan | PASS |
| 17 | edit with a blank description | 400 | 400, Opis je obavezan | PASS |
| 18 | edit with latitude 95 | 400 for the location, not for the distance | 400, Lokacija nije ispravna | PASS |
| 19 | edit removing every photo | 400 | 400, Potrebna je bar jedna fotografija | PASS |
| 20 | edit cannot change the access code | the statement holds | 200, {"id": "77fb0bda-a3e1-40e4-bf8c-1370b8566058", "ownerId": "5eed0001-0000-4000-8000-0… | PASS |
| 21 | valid edit still works | 200 | 200 | PASS |
| 22 | profile name of 41 characters | 400 | 400 | PASS |
| 23 | profile name of exactly 40 characters | 200 | 200, {"id": "5eed0001-0000-4000-8000-000000000004", "displayName": "nnnnnnnnnnnnnnnnnnnnn… | PASS |
| 24 | cleanup: the seed name is back | the statement holds | {"id": "5eed0001-0000-4000-8000-000000000004", "displayName": "Marko Ilić", "interests": … | PASS |
| 25 | sign up with a name of 41 characters | 400 | 400 | PASS |

## `test_access`

| # | Scenario | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | view the event on the public event as owner | 200 | 200 | PASS |
| 2 | view the event on the public event as stranger | 200 | 200 | PASS |
| 3 | view the event on the public event as member | 200 | 200 | PASS |
| 4 | view the event on the public event as blocked | 404 | 404 | PASS |
| 5 | view the photo on the public event as owner | 200 | 200 | PASS |
| 6 | view the photo on the public event as stranger | 200 | 200 | PASS |
| 7 | view the photo on the public event as member | 200 | 200 | PASS |
| 8 | view the photo on the public event as blocked | 404 | 404 | PASS |
| 9 | view the ratings on the public event as owner | 200 | 200 | PASS |
| 10 | view the ratings on the public event as stranger | 200 | 200 | PASS |
| 11 | view the ratings on the public event as member | 200 | 200 | PASS |
| 12 | view the ratings on the public event as blocked | 404 | 404 | PASS |
| 13 | view the guest list on the public event as owner | 200 | 200 | PASS |
| 14 | view the guest list on the public event as stranger | 403 | 403 | PASS |
| 15 | view the guest list on the public event as member | 403 | 403 | PASS |
| 16 | view the guest list on the public event as blocked | 404 | 404 | PASS |
| 17 | view the entry QR code on the public event as owner | 200 | 200 | PASS |
| 18 | view the entry QR code on the public event as stranger | 403 | 403 | PASS |
| 19 | view the entry QR code on the public event as member | 403 | 403 | PASS |
| 20 | view the entry QR code on the public event as blocked | 404 | 404 | PASS |
| 21 | register on the public event as owner | 400 | 400 | PASS |
| 22 | register on the public event as stranger | 200 | 200 | PASS |
| 23 | register on the public event as member | 200 | 200 | PASS |
| 24 | register on the public event as blocked | 404 | 404 | PASS |
| 25 | cancel a registration on the public event as stranger | 200 | 200 | PASS |
| 26 | cancel a registration on the public event as blocked | 404 | 404 | PASS |
| 27 | check in on the public event as stranger | 409 | 409 | PASS |
| 28 | check in on the public event as member | 409 | 409 | PASS |
| 29 | check in on the public event as blocked | 404 | 404 | PASS |
| 30 | rate on the public event as stranger | 409 | 409 | PASS |
| 31 | rate on the public event as member | 409 | 409 | PASS |
| 32 | rate on the public event as blocked | 404 | 404 | PASS |
| 33 | edit on the public event as stranger | 403 | 403 | PASS |
| 34 | edit on the public event as member | 403 | 403 | PASS |
| 35 | edit on the public event as blocked | 404 | 404 | PASS |
| 36 | cancel the event on the public event as stranger | 403 | 403 | PASS |
| 37 | cancel the event on the public event as member | 403 | 403 | PASS |
| 38 | cancel the event on the public event as blocked | 404 | 404 | PASS |
| 39 | delete the event on the public event as stranger | 403 | 403 | PASS |
| 40 | delete the event on the public event as member | 403 | 403 | PASS |
| 41 | delete the event on the public event as blocked | 404 | 404 | PASS |
| 42 | edit on the public event as owner | 200 | 200 | PASS |
| 43 | view the event on the private event as owner | 200 | 200 | PASS |
| 44 | view the event on the private event as stranger | 404 | 404 | PASS |
| 45 | view the event on the private event as member | 200 | 200 | PASS |
| 46 | view the event on the private event as blocked | 404 | 404 | PASS |
| 47 | view the photo on the private event as owner | 200 | 200 | PASS |
| 48 | view the photo on the private event as stranger | 404 | 404 | PASS |
| 49 | view the photo on the private event as member | 200 | 200 | PASS |
| 50 | view the photo on the private event as blocked | 404 | 404 | PASS |
| 51 | view the ratings on the private event as owner | 200 | 200 | PASS |
| 52 | view the ratings on the private event as stranger | 404 | 404 | PASS |
| 53 | view the ratings on the private event as member | 200 | 200 | PASS |
| 54 | view the ratings on the private event as blocked | 404 | 404 | PASS |
| 55 | view the guest list on the private event as owner | 200 | 200 | PASS |
| 56 | view the guest list on the private event as stranger | 404 | 404 | PASS |
| 57 | view the guest list on the private event as member | 403 | 403 | PASS |
| 58 | view the guest list on the private event as blocked | 404 | 404 | PASS |
| 59 | view the entry QR code on the private event as owner | 200 | 200 | PASS |
| 60 | view the entry QR code on the private event as stranger | 404 | 404 | PASS |
| 61 | view the entry QR code on the private event as member | 403 | 403 | PASS |
| 62 | view the entry QR code on the private event as blocked | 404 | 404 | PASS |
| 63 | register on the private event as owner | 400 | 400 | PASS |
| 64 | register on the private event as stranger | 404 | 404 | PASS |
| 65 | register on the private event as member | 200 | 200 | PASS |
| 66 | register on the private event as blocked | 404 | 404 | PASS |
| 67 | cancel a registration on the private event as stranger | 404 | 404 | PASS |
| 68 | cancel a registration on the private event as blocked | 404 | 404 | PASS |
| 69 | check in on the private event as stranger | 404 | 404 | PASS |
| 70 | check in on the private event as member | 409 | 409 | PASS |
| 71 | check in on the private event as blocked | 404 | 404 | PASS |
| 72 | rate on the private event as stranger | 404 | 404 | PASS |
| 73 | rate on the private event as member | 409 | 409 | PASS |
| 74 | rate on the private event as blocked | 404 | 404 | PASS |
| 75 | edit on the private event as stranger | 404 | 404 | PASS |
| 76 | edit on the private event as member | 403 | 403 | PASS |
| 77 | edit on the private event as blocked | 404 | 404 | PASS |
| 78 | cancel the event on the private event as stranger | 404 | 404 | PASS |
| 79 | cancel the event on the private event as member | 403 | 403 | PASS |
| 80 | cancel the event on the private event as blocked | 404 | 404 | PASS |
| 81 | delete the event on the private event as stranger | 404 | 404 | PASS |
| 82 | delete the event on the private event as member | 403 | 403 | PASS |
| 83 | delete the event on the private event as blocked | 404 | 404 | PASS |
| 84 | edit on the private event as owner | 200 | 200 | PASS |
| 85 | view the organiser's events on the profile event as owner | 200 | 200 | PASS |
| 86 | view the organiser's events on the profile event as stranger | 200 | 200 | PASS |
| 87 | view the organiser's events on the profile event as blocked | 404 | 404 | PASS |
| 88 | the organiser's profile lists the public event and not the private one | public listed, private not | ["b0590827-e66f-48bf-82de-3eef40cc7bab", "5eed0002-0000-4000-8000-000000000008"] | PASS |
| 89 | view the organiser's name on the profile event as stranger | 200 | 200 | PASS |
| 90 | view the organiser's name on the profile event as blocked | 404 | 404 | PASS |
| 91 | join with the access code on the private event as blocked | 404 | 404 | PASS |
| 92 | join with the access code on the private event as stranger | 200 | 200 | PASS |

## `test_concurrency`

| # | Scenario | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | 5 people register at once for the last seat(s), 20 rounds with capacity 1 and 2 | each round: capacity × 200, the rest 409, counter = rows = capacity | all 20 rounds: exactly capacity × 200, rest 409, counter = rows = capacity | PASS |
| 2 | one person sends the same registration 5 times at once, 20 rounds | each round: 200 for all, counter 1, 1 registration row | all 20 rounds: 5 × 200, counter 1, 1 row | PASS |
| 3 | 5 walk-ins check in at once for the last spot, 10 rounds | each round: one 200, four 409, counter 1, 1 registration, 1 attendance | all 10 rounds: one 200, four 409, counter 1, 1 registration, 1 attendance | PASS |
| 4 | one person registers and cancels 3 + 3 times at once, 20 rounds | each round: counter = number of rows, 0 or 1 | all 20 rounds: counter equals the rows (0 or 1) | PASS |

## `test_search`

| # | Scenario | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | search without a query | 200 | 200, 9 | PASS |
| 2 | no query means no relevance field | the statement holds | [] | PASS |
| 3 | private events stay out of search | the statement holds | ["Hakaton na ETF-u", "Izložba mladih ilustratora", "Kviz veče u Skadarliji", "Okupljanje … | PASS |
| 4 | keyword search still finds the literal match | the statement holds | ["Hakaton na ETF-u"] | PASS |
| 5 | keyword search matches the description too | the statement holds | ["Kviz veče u Skadarliji"] | PASS |
| 6 | a word nobody uses returns nothing | the statement holds | [] | PASS |
| 7 | a natural-language question finds the wine tasting | the statement holds | ["Degustacija domaćih vina"] | PASS |
| 8 | matches carry a similarity score | the statement holds | [0.7421931290109581] | PASS |
| 9 | scored results are ordered from most to least similar | the statement holds | [0.7421931290109581] | PASS |
| 10 | semantic search does not leak private events | the statement holds | ["Degustacija domaćih vina"] | PASS |
| 11 | another question finds the hackathon | the statement holds | ["Hakaton na ETF-u"] | PASS |
| 12 | the literal match survives semantic ranking | the statement holds | ["Hakaton na ETF-u"] | PASS |
| 13 | category filter still applies to semantic results | the statement holds | [] | PASS |
| 14 | radius filter still applies to semantic results | the statement holds | 0, 1 | PASS |
| 15 | create event | 201 | 201 | PASS |
| 16 | a new event gets an embedding without blocking the request | the statement holds | 1790527475902 | PASS |
| 17 | the new event is found by meaning, not by words | the statement holds | ["SRCHTEST Radionica keramike"] | PASS |
| 18 | edit | 200 | 200 | PASS |
| 19 | editing the text refreshes the embedding | the statement holds | 1790527475902, 1790527477532 | PASS |
| 20 | after the edit the event no longer matches the old meaning | the statement holds | [] | PASS |
| 21 | after the edit the event is found by the new meaning | the statement holds | ["SRCHTEST Poreske prijave"] | PASS |
| 22 | delete | 204 | 204 | PASS |
| 23 | deleting an event removes its embedding | the statement holds | holds | PASS |

## `test_search_parse`

| # | Scenario | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | without a token | 401 | 401 | PASS |
| 2 | blank text | 400 | 400 | PASS |
| 3 | text over 200 characters | 400 | 400 | PASS |
| 4 | the mentor's sentence | 200 | 200 | PASS |
| 5 | mentor's sentence: keywords is a string | the statement holds | {"keywords": "", "category": "MUSIC", "radius": "NEARBY", "dateWindow": null, "sort": "NE… | PASS |
| 6 | mentor's sentence: category is empty or an allowed name | the statement holds | MUSIC | PASS |
| 7 | mentor's sentence: radius is empty or an allowed name | the statement holds | NEARBY | PASS |
| 8 | mentor's sentence: dateWindow is empty or an allowed name | the statement holds | None | PASS |
| 9 | mentor's sentence: sort is empty or an allowed name | the statement holds | NEAREST | PASS |
| 10 | mentor's sentence: price is empty or an allowed name | the statement holds | None | PASS |
| 11 | a single word | 200 | 200 | PASS |
| 12 | single word: keywords is a string | the statement holds | {"keywords": "kviz", "category": null, "radius": null, "dateWindow": null, "sort": null, … | PASS |
| 13 | single word: category is empty or an allowed name | the statement holds | None | PASS |
| 14 | single word: radius is empty or an allowed name | the statement holds | None | PASS |
| 15 | single word: dateWindow is empty or an allowed name | the statement holds | None | PASS |
| 16 | single word: sort is empty or an allowed name | the statement holds | None | PASS |
| 17 | single word: price is empty or an allowed name | the statement holds | None | PASS |

## `test_profile`

| # | Scenario | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | without a token | 401 | 401 | PASS |
| 2 | another organiser's profile | 200 | 200 | PASS |
| 3 | a past public event is listed | the statement holds | ["Hakaton na ETF-u", "Okupljanje na Studentskom trgu"] | PASS |
| 4 | a private event is not listed | the statement holds | ["Hakaton na ETF-u", "Okupljanje na Studentskom trgu"] | PASS |
| 5 | every listed event is public | the statement holds | ["PUBLIC", "PUBLIC"] | PASS |
| 6 | the list includes events that already started | the statement holds | [1789836049000, 1790527549000] | PASS |
| 7 | every seeded event carries at least one photo | the statement holds | [["/images/5eed1a9e-0000-4000-8000-000000000014.jpg", "/images/5eed1a9e-0000-4000-8000-00… | PASS |
| 8 | another private event is hidden too | the statement holds | ["Izložba mladih ilustratora"] | PASS |
| 9 | the blocker cannot open the blocked profile | 404 | 404 | PASS |
| 10 | the blocked user cannot open the blocker's profile | 404 | 404 | PASS |
| 11 | unknown user | 200 with an empty list | 200, [] | PASS |

## `test_blocking`

| # | Scenario | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | before blocking Ana sees Milica's event | the statement holds | holds | PASS |
| 2 | before blocking Milica sees Ana's event | the statement holds | holds | PASS |
| 3 | Ana can register before being blocked | the statement holds | 200 | PASS |
| 4 | the registration shows up in Ana's sync | the statement holds | holds | PASS |
| 5 | Milica blocks Ana | the statement holds | 204 | PASS |
| 6 | Milica no longer sees Ana's event | the statement holds | holds | PASS |
| 7 | Ana no longer sees Milica's event in the list | the statement holds | holds | PASS |
| 8 | the detail looks like it does not exist for Ana | the statement holds | 404 | PASS |
| 9 | Milica cannot register for Ana's event | the statement holds | 404 | PASS |
| 10 | the blocked event drops out of Ana's sync | the statement holds | holds | PASS |
| 11 | Milica still sees her own event | the statement holds | holds | PASS |
| 12 | Ana still sees her own event | the statement holds | holds | PASS |
| 13 | Ana's blocked list stays empty, she cannot learn who blocked her | the statement holds | ["5eed0001-0000-4000-8000-000000000004"] | PASS |
| 14 | Milica unblocks Ana | the statement holds | 204 | PASS |
| 15 | after unblocking Ana sees the event again | the statement holds | holds | PASS |
| 16 | after unblocking the registration is back in sync | the statement holds | holds | PASS |

## `test_tokens`

| # | Scenario | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | a real token from login | 200 | 200 | PASS |
| 2 | a token the test signs like the server does | 200 | 200 | PASS |
| 3 | token that expired an hour ago | 401 | 401 | PASS |
| 4 | token that expired one second ago | 401 | 401 | PASS |
| 5 | the same token works before its expiry and not after it | 200, then 401 | 200, 401 | PASS |
| 6 | token issued in the future | 401 | 401 | PASS |
| 7 | token signed with another key | 401 | 401 | PASS |
| 8 | payload changed to another user, old signature kept | 401 | 401 | PASS |
| 9 | unsigned token (alg none) | 401 | 401 | PASS |
| 10 | token that claims another algorithm | 401 | 401 | PASS |
| 11 | token from another issuer | 401 | 401 | PASS |
| 12 | token for another audience | 401 | 401 | PASS |
| 13 | token without a user id | 401 | 401 | PASS |
| 14 | token with an empty user id | 401 | 401 | PASS |
| 15 | valid token without the 'Bearer ' prefix | 401 | 401 | PASS |
| 16 | header with garbage instead of a token | 401 | 401 | PASS |
| 17 | no Authorization header | 401 | 401 | PASS |

## `test_password_reset`

| # | Scenario | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | forgot-password answers 200 for an existing account | the statement holds | 200, Ako nalog sa ovim emailom postoji, kod je poslat | PASS |
| 2 | an unknown email gets exactly the same answer | the statement holds | 200, Ako nalog sa ovim emailom postoji, kod je poslat | PASS |
| 3 | a code is stored only for the existing account, as a bcrypt hash | the statement holds | $2a$12$BgGMBQpJZsqmCmhRN1TVEuGYHcV2jIvMfc1Sr2RwGo7eWfkjVZHmW, 0 | PASS |
| 4 | forgot-password with an invalid email | 400 | 400 | PASS |
| 5 | code that is not 6 digits | 400 without using an attempt | 400, Kod nije ispravan ili je istekao, 0 | PASS |
| 6 | short new password | 400 without using an attempt | 400, Password must have at least 8 characters, 0 | PASS |
| 7 | wrong code | 400 and one attempt used | 400, Kod nije ispravan ili je istekao, 1 | PASS |
| 8 | correct code | 200 with a token for the same account | 200, {"token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJvcmJpdC1zZXJ2ZXIiLCJhdWQi… | PASS |
| 9 | the same code cannot be used twice | 400 | 400, Kod nije ispravan ili je istekao | PASS |
| 10 | login with the new password | 200 | 200 | PASS |
| 11 | login with the old password | 401 | 401 | PASS |
| 12 | a token issued before the reset | 401 | 401 | PASS |
| 13 | the token from the reset works | 200 | 200 | PASS |
| 14 | after 5 wrong codes even the correct one | 400 | [400, 400, 400, 400, 400], 400, 5 | PASS |
| 15 | expired code | 400 | 400 | PASS |
| 16 | a new request replaces the previous code | old code 400 | 400 | PASS |
| 17 | request without a code (old app) | 400, not 500 | 400 | PASS |
| 18 | cleanup: the seed password works again | the statement holds | 200 | PASS |

## `test_auth_rate_limit`

| # | Scenario | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | first 10 wrong logins | 401 | [401, 401, 401, 401, 401, 401, 401, 401, 401, 401, 429] | PASS |
| 2 | attempt 11 | 429 | [401, 401, 401, 401, 401, 401, 401, 401, 401, 401, 429] | PASS |
| 3 | 429 carries Retry-After | the statement holds | 57 | PASS |
| 4 | correct password is also blocked until the limit refills | the statement holds | 429 | PASS |
| 5 | signup shares the same limit | 429 | 429 | PASS |
| 6 | forgot-password shares the same limit | 429 | 429 | PASS |
| 7 | other routes are not limited | the statement holds | 200 | PASS |
| 8 | after Retry-After the correct login works again | the statement holds | 200 | PASS |
