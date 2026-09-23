-- Orbit: jedan dogadjaj za odbranu, za prikaz QR potvrde dolaska (id-jevi odbr)
--
-- Pokrenuti POSLE seed.sql - koristi njegove korisnike (5eed...).
-- Organizator je Stefan (stefan@orbit.test), a gost je Milica (milica@orbit.test / orbit123),
-- jer organizator ne moze da potvrdi dolazak na sopstveni dogadjaj.
--
-- VAZNO: dogadjaj pocinje deset minuta posle pokretanja skripte, a potvrda dolaska
-- (i QR i GPS) radi tek od pocetka. Pokrenuti oko 11:50 za pocetak u 12:00.
-- Za prikaz odmah, bez cekanja, zameniti "+ 600" sa "- 300" u start_time.
--
-- Ulazni kod je upisan unapred, pa QR vazi odmah:
--   tekst u QR kodu je  orbit:odbr0001-0000-4000-8000-000000000001:ODBRANA7
--
-- Brisanje svega iz ove skripte:
--   DELETE FROM attendances WHERE event_id LIKE 'odbr%';
--   DELETE FROM registrations WHERE event_id LIKE 'odbr%';
--   DELETE FROM ratings WHERE event_id LIKE 'odbr%';
--   DELETE FROM event_members WHERE event_id LIKE 'odbr%';
--   DELETE FROM event_embeddings WHERE event_id LIKE 'odbr%';
--   DELETE FROM events WHERE id LIKE 'odbr%';

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Ponovno pokretanje: prvo zavisne tabele
DELETE FROM attendances      WHERE event_id LIKE 'odbr%';
DELETE FROM registrations    WHERE event_id LIKE 'odbr%';
DELETE FROM ratings          WHERE event_id LIKE 'odbr%';
DELETE FROM event_members    WHERE event_id LIKE 'odbr%';
DELETE FROM event_embeddings WHERE event_id LIKE 'odbr%';
DELETE FROM events           WHERE id       LIKE 'odbr%';

INSERT INTO events
(id, owner_id, title, description, latitude, longitude, address,
 start_time, duration_minutes, category, visibility, image_uris,
 capacity, price, access_code,
 avg_rating, rating_count, created_at, status, check_in_code)
VALUES

-- Pocinje za deset minuta i traje tri sata; Milica je prijavljena,
-- pa potvrda dolaska radi sve do kraja, a ne samo prvih petnaest minuta
('odbr0001-0000-4000-8000-000000000001',
 '5eed0001-0000-4000-8000-000000000002',
 'Odbrana projekta',
 'Prikaz aplikacije Orbit. Gosti potvrdjuju dolazak skeniranjem QR koda na ulazu, bez lokacije.',
 44.820194, 20.398972, 'Bulevar Arsenija Carnojevica 213',
 (UNIX_TIMESTAMP(NOW()) + 60) * 1000, 180, 'TECH', 'PUBLIC',
 JSON_ARRAY('/images/5eed1a9e-0000-4000-8000-000000000014.jpg'),
 30, NULL, NULL, 0, 0, (UNIX_TIMESTAMP(NOW()) - 86400) * 1000, 'ACTIVE', 'ODBRANA7');

-- Milica je prijavljena, pa dogadjaj stoji u tabu Plans i potvrda vazi do kraja
INSERT INTO registrations (event_id, user_id, registered_at) VALUES
('odbr0001-0000-4000-8000-000000000001', '5eed0001-0000-4000-8000-000000000001', (UNIX_TIMESTAMP(NOW()) - 3600) * 1000);

-- Broj prijava se cuva u koloni, isto kao u seed.sql
UPDATE events e
SET e.registered_count = (
    SELECT COUNT(*) FROM registrations r WHERE r.event_id = e.id
)
WHERE e.id LIKE 'odbr%';

-- Pregled
SELECT e.id,
       e.title,
       FROM_UNIXTIME(e.start_time / 1000) AS pocetak,
       e.check_in_code                    AS ulazni_kod,
       e.registered_count                 AS prijave
FROM events e
WHERE e.id LIKE 'odbr%';
