"""F-37: otpremanje slika, pristup slikama, samo sopstvene slike na dogadjaju i utisku."""
import json
import re
import time
import uuid

from common import TINY_PNG as PNG, call, check, delete_test_events, finish, login, multipart, request_bytes, sql

PREFIX = "IMGTEST"
STORED_PATH = re.compile(r"^/images/[0-9a-f-]{36}\.(jpg|png|webp)$")
# Izlozba iz seed.sql: javna, Jelenina, Milica je potvrdila dolazak i ocenila je
IZLOZBA = "5eed0002-0000-4000-8000-000000000004"
MILICA_RATING = "5eed0003-0000-4000-8000-000000000008"



def upload(blob, filename="photo.png", content_type="image/png", token=None):
    body, header = multipart(blob, filename, content_type)
    status, raw, _ = request_bytes("POST", "/images", body, header, token)
    return status, raw


def upload_path(token):
    status, raw = upload(PNG, token=token)
    return json.loads(raw)["path"] if status == 201 else ""


def new_event(token, images, starts_in_ms=2 * 86400000, visibility="PUBLIC"):
    now = int(time.time() * 1000)
    event = {"id": str(uuid.uuid4()), "ownerId": "ignored", "title": f"{PREFIX} {uuid.uuid4().hex[:6]}",
             "description": "image test", "latitude": 44.8, "longitude": 20.46,
             "startTime": now + starts_in_ms, "category": "OTHER", "visibility": visibility,
             "imageUris": images, "createdAt": now}
    status, body = call("POST", "/events", event, token)
    return status, body


def download_status(path, token):
    status, _, _ = request_bytes("GET", path, token=token)
    return status


marko, milica, ana, stefan = login("marko"), login("milica"), login("ana"), login("stefan")

# ---- otpremanje
status, raw = upload(PNG, token=marko)
path = json.loads(raw)["path"] if status == 201 else ""
check("upload png -> 201 with a generated path", status == 201 and STORED_PATH.match(path), (status, raw[:120]))

status, raw, headers = request_bytes("GET", path, token=marko)
check("uploader downloads the image before it is on any event", status == 200 and raw == PNG, (status, len(raw)))
check("cache is private, only the phone may keep the image", "private" in (headers.get("Cache-Control") or ""),
      headers.get("Cache-Control"))

status = download_status(path, milica)
check("another user cannot download an image that is on no event -> 404", status == 404, status)

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
status = download_status("/images/" + str(uuid.uuid4()) + ".png", marko)
check("unknown image -> 404", status == 404, status)
status = download_status("/images/..%2f..%2fapplication.yaml", marko)
check("path traversal -> 404", status == 404, status)
status = download_status("/images/notes.txt", marko)
check("name that is not a stored image -> 404", status == 404, status)

# ---- dogadjaj nosi samo putanje sa servera
second_path = upload_path(marko)
status, event = new_event(marko, [path, second_path])
check("create event with two uploaded images -> 201", status == 201 and event["imageUris"] == [path, second_path],
      (status, event))
EID = event["id"]

status, seen = call("GET", f"/events/{EID}", token=milica)
check("another user sees the same paths", seen["imageUris"] == [path, second_path], seen)

status, raw, _ = request_bytes("GET", path, token=milica)
check("once on a public event, another user downloads the image", status == 200 and raw == PNG, (status, len(raw)))

# seed.sql: Ana je blokirala Marka, a blokada vazi i za slike
status = download_status(path, ana)
check("user who blocked the organiser cannot download the image -> 404", status == 404, status)

status, text = new_event(marko, ["content://media/external/images/media/42"])
check("create event with a local content URI -> 400", status == 400 and "uploaded" in text, (status, text))
# Granica je 5 (MAX_IMAGES); 6 je prvi broj koji se odbija
status, text = new_event(marko, [path] * 6)
check("create event with 6 images -> 400", status == 400 and "at most" in text, (status, text))

# ---- samo sopstvene slike: tudja slika bi se inace obrisala izmenom
status, text = new_event(milica, [path])
check("create event with someone else's image -> 400", status == 400 and "sopstvene" in text, (status, text))

milica_path = upload_path(milica)
status, milica_event = new_event(milica, [milica_path])
check("create event with own image -> 201", status == 201, (status, milica_event))
status, text = call("PUT", f"/events/{milica_event['id']}",
                    {**milica_event, "imageUris": [milica_path, second_path]}, token=milica)
check("edit that adds someone else's image -> 400", status == 400 and "sopstvene" in text, (status, text))
status = download_status(second_path, marko)
check("after the rejected edit the other organiser's image is still on disk", status == 200, status)

# ---- privatni dogadjaj: slika tek posle ulaska kodom
private_path = upload_path(marko)
status, private_event = new_event(marko, [private_path], visibility="PRIVATE")
check("create private event -> 201", status == 201, (status, private_event))
# Pristupni kod pravi server
code = private_event["accessCode"]
status = download_status(private_path, milica)
check("non-member cannot download a private event's image -> 404", status == 404, status)
status, _ = call("POST", "/events/join", {"accessCode": code}, milica)
check("milica joins with the access code -> 200", status == 200, status)
status = download_status(private_path, milica)
check("member downloads the private event's image", status == 200, status)

# ---- utisak: ista pravila, pa se ocena vraca na seed stanje
original = sql(f"SELECT value, comment, created_at FROM ratings WHERE id = '{MILICA_RATING}'").split("\t")
status, text = call("PATCH", f"/events/{IZLOZBA}/rating", {"value": 5, "imagePath": path}, milica)
check("rating with someone else's image -> 400", status == 400 and "sopstvene" in text, (status, text))

rating_path = upload_path(milica)
status, _ = call("PATCH", f"/events/{IZLOZBA}/rating", {"value": 5, "imagePath": rating_path}, milica)
check("rating with own image -> 200", status == 200, status)
status = download_status(rating_path, stefan)
check("anyone who sees the event downloads the rating image", status == 200, status)

sql(f"UPDATE ratings SET value = {original[0]}, comment = '{original[1]}', image_path = NULL, "
    f"created_at = {original[2]} WHERE id = '{MILICA_RATING}'")

# ---- uklonjena slika nestaje i sa diska
status, current = call("GET", f"/events/{EID}", token=marko)
status, updated = call("PUT", f"/events/{EID}", {**current, "imageUris": [path]}, token=marko)
check("edit keeps the remaining image", status == 200 and updated["imageUris"] == [path], (status, updated))
status = download_status(second_path, marko)
check("image removed from the event is deleted from disk -> 404", status == 404, status)

status, _ = call("DELETE", f"/events/{EID}", token=marko)
check("organiser deletes the event -> 204", status == 204, status)
status = download_status(path, marko)
check("images of a deleted event are gone -> 404", status == 404, status)

delete_test_events(PREFIX)
finish()
