-- Orbit: dogadjaji za snimanje demonstracije (id-jevi d3m0)
--
-- Pokrenuti POSLE seed.sql - koristi njegove korisnike (5eed...).
-- Nalog za snimanje je Milica (milica@orbit.test / orbit123).
--
-- VAZNO: tri dogadjaja su vezana za trenutak pokretanja skripte.
-- Prozor za upad bez prijave traje samo 15 minuta, pa ovo pokrenuti
-- neposredno pre snimanja, ne dan ranije.
--
-- Brisanje svega iz ove skripte:
--   DELETE FROM attendances WHERE event_id LIKE 'd3m0%';
--   DELETE FROM registrations WHERE event_id LIKE 'd3m0%';
--   DELETE FROM ratings WHERE event_id LIKE 'd3m0%';
--   DELETE FROM event_members WHERE event_id LIKE 'd3m0%';
--   DELETE FROM event_embeddings WHERE event_id LIKE 'd3m0%';
--   DELETE FROM events WHERE id LIKE 'd3m0%';

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Ponovno pokretanje: prvo zavisne tabele
DELETE FROM attendances      WHERE event_id LIKE 'd3m0%';
DELETE FROM registrations    WHERE event_id LIKE 'd3m0%';
DELETE FROM ratings          WHERE event_id LIKE 'd3m0%';
DELETE FROM event_members    WHERE event_id LIKE 'd3m0%';
DELETE FROM event_embeddings WHERE event_id LIKE 'd3m0%';
DELETE FROM events           WHERE id       LIKE 'd3m0%';

-- Sve tacke za potvrdu dolaska su na Studentskom trgu (44.8189, 20.4587),
-- pa jedan `adb emu geo fix 20.4587 44.8189` pokriva sve provere dolaska.

INSERT INTO events
(id, owner_id, title, description, latitude, longitude, address,
 start_time, duration_minutes, category, visibility, image_uris,
 capacity, price, access_code,
 avg_rating, rating_count, created_at)
VALUES

-- 1. Poceo, Milica je prijavljena -> detalj nudi "Confirm attendance"
('d3m00001-0000-4000-8000-000000000001',
 '5eed0001-0000-4000-8000-000000000002',
 'Radionica keramike - u toku, prijavljeni potvrdjuju dolazak',
 'Poceo pre pet minuta i traje tri sata. Prijavljeni gosti mogu da potvrde dolazak sve do kraja, dokle god su u krugu od 200 m.',
 44.8189, 20.4587, 'Studentski trg 1, Beograd',
 (UNIX_TIMESTAMP(NOW()) - 300) * 1000, 180, 'ART', 'PUBLIC', JSON_ARRAY(),
 30, NULL, NULL, 0, 0, (UNIX_TIMESTAMP(NOW()) - 7*86400) * 1000),

-- 2. Poceo, Milica NIJE prijavljena, ima mesta -> upad u prvih 15 minuta
('d3m00001-0000-4000-8000-000000000002',
 '5eed0001-0000-4000-8000-000000000003',
 'Ulicni koncert - u toku, upad bez prijave',
 'Poceo pre pet minuta. Ko nije prijavljen moze da potvrdi dolazak samo u prvih petnaest minuta i samo ako ima slobodnih mesta.',
 44.8189, 20.4587, 'Studentski trg 1, Beograd',
 (UNIX_TIMESTAMP(NOW()) - 300) * 1000, 120, 'MUSIC', 'PUBLIC', JSON_ARRAY(),
 20, NULL, NULL, 0, 0, (UNIX_TIMESTAMP(NOW()) - 6*86400) * 1000),

-- 3. Poceo pre 45 min, Milica nije prijavljena -> prozor za upad istekao
('d3m00001-0000-4000-8000-000000000003',
 '5eed0001-0000-4000-8000-000000000005',
 'Predavanje o fotografiji - u toku, prozor za upad istekao',
 'Poceo pre cetrdeset pet minuta. Neprijavljeni vise ne mogu da potvrde dolazak, jer je proslo petnaest minuta od pocetka.',
 44.8189, 20.4587, 'Studentski trg 1, Beograd',
 (UNIX_TIMESTAMP(NOW()) - 2700) * 1000, 180, 'TECH', 'PUBLIC', JSON_ARRAY(),
 NULL, NULL, NULL, 0, 0, (UNIX_TIMESTAMP(NOW()) - 9*86400) * 1000),

