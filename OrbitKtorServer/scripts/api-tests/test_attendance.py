"""F-34/F-27: potvrda dolaska (vreme, udaljenost, kapacitet), spisak gostiju i ocene samo za posetioce."""
import threading
import time
import uuid

from common import call, check, delete_test_events, finish, login, sql

PREFIX = "ATTTEST"
LAT, LNG = 44.8, 20.46
MIN = 60_000
KVIZ = "5eed0002-0000-4000-8000-000000000001"
HAKATON = "5eed0002-0000-4000-8000-000000000002"
IZLOZBA = "5eed0002-0000-4000-8000-000000000004"
AVALA = "5eed0002-0000-4000-8000-000000000007"
RODJENDAN = "5eed0002-0000-4000-8000-000000000009"
USER = {"5eed0001-0000-4000-8000-000000000002": "stefan", "5eed0001-0000-4000-8000-000000000003": "ana",
        "5eed0001-0000-4000-8000-000000000005": "jelena", "5eed0001-0000-4000-8000-000000000006": "nikola"}


def now_ms():
    return int(time.time() * 1000)


def new_event(token, starts_in_ms, capacity=None, duration=60, visibility="PUBLIC"):
    event = {"id": str(uuid.uuid4()), "ownerId": "ignored", "title": f"{PREFIX} {uuid.uuid4().hex[:6]}",
             "description": "attendance test", "latitude": LAT, "longitude": LNG,
             "startTime": now_ms() + starts_in_ms, "durationMinutes": duration, "category": "OTHER",
             "visibility": visibility, "capacity": capacity, "createdAt": now_ms()}
    if visibility == "PRIVATE":
        event["accessCode"] = uuid.uuid4().hex[:6].upper()
    status, _ = call("POST", "/events", event, token)
    assert status == 201, status
    return event["id"]


def check_in(event_id, token, lat=LAT, lng=LNG):
    return call("PUT", f"/events/{event_id}/attendance", {"latitude": lat, "longitude": lng}, token)


def shift_start(event_id, starts_in_ms):
    """Prijava je moguca samo pre pocetka, pa se pocetak test dogadjaja pomera u bazi."""
    sql(f"UPDATE events SET start_time = {now_ms() + starts_in_ms} WHERE id = '{event_id}' AND title LIKE '{PREFIX}%'")


milica, ana, marko, stefan, jelena, nikola = (login(n) for n in ("milica", "ana", "marko", "stefan", "jelena", "nikola"))

# ---- seed: dolasci, spisak i pravilo za ocene
s, d = call("GET", "/users/me/sync", token=milica)
check("milica sync has attendances for hakaton and izlozba",
      s == 200 and sorted(a["eventId"] for a in d["attendances"]) == sorted([HAKATON, IZLOZBA]), d.get("attendances"))

s, rows = call("GET", f"/events/{KVIZ}/attendees", token=milica)
summary = {USER.get(r["userId"], r["userId"]): (r["checkedInAt"] is not None, r["walkIn"]) for r in rows} if s == 200 else rows
check("organiser sees kviz list: 3 checked in, nikola walk-in, jelena without check-in",
      summary == {"stefan": (True, False), "ana": (True, False), "nikola": (True, True), "jelena": (False, False)}, summary)
check("attendee rows carry display names", s == 200 and all(r.get("displayName") for r in rows), rows)
s, _ = call("GET", f"/events/{KVIZ}/attendees", token=stefan)
check("participant cannot see the guest list -> 403", s == 403, s)
s, _ = call("GET", f"/events/{RODJENDAN}/attendees", token=marko)
check("private event without access -> 404", s == 404, s)

s, e = call("PATCH", f"/events/{KVIZ}/rating", {"value": 3}, ana)
check("attendee (ana) can change rating on kviz -> 200", s == 200 and e["ratingCount"] == 3, (s, e))
s, t = call("PATCH", f"/events/{KVIZ}/rating", {"value": 5}, jelena)
check("registered without check-in (jelena) cannot rate -> 403", s == 403, (s, t))
s, t = call("PATCH", f"/events/{KVIZ}/rating", {"value": 5}, milica)
check("organiser cannot rate own event -> 403", s == 403, (s, t))
s, t = call("PATCH", f"/events/{AVALA}/rating", {"value": 5}, milica)
check("registered user on a future event -> 409 not started", s == 409, (s, t))

