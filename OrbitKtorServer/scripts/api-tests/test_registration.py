"""F-33: prijava na dogadjaje, kapacitet, trka za poslednje mesto i pristup."""
import threading
import time
import uuid

from common import call, check, delete_test_events, finish, login

PREFIX = "REGTEST"
AVALA = "5eed0002-0000-4000-8000-000000000007"
RODJENDAN = "5eed0002-0000-4000-8000-000000000009"


def new_event(token, capacity, starts_in_ms=2 * 86400000, **extra):
    now = int(time.time() * 1000)
    event = {"id": str(uuid.uuid4()), "ownerId": "ignored", "title": f"{PREFIX} {uuid.uuid4().hex[:6]}",
             "description": "registration test", "latitude": 44.8, "longitude": 20.46,
             "startTime": now + starts_in_ms, "category": "OTHER", "visibility": "PUBLIC",
             "capacity": capacity, "createdAt": now, **extra}
    status, _ = call("POST", "/events", event, token)
    return status, event


def titles(events):
    return sorted(e["title"] for e in events)


milica, ana, marko, stefan, jelena, nikola = (login(n) for n in ("milica", "ana", "marko", "stefan", "jelena", "nikola"))

# ---- podaci iz seed-a
s, d = call("GET", "/users/me/sync", token=milica)
check("milica sync lists her 6 seed registrations",
      titles(d["registeredEvents"]) == ["Hakaton na ETF-u", "Izložba mladih ilustratora", "Jutarnje trčanje na Adi",
                                        "Okupljanje na Studentskom trgu", "Planinarenje na Avali", "Rođendan u Zemunu"],
      titles(d.get("registeredEvents", [])))
s, e = call("GET", f"/events/{AVALA}", token=milica)
check("event JSON has registeredCount 3", s == 200 and e["registeredCount"] == 3, e)

# ---- validacija pri pravljenju
s, _ = new_event(marko, 0)
check("create with capacity 0 -> 400", s == 400, s)
s, _ = new_event(marko, 5, price=-1)
check("create with negative price -> 400", s == 400, s)

# ---- tok sa kapacitetom 2
s, ev = new_event(marko, 2)
check("create capacity-2 event -> 201", s == 201, s)
EID = ev["id"]
s, _ = call("PUT", f"/events/{EID}/registration", token=marko)
check("organiser cannot register for own event -> 400", s == 400, s)
s, e = call("PUT", f"/events/{EID}/registration", token=milica)
check("milica registers -> 200, count 1", s == 200 and e["registeredCount"] == 1, (s, e))
s, e = call("PUT", f"/events/{EID}/registration", token=milica)
check("registering twice is idempotent, count stays 1", s == 200 and e["registeredCount"] == 1, (s, e))
s, e = call("PUT", f"/events/{EID}/registration", token=ana)
check("ana registers -> count 2", s == 200 and e["registeredCount"] == 2, (s, e))
s, t = call("PUT", f"/events/{EID}/registration", token=stefan)
check("stefan on a full event -> 409 full", s == 409 and "full" in t, (s, t))
s, e = call("DELETE", f"/events/{EID}/registration", token=ana)
check("ana cancels -> count 1", s == 200 and e["registeredCount"] == 1, (s, e))
s, e = call("DELETE", f"/events/{EID}/registration", token=ana)
check("cancelling twice is idempotent, count stays 1", s == 200 and e["registeredCount"] == 1, (s, e))
s, e = call("PUT", f"/events/{EID}/registration", token=stefan)
check("freed spot: stefan registers -> count 2", s == 200 and e["registeredCount"] == 2, (s, e))
s, d = call("GET", "/users/me/sync", token=milica)
check("new registration appears in milica's sync", EID in [x["id"] for x in d["registeredEvents"]])

# ---- izmena kapaciteta
s, current = call("GET", f"/events/{EID}", token=marko)
s, t = call("PUT", f"/events/{EID}", {**current, "capacity": 1}, token=marko)
check("organiser lowers capacity below registered -> 409", s == 409, (s, t))
s, e = call("PUT", f"/events/{EID}", {**current, "capacity": 3}, token=marko)
check("organiser raises capacity -> 200, count kept", s == 200 and e["capacity"] == 3 and e["registeredCount"] == 2, (s, e))

# ---- petoro za poslednje mesto u isto vreme
s, last = new_event(marko, 1)
LAST = last["id"]
statuses = []
lock = threading.Lock()


def race(token):
    status, _ = call("PUT", f"/events/{LAST}/registration", token=token)
    with lock:
        statuses.append(status)


threads = [threading.Thread(target=race, args=(t,)) for t in (milica, ana, stefan, jelena, nikola)]
for th in threads:
    th.start()
for th in threads:
    th.join()
s, e = call("GET", f"/events/{LAST}", token=marko)
check("5 simultaneous registrations for 1 spot: exactly one 200, count 1",
      sorted(statuses) == [200, 409, 409, 409, 409] and e["registeredCount"] == 1, (sorted(statuses), e))

# ---- bez ogranicenja
s, open_event = new_event(marko, None)
for token in (milica, ana, stefan):
    call("PUT", f"/events/{open_event['id']}/registration", token=token)
s, e = call("GET", f"/events/{open_event['id']}", token=marko)
check("event without capacity counts unlimited registrations", e["capacity"] is None and e["registeredCount"] == 3, e)

# ---- posle pocetka
s, started = new_event(marko, 10, starts_in_ms=-3600000)
s, t = call("PUT", f"/events/{started['id']}/registration", token=milica)
check("register after start -> 409 closed", s == 409 and "closed" in t, (s, t))
s, t = call("DELETE", f"/events/{started['id']}/registration", token=milica)
check("cancel after start -> 409", s == 409, (s, t))
s, t = call("DELETE", f"/events/{started['id']}", token=marko)
check("organiser cannot delete a started event -> 409", s == 409, (s, t))

# ---- privatni dogadjaj
s, _ = call("PUT", f"/events/{RODJENDAN}/registration", token=marko)
check("non-member registers for private event -> 404", s == 404, s)
s, e = call("PUT", f"/events/{RODJENDAN}/registration", token=milica)
check("member (already registered) -> 200 idempotent", s == 200 and e["registeredCount"] == 1, (s, e))

# ---- brisanje buduceg dogadjaja brise prijave
s, _ = call("DELETE", f"/events/{EID}", token=marko)
check("organiser deletes a future event -> 204", s == 204, s)
s, d = call("GET", "/users/me/sync", token=milica)
check("deleted event is gone from milica's registrations", EID not in [x["id"] for x in d["registeredEvents"]])

delete_test_events(PREFIX)
finish()
