"""F-13: nova lozinka samo uz jednokratni kod sa emaila; stari tokeni posle promene ne vaze."""
import time

from common import DEMO_PASSWORD, call, check, finish, login, request, sql

EMAIL = "stefan@orbit.test"
UNKNOWN = "nobody-here@orbit.test"
NEW_PASSWORD = "nova-lozinka-1"

# Server cuva samo bcrypt hash koda, pa test upisuje hash poznatog koda.
# Napravljen istom bibliotekom kao na serveru (at.favre.lib:bcrypt, cost 12).
KNOWN_CODE = "482913"
KNOWN_HASH = "$2a$12$npEWaAI98ZxTehaRUWz.7u2vHSGFhy5/9DdqSUCTIFcbSl4ycqACy"


def auth(path, body):
    """/auth ima limit po IP adresi; na 429 ceka koliko server kaze, kao common.login."""
    for _ in range(3):
        status, parsed, headers = request("POST", path, body)
        if status != 429:
            return status, parsed
        wait = int(headers.get("Retry-After") or 60) + 1
        print(f"auth rate limit reached, waiting {wait} s")
        time.sleep(wait)
    return status, parsed


def reset(code, password=NEW_PASSWORD):
    return auth("/auth/reset-password", {"email": EMAIL, "code": code, "password": password})


def code_row():
    """(hash, attempts) ili None; kod nastaje u pozadini, posle odgovora"""
    row = sql(f"SELECT code_hash, attempts FROM password_reset_codes WHERE email = '{EMAIL}'")
    return tuple(row.split("\t")) if row else None


def request_new_code():
    """Trazi nov kod i ceka da ga server upise; vraca hash novog koda"""
    before = code_row()
    status, _ = auth("/auth/forgot-password", {"email": EMAIL})
    assert status == 200, status
    for _ in range(40):
        row = code_row()
        if row and row != before:
            return row[0]
        time.sleep(0.25)
    raise RuntimeError("the server did not store a reset code")


def use_known_code(extra=""):
    sql(f"UPDATE password_reset_codes SET code_hash = '{KNOWN_HASH}'{extra} WHERE email = '{EMAIL}'")


def attempts():
    row = code_row()
    return int(row[1]) if row else None


seed_hash = sql(f"SELECT password_hash FROM user_credentials WHERE email = '{EMAIL}'")
sql(f"DELETE FROM password_reset_codes WHERE email IN ('{EMAIL}', '{UNKNOWN}')")
old_token = login("stefan")

try:
    # ---- prvi korak: isti odgovor za postojeci i nepostojeci nalog
    status_known, body_known = auth("/auth/forgot-password", {"email": EMAIL})
    status_unknown, body_unknown = auth("/auth/forgot-password", {"email": UNKNOWN})
    check("forgot-password answers 200 for an existing account", status_known == 200, (status_known, body_known))
    check("an unknown email gets exactly the same answer", (status_unknown, body_unknown) == (status_known, body_known),
          (status_unknown, body_unknown))
    time.sleep(3)
    check("a code is stored only for the existing account, as a bcrypt hash",
          code_row() is not None and code_row()[0].startswith("$2a$")
          and sql(f"SELECT COUNT(*) FROM password_reset_codes WHERE email = '{UNKNOWN}'") == "0", code_row())
    status, _ = auth("/auth/forgot-password", {"email": "not-an-email"})
    check("forgot-password with an invalid email -> 400", status == 400, status)

    # ---- provere pre koda ne trose pokusaj
    status, text = reset("12ab")
    check("code that is not 6 digits -> 400 without using an attempt", status == 400 and attempts() == 0,
          (status, text, attempts()))
    status, text = reset("000000", password="short")
    check("short new password -> 400 without using an attempt", status == 400 and attempts() == 0,
          (status, text, attempts()))

    # ---- pogresan pa tacan kod
    use_known_code()
    status, text = reset("000000")
    check("wrong code -> 400 and one attempt used", status == 400 and "Kod" in text and attempts() == 1,
          (status, text, attempts()))
    status, body = reset(KNOWN_CODE)
    check("correct code -> 200 with a token for the same account",
          status == 200 and body["user"]["id"] == "5eed0001-0000-4000-8000-000000000002", (status, body))
    new_token = body["token"] if status == 200 else None
    status, text = reset(KNOWN_CODE)
    check("the same code cannot be used twice -> 400", status == 400, (status, text))

    status, _ = auth("/auth/login", {"email": EMAIL, "password": NEW_PASSWORD})
    check("login with the new password -> 200", status == 200, status)
    status, _ = auth("/auth/login", {"email": EMAIL, "password": DEMO_PASSWORD})
    check("login with the old password -> 401", status == 401, status)

    # ---- stari tokeni prestaju da vaze
    status, _ = call("GET", "/users/me/sync", token=old_token)
    check("a token issued before the reset -> 401", status == 401, status)
    status, _ = call("GET", "/users/me/sync", token=new_token)
    check("the token from the reset works -> 200", status == 200, status)

    # ---- pet pokusaja, pa ni tacan kod ne prolazi
    request_new_code()
    use_known_code()
    wrong = [reset("111111")[0] for _ in range(5)]
    status, _ = reset(KNOWN_CODE)
    check("after 5 wrong codes even the correct one -> 400", wrong == [400] * 5 and status == 400,
          (wrong, status, attempts()))

    # ---- istekao kod
    request_new_code()
    use_known_code(", expires_at = 1000")
    status, _ = reset(KNOWN_CODE)
    check("expired code -> 400", status == 400, status)

    # ---- nov zahtev ponistava stari kod
    request_new_code()
    use_known_code()
    request_new_code()
    status, _ = reset(KNOWN_CODE)
    check("a new request replaces the previous code -> old code 400", status == 400, status)

    status, _ = auth("/auth/reset-password", {"email": EMAIL, "password": NEW_PASSWORD})
    check("request without a code (old app) -> 400, not 500", status == 400, status)
finally:
    # Stefan dobija nazad lozinku iz seed.sql, da ostali testovi mogu da ga prijave
    sql(f"UPDATE user_credentials SET password_hash = '{seed_hash}', password_changed_at = 0 WHERE email = '{EMAIL}'")
    sql(f"DELETE FROM password_reset_codes WHERE email IN ('{EMAIL}', '{UNKNOWN}')")

status, _ = auth("/auth/login", {"email": EMAIL, "password": DEMO_PASSWORD})
check("cleanup: the seed password works again", status == 200, status)

finish()
