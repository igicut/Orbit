"""Zajednicke funkcije za API testove; server mora da radi, a baza da ima seed.sql."""
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

# Windows konzola je cp1252, a naslovi imaju nasa slova
sys.stdout.reconfigure(encoding="utf-8", errors="replace")

API = os.environ.get("ORBIT_API", "http://localhost:8080")
DB_NAME = os.environ.get("ORBIT_DB", "orbit_database")
DB_USER = os.environ.get("ORBIT_DB_USER", "root")
MYSQL = os.environ.get("MYSQL_BIN", r"C:\Program Files\MySQL\MySQL Server 9.1\bin\mysql.exe")
DEMO_PASSWORD = "orbit123"  # demo nalozi iz seed.sql

_results = []


def request(method, path, body=None, token=None):
    """Vraca (status, telo, zaglavlja); JSON telo se parsira, ostalo ostaje tekst."""
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(API + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            raw = response.read().decode()
            return response.status, (json.loads(raw) if raw and raw[0] in "[{" else raw), response.headers
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode(), error.headers


def call(method, path, body=None, token=None):
    status, parsed, _ = request(method, path, body, token)
    return status, parsed


def request_bytes(method, path, data=None, content_type=None, token=None):
    """Telo i odgovor ostaju bajtovi; slike ne prolaze kroz JSON pomocnike."""
    headers = {}
    if content_type:
        headers["Content-Type"] = content_type
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(API + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            return response.status, response.read(), response.headers
    except urllib.error.HTTPError as error:
        return error.code, error.read(), error.headers


def multipart(blob, filename, content_type, field="file"):
    """Rucno sklopljeno multipart telo; filename=None pravi obicno polje, ne fajl."""
    boundary = "orbit" + uuid.uuid4().hex
    name = f'; filename="{filename}"' if filename is not None else ""
    head = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="{field}"{name}\r\n'
        f"Content-Type: {content_type}\r\n\r\n"
    ).encode()
    return head + blob + f"\r\n--{boundary}--\r\n".encode(), f"multipart/form-data; boundary={boundary}"


def check(name, ok, detail=""):
    _results.append(bool(ok))
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))


def login(name):
    """/auth ima limit po IP adresi; na 429 ceka koliko server kaze, kao pravi klijent."""
    for _ in range(3):
        status, body, headers = request("POST", "/auth/login", {"email": f"{name}@orbit.test", "password": DEMO_PASSWORD})
        if status != 429:
            break
        wait = int(headers.get("Retry-After") or 60) + 1
        print(f"login rate limit reached, waiting {wait} s")
        time.sleep(wait)
    if status != 200:
        sys.exit(f"Login for {name} failed ({status}). Is the server running and seed.sql loaded?")
    return body["token"]


def sql(statement):
    """MYSQL_PWD mora biti postavljen u okruzenju; lozinka se nikad ne pise u skriptu."""
    if "MYSQL_PWD" not in os.environ:
        sys.exit("Set MYSQL_PWD before running the tests (see README.md).")
    result = subprocess.run([MYSQL, "-u", DB_USER, DB_NAME, "-N", "-e", statement],
                            capture_output=True, text=True, env=os.environ)
    if result.returncode != 0:
        raise RuntimeError(result.stderr)
    return result.stdout.strip()


def delete_test_events(title_prefix):
    """Zapoceti dogadjaji ne mogu da se obrisu preko API-ja, pa test podatke brise SQL."""
    ids = f"(SELECT id FROM events WHERE title LIKE '{title_prefix}%')"
    for table in ("ratings", "attendances", "registrations", "event_members", "event_embeddings"):
        sql(f"DELETE FROM {table} WHERE event_id IN {ids}")
    sql(f"DELETE FROM events WHERE title LIKE '{title_prefix}%'")
    left = sql(f"SELECT COUNT(*) FROM events WHERE title LIKE '{title_prefix}%'")
    print(f"cleanup: {title_prefix} events left = {left}")


def finish():
    """Izlazni kod 1 ako je bar jedna provera pala, da run_all.py to vidi."""
    passed = sum(_results)
    print(f"\n{passed}/{len(_results)} passed")
    sys.exit(0 if passed == len(_results) else 1)
