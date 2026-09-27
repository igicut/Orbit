# Seed photos

Photos referenced by `seed.sql`, `demo.sql`, `events_catalog.sql` and `licni.sql`. The server serves
images only from its upload folder (`uploads/`, gitignored), so copy these there after
loading the SQL:

```bash
cp db/seed_images/*.jpg db/seed_images/*.png db/seed_images/*.webp uploads/
```

File names follow the server rule `^[0-9a-f-]{36}\.(jpg|png|webp)$`.

| File | Original | Used for |
|---|---|---|
| `5eed1a9e-0000-4000-8000-000000000001.jpg` | art_exibition.jpg | ART |
| `5eed1a9e-0000-4000-8000-000000000002.jpg` | art_festival.jpg | ART (portrait poster) |
| `5eed1a9e-0000-4000-8000-000000000003.webp` | diploma_recieving.webp | OTHER |
| `5eed1a9e-0000-4000-8000-000000000004.png` | hiking.png | OUTDOOR |
| `5eed1a9e-0000-4000-8000-000000000005.jpg` | music.jpg | MUSIC |
| `5eed1a9e-0000-4000-8000-000000000006.jpg` | music_festiaval.jpeg | MUSIC |
| `5eed1a9e-0000-4000-8000-000000000007.jpg` | social_event.jpeg | SOCIAL, OTHER |
| `5eed1a9e-0000-4000-8000-000000000008.png` | social_event2.png | FOOD, SOCIAL |
| `5eed1a9e-0000-4000-8000-000000000009.jpg` | social_event3.jpeg | FOOD, SOCIAL |
| `5eed1a9e-0000-4000-8000-000000000010.jpg` | sport_tournament.jpg | SPORT (portrait poster) |
| `5eed1a9e-0000-4000-8000-000000000011.jpg` | sports.jpg | SPORT, OUTDOOR |
| `5eed1a9e-0000-4000-8000-000000000012.jpg` | sports_basic.jpeg | SPORT |
| `5eed1a9e-0000-4000-8000-000000000013.jpg` | tech_class.jpeg | TECH |
| `5eed1a9e-0000-4000-8000-000000000014.jpg` | tech_conference.jpeg | TECH |
| `5eed1a9e-0000-4000-8000-000000000015.jpg` | etf_zgrada.jpg | TECH (licni.sql, odbrana) |
| `5eed1a9e-0000-4000-8000-000000000016.jpg` | izbori.jpg | OTHER (licni.sql, izbori) |
| `5eed1a9e-0000-4000-8000-000000000017.webp` | rodj_2.webp | SOCIAL (licni.sql, rodjendan) |
| `5eed1a9e-0000-4000-8000-000000000018.jpg` | studentska_lista.jpg | OTHER (licni.sql, izbori) |
| `5eed1a9e-0000-4000-8000-000000000019.jpg` | birthday-cake-celebration-stockcake.jpg | SOCIAL (licni.sql, rodjendan) |
| `5eed1a9e-0000-4000-8000-000000000020.jpg` | igor_rodj.jpg | SOCIAL (licni.sql, rodjendan) |
| `5eed1a9e-0000-4000-8000-000000000021.webp` | diplomiranje.webp | TECH (licni.sql, odbrana) |
