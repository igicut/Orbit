"""F-35: granice trajanja pri pravljenju i izmeni, izmena ne sme da izgubi trajanje."""
import time
import uuid

from common import call, check, delete_test_events, finish, login

PREFIX = "DURTEST"
MAX_MINUTES = 7 * 24 * 60

marko = login("marko")
now = int(time.time() * 1000)


def event(duration):
    return {"id": str(uuid.uuid4()), "ownerId": "ignored", "title": PREFIX, "description": "duration test",
            "latitude": 44.8, "longitude": 20.46, "startTime": now + 3 * 86400000, "durationMinutes": duration,
            "category": "OTHER", "visibility": "PUBLIC", "createdAt": now}


for bad in (0, -5, MAX_MINUTES + 1):
    s, _ = call("POST", "/events", event(bad), marko)
    check(f"create with duration {bad} -> 400", s == 400, s)

s, _ = call("POST", "/events", event(MAX_MINUTES), marko)
check("create with exactly 7 days -> 201", s == 201, s)

body = event(90)
s, e = call("POST", "/events", body, marko)
check("create with 90 min -> 201 and stored", s == 201 and e["durationMinutes"] == 90, (s, e))

s, current = call("GET", f"/events/{body['id']}", token=marko)
s, e = call("PUT", f"/events/{body['id']}", {**current, "title": f"{PREFIX} edited"}, marko)
check("edit that sends the duration keeps it", s == 200 and e["durationMinutes"] == 90, (s, e))
s, e = call("PUT", f"/events/{body['id']}", {**current, "durationMinutes": 150}, marko)
check("edit changes duration to 150", s == 200 and e["durationMinutes"] == 150, (s, e))
s, _ = call("PUT", f"/events/{body['id']}", {**current, "durationMinutes": 0}, marko)
check("edit with duration 0 -> 400", s == 400, s)
s, e = call("PUT", f"/events/{body['id']}", {**current, "durationMinutes": None}, marko)
check("edit clearing duration -> 200 null", s == 200 and e.get("durationMinutes") is None, (s, e))

delete_test_events(PREFIX)
finish()
