"""
Pristup tudjim i privatnim resursima, kao matrica: svaka zasticena ruta x svaka uloga.

Ocekivani statusi su izvedeni iz pravila projekta, a ne iz trenutnog koda:
  - privatni dogadjaj bez pristupa izgleda kao da ne postoji: 404
  - blokada u bilo kom smeru krije sve sto pripada tom nalogu: 404
  - radnja samo za organizatora, a korisnik dogadjaj vidi: 403
"""
import time
import uuid
from pathlib import Path

from common import call, check, delete_test_events, finish, login, photo, request_bytes

PREFIX = "ACCTEST"
DAY = 86400000
MARKO = "5eed0001-0000-4000-8000-000000000004"

marko, milica, stefan, ana = login("marko"), login("milica"), login("stefan"), login("ana")
# Ana je u seed.sql blokirala Marka; Stefan postaje clan privatnog dogadjaja kodom
CALLERS = {"owner": marko, "stranger": milica, "member": stefan, "blocked": ana}


def create(visibility):
    now = int(time.time() * 1000)
    event = {"id": str(uuid.uuid4()), "ownerId": "ignored", "title": f"{PREFIX} {visibility.lower()}",
             "description": "access matrix", "latitude": 44.8, "longitude": 20.46, "startTime": now + 3 * DAY,
             "durationMinutes": 60, "category": "OTHER", "visibility": visibility, "capacity": 10,
             "imageUris": [photo(marko)], "createdAt": now}
    status, body = call("POST", "/events", event, marko)
    assert status == 201, (status, body)
    return body


public = create("PUBLIC")
private = create("PRIVATE")
status, _ = call("POST", "/events/join", {"accessCode": private["accessCode"]}, stefan)
assert status == 200, status

matrix = []  # (radnja, dogadjaj, uloga, ocekivano, dobijeno) za tabelu na kraju


def cell(action, event_label, role, expected, send):
    """Jedna celija matrice: poziv u ime uloge i provera statusa"""
    status = send(CALLERS[role])
    ok = status in expected if isinstance(expected, tuple) else status == expected
    shown = " or ".join(map(str, expected)) if isinstance(expected, tuple) else str(expected)
    check(f"{action} on the {event_label} event as {role}", ok, status, expected=shown)
    matrix.append((action, event_label, role, shown, status, ok))


def json_call(method, path, body=None):
    return lambda token: call(method, path, body, token)[0]


def image_call(path):
    return lambda token: request_bytes("GET", path, token=token)[0]


