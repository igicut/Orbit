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


# Najmanji ispravan PNG, 1x1 providan piksel
TINY_PNG = bytes.fromhex(
    "89504e470d0a1a0a0000000d494844520000000100000001080600000"
    "01f15c4890000000a49444154789c63000100000500010d0a2db4000000"
    "0049454e44ae426082"
)


def photo(token):
    """Dogadjaj mora imati bar jednu sopstvenu sliku, pa je test prvo otpremi."""
    body, header = multipart(TINY_PNG, "photo.png", "image/png")
    status, raw, _ = request_bytes("POST", "/images", body, header, token)
    if status != 201:
        sys.exit(f"Photo upload failed ({status}): {raw[:200]}")
    return json.loads(raw)["path"]


# run_all.py ovde skuplja sve provere za zbirnu tabelu; pokretanje jednog fajla je ne pise
RESULTS_FILE = os.environ.get("ORBIT_RESULTS_FILE")
SUITE = os.path.splitext(os.path.basename(sys.argv[0]))[0]


def compact(value, limit=90):
    """Kratak tekst za kolonu 'Dobijeno': bez novih redova i bez znaka | koji lomi tabelu"""
    if isinstance(value, (dict, list)):
        text = json.dumps(value, ensure_ascii=False)
    elif isinstance(value, tuple):
        text = ", ".join(compact(part, limit) for part in value)
    else:
        text = str(value)
    text = " ".join(text.split()).replace("|", "/")
    return text if len(text) <= limit else text[:limit - 1] + "…"


def check(name, ok, detail="", expected=None):
    """
    detail je ono sto je stvarno dobijeno i uvek ide u tabelu, ne samo kad provera padne.
    expected je ocekivani ishod; bez njega se uzima deo imena posle '->',
    a ime bez strelice je tvrdnja koja treba da vazi.
    """
    _results.append(bool(ok))
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))

    if RESULTS_FILE:
        if expected is None:
            expected = name.split("->", 1)[1].strip() if "->" in name else "the statement holds"
        scenario = name.split("->", 1)[0].strip()
        # Provera bez detalja je samo tvrdnja, pa je dobijeno da li vazi
        actual = detail if detail != "" else ("holds" if ok else "does not hold")
        row = {"suite": SUITE, "scenario": scenario, "expected": compact(expected),
               "actual": compact(actual), "ok": bool(ok)}
        with open(RESULTS_FILE, "a", encoding="utf-8") as out:
            out.write(json.dumps(row, ensure_ascii=False) + "\n")


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


def move_start(event_id, start_time):
    """Server ne prima dogadjaj koji je vec poceo, pa test pomera pocetak direktno u bazi."""
    sql(f"UPDATE events SET start_time = {start_time} WHERE id = '{event_id}'")


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