# ---- dolazak bez prijave, kapacitet 2, poceo pre 5 min
E1 = new_event(marko, -5 * MIN, capacity=2)
s, e = check_in(E1, milica)
check("walk-in within 15 min at the location -> 200, count 1", s == 200 and e["registeredCount"] == 1, (s, e))
first = sql(f"SELECT checked_in_at FROM attendances WHERE event_id = '{E1}'")
time.sleep(1.1)
s, e = check_in(E1, milica)
check("repeated check-in is idempotent, count stays 1", s == 200 and e["registeredCount"] == 1, (s, e))
again = sql(f"SELECT checked_in_at FROM attendances WHERE event_id = '{E1}'")
check("repeated check-in keeps the first time", first != "" and again == first, (first, again))
s, t = check_in(E1, marko)
check("organiser cannot check in -> 400", s == 400, (s, t))
s, t = check_in(E1, ana, lat=LAT + 0.01)
check("1.1 km away -> 403 with distance", s == 403 and "m from the event" in t, (s, t))
s, t = check_in(E1, ana, lat=LAT + 0.0019)
check("about 211 m away -> 403", s == 403, (s, t))
s, e = check_in(E1, ana, lat=LAT + 0.00135)
check("about 150 m away -> 200, count 2", s == 200 and e["registeredCount"] == 2, (s, e))
s, t = check_in(E1, stefan)
check("walk-in on a full event -> 409 full", s == 409 and "full" in t, (s, t))
s, t = check_in(E1, stefan, lat=91)
check("invalid coordinates -> 400", s == 400, (s, t))
s, d = call("GET", "/users/me/sync", token=milica)
check("walk-in appears in sync as registration and attendance",
      E1 in [x["id"] for x in d["registeredEvents"]] and E1 in [a["eventId"] for a in d["attendances"]], d["attendances"])
s, e = call("PATCH", f"/events/{E1}/rating", {"value": 4}, milica)
check("after check-in milica can rate -> 200", s == 200 and e["ratingCount"] == 1, (s, e))
s, rows = call("GET", f"/events/{E1}/attendees", token=marko)
check("organiser list: 2 walk-ins, both checked in",
      s == 200 and len(rows) == 2 and all(r["walkIn"] and r["checkedInAt"] for r in rows), rows)

# ---- zapocet dogadjaj sa dolascima ne sme da se obrise, istorija gostiju ostaje
s, t = call("DELETE", f"/events/{E1}", token=marko)
kept = sql(f"SELECT (SELECT COUNT(*) FROM attendances WHERE event_id = '{E1}'), "
           f"(SELECT COUNT(*) FROM ratings WHERE event_id = '{E1}')")
check("deleting a started event -> 409, attendances and ratings stay", s == 409 and kept.split() == ["2", "1"], (s, t, kept))

# ---- pre pocetka
E2 = new_event(marko, 2 * 86400000, capacity=10)
call("PUT", f"/events/{E2}/registration", token=ana)
s, t = check_in(E2, ana)
check("check-in before start -> 409 opens at start", s == 409 and "opens" in t, (s, t))

# ---- prijavljen dolazi posle 15 min, neprijavljen ne moze
E3 = new_event(marko, 60 * MIN, capacity=5, duration=180)
call("PUT", f"/events/{E3}/registration", token=jelena)
shift_start(E3, -30 * MIN)
s, t = check_in(E3, nikola)
check("walk-in after 15 min -> 409 first 15 minutes", s == 409 and "15 minutes" in t, (s, t))
s, e = check_in(E3, jelena)
check("registered after 30 min -> 200, count stays 1", s == 200 and e["registeredCount"] == 1, (s, e))

# ---- kraj: trajanje 60 min, i podrazumevanih 180 min kad trajanja nema
E4 = new_event(marko, -61 * MIN, capacity=5, duration=60)
s, t = check_in(E4, milica)
check("after the end (duration 60) -> 409 ended", s == 409 and "ended" in t, (s, t))
E5 = new_event(marko, 60 * MIN, capacity=5, duration=None)
call("PUT", f"/events/{E5}/registration", token=ana)
call("PUT", f"/events/{E5}/registration", token=stefan)
shift_start(E5, -170 * MIN)
s, e = check_in(E5, ana)
check("no duration: 170 min after start is still open -> 200", s == 200, (s, e))
shift_start(E5, -190 * MIN)
s, t = check_in(E5, stefan)
check("no duration: 190 min after start is closed -> 409 ended", s == 409 and "ended" in t, (s, t))

# ---- petoro bez prijave za poslednje mesto
E6 = new_event(marko, -2 * MIN, capacity=1)
statuses = []
lock = threading.Lock()


def race(token):
    status, _ = check_in(E6, token)
    with lock:
        statuses.append(status)


threads = [threading.Thread(target=race, args=(t,)) for t in (milica, ana, stefan, jelena, nikola)]
for th in threads:
    th.start()
for th in threads:
    th.join()
counts = sql(f"SELECT (SELECT registered_count FROM events WHERE id = '{E6}'), "
             f"(SELECT COUNT(*) FROM registrations WHERE event_id = '{E6}'), "
             f"(SELECT COUNT(*) FROM attendances WHERE event_id = '{E6}')")
check("5 simultaneous walk-ins for 1 spot: one 200, counter 1, 1 registration, 1 attendance",
      sorted(statuses) == [200, 409, 409, 409, 409] and counts.split() == ["1", "1", "1"], (sorted(statuses), counts))

# ---- privatni bez clanstva
E7 = new_event(marko, -2 * MIN, capacity=5, visibility="PRIVATE")
s, _ = check_in(E7, milica)
check("private event without membership -> 404", s == 404, s)

# ---- vracanje seed ocene (vreme ocene ostaje novo) i ciscenje
call("PATCH", f"/events/{KVIZ}/rating", {"value": 4}, ana)
delete_test_events(PREFIX)
finish()
