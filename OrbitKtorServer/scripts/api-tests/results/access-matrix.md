# Access matrix

Each cell: expected status / actual status. ✗ marks a mismatch.

| Action | Event | owner | stranger | member | blocked |
|---|---|---|---|---|---|
| view the event | public | 200 / 200 | 200 / 200 | 200 / 200 | 404 / 404 |
| view the photo | public | 200 / 200 | 200 / 200 | 200 / 200 | 404 / 404 |
| view the ratings | public | 200 / 200 | 200 / 200 | 200 / 200 | 404 / 404 |
| view the guest list | public | 200 / 200 | 403 / 403 | 403 / 403 | 404 / 404 |
| view the entry QR code | public | 200 / 200 | 403 / 403 | 403 / 403 | 404 / 404 |
| register | public | 400 / 400 | 200 / 200 | 200 / 200 | 404 / 404 |
| cancel a registration | public | — | 200 / 200 | — | 404 / 404 |
| check in | public | — | 409 / 409 | 409 / 409 | 404 / 404 |
| rate | public | — | 409 / 409 | 409 / 409 | 404 / 404 |
| edit | public | 200 / 200 | 403 / 403 | 403 / 403 | 404 / 404 |
| cancel the event | public | — | 403 / 403 | 403 / 403 | 404 / 404 |
| delete the event | public | — | 403 / 403 | 403 / 403 | 404 / 404 |
| view the event | private | 200 / 200 | 404 / 404 | 200 / 200 | 404 / 404 |
| view the photo | private | 200 / 200 | 404 / 404 | 200 / 200 | 404 / 404 |
| view the ratings | private | 200 / 200 | 404 / 404 | 200 / 200 | 404 / 404 |
| view the guest list | private | 200 / 200 | 404 / 404 | 403 / 403 | 404 / 404 |
| view the entry QR code | private | 200 / 200 | 404 / 404 | 403 / 403 | 404 / 404 |
| register | private | 400 / 400 | 404 / 404 | 200 / 200 | 404 / 404 |
| cancel a registration | private | — | 404 / 404 | — | 404 / 404 |
| check in | private | — | 404 / 404 | 409 / 409 | 404 / 404 |
| rate | private | — | 404 / 404 | 409 / 409 | 404 / 404 |
| edit | private | 200 / 200 | 404 / 404 | 403 / 403 | 404 / 404 |
| cancel the event | private | — | 404 / 404 | 403 / 403 | 404 / 404 |
| delete the event | private | — | 404 / 404 | 403 / 403 | 404 / 404 |
| view the organiser's events | profile | 200 / 200 | 200 / 200 | — | 404 / 404 |
| view the organiser's name | profile | — | 200 / 200 | — | 404 / 404 |
| join with the access code | private | — | 200 / 200 | — | 404 / 404 |
