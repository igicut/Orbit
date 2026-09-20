# Event Discovery App — Architecture Decisions & Workplan

**Student:** Igor Čutović 22/384 | PMU
**Course defense target:** ~4 weeks from Aug 5, 2026 (week of Sep 1)
**Bachelor's defense target:** ~1–2 weeks after that (mid-September)

---

## 1. Architecture Decisions

### ADR-1: How public events are discovered across devices

**Decision: Custom backend — Ktor server + a simple DB, consumed via Retrofit.**

You weren't sure how option 1 (custom API) differs from option 2 (Firebase/Supabase-style backend-as-a-service), so here's the actual trade-off:

| | Custom Ktor + DB | Firebase / Supabase (BaaS) | Local/P2P only |
|---|---|---|---|
| What it is | You write and run your own server (endpoints, DB schema, business logic) | A managed backend — you configure a hosted DB and get auto-generated APIs, auth, etc. | No server; devices only exchange data directly (Bluetooth/Wi-Fi) |
| Setup time | Medium — you build the endpoints | Low — mostly config | Low-medium, but limited range |
| Course alignment | High — Ktor and Retrofit are both in your syllabus, just client + server side of the same tool | Low — new SDK/paradigm not covered in class | Low |
| Thesis value | High — you can show and defend a real server component, DB schema, API design | Lower — a grader may see it as "someone else's backend" | Doesn't satisfy "discoverable by any user" requirement well |
| Cost | Free (self-hosted or run locally during demos) | Free tier, but ties you to a vendor | Free |

**Why this fits you:** you already leaned toward Ktor + Retrofit + DB, it reuses skills from your own course material instead of introducing a new vendor SDK, and for a bachelor's thesis "I designed and built the backend" is a stronger claim than "I configured Firebase." Keep the server itself simple — plain SQLite or an in-memory/H2 DB is enough; you don't need Postgres/Docker for a demo app.

**Consequence:** you now own a server component. Run it locally on your laptop over the same Wi-Fi/hotspot as your phone or emulator during development and during the defense demo — don't add cloud hosting as a dependency unless you have spare time later.

### ADR-2: Private event sharing (P2P)

**Decision: Confirmed — implement alongside the backend, not instead of it** (per the original spec: private events join via access code *or* direct device-to-device sharing).

