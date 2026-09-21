"""F-43: recenica u filtere preko POST /search/parse.

Proverava se samo ugovor: statusi i oblik odgovora. Sta model izabere za neku recenicu nije
deterministicki, pa se takav izbor ne tvrdi nego samo ispisuje, da se vidi golim okom.
"""
from common import call, check, finish, login

# Ista imena kao enumi u aplikaciji (EventFilters.kt); podrazumevane vrednosti se ne salju
CATEGORIES = {"MUSIC", "SPORT", "FOOD", "ART", "TECH", "OUTDOOR", "SOCIAL", "OTHER"}
RADII = {"WALK", "NEARBY", "CITY", "REGION"}
DATE_WINDOWS = {"TODAY", "THIS_WEEK", "THIS_MONTH", "WEEKEND"}
SORTS = {"NEAREST", "TOP_RATED"}

ALLOWED = {"category": CATEGORIES, "radius": RADII, "dateWindow": DATE_WINDOWS, "sort": SORTS}


def parse(token, text):
    return call("POST", "/search/parse", {"text": text}, token=token)


def check_shape(name, body):
    """keywords je uvek tekst; ostala polja ili fale ili su iz dozvoljenog spiska."""
    ok = isinstance(body, dict) and isinstance(body.get("keywords"), str)
    check(f"{name}: keywords is a string", ok, body)
    if not ok:
        return
    for field, allowed in ALLOWED.items():
        value = body.get(field)
        check(f"{name}: {field} is empty or an allowed name", value is None or value in allowed, value)


milica = login("milica")

# ---- ugovor koji ne zavisi od AI-ja
status, _ = call("POST", "/search/parse", {"text": "muzika"})
check("without a token -> 401", status == 401, status)

status, _ = parse(milica, "   ")
check("blank text -> 400", status == 400, status)

status, _ = parse(milica, "a" * 201)
check("text over 200 characters -> 400", status == 400, status)

# ---- odgovor modela
sentence = "želim da slušam muziku blizu mene krajem nedelje"
status, body = parse(milica, sentence)

if status == 503:
    print("GEMINI_API_KEY is not set on the server; skipping the checks that need the model")
else:
    check("the mentor's sentence -> 200", status == 200, status)
    if status == 200:
        check_shape("mentor's sentence", body)
        print(f"  model read: {body}")

    status, body = parse(milica, "kviz")
    check("a single word -> 200", status == 200, status)
    if status == 200:
        check_shape("single word", body)
        print(f"  model read: {body}")

finish()
