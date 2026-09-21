"""Profil organizatora: GET /users/{id}/events.

Prosli dogadjaji nisu u pretrazi, pa je ovo jedini put do njihovih utisaka. Zato se ovde
proverava da prosli stizu, a privatni i blokirani ne.
Cita samo seed.sql podatke i nista ne upisuje.
"""
import time

from common import call, check, finish, login

STEFAN = "5eed0001-0000-4000-8000-000000000002"
ANA = "5eed0001-0000-4000-8000-000000000003"
MARKO = "5eed0001-0000-4000-8000-000000000004"
JELENA = "5eed0001-0000-4000-8000-000000000005"


def profile_events(token, user_id):
    status, body = call("GET", f"/users/{user_id}/events", token=token)
    return status, body if isinstance(body, list) else []


def titles(events):
    return [e["title"] for e in events]


jelena = login("jelena")
milica = login("milica")
ana = login("ana")
marko = login("marko")

status, _ = call("GET", f"/users/{STEFAN}/events")
check("without a token -> 401", status == 401, status)

# ---- tudji profil: javni, i buduci i prosli
status, stefan_events = profile_events(jelena, STEFAN)
check("another organiser's profile -> 200", status == 200, status)
check("a past public event is listed", "Hakaton na ETF-u" in titles(stefan_events), titles(stefan_events))
check("a private event is not listed", "Rođendan u Zemunu" not in titles(stefan_events), titles(stefan_events))
check("every listed event is public",
      all(e["visibility"] == "PUBLIC" for e in stefan_events), [e["visibility"] for e in stefan_events])
now = int(time.time() * 1000)
check("the list includes events that already started",
      any(e["startTime"] < now for e in stefan_events), [e["startTime"] for e in stefan_events])
check("every seeded event carries at least one photo",
      all(len(e["imageUris"]) >= 1 for e in stefan_events), [e["imageUris"] for e in stefan_events])

status, jelena_events = profile_events(milica, JELENA)
check("another private event is hidden too",
      "Zatvorena projekcija dokumenta" not in titles(jelena_events), titles(jelena_events))

# ---- F-28: blokada u oba smera
status, _ = profile_events(ana, MARKO)
check("the blocker cannot open the blocked profile -> 404", status == 404, status)
status, _ = profile_events(marko, ANA)
check("the blocked user cannot open the blocker's profile -> 404", status == 404, status)

# ---- nepostojeci nalog nije greska, samo nema dogadjaja
status, unknown = profile_events(milica, "00000000-0000-4000-8000-000000000000")
check("unknown user -> 200 with an empty list", status == 200 and unknown == [], (status, unknown))

finish()
