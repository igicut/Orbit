"""F-28: blokiranje vazi u oba smera - blokirani vise ne vidi dogadjaje onoga ko ga je blokirao."""
import time
import uuid

from common import call, check, delete_test_events, finish, login, sql

PREFIX = "BLOCKTEST"
BELGRADE = "lat=44.8125&lng=20.4612"

MILICA = "5eed0001-0000-4000-8000-000000000001"
ANA = "5eed0001-0000-4000-8000-000000000003"

HOUR_MS = 3600 * 1000


def create_event(token, title):
    """Dogadjaj preko API-ja, da ga napravi bas prijavljeni nalog."""
    body = {
        "id": str(uuid.uuid4()),
        "ownerId": "ignored, server uzima iz tokena",
        "title": title,
        "description": "Test blokiranja",
        "imageUris": [],
        "latitude": 44.8125,
        "longitude": 20.4612,
        "address": "Beograd",
        "startTime": int(time.time() * 1000) + 48 * HOUR_MS,
        "durationMinutes": 120,
        "category": "SOCIAL",
        "visibility": "PUBLIC",
        "capacity": 10,
        "price": None,
        "accessCode": None,
        "avgRating": 0.0,
        "ratingCount": 0,
        "registeredCount": 0,
        "createdAt": int(time.time() * 1000),
    }
    status, created = call("POST", "/events", body, token=token)
    if status != 201:
        raise RuntimeError(f"create failed: {status} {created}")
    return created["id"]


def visible(token, event_id):
    status, body = call("GET", f"/events?{BELGRADE}", token=token)
    return status == 200 and any(e["id"] == event_id for e in body)


def registered_ids(token):
    status, body = call("GET", "/users/me/sync", token=token)
    return [e["id"] for e in body["registeredEvents"]] if status == 200 else []


def unblock(token, user_id):
    call("DELETE", f"/users/me/blocked/{user_id}", token=token)


delete_test_events(PREFIX)
sql(f"DELETE FROM blocked_users WHERE blocker_id = '{MILICA}' AND blocked_id = '{ANA}'")

milica = login("milica")
ana = login("ana")

milica_event = create_event(milica, f"{PREFIX} Milicin dogadjaj")
ana_event = create_event(ana, f"{PREFIX} Anin dogadjaj")

# ---- pre blokade oboje vide sve ----
check("before blocking Ana sees Milica's event", visible(ana, milica_event))
check("before blocking Milica sees Ana's event", visible(milica, ana_event))

# Ana se prijavljuje, da se vidi sta biva sa postojecom prijavom
status, _ = call("PUT", f"/events/{milica_event}/registration", token=ana)
check("Ana can register before being blocked", status == 200, status)
check("the registration shows up in Ana's sync", milica_event in registered_ids(ana))

# ---- Milica blokira Anu ----
status, _ = call("PUT", f"/users/me/blocked/{ANA}", token=milica)
check("Milica blocks Ana", status == 204, status)

# smer koji je i ranije radio, sada ga sprovodi server
check("Milica no longer sees Ana's event", not visible(milica, ana_event))

# novo: obrnuti smer
check("Ana no longer sees Milica's event in the list", not visible(ana, milica_event))

status, _ = call("GET", f"/events/{milica_event}", token=ana)
check("the detail looks like it does not exist for Ana", status == 404, status)

status, _ = call("PUT", f"/events/{ana_event}/registration", token=milica)
check("Milica cannot register for Ana's event", status == 404, status)

check("the blocked event drops out of Ana's sync", milica_event not in registered_ids(ana))

# ---- vlasnik uvek vidi svoje ----
check("Milica still sees her own event", visible(milica, milica_event))
check("Ana still sees her own event", visible(ana, ana_event))

# ---- Ana ne sme da sazna ko ju je blokirao ----
status, body = call("GET", "/users/me/sync", token=ana)
blocked_by_ana = [u["id"] for u in body["blockedUsers"]] if status == 200 else []
check("Ana's blocked list stays empty, she cannot learn who blocked her",
      MILICA not in blocked_by_ana, blocked_by_ana)

# ---- odblokiranje vraca sve ----
status, _ = call("DELETE", f"/users/me/blocked/{ANA}", token=milica)
check("Milica unblocks Ana", status == 204, status)
check("after unblocking Ana sees the event again", visible(ana, milica_event))
check("after unblocking the registration is back in sync", milica_event in registered_ids(ana))

# ---- ciscenje ----
unblock(milica, ANA)
delete_test_events(PREFIX)

finish()
