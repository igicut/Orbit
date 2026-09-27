"""F-13: istek i izmena JWT-a. Test sam potpisuje tokene istim JWT_SECRET kao server (HS256)."""
import base64
import hashlib
import hmac
import json
import os
import sys
import time
import urllib.error
import urllib.request

from common import API, check, finish, login

MILICA = "5eed0001-0000-4000-8000-000000000001"
STEFAN = "5eed0001-0000-4000-8000-000000000002"
ISSUER = "orbit-server"     # TokenService.kt
AUDIENCE = "orbit-app"

SECRET = os.environ.get("JWT_SECRET")
if not SECRET:
    sys.exit("Set JWT_SECRET (the same value the server runs with) before running this test.")


def b64(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def sign(payload, secret=SECRET, header=None):
    """JWT kao sto ga pravi TokenService, ali sa poljima koja test bira"""
    head = b64(json.dumps(header or {"alg": "HS256", "typ": "JWT"}).encode())
    body = b64(json.dumps(payload).encode())
    signature = hmac.new(secret.encode(), f"{head}.{body}".encode(), hashlib.sha256).digest()
    return f"{head}.{body}.{b64(signature)}"


def claims(**changes):
    """Ista polja kao pravi token; None izbacuje polje"""
    now = int(time.time())
    base = {"iss": ISSUER, "aud": AUDIENCE, "sub": MILICA, "iat": now - 60, "exp": now + 3600}
    base.update(changes)
    return {key: value for key, value in base.items() if value is not None}


def status_with(authorization):
    """Status zasticene rute sa tacno ovim Authorization zaglavljem; None znaci bez zaglavlja"""
    headers = {} if authorization is None else {"Authorization": authorization}
    req = urllib.request.Request(API + "/users/me/sync", headers=headers, method="GET")
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            return response.status
    except urllib.error.HTTPError as error:
        return error.code


def bearer(token):
    return status_with("Bearer " + token)


now = int(time.time())

# ---- kontrola: bez nje bi svaki 401 ispod mogao da znaci samo da test pogresno potpisuje
status = bearer(login("milica"))
check("a real token from login", status == 200, status, expected="200")
status = bearer(sign(claims()))
check("a token the test signs like the server does", status == 200, status, expected="200")

# ---- istek
status = bearer(sign(claims(iat=now - 7200, exp=now - 3600)))
check("token that expired an hour ago", status == 401, status, expected="401")
status = bearer(sign(claims(exp=now - 1)))
check("token that expired one second ago", status == 401, status, expected="401")

# Sveze vreme: login i zahtevi iznad traju sekundama, a token zivi samo dve
issued = int(time.time())
short_lived = sign(claims(iat=issued, exp=issued + 2))
first = bearer(short_lived)
time.sleep(3)
second = bearer(short_lived)
check("the same token works before its expiry and not after it", (first, second) == (200, 401),
      (first, second), expected="200, then 401")

status = bearer(sign(claims(iat=now + 3600)))
check("token issued in the future", status == 401, status, expected="401")

# ---- izmena ili pogresan potpis
status = bearer(sign(claims(), secret="not-the-server-secret"))
check("token signed with another key", status == 401, status, expected="401")

head, body, signature = sign(claims()).split(".")
forged_body = b64(json.dumps(claims(sub=STEFAN)).encode())
status = bearer(f"{head}.{forged_body}.{signature}")
check("payload changed to another user, old signature kept", status == 401, status, expected="401")

status = bearer(f"{b64(json.dumps({'alg': 'none', 'typ': 'JWT'}).encode())}.{body}.")
check("unsigned token (alg none)", status == 401, status, expected="401")

status = bearer(sign(claims(), header={"alg": "HS512", "typ": "JWT"}))
check("token that claims another algorithm", status == 401, status, expected="401")

status = bearer(sign(claims(iss="someone-else")))
check("token from another issuer", status == 401, status, expected="401")
status = bearer(sign(claims(aud="another-app")))
check("token for another audience", status == 401, status, expected="401")
status = bearer(sign(claims(sub=None)))
check("token without a user id", status == 401, status, expected="401")
status = bearer(sign(claims(sub="")))
check("token with an empty user id", status == 401, status, expected="401")

# ---- oblik zaglavlja
status = status_with(sign(claims()))
check("valid token without the 'Bearer ' prefix", status == 401, status, expected="401")
status = status_with("Bearer not.a.token")
check("header with garbage instead of a token", status == 401, status, expected="401")
status = status_with(None)
check("no Authorization header", status == 401, status, expected="401")

finish()
