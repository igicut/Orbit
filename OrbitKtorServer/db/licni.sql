-- Orbit: tri dogadjaja sa tacnim datumima (id-jevi l1cn)
--
-- Pokrenuti POSLE seed.sql - koristi njegove korisnike (5eed...).
-- Vlasnik nijednog dogadjaja nije Milica, pa se sva tri vide u tabu Explore
-- i naloga milica@orbit.test moze da se prijavi na njih.
--
-- Datumi su fiksni, ne racunaju se od NOW(): 26. 9, 29. 9. i 25. 10. 2026.
-- Slike stoje u db/seed_images/ i moraju se prekopirati u uploads/, inace
-- se na karticama vide prazne plocice.
--
-- VAZNO: zbog SET time_zone nize, vremena ispod su u UTC-u, a aplikacija ih prikazuje
-- u lokalnoj zoni. Beograd je u septembru CEST (+2), a 25. 10. 2026. vec CET (+1), pa je:
--   18:00 UTC -> 20:00 rodjendan,  09:00 UTC -> 11:00 odbrana,  06:00 UTC -> 07:00 izbori.
--
-- Skripta moze da se pokrene vise puta.

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Ponovno pokretanje: prvo zavisne tabele
DELETE FROM attendances      WHERE event_id LIKE 'l1cn%';
DELETE FROM registrations    WHERE event_id LIKE 'l1cn%';
DELETE FROM ratings          WHERE event_id LIKE 'l1cn%';
DELETE FROM event_members    WHERE event_id LIKE 'l1cn%';
DELETE FROM event_embeddings WHERE event_id LIKE 'l1cn%';
DELETE FROM events           WHERE id       LIKE 'l1cn%';

INSERT INTO events
(id, owner_id, title, description, latitude, longitude, address,
 start_time, duration_minutes, category, visibility, image_uris,
 capacity, price, access_code,
 avg_rating, rating_count, created_at, status, check_in_code)
VALUES

-- Rodjendan; organizator je Stefan
('l1cn0001-0000-4000-8000-000000000001',
 '5eed0001-0000-4000-8000-000000000002',
 'Igorov rođendan',
 'Proslava rođendana uz tortu i druženje. Ponesite dobro raspoloženje, za ostalo je pobrinuto.',
 44.8202, 20.3992, 'Bulevar Arsenija Čarnojevića 215, Beograd',
 UNIX_TIMESTAMP('2026-09-26 18:00:00') * 1000, 300, 'SOCIAL', 'PUBLIC',
 JSON_ARRAY('/images/5eed1a9e-0000-4000-8000-000000000019.jpg',
            '/images/5eed1a9e-0000-4000-8000-000000000020.jpg',
            '/images/5eed1a9e-0000-4000-8000-000000000017.webp'),
 25, NULL, NULL, 0, 0, UNIX_TIMESTAMP('2026-09-12 10:00:00') * 1000, 'ACTIVE', NULL),

-- Odbrana diplomskog rada; organizator je Jelena
('l1cn0001-0000-4000-8000-000000000002',
 '5eed0001-0000-4000-8000-000000000005',
 'Odbrana diplomskog rada',
 'Javna odbrana diplomskog rada pred komisijom, uz kratko izlaganje i prikaz aplikacije.',
 44.8069, 20.4771, 'ETF, Bulevar kralja Aleksandra 73, Beograd',
 UNIX_TIMESTAMP('2026-09-29 09:00:00') * 1000, 60, 'TECH', 'PUBLIC',
 JSON_ARRAY('/images/5eed1a9e-0000-4000-8000-000000000015.jpg',
            '/images/5eed1a9e-0000-4000-8000-000000000021.webp'),
 40, NULL, NULL, 0, 0, UNIX_TIMESTAMP('2026-09-15 09:00:00') * 1000, 'ACTIVE', NULL),

-- Izbori; organizator je Nikola. Bez ogranicenja mesta, traje ceo dan
('l1cn0001-0000-4000-8000-000000000003',
 '5eed0001-0000-4000-8000-000000000006',
 'Parlamentarni izbori',
 'Glasanje na redovnom biračkom mestu. Otvoreno od 7 do 20 časova, potrebna je lična karta.',
 44.8125, 20.4612, 'Studentski trg 1, Beograd',
 UNIX_TIMESTAMP('2026-10-25 06:00:00') * 1000, 780, 'OTHER', 'PUBLIC',
 JSON_ARRAY('/images/5eed1a9e-0000-4000-8000-000000000018.jpg',
            '/images/5eed1a9e-0000-4000-8000-000000000016.jpg'),
 NULL, NULL, NULL, 0, 0, UNIX_TIMESTAMP('2026-10-01 12:00:00') * 1000, 'ACTIVE', NULL);

-- Broj prijava se cuva u koloni, isto kao u seed.sql
UPDATE events e
SET e.registered_count = (
    SELECT COUNT(*) FROM registrations r WHERE r.event_id = e.id
)
WHERE e.id LIKE 'l1cn%';

-- Pregled
SELECT e.id,
       e.title,
       FROM_UNIXTIME(e.start_time / 1000) AS pocetak,
       e.category                         AS kategorija,
       u.display_name                     AS organizator,
       JSON_LENGTH(e.image_uris)          AS slika
FROM events e
JOIN users u ON u.id = e.owner_id
WHERE e.id LIKE 'l1cn%'
ORDER BY e.start_time;
