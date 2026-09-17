"""F-32: semanticko rangiranje kao sloj iznad postojece pretrage po recima."""
import time
import urllib.parse
import uuid

from common import call, check, delete_test_events, finish, login, sql

PREFIX = "SRCHTEST"
BELGRADE = "lat=44.8125&lng=20.4612"

WINE = "Degustacija domaćih vina"
HACKATHON = "Hakaton na ETF-u"
QUIZ = "Kviz veče u Skadarliji"
BIRTHDAY = "Rođendan u Zemunu"  # privatan, ne sme da se pojavi u pretrazi


def search(token, query=None, extra=""):
    path = f"/events?{BELGRADE}{extra}"
    if query is not None:
        path += "&q=" + urllib.parse.quote(query)
    status, body = call("GET", path, token=token)
    return status, body if isinstance(body, list) else []


def titles(events):
    return [e["title"] for e in events]


def scored(events):
    return [e for e in events if e.get("relevance") is not None]


def embedding_row(event_id):
    return sql(f"SELECT updated_at FROM event_embeddings WHERE event_id = '{event_id}'")


def wait_for_embedding(event_id, previous=None, seconds=25):
    """Vektor se pravi asinhrono, zato se ceka umesto da se odmah tvrdi."""
    for _ in range(seconds):
        row = embedding_row(event_id)
        if row and row != previous:
            return row
        time.sleep(1)
    return ""


marko, milica = login("marko"), login("milica")

# ---- bez upita se nista ne menja
status, all_events = search(milica)
check("search without a query -> 200", status == 200 and len(all_events) > 0, (status, len(all_events)))
check("no query means no relevance field", scored(all_events) == [], titles(scored(all_events)))
check("private events stay out of search", BIRTHDAY not in titles(all_events), titles(all_events))

# ---- pretraga po recima radi kao i pre
status, hits = search(milica, "Hakaton")
check("keyword search still finds the literal match", HACKATHON in titles(hits), titles(hits))
status, hits = search(milica, "Skadarliji")
check("keyword search matches the description too", QUIZ in titles(hits), titles(hits))
status, hits = search(milica, "zzznepostojecirec")
check("a word nobody uses returns nothing", hits == [], titles(hits))

# ---- da li je semantika uopste ukljucena na ovom serveru
status, wine_hits = search(milica, "gde mogu da probam vina iz Srbije")
semantic_on = len(scored(wine_hits)) > 0

if not semantic_on:
    print("\nSKIP: server has no GEMINI_API_KEY, only the keyword fallback was checked")
    print("      (that fallback is the point of the checks above)")
    finish()

check("a natural-language question finds the wine tasting", WINE in titles(wine_hits), titles(wine_hits))
check("matches carry a similarity score", all(-1.0 <= e["relevance"] <= 1.0 for e in scored(wine_hits)),
      [e["relevance"] for e in scored(wine_hits)])
check("scored results are ordered from most to least similar",
      [e["relevance"] for e in scored(wine_hits)] == sorted((e["relevance"] for e in scored(wine_hits)), reverse=True),
      [e["relevance"] for e in scored(wine_hits)])
check("semantic search does not leak private events", BIRTHDAY not in titles(wine_hits), titles(wine_hits))

status, code_hits = search(milica, "druženje uz programiranje preko noći")
check("another question finds the hackathon", HACKATHON in titles(code_hits), titles(code_hits))

# ---- semantika dodaje, nikad ne oduzima
status, literal = search(milica, "Hakaton")
check("the literal match survives semantic ranking", HACKATHON in titles(literal), titles(literal))

status, narrowed = search(milica, "gde mogu da probam vina iz Srbije", extra="&category=TECH")
check("category filter still applies to semantic results",
      all(e["category"] == "TECH" for e in narrowed), titles(narrowed))

status, near = search(milica, "gde mogu da probam vina iz Srbije", extra="&radiusKm=1")
check("radius filter still applies to semantic results", len(near) <= len(wine_hits), (len(near), len(wine_hits)))

# ---- novi dogadjaj dobija vektor u pozadini
now = int(time.time() * 1000)
event = {"id": str(uuid.uuid4()), "ownerId": "ignored", "title": f"{PREFIX} Radionica keramike",
         "description": "Pravljenje šolja i tanjira na grnčarskom kolu, glina i pečenje uključeni.",
         "latitude": 44.8125, "longitude": 20.4612, "startTime": now + 3 * 86400000,
         "category": "ART", "visibility": "PUBLIC", "createdAt": now}
status, _ = call("POST", "/events", event, marko)
check("create event -> 201", status == 201, status)

first_vector = wait_for_embedding(event["id"])
check("a new event gets an embedding without blocking the request", first_vector != "", first_vector)

status, pottery = search(milica, "želim da radim nešto rukama od gline")
check("the new event is found by meaning, not by words",
      any(t.startswith(PREFIX) for t in titles(pottery)), titles(pottery))

# ---- izmena osvezava vektor
status, current = call("GET", f"/events/{event['id']}", token=marko)
status, _ = call("PUT", f"/events/{event['id']}", {
    **current,
    "title": f"{PREFIX} Poreske prijave",
    "description": "Predavanje o poreskim prijavama i knjigovodstvu za preduzetnike.",
}, marko)
check("edit -> 200", status == 200, status)
second_vector = wait_for_embedding(event["id"], previous=first_vector)
check("editing the text refreshes the embedding", second_vector not in ("", first_vector),
      (first_vector, second_vector))

status, pottery_again = search(milica, "želim da radim nešto rukama od gline")
check("after the edit the event no longer matches the old meaning",
      not any(t.startswith(PREFIX) for t in titles(pottery_again)), titles(pottery_again))

status, taxes = search(milica, "kako da vodim papirologiju za svoju firmu")
check("after the edit the event is found by the new meaning",
      any(t.startswith(PREFIX) for t in titles(taxes)), titles(taxes))

# ---- brisanje nosi i vektor
status, _ = call("DELETE", f"/events/{event['id']}", token=marko)
check("delete -> 204", status == 204, status)
check("deleting an event removes its embedding", embedding_row(event["id"]) == "", embedding_row(event["id"]))

delete_test_events(PREFIX)
finish()
