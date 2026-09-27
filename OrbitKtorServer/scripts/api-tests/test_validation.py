"""Pravila forme vaze i na serveru: izmenjen klijent ili direktan API poziv ne mogu da ih zaobidju."""
import re
import time
import uuid

from common import call, check, delete_test_events, finish, login, photo, request, sql

PREFIX = "VALTEST"
DAY = 86400000
# Isto kao CODE_ALPHABET na serveru: bez I, O, 0 i 1
ACCESS_CODE = re.compile(r"^[A-HJ-NP-Z2-9]{6}$")


def event(token, **fields):
    now = int(time.time() * 1000)
    body = {"id": str(uuid.uuid4()), "ownerId": "ignored", "title": f"{PREFIX} {uuid.uuid4().hex[:6]}",
            "description": "validation test", "latitude": 44.8, "longitude": 20.46,
            "startTime": now + 3 * DAY, "category": "OTHER", "visibility": "PUBLIC",
            "imageUris": [photo(token)], "createdAt": now}
    body.update(fields)
    return body


def create(token, **fields):
    return call("POST", "/events", event(token, **fields), token)


marko, milica = login("marko"), login("milica")

# ---- kreiranje: svako pravilo iz forme odbija isti zahtev na serveru
status, base = create(marko)
check("valid event -> 201", status == 201, (status, base))

status, text = create(marko, description="   ")
check("blank description -> 400", status == 400 and "Opis" in text, (status, text))
status, text = create(marko, imageUris=[])
check("no photo -> 400", status == 400 and "fotografija" in text, (status, text))
status, text = create(marko, startTime=int(time.time() * 1000) - 60_000)
check("start in the past -> 400", status == 400 and "buducnosti" in text, (status, text))
status, text = create(marko, latitude=95.0)
check("latitude 95 -> 400", status == 400 and "Lokacija" in text, (status, text))
status, text = create(marko, longitude=-181.0)
check("longitude -181 -> 400", status == 400 and "Lokacija" in text, (status, text))

# Ranije je baza odbijala predug tekst, pa je klijent dobijao 500
status, text = create(marko, title=PREFIX + "x" * 200)
check("title over 200 characters -> 400, not 500", status == 400 and "Naslov" in text, (status, text))
status, text = create(marko, address="a" * 301)
check("address over 300 characters -> 400, not 500", status == 400 and "Adresa" in text, (status, text))
status, text = create(marko, description="d" * 10_001)
check("description over 10000 characters -> 400", status == 400 and "Opis" in text, (status, text))

# ---- pristupni kod pravi server, klijentski se ne koristi
status, private = create(marko, visibility="PRIVATE", accessCode="AAAAAA")
code = private.get("accessCode") if status == 201 else None
check("private event gets a server code of 6 readable characters",
      status == 201 and code is not None and ACCESS_CODE.match(code), (status, private))
check("the code the client sent is ignored", code != "AAAAAA", code)
status, second = create(marko, visibility="PRIVATE")
check("two private events get different codes", status == 201 and second["accessCode"] != code, (status, second))
status, joined = call("POST", "/events/join", {"accessCode": code}, milica)
check("another user joins with the server code", status == 200 and joined["id"] == private["id"], (status, joined))

status, public = create(marko, accessCode="BBBBBB")
check("public event never carries an access code", status == 201 and public.get("accessCode") is None,
      (status, public))

status, created = create(marko, status="CANCELLED", cancelReason="lazno")
status_get, stored = call("GET", f"/events/{created['id']}", token=marko)
check("a new event is always active, whatever the client sends",
      created["status"] == "ACTIVE" and stored["status"] == "ACTIVE" and stored.get("cancelReason") is None,
      (created.get("status"), stored.get("status")))

# ---- izmena: ista pravila kao kreiranje
eid = base["id"]
status, text = call("PUT", f"/events/{eid}", {**base, "title": "  "}, marko)
check("edit with a blank title -> 400", status == 400 and "Naslov" in text, (status, text))
status, text = call("PUT", f"/events/{eid}", {**base, "description": ""}, marko)
check("edit with a blank description -> 400", status == 400 and "Opis" in text, (status, text))
status, text = call("PUT", f"/events/{eid}", {**base, "latitude": 95.0}, marko)
check("edit with latitude 95 -> 400 for the location, not for the distance",
      status == 400 and "Lokacija" in text, (status, text))
status, text = call("PUT", f"/events/{eid}", {**base, "imageUris": []}, marko)
check("edit removing every photo -> 400", status == 400 and "fotografija" in text, (status, text))

status, edited = call("PUT", f"/events/{private['id']}", {**private, "accessCode": "CCCCCC"}, marko)
check("edit cannot change the access code", status == 200 and edited["accessCode"] == code, (status, edited))

status, _ = call("PUT", f"/events/{eid}", {**base, "title": f"{PREFIX} izmenjen"}, marko)
check("valid edit still works -> 200", status == 200, status)

# ---- ime: aplikacija dozvoljava 40 znakova, pa ni server ne sme vise
MARKO = "5eed0001-0000-4000-8000-000000000004"
# Preko API-ja, ne SQL-a: ime ima nasa slova, a Windows konzola bi ih pokvarila
status, profile = call("GET", f"/users/{MARKO}", token=marko)
original_name = profile["displayName"]
try:
    status, _ = call("PATCH", "/users/me", {"displayName": "n" * 41}, marko)
    check("profile name of 41 characters -> 400", status == 400, status)
    status, updated = call("PATCH", "/users/me", {"displayName": "n" * 40}, marko)
    check("profile name of exactly 40 characters -> 200", status == 200 and updated["displayName"] == "n" * 40,
          (status, updated))
finally:
    call("PATCH", "/users/me", {"displayName": original_name}, marko)
status, profile = call("GET", f"/users/{MARKO}", token=marko)
check("cleanup: the seed name is back", profile.get("displayName") == original_name, profile)

long_name_email = f"valtest-{uuid.uuid4().hex[:8]}@orbit.test"
status, _, _ = request("POST", "/auth/signup",
                       {"email": long_name_email, "password": "valtest-lozinka", "displayName": "n" * 41})
check("sign up with a name of 41 characters -> 400", status == 400, status)
# Ako bi server ipak primio nalog, test ga brise
sql(f"DELETE FROM users WHERE id IN (SELECT user_id FROM user_credentials WHERE email = '{long_name_email}')")
sql(f"DELETE FROM user_credentials WHERE email = '{long_name_email}'")

delete_test_events(PREFIX)
finish()
