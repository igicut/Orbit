"""F-37: otpremanje slika, izdavanje uz token i ciscenje fajlova uz dogadjaj."""
import json
import re
import time
import uuid

from common import call, check, delete_test_events, finish, login, multipart, request_bytes

PREFIX = "IMGTEST"
STORED_PATH = re.compile(r"^/images/[0-9a-f-]{36}\.(jpg|png|webp)$")

# Najmanji ispravan PNG, 1x1 providan piksel
PNG = bytes.fromhex(
    "89504e470d0a1a0a0000000d494844520000000100000001080600000"
    "01f15c4890000000a49444154789c63000100000500010d0a2db4000000"
    "0049454e44ae426082"
)


def upload(blob, filename="photo.png", content_type="image/png", token=None):
    body, header = multipart(blob, filename, content_type)
    status, raw, _ = request_bytes("POST", "/images", body, header, token)
    return status, raw


def new_event(token, images, starts_in_ms=2 * 86400000):
    now = int(time.time() * 1000)
    event = {"id": str(uuid.uuid4()), "ownerId": "ignored", "title": f"{PREFIX} {uuid.uuid4().hex[:6]}",
             "description": "image test", "latitude": 44.8, "longitude": 20.46,
             "startTime": now + starts_in_ms, "category": "OTHER", "visibility": "PUBLIC",
             "imageUris": images, "createdAt": now}
    status, body = call("POST", "/events", event, token)
    return status, body


marko, milica = login("marko"), login("milica")

# ---- otpremanje
status, raw = upload(PNG, token=marko)
path = json.loads(raw)["path"] if status == 201 else ""
check("upload png -> 201 with a generated path", status == 201 and STORED_PATH.match(path), (status, raw[:120]))

status, raw, _ = request_bytes("GET", path, token=milica)
check("another user downloads the same bytes", status == 200 and raw == PNG, (status, len(raw)))

status, _, _ = request_bytes("GET", path)
check("download without a token -> 401", status == 401, status)

status, _ = upload(PNG, filename="photo.txt", content_type="text/plain", token=marko)
check("upload text/plain -> 415", status == 415, status)

status, _ = upload(b"x" * (8 * 1024 * 1024 + 1), filename="big.jpg", content_type="image/jpeg", token=marko)
check("upload over 8 MB -> 413", status == 413, status)

status, _, _ = request_bytes("POST", "/images", b"", "application/octet-stream", marko)
check("upload that is not multipart -> 415", status == 415, status)

body, header = multipart(b"no file here", None, "text/plain", field="note")
status, _, _ = request_bytes("POST", "/images", body, header, marko)
check("multipart without a file part -> 400", status == 400, status)

# ---- ime fajla dolazi od servera, ne od klijenta
status, _, _ = request_bytes("GET", "/images/" + str(uuid.uuid4()) + ".png", token=marko)
check("unknown image -> 404", status == 404, status)
status, _, _ = request_bytes("GET", "/images/..%2f..%2fapplication.yaml", token=marko)
check("path traversal -> 404", status == 404, status)
status, _, _ = request_bytes("GET", "/images/notes.txt", token=marko)
check("name that is not a stored image -> 404", status == 404, status)

# ---- dogadjaj nosi samo putanje sa servera
status, second = upload(PNG, token=marko)
second_path = json.loads(second)["path"]
status, event = new_event(marko, [path, second_path])
check("create event with two uploaded images -> 201", status == 201 and event["imageUris"] == [path, second_path],
      (status, event))
EID = event["id"]

status, seen = call("GET", f"/events/{EID}", token=milica)
check("another user sees the same paths", seen["imageUris"] == [path, second_path], seen)

status, text = new_event(marko, ["content://media/external/images/media/42"])
check("create event with a local content URI -> 400", status == 400 and "uploaded" in text, (status, text))
status, text = new_event(marko, [path] * 11)
check("create event with 11 images -> 400", status == 400 and "at most" in text, (status, text))

# ---- uklonjena slika nestaje i sa diska
status, current = call("GET", f"/events/{EID}", token=marko)
status, updated = call("PUT", f"/events/{EID}", {**current, "imageUris": [path]}, token=marko)
check("edit keeps the remaining image", status == 200 and updated["imageUris"] == [path], (status, updated))
status, _, _ = request_bytes("GET", second_path, token=marko)
check("image removed from the event is deleted from disk -> 404", status == 404, status)

status, _ = call("DELETE", f"/events/{EID}", token=marko)
check("organiser deletes the event -> 204", status == 204, status)
status, _, _ = request_bytes("GET", path, token=marko)
check("images of a deleted event are gone -> 404", status == 404, status)

delete_test_events(PREFIX)
finish()
