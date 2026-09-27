"""
F-37: neuspesno otpremanje fotografija. Posle svakog neuspeha na disku i u bazi ne sme da ostane
nista: ni delimican fajl u uploads/, ni red u tabeli images.
Test mora da radi na istom racunaru kao server, jer broji fajlove u njegovom folderu.
"""
import os
import socket
import time
import uuid
from pathlib import Path
from urllib.parse import urlparse

from common import API, TINY_PNG, call, check, delete_test_events, finish, login, multipart, request_bytes, sql

PREFIX = "UPLTEST"
# Server se pokrece iz OrbitKtorServer/, pa je podrazumevani folder OrbitKtorServer/uploads
UPLOAD_DIR = Path(os.environ.get("ORBIT_UPLOAD_DIR") or Path(__file__).resolve().parents[2] / "uploads")


def upload(blob, filename="photo.png", content_type="image/png", token=None):
    body, header = multipart(blob, filename, content_type)
    status, _, _ = request_bytes("POST", "/images", body, header, token)
    return status


def leftovers():
    """(fajlova u uploads/, redova u images)"""
    return len(list(UPLOAD_DIR.iterdir())), int(sql("SELECT COUNT(*) FROM images"))


def settles_back_to(before, seconds=5):
    """Server brise nedovrsen fajl tek kad primeti da je veza pukla, pa se kratko ceka"""
    deadline = time.time() + seconds
    while time.time() < deadline:
        if leftovers() == before:
            return before
        time.sleep(0.25)
    return leftovers()


def interrupted_upload(token, announced=1_000_000, sent=200_000):
    """Najavi 1 MB, posalje 200 KB i prekine vezu, kao telefon koji izgubi mrezu usred slanja"""
    url = urlparse(API)
    boundary = "orbit" + uuid.uuid4().hex
    part = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"cut.png\"\r\n"
            f"Content-Type: image/png\r\n\r\n").encode()
    head = (f"POST /images HTTP/1.1\r\nHost: {url.hostname}:{url.port}\r\n"
            f"Authorization: Bearer {token}\r\n"
            f"Content-Type: multipart/form-data; boundary={boundary}\r\n"
            f"Content-Length: {len(part) + announced}\r\n\r\n").encode()
    with socket.create_connection((url.hostname, url.port), timeout=10) as connection:
        connection.sendall(head + part + TINY_PNG + b"x" * (sent - len(TINY_PNG)))
        time.sleep(0.5)


marko = login("marko")
assert UPLOAD_DIR.is_dir(), f"upload folder not found: {UPLOAD_DIR} (set ORBIT_UPLOAD_DIR)"

# Svaka provera meri od svog pocetka, da ostatak jedne ne obori sledece
before = leftovers()
status = upload(TINY_PNG)
check("upload without a token", status == 401, status, expected="401")

before = leftovers()
interrupted_upload(marko)
after = settles_back_to(before)
check("upload cut off halfway leaves no file and no images row", after == before,
      f"files and rows before {before}, after {after}", expected=f"unchanged: {before}")

before = leftovers()
status = upload(b"x" * (8 * 1024 * 1024 + 1), filename="big.jpg", content_type="image/jpeg", token=marko)
after = settles_back_to(before)
check("upload over 8 MB is refused and leaves nothing behind", status == 413 and after == before,
      f"{status}; before {before}, after {after}", expected="413, unchanged")

before = leftovers()
status = upload(b"just text", filename="note.txt", content_type="text/plain", token=marko)
after = settles_back_to(before)
check("upload of a text file is refused and leaves nothing behind", status == 415 and after == before,
      f"{status}; before {before}, after {after}", expected="415, unchanged")

before = leftovers()
status = upload(b"", token=marko)
after = settles_back_to(before)
check("empty file is refused and leaves nothing behind", status == 400 and after == before,
      f"{status}; before {before}, after {after}", expected="400, unchanged")

# ---- dogadjaj sa fotografijom koja nikad nije stigla na server
now = int(time.time() * 1000)
never_uploaded = f"/images/{uuid.uuid4()}.jpg"
event = {"id": str(uuid.uuid4()), "ownerId": "ignored", "title": f"{PREFIX} missing photo",
         "description": "upload failure test", "latitude": 44.8, "longitude": 20.46,
         "startTime": now + 3 * 86400000, "category": "OTHER", "visibility": "PUBLIC",
         "imageUris": [never_uploaded], "createdAt": now}
status, text = call("POST", "/events", event, marko)
check("event with a photo path that was never uploaded", status == 400, (status, text), expected="400")

delete_test_events(PREFIX)
finish()