-- 4. Predstojeci, kapacitet popunjen -> "No spots left", sa cenom
('d3m00001-0000-4000-8000-000000000004',
 '5eed0001-0000-4000-8000-000000000006',
 'Degustacija sireva - popunjeno, nema slobodnih mesta',
 'Dva mesta i oba su zauzeta, pa se dugme za prijavu ne nudi. Primer i za dogadjaj sa cenom.',
 44.8068, 20.4739, 'Metropol Palace, Bulevar kralja Aleksandra 69',
 (UNIX_TIMESTAMP(NOW()) + 2*86400) * 1000, 150, 'FOOD', 'PUBLIC', JSON_ARRAY(),
 2, 1200.0, NULL, 0, 0, (UNIX_TIMESTAMP(NOW()) - 4*86400) * 1000),

-- 5. Zavrsen, Milica prisustvovala i nije ocenila -> unos ocene + prosek 4.5
('d3m00001-0000-4000-8000-000000000005',
 '5eed0001-0000-4000-8000-000000000002',
 'Kviz u Dorcolu - zavrsen, ocenjivanje otvoreno',
 'Zavrsio se pre tri sata. Ocenu daju samo oni kojima je dolazak potvrdjen; prosek postojecih ocena je 4.5 od dve ocene.',
 44.8258, 20.4633, 'Dorcol Platz, Dobracina 59b',
 (UNIX_TIMESTAMP(NOW()) - 4*3600) * 1000, 60, 'SOCIAL', 'PUBLIC', JSON_ARRAY(),
 50, NULL, NULL, 0, 0, (UNIX_TIMESTAMP(NOW()) - 12*86400) * 1000),

-- 6. Privatni, Milica nije clan -> ulaz kodom DEMO24 (F-21)
('d3m00001-0000-4000-8000-000000000006',
 '5eed0001-0000-4000-8000-000000000002',
 'Zatvorena projekcija - privatni dogadjaj sa kodom',
 'Ne vidi se u pretrazi ni na mapi dok se ne udje kodom. Kod za demonstraciju je DEMO24.',
 44.8225, 20.4506, 'Kalemegdan, Beograd',
 (UNIX_TIMESTAMP(NOW()) + 3*86400) * 1000, 120, 'ART', 'PRIVATE', JSON_ARRAY(),
 15, NULL, 'DEMO24', 0, 0, (UNIX_TIMESTAMP(NOW()) - 2*86400) * 1000),

-- 7. Milicin dogadjaj -> pogled vlasnika: spisak gostiju, izmena i brisanje
('d3m00001-0000-4000-8000-000000000007',
 '5eed0001-0000-4000-8000-000000000001',
 'Setnja Kosancicevim vencem - moj dogadjaj, spisak gostiju',
 'Dogadjaj koji je napravio prijavljeni nalog. Vlasnik ne zauzima mesto, vidi spisak gostiju i moze da menja i obrise dogadjaj dok ne pocne.',
 44.8176, 20.4569, 'Kosancicev venac, Beograd',
 (UNIX_TIMESTAMP(NOW()) + 5*86400) * 1000, 90, 'OUTDOOR', 'PUBLIC', JSON_ARRAY(),
 NULL, NULL, NULL, 0, 0, (UNIX_TIMESTAMP(NOW()) - 86400) * 1000);

-- ---- prijave ----

INSERT INTO registrations (event_id, user_id, registered_at) VALUES
-- 1: Milica prijavljena, pa joj se nudi potvrda dolaska
('d3m00001-0000-4000-8000-000000000001', '5eed0001-0000-4000-8000-000000000001', (UNIX_TIMESTAMP(NOW()) - 2*86400) * 1000),
('d3m00001-0000-4000-8000-000000000001', '5eed0001-0000-4000-8000-000000000003', (UNIX_TIMESTAMP(NOW()) - 2*86400) * 1000),
('d3m00001-0000-4000-8000-000000000001', '5eed0001-0000-4000-8000-000000000006', (UNIX_TIMESTAMP(NOW()) - 86400) * 1000),

-- 2: neko drugi je prijavljen, ali ostaje slobodnih mesta za upad
('d3m00001-0000-4000-8000-000000000002', '5eed0001-0000-4000-8000-000000000006', (UNIX_TIMESTAMP(NOW()) - 86400) * 1000),

-- 4: oba mesta zauzeta, Milica nije medju njima
('d3m00001-0000-4000-8000-000000000004', '5eed0001-0000-4000-8000-000000000002', (UNIX_TIMESTAMP(NOW()) - 3*86400) * 1000),
('d3m00001-0000-4000-8000-000000000004', '5eed0001-0000-4000-8000-000000000003', (UNIX_TIMESTAMP(NOW()) - 3*86400) * 1000),

