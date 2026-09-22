"""F-41: kod za QR na ulazu (samo organizator, ne menja se) i potvrda dolaska kodom umesto lokacije."""
import time
import uuid

from common import call, check, delete_test_events, finish, login

PREFIX = "QRTEST"
LAT, LNG = 44.8, 20.46
MIN = 60_000


def now_ms():
    return int(time.time() * 1000)


def new_event(token, starts_in_ms, capacity=None):
    event = {"id": str(uuid.uuid4()), "ownerId": "ignored", "title": f"{PREFIX} {uuid.uuid4().hex[:6]}",
             "description": "qr test", "latitude": LAT, "longitude": LNG,
             "startTime": now_ms() + starts_in_ms, "durationMinutes": 60, "category": "OTHER",
             "visibility": "PUBLIC", "capacity": capacity, "createdAt": now_ms()}
    status, _ = call("POST", "/events", event, token)
    assert status == 201, status
    return event["id"]


def check_in_with_code(event_id, token, code):
    return call("PUT", f"/events/{event_id}/attendance", {"code": code}, token)


# Organizator je Jelena, kao u test_attendance: Ana i Marko su razdvojeni blokadom iz seed-a
jelena, milica, stefan = (login(n) for n in ("jelena", "milica", "stefan"))

# ---- kod vidi samo organizator, i uvek isti
STARTED = new_event(jelena, -5 * MIN, capacity=5)

s, _ = call("GET", f"/events/{STARTED}/check-in-code")
check("code without a token -> 401", s == 401, s)

s, body = call("GET", f"/events/{STARTED}/check-in-code", token=jelena)
code = body.get("code", "") if s == 200 else ""
check("organiser gets an 8-character code", s == 200 and len(code) == 8, (s, body))

s, again = call("GET", f"/events/{STARTED}/check-in-code", token=jelena)
check("the code never changes, so a printed QR keeps working", s == 200 and again.get("code") == code, (s, again))

s, _ = call("GET", f"/events/{STARTED}/check-in-code", token=milica)
check("a guest cannot read the code -> 403", s == 403, s)

s, event = call("GET", f"/events/{STARTED}", token=milica)
check("the event itself does not carry the code", s == 200 and code not in str(event), event)

# ---- potvrda dolaska kodom, bez koordinata
s, _ = check_in_with_code(STARTED, milica, "WRONG123")
check("wrong code -> 403", s == 403, s)

s, _ = call("PUT", f"/events/{STARTED}/attendance", {}, milica)
check("neither a code nor coordinates -> 400", s == 400, s)

s, e = check_in_with_code(STARTED, milica, code)
check("right code without location -> 200, count 1", s == 200 and e["registeredCount"] == 1, (s, e))

s, _ = check_in_with_code(STARTED, jelena, code)
check("the organiser still cannot check in -> 400", s == 400, s)

# ---- ostala pravila vaze isto kao za GPS
FUTURE = new_event(jelena, 60 * MIN)
_, future_body = call("GET", f"/events/{FUTURE}/check-in-code", token=jelena)
s, _ = check_in_with_code(FUTURE, stefan, future_body.get("code"))
check("right code before the start -> 409", s == 409, s)

# Vreme se proverava pre koda (isto kao pre udaljenosti), pa ovaj dogadjaj mora da je poceo
OTHER = new_event(jelena, -5 * MIN)
call("GET", f"/events/{OTHER}/check-in-code", token=jelena)
s, _ = check_in_with_code(OTHER, stefan, code)
check("a code from another event -> 403", s == 403, s)

LATE = new_event(jelena, -20 * MIN)
_, late_body = call("GET", f"/events/{LATE}/check-in-code", token=jelena)
s, _ = check_in_with_code(LATE, stefan, late_body.get("code"))
check("without registration, after 15 minutes -> 409 even with the right code", s == 409, s)

NEVER_OPENED = new_event(jelena, -5 * MIN)
s, _ = check_in_with_code(NEVER_OPENED, stefan, code)
check("an event whose QR was never opened has no code -> 403", s == 403, s)

delete_test_events(PREFIX)
finish()