# Uloge po dogadjaju: kod javnog je "stranger" svako bez veze sa njim, kod privatnog ne-clan
for label, event in (("public", public), ("private", private)):
    eid = event["id"]
    hidden = 404 if label == "private" else None   # sta stranac dobija kad dogadjaj ne sme ni da vidi
    seen = 200 if label == "public" else 404

    cell("view the event", label, "owner", 200, json_call("GET", f"/events/{eid}"))
    cell("view the event", label, "stranger", seen, json_call("GET", f"/events/{eid}"))
    cell("view the event", label, "member", 200, json_call("GET", f"/events/{eid}"))
    cell("view the event", label, "blocked", 404, json_call("GET", f"/events/{eid}"))

    cell("view the photo", label, "owner", 200, image_call(event["imageUris"][0]))
    cell("view the photo", label, "stranger", seen, image_call(event["imageUris"][0]))
    cell("view the photo", label, "member", 200, image_call(event["imageUris"][0]))
    cell("view the photo", label, "blocked", 404, image_call(event["imageUris"][0]))

    cell("view the ratings", label, "owner", 200, json_call("GET", f"/events/{eid}/ratings"))
    cell("view the ratings", label, "stranger", seen, json_call("GET", f"/events/{eid}/ratings"))
    cell("view the ratings", label, "member", 200, json_call("GET", f"/events/{eid}/ratings"))
    cell("view the ratings", label, "blocked", 404, json_call("GET", f"/events/{eid}/ratings"))

    for action, path in (("view the guest list", f"/events/{eid}/attendees"),
                         ("view the entry QR code", f"/events/{eid}/check-in-code")):
        cell(action, label, "owner", 200, json_call("GET", path))
        cell(action, label, "stranger", hidden or 403, json_call("GET", path))
        cell(action, label, "member", 403, json_call("GET", path))
        cell(action, label, "blocked", 404, json_call("GET", path))

    # Prijava: organizator se ne prijavljuje na svoj (400); ostali po pravu da vide dogadjaj
    register = json_call("PUT", f"/events/{eid}/registration")
    cell("register", label, "owner", 400, register)
    cell("register", label, "stranger", seen, register)
    cell("register", label, "member", 200, register)
    cell("register", label, "blocked", 404, register)
    unregister = json_call("DELETE", f"/events/{eid}/registration")
    cell("cancel a registration", label, "stranger", seen, unregister)
    cell("cancel a registration", label, "blocked", 404, unregister)

    # Dogadjaj jos nije poceo: ko sme da ga vidi dobija 409 zbog vremena, ostali 404 zbog pristupa
    check_in = json_call("PUT", f"/events/{eid}/attendance", {"latitude": 44.8, "longitude": 20.46})
    cell("check in", label, "stranger", 409 if label == "public" else 404, check_in)
    cell("check in", label, "member", 409, check_in)
    cell("check in", label, "blocked", 404, check_in)
    rate = json_call("PATCH", f"/events/{eid}/rating", {"value": 5})
    cell("rate", label, "stranger", 409 if label == "public" else 404, rate)
    cell("rate", label, "member", 409, rate)
    cell("rate", label, "blocked", 404, rate)

    # Radnje organizatora; uspeh vlasnika se ovde ne proverava, jer bi obrisao ulazne podatke testa
    for action, method, path, body in (("edit", "PUT", f"/events/{eid}", event),
                                       ("cancel the event", "POST", f"/events/{eid}/cancel", {"reason": None}),
                                       ("delete the event", "DELETE", f"/events/{eid}", None)):
        cell(action, label, "stranger", hidden or 403, json_call(method, path, body))
        cell(action, label, "member", 403, json_call(method, path, body))
        cell(action, label, "blocked", 404, json_call(method, path, body))
    cell("edit", label, "owner", 200, json_call("PUT", f"/events/{eid}", event))

# ---- profil organizatora
profile_events = json_call("GET", f"/users/{MARKO}/events")
cell("view the organiser's events", "profile", "owner", 200, profile_events)
cell("view the organiser's events", "profile", "stranger", 200, profile_events)
cell("view the organiser's events", "profile", "blocked", 404, profile_events)
status, listed = call("GET", f"/users/{MARKO}/events", token=milica)
listed_ids = [e["id"] for e in listed] if status == 200 else []
check("the organiser's profile lists the public event and not the private one",
      public["id"] in listed_ids and private["id"] not in listed_ids, listed_ids,
      expected="public listed, private not")
profile = json_call("GET", f"/users/{MARKO}")
cell("view the organiser's name", "profile", "stranger", 200, profile)
cell("view the organiser's name", "profile", "blocked", 404, profile)

# ---- ulazak kodom: blokirani ne sme ni kodom da dobije dogadjaj
join = json_call("POST", "/events/join", {"accessCode": private["accessCode"]})
cell("join with the access code", "private", "blocked", 404, join)
cell("join with the access code", "private", "stranger", 200, join)

# ---- tabela za rad: redovi su radnje, kolone uloge
roles = ["owner", "stranger", "member", "blocked"]
lines = ["# Access matrix", "", "Each cell: expected status / actual status. ✗ marks a mismatch.", "",
         "| Action | Event | " + " | ".join(roles) + " |", "|---|---|" + "---|" * len(roles)]
seen_rows = []
for action, event_label, *_ in matrix:
    if (action, event_label) not in seen_rows:
        seen_rows.append((action, event_label))
for action, event_label in seen_rows:
    cells = []
    for role in roles:
        found = [m for m in matrix if m[0] == action and m[1] == event_label and m[2] == role]
        if not found:
            cells.append("—")
        else:
            _, _, _, shown, status, ok = found[0]
            cells.append(f"{shown} / {status}" + ("" if ok else " ✗"))
    lines.append(f"| {action} | {event_label} | " + " | ".join(cells) + " |")
results = Path(__file__).parent / "results"
results.mkdir(exist_ok=True)
(results / "access-matrix.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

delete_test_events(PREFIX)
finish()