-- 5: troje prijavljenih na zavrsenom dogadjaju
('d3m00001-0000-4000-8000-000000000005', '5eed0001-0000-4000-8000-000000000001', (UNIX_TIMESTAMP(NOW()) - 5*86400) * 1000),
('d3m00001-0000-4000-8000-000000000005', '5eed0001-0000-4000-8000-000000000003', (UNIX_TIMESTAMP(NOW()) - 5*86400) * 1000),
('d3m00001-0000-4000-8000-000000000005', '5eed0001-0000-4000-8000-000000000005', (UNIX_TIMESTAMP(NOW()) - 4*86400) * 1000),

-- 7: gosti na Milicinom dogadjaju
('d3m00001-0000-4000-8000-000000000007', '5eed0001-0000-4000-8000-000000000002', (UNIX_TIMESTAMP(NOW()) - 43200) * 1000),
('d3m00001-0000-4000-8000-000000000007', '5eed0001-0000-4000-8000-000000000003', (UNIX_TIMESTAMP(NOW()) - 43200) * 1000),
('d3m00001-0000-4000-8000-000000000007', '5eed0001-0000-4000-8000-000000000006', (UNIX_TIMESTAMP(NOW()) - 21600) * 1000);

-- ---- potvrdjeni dolasci ----
-- Samo na zavrsenom dogadjaju; Milica jeste dosla, ali jos nije ocenila

INSERT INTO attendances (event_id, user_id, checked_in_at) VALUES
('d3m00001-0000-4000-8000-000000000005', '5eed0001-0000-4000-8000-000000000001', (UNIX_TIMESTAMP(NOW()) - 4*3600 + 600) * 1000),
('d3m00001-0000-4000-8000-000000000005', '5eed0001-0000-4000-8000-000000000003', (UNIX_TIMESTAMP(NOW()) - 4*3600 + 420) * 1000),
('d3m00001-0000-4000-8000-000000000005', '5eed0001-0000-4000-8000-000000000005', (UNIX_TIMESTAMP(NOW()) - 4*3600 + 900) * 1000);

-- ---- ocene ----
-- Ana 5 i Jelena 4 -> prosek 4.5, sto na detalju daje cetiri pune zvezdice

INSERT INTO ratings (id, event_id, user_id, value, comment, created_at) VALUES
('d3m00003-0000-4000-8000-000000000001', 'd3m00001-0000-4000-8000-000000000005', '5eed0001-0000-4000-8000-000000000003', 5, NULL, (UNIX_TIMESTAMP(NOW()) - 2*3600) * 1000),
('d3m00003-0000-4000-8000-000000000002', 'd3m00001-0000-4000-8000-000000000005', '5eed0001-0000-4000-8000-000000000005', 4, NULL, (UNIX_TIMESTAMP(NOW()) - 3600) * 1000);

-- ---- izvedene kolone ----

UPDATE events e
LEFT JOIN (
    SELECT event_id, AVG(value) AS avg_value, COUNT(*) AS total
    FROM ratings
    GROUP BY event_id
) r ON r.event_id = e.id
SET e.avg_rating  = COALESCE(r.avg_value, 0),
    e.rating_count = COALESCE(r.total, 0)
WHERE e.id LIKE 'd3m0%';

UPDATE events e
LEFT JOIN (
    SELECT event_id, COUNT(*) AS total
    FROM registrations
    GROUP BY event_id
) r ON r.event_id = e.id
SET e.registered_count = COALESCE(r.total, 0)
WHERE e.id LIKE 'd3m0%';

-- ---- pregled ----

SELECT
    SUBSTRING(e.id, 31) AS n,
    e.title,
    CASE
        WHEN UNIX_TIMESTAMP(NOW()) * 1000 > e.start_time + COALESCE(e.duration_minutes, 180) * 60000 THEN 'zavrsen'
        WHEN UNIX_TIMESTAMP(NOW()) * 1000 >= e.start_time THEN 'u toku'
        ELSE 'predstoji'
    END AS stanje,
    CONCAT(e.registered_count, '/', COALESCE(e.capacity, '-')) AS prijave,
    e.visibility AS vidljivost,
    COALESCE(e.price, 0) AS cena,
    CONCAT(ROUND(e.avg_rating, 1), ' (', e.rating_count, ')') AS ocena
FROM events e
WHERE e.id LIKE 'd3m0%'
ORDER BY e.id;