One sub-decision worth flagging: raw `BluetoothAdapter`/socket programming vs. **Nearby Connections API** (Google Play Services). Nearby Connections handles discovery + connection + data transfer over Bluetooth/Wi-Fi automatically and will save you real time. It still satisfies "peer-to-peer, e.g. Bluetooth" from your spec. The trade-off: if your evaluators specifically expect to see low-level `BluetoothAdapter`/BLE GATT code (since it's not in your course material, it might be a point of interest), raw Bluetooth demonstrates that better. Given your timeline, **I'd default to Nearby Connections for the course defense**, and note in your report that raw BLE is a possible extension — flag me if you'd rather do it the hard way from the start.

### ADR-3: Maps provider

**Decision (default, easy to override): osmdroid.**

No lecture covers maps, so this is a pure self-study item either way. osmdroid needs no API key or billing account — you can start immediately. Google Maps Compose is more "idiomatic" alongside the rest of your Compose UI and has better docs/community examples, but requires a Google Cloud project + billing account attached (free tier, but the setup step has failure points you don't want to hit in week 3). Set this up in **week 1**, whichever you pick, so config issues surface early.

### ADR-4: AI features

**Decision: Deferred to the thesis phase (confirmed by you).**
MVP uses manual categorization (chips/dropdown) and manual description entry. When you add AI later, call the model from your **Ktor backend**, not the Android client — keeps API keys off the device, and you already have the backend built by then, so it's a natural extension rather than new infrastructure.

---

## 2. Feature Scope

| Feature | Course defense (MVP) | Thesis version (later) |
|---|---|---|
| Create event (name, description, photo, location, time) | ✅ | ✅ |
| Manual categorization | ✅ | — |
| AI-assisted categorization/description | ❌ | ✅ |
| Public events via backend + map | ✅ | ✅ + AI ranking |
| Private events via access code | ✅ | ✅ |
| Private events via P2P (Nearby Connections) | ✅ | ✅ (or raw BT if time allows) |
| Location-based search/filter (manual radius/params) | ✅ | ✅ |
| AI-personalized recommendations | ❌ | ✅ |
| Navigation to event (deep-link to Maps/Waze) | ✅ | ✅ |
| Notifications (nearby, capacity changes) | ✅ basic (WorkManager + local notifications) | ✅ refined |
| Ratings | ✅ | ✅, feeds into ranking |
| Moderation (block user) | ✅ basic | ✅ + reporting |
| Capacity / price fields, reservation | ✅ if time allows | ✅ |

---

## 3. Tech Stack Summary

- **Client:** Kotlin, Jetpack Compose, ViewModel, Navigation-Compose, Hilt, Coroutines, Room (local cache/offline), Retrofit (backend calls), FusedLocationProviderClient, WorkManager, Nearby Connections API, osmdroid
- **Server:** Ktor, lightweight DB (SQLite/H2 to start)
- **Deferred (thesis):** LLM API call from the server for classification/description/ranking

---

## 4. Weekly Workplan — Phase 1 (Course Defense)

| Week | Focus | Deliverables | Leverages from course | New self-study |
|---|---|---|---|---|
| **1** (Aug 5–11) | Foundation | Project skeleton (Compose + Navigation + Hilt), Room schema (Event entity, DAO, repo), basic list/create/detail screens on local data only. **Also:** set up maps SDK and do a Bluetooth/Nearby Connections "hello world" spike — surface config problems now, not week 3 | Compose, ViewModel, Navigation, Room, Hilt | Maps SDK setup, Nearby Connections basics |
| **2** (Aug 12–18) | Backend + location | Minimal Ktor REST API (create/list/search events, rate event), Retrofit wired into the app, FusedLocationProviderClient for user location + radius search, image picker (Photo Picker API) storing local URIs | Retrofit/Ktor client, Location lecture | Ktor server-side, image handling |
| **3** (Aug 19–25) | Map, private events, notifications | Interactive map screen with event markers + navigate deep-link, access-code join flow, Nearby Connections P2P share for private events, WorkManager job for nearby/capacity notifications | Services, WorkManager, Notifications/Permissions | osmdroid/Maps integration, Nearby Connections full flow |
| **4** (Aug 26–Sep 1) | Ratings, moderation, polish | Star ratings affecting basic ranking, block-user moderation, manual category filters, UI polish, bug pass, demo script + slides | — | — |

## 5. Weekly Workplan — Phase 2 (Bachelor's Thesis Extension)

| Week | Focus | Deliverables |
|---|---|---|
| **5** (Sep 2–8) | AI integration | LLM call from Ktor backend for description generation + classification; wire AI category suggestions into the create-event flow |
| **6** (Sep 9–15) | Recommendations + write-up | AI-personalized ranking using interests/history/location; refine P2P reliability; finish thesis chapters (related work, methodology, evaluation) |

---

## 6. Risks & Cut List

Solo build, ~1 month, several components outside your course material — build in slack, not just features.

**Early warning signals to watch for (spikes in week 1):**
- Bluetooth/Nearby Connections not working reliably between test devices
- Maps SDK setup blocked on API keys/billing

**If you fall behind, cut in this order** (keeps everything that demonstrates course learning outcomes and the thesis's novel angle — P2P sharing — intact as long as possible):
1. Price/capacity/reservation fields
2. Cloud hosting for the backend (just run it locally during the demo instead)
3. Notifications polish (keep a minimal version)
4. Full moderation/reporting (keep basic block only)

Never cut: local persistence (Room), basic create/view flow, map view, P2P private sharing — these are your core deliverables.

---

## 7. Defense Checklist

**Course defense (week 4):**
- [ ] App builds and runs on a clean device/emulator
- [ ] Backend runs locally, reachable from the demo device
- [ ] Demo script covering: create public event → discover on map → create private event → share via P2P → join via code → rate an event → receive a notification
- [ ] Short architecture write-up (client/server/P2P layers, DB schema, key decisions from this doc)

**Thesis defense (week 6):**
- [ ] AI features demoed (classification/description/recommendations)
- [ ] Full write-up: related work, methodology, evaluation, limitations, future work
