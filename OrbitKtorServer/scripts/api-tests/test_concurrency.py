"""
Konkurentni zahtevi za poslednja mesta, ponovljeni vise puta.
Jedan uspesan krug moze biti i srecan raspored niti, pa svaki scenario ide u vise krugova,
a posle svakog se proverava i sta je stvarno upisano u bazu.
"""
import threading
import time
import uuid

from common import call, check, delete_test_events, finish, login, move_start, photo, sql

PREFIX = "CONCTEST"
DAY = 86400000
REGISTRATION_ROUNDS = 20
WALK_IN_ROUNDS = 10

jelena = login("jelena")
# Jelena je organizator; blokada Ana-Marko iz seed-a se nje ne tice, pa svih pet sme da se prijavi
guests = [login(name) for name in ("milica", "ana", "stefan", "marko", "nikola")]


def new_event(capacity, starts_in_ms=3 * DAY):
    now = int(time.time() * 1000)
    event = {"id": str(uuid.uuid4()), "ownerId": "ignored", "title": f"{PREFIX} {uuid.uuid4().hex[:6]}",
             "description": "concurrency test", "latitude": 44.8, "longitude": 20.46,
             "startTime": now + max(starts_in_ms, DAY), "durationMinutes": 120, "category": "OTHER",
             "visibility": "PUBLIC", "capacity": capacity, "imageUris": [photo(jelena)], "createdAt": now}
    status, _ = call("POST", "/events", event, jelena)
    assert status == 201, status
    if starts_in_ms <= 0:
        move_start(event["id"], now + starts_in_ms)
    return event["id"]


def at_once(tokens, send):
    """Svi zahtevi krecu u istom trenutku (Barrier); vraca statuse, sortirane"""
    barrier = threading.Barrier(len(tokens))
    statuses = []
    lock = threading.Lock()

    def worker(token):
        barrier.wait()
        status = send(token)
        with lock:
            statuses.append(status)

    threads = [threading.Thread(target=worker, args=(token,)) for token in tokens]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    return sorted(statuses)


def stored(event_id):
    """(registered_count, redova u registrations, redova u attendances)"""
    row = sql(f"SELECT (SELECT registered_count FROM events WHERE id = '{event_id}'), "
              f"(SELECT COUNT(*) FROM registrations WHERE event_id = '{event_id}'), "
              f"(SELECT COUNT(*) FROM attendances WHERE event_id = '{event_id}')")
    return tuple(int(part) for part in row.split())


# ---- petoro se prijavljuje za 1 ili 2 poslednja mesta, 20 krugova
bad_rounds = []
for round_number in range(REGISTRATION_ROUNDS):
    capacity = 1 if round_number % 2 == 0 else 2
    eid = new_event(capacity)
    statuses = at_once(guests, lambda token: call("PUT", f"/events/{eid}/registration", token=token)[0])
    expected = sorted([200] * capacity + [409] * (5 - capacity))
    counts = stored(eid)
    if statuses != expected or counts[:2] != (capacity, capacity):
        bad_rounds.append((round_number, capacity, statuses, counts))
check(f"5 people register at once for the last seat(s), {REGISTRATION_ROUNDS} rounds with capacity 1 and 2",
      not bad_rounds, bad_rounds or f"all {REGISTRATION_ROUNDS} rounds: exactly capacity × 200, rest 409, "
                                    f"counter = rows = capacity",
      expected="each round: capacity × 200, the rest 409, counter = rows = capacity")

# ---- ista osoba salje prijavu pet puta istovremeno
bad_rounds = []
for round_number in range(REGISTRATION_ROUNDS):
    eid = new_event(capacity=5)
    statuses = at_once([guests[0]] * 5, lambda token: call("PUT", f"/events/{eid}/registration", token=token)[0])
    counts = stored(eid)
    if any(status != 200 for status in statuses) or counts[:2] != (1, 1):
        bad_rounds.append((round_number, statuses, counts))
check(f"one person sends the same registration 5 times at once, {REGISTRATION_ROUNDS} rounds",
      not bad_rounds, bad_rounds or f"all {REGISTRATION_ROUNDS} rounds: 5 × 200, counter 1, 1 row",
      expected="each round: 200 for all, counter 1, 1 registration row")

# ---- petoro bez prijave, na licu mesta, za poslednje mesto (dogadjaj je poceo pre 2 minuta)
bad_rounds = []
for round_number in range(WALK_IN_ROUNDS):
    eid = new_event(capacity=1, starts_in_ms=-2 * 60_000)
    body = {"latitude": 44.8, "longitude": 20.46}
    statuses = at_once(guests, lambda token: call("PUT", f"/events/{eid}/attendance", body, token)[0])
    counts = stored(eid)
    if statuses != [200, 409, 409, 409, 409] or counts != (1, 1, 1):
        bad_rounds.append((round_number, statuses, counts))
check(f"5 walk-ins check in at once for the last spot, {WALK_IN_ROUNDS} rounds",
      not bad_rounds, bad_rounds or f"all {WALK_IN_ROUNDS} rounds: one 200, four 409, counter 1, "
                                    f"1 registration, 1 attendance",
      expected="each round: one 200, four 409, counter 1, 1 registration, 1 attendance")

# ---- prijava i odjava iste osobe ukrsteno: brojac mora da prati redove
bad_rounds = []
for round_number in range(REGISTRATION_ROUNDS):
    eid = new_event(capacity=5)
    token = guests[0]
    call("PUT", f"/events/{eid}/registration", token=token)
    senders = [lambda t: call("DELETE", f"/events/{eid}/registration", token=t)[0],
               lambda t: call("PUT", f"/events/{eid}/registration", token=t)[0]] * 3
    barrier = threading.Barrier(len(senders))
    threads = [threading.Thread(target=lambda s=s: (barrier.wait(), s(token))) for s in senders]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    counts = stored(eid)
    if counts[0] != counts[1] or counts[1] not in (0, 1):
        bad_rounds.append((round_number, counts))
check(f"one person registers and cancels 3 + 3 times at once, {REGISTRATION_ROUNDS} rounds",
      not bad_rounds, bad_rounds or f"all {REGISTRATION_ROUNDS} rounds: counter equals the rows (0 or 1)",
      expected="each round: counter = number of rows, 0 or 1")

delete_test_events(PREFIX)
finish()
