"""F-13: /auth rute imaju limit po IP adresi. Pokrenuti poslednji, jer potrosi limit za minut."""
import time

from common import DEMO_PASSWORD, check, finish, login, request

LIMIT = 10  # AUTH_REQUESTS_PER_MINUTE u plugins/Security.kt

# Pocinje od punog limita, da prethodni testovi ne pomere brojanje
login("ana")
print("waiting 61 s for a full limit")
time.sleep(61)

statuses = []
retry_after = None
for _ in range(LIMIT + 1):
    status, _, headers = request("POST", "/auth/login", {"email": "ana@orbit.test", "password": "wrong-password"})
    statuses.append(status)
    if status == 429:
        retry_after = headers.get("Retry-After")

check(f"first {LIMIT} wrong logins -> 401", statuses[:LIMIT] == [401] * LIMIT, statuses)
check(f"attempt {LIMIT + 1} -> 429", statuses[LIMIT] == 429, statuses)
check("429 carries Retry-After", retry_after is not None and int(retry_after) > 0, retry_after)

status, _, _ = request("POST", "/auth/login", {"email": "ana@orbit.test", "password": DEMO_PASSWORD})
check("correct password is also blocked until the limit refills", status == 429, status)
# Prazno ime: i bez limita bi bilo 400, pa test nikad ne pravi nalog
status, _, _ = request("POST", "/auth/signup", {"email": "new@orbit.test", "password": "whatever1", "displayName": ""})
check("signup shares the same limit -> 429", status == 429, status)
status, _, _ = request("GET", "/health")
check("other routes are not limited", status == 200, status)

wait = int(retry_after or 60) + 1
print(f"waiting {wait} s for the limit to refill")
time.sleep(wait)
status, _, _ = request("POST", "/auth/login", {"email": "ana@orbit.test", "password": DEMO_PASSWORD})
check("after Retry-After the correct login works again", status == 200, status)

finish()
