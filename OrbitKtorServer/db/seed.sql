-- Orbit: demo podaci, moze ponovo da se pokrene (id-jevi 5eed)

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Brisanje starih seed redova, prvo zavisne tabele
DELETE FROM ratings       WHERE id         LIKE '5eed%';
DELETE FROM events        WHERE id         LIKE '5eed%';
DELETE FROM users         WHERE id         LIKE '5eed%';

-- Korisnici: drugi ljudi, ne id tvog uredjaja
INSERT INTO users (id, display_name, interests) VALUES
('5eed0001-0000-4000-8000-000000000001', 'Milica Jovanović',  JSON_ARRAY('music','art','food')),
('5eed0001-0000-4000-8000-000000000002', 'Stefan Petrović',   JSON_ARRAY('tech','sport')),
('5eed0001-0000-4000-8000-000000000003', 'Ana Nikolić',       JSON_ARRAY('outdoor','social','food')),
('5eed0001-0000-4000-8000-000000000004', 'Marko Ilić',        JSON_ARRAY('sport','tech')),
('5eed0001-0000-4000-8000-000000000005', 'Jelena Stanković',  JSON_ARRAY('art','music','social')),
('5eed0001-0000-4000-8000-000000000006', 'Nikola Đorđević',   JSON_ARRAY('food','outdoor'));

-- Dogadjaji: prave koordinate Beograda, vreme relativno od NOW()
INSERT INTO events
(id, owner_id, title, description, latitude, longitude, address,
 start_time, duration_minutes, category, visibility, image_uris,
 capacity, price, requires_reservation, access_code,
 avg_rating, rating_count, created_at)
VALUES

('5eed0002-0000-4000-8000-000000000001',
 '5eed0001-0000-4000-8000-000000000001',
 'Kviz veče u Skadarliji',
 'Opšte znanje u ekipama do pet ljudi. Prijave na licu mesta od 19h, početak u 20h. Pobednička ekipa dobija piće za sto.',
 44.8188, 20.4640, 'Skadarska 34, Beograd',
 (UNIX_TIMESTAMP(NOW()) - 3*86400) * 1000, 180, 'SOCIAL', 'PUBLIC', JSON_ARRAY(),
 40, NULL, 0, NULL, 0, 0, (UNIX_TIMESTAMP(NOW()) - 9*86400) * 1000),

('5eed0002-0000-4000-8000-000000000002',
 '5eed0001-0000-4000-8000-000000000002',
 'Hakaton na ETF-u',
 'Dvadesetčetvoročasovni hakaton otvoren za sve studente. Timovi do četiri člana, teme se objavljuju na početku. Hrana i piće obezbeđeni.',
 44.9055, 20.4751, 'Elektrotehnički fakultet, Bulevar kralja Aleksandra 73',
 (UNIX_TIMESTAMP(NOW()) - 8*86400) * 1000, 1440, 'TECH', 'PUBLIC', JSON_ARRAY(),
 120, 500.0, 1, NULL, 0, 0, (UNIX_TIMESTAMP(NOW()) - 14*86400) * 1000),

('5eed0002-0000-4000-8000-000000000003',
 '5eed0001-0000-4000-8000-000000000003',
 'Jutarnje trčanje na Adi',
 'Krug oko Ade Ciganlije, tempo za sve nivoe. Nalazimo se kod mosta, poneti vodu.',
 44.7866, 20.4083, 'Ada Ciganlija, Beograd',
 (UNIX_TIMESTAMP(NOW()) + 1*86400) * 1000, 90, 'SPORT', 'PUBLIC', JSON_ARRAY(),
 NULL, NULL, 0, NULL, 0, 0, (UNIX_TIMESTAMP(NOW()) - 3*86400) * 1000),

('5eed0002-0000-4000-8000-000000000004',
 '5eed0001-0000-4000-8000-000000000005',
 'Izložba mladih ilustratora',
 'Radovi dvanaest ilustratora iz Beograda i Novog Sada. Ulaz slobodan, otvaranje uz koktel.',
 44.8258, 20.4633, 'Dorćol Platz, Dobračina 59b',
 (UNIX_TIMESTAMP(NOW()) - 5*86400) * 1000, 240, 'ART', 'PUBLIC', JSON_ARRAY(),
 200, NULL, 0, NULL, 0, 0, (UNIX_TIMESTAMP(NOW()) - 20*86400) * 1000),

('5eed0002-0000-4000-8000-000000000005',
 '5eed0001-0000-4000-8000-000000000006',
 'Degustacija domaćih vina',
 'Osam vinarija iz Šumadije i Negotinske krajine. Vođena degustacija uz sommeliera, obavezna rezervacija.',
 44.8068, 20.4739, 'Metropol Palace, Bulevar kralja Aleksandra 69',
 (UNIX_TIMESTAMP(NOW()) + 10*86400) * 1000, 150, 'FOOD', 'PUBLIC', JSON_ARRAY(),
 60, 2500.0, 1, NULL, 0, 0, (UNIX_TIMESTAMP(NOW()) - 6*86400) * 1000),

('5eed0002-0000-4000-8000-000000000006',
 '5eed0001-0000-4000-8000-000000000001',
 'Koncert na Kalemegdanu',
 'Tri lokalna benda, otvorena scena kod Sahat kule. U slučaju kiše događaj se pomera za nedelju dana.',
 44.8225, 20.4506, 'Kalemegdan, Beograd',
 (UNIX_TIMESTAMP(NOW()) + 12*86400) * 1000, 300, 'MUSIC', 'PUBLIC', JSON_ARRAY(),
 NULL, 800.0, 0, NULL, 0, 0, (UNIX_TIMESTAMP(NOW()) - 11*86400) * 1000),

('5eed0002-0000-4000-8000-000000000007',
 '5eed0001-0000-4000-8000-000000000003',
 'Planinarenje na Avali',
 'Uspon do tornja i nazad, oko četiri sata hoda. Nalazimo se na parkingu, prevoz organizujemo u dogovoru.',
 44.6917, 20.5147, 'Avala, Beograd',
 (UNIX_TIMESTAMP(NOW()) + 4*86400) * 1000, 300, 'OUTDOOR', 'PUBLIC', JSON_ARRAY(),
 25, NULL, 1, NULL, 0, 0, (UNIX_TIMESTAMP(NOW()) - 5*86400) * 1000),

('5eed0002-0000-4000-8000-000000000008',
 '5eed0001-0000-4000-8000-000000000004',
 'Turnir u basketu 3x3',
 'Ulični turnir na Tašmajdanu, prijave po ekipama. Nagradni fond za prve tri ekipe.',
 44.8106, 20.4726, 'Tašmajdan, Beograd',
 (UNIX_TIMESTAMP(NOW()) + 8*86400) * 1000, 360, 'SPORT', 'PUBLIC', JSON_ARRAY(),
 48, 1000.0, 1, NULL, 0, 0, (UNIX_TIMESTAMP(NOW()) - 8*86400) * 1000),

-- F-21: privatni dogadjaji, samo preko pristupnog koda
('5eed0002-0000-4000-8000-000000000009',
 '5eed0001-0000-4000-8000-000000000002',
 'Rođendan u Zemunu',
 'Proslava na keju, ponesi nešto za roštilj. Adresa se šalje uz kod.',
 44.8447, 20.4074, 'Zemunski kej, Beograd',
 (UNIX_TIMESTAMP(NOW()) + 3*86400) * 1000, 300, 'SOCIAL', 'PRIVATE', JSON_ARRAY(),
 30, NULL, 0, 'K7M2QP', 0, 0, (UNIX_TIMESTAMP(NOW()) - 4*86400) * 1000),

('5eed0002-0000-4000-8000-000000000010',
 '5eed0001-0000-4000-8000-000000000005',
 'Zatvorena projekcija dokumentarca',
 'Prikazivanje radne verzije filma, uz razgovor sa autorkom. Mesta ograničena.',
 44.8149, 20.3919, 'Novi Beograd, Blok 45',
 (UNIX_TIMESTAMP(NOW()) + 6*86400) * 1000, 120, 'ART', 'PRIVATE', JSON_ARRAY(),
 20, NULL, 1, 'R4XB9T', 0, 0, (UNIX_TIMESTAMP(NOW()) - 2*86400) * 1000),

('5eed0002-0000-4000-8000-000000000011',
 '5eed0001-0000-4000-8000-000000000004',
 'Radionica lemljenja',
 'Osnove lemljenja i rada sa mikrokontrolerima. Alat obezbeđen, ponesi laptop.',
 44.8168, 20.4590, 'Knez Mihailova 6, Beograd',
 (UNIX_TIMESTAMP(NOW()) + 15*86400) * 1000, 210, 'OTHER', 'PRIVATE', JSON_ARRAY(),
 12, 1200.0, 1, 'H3NDZ8', 0, 0, (UNIX_TIMESTAMP(NOW()) - 1*86400) * 1000);

-- Ocene: jedna po osobi i dogadjaju (F-27)
INSERT INTO ratings (id, event_id, user_id, value, comment, created_at) VALUES

-- Kviz vece
('5eed0003-0000-4000-8000-000000000001', '5eed0002-0000-4000-8000-000000000001', '5eed0001-0000-4000-8000-000000000002', 5, 'Odlična atmosfera, pitanja taman teška.', (UNIX_TIMESTAMP(NOW()) - 2*86400) * 1000),
('5eed0003-0000-4000-8000-000000000002', '5eed0002-0000-4000-8000-000000000001', '5eed0001-0000-4000-8000-000000000003', 4, NULL, (UNIX_TIMESTAMP(NOW()) - 2*86400) * 1000),
('5eed0003-0000-4000-8000-000000000003', '5eed0002-0000-4000-8000-000000000001', '5eed0001-0000-4000-8000-000000000006', 5, 'Dolazimo opet.', (UNIX_TIMESTAMP(NOW()) - 1*86400) * 1000),

-- Hakaton
('5eed0003-0000-4000-8000-000000000004', '5eed0002-0000-4000-8000-000000000002', '5eed0001-0000-4000-8000-000000000004', 5, 'Najbolje organizovan hakaton do sada.', (UNIX_TIMESTAMP(NOW()) - 3*86400) * 1000),
('5eed0003-0000-4000-8000-000000000005', '5eed0002-0000-4000-8000-000000000002', '5eed0001-0000-4000-8000-000000000001', 4, 'Malo premalo pauza.', (UNIX_TIMESTAMP(NOW()) - 3*86400) * 1000),

-- Trcanje na Adi
('5eed0003-0000-4000-8000-000000000006', '5eed0002-0000-4000-8000-000000000003', '5eed0001-0000-4000-8000-000000000004', 4, NULL, (UNIX_TIMESTAMP(NOW()) - 1*86400) * 1000),
('5eed0003-0000-4000-8000-000000000007', '5eed0002-0000-4000-8000-000000000003', '5eed0001-0000-4000-8000-000000000005', 3, 'Tempo malo prebrz za početnike.', (UNIX_TIMESTAMP(NOW()) - 1*86400) * 1000),

-- Izlozba
('5eed0003-0000-4000-8000-000000000008', '5eed0002-0000-4000-8000-000000000004', '5eed0001-0000-4000-8000-000000000001', 5, 'Sjajan izbor radova.', (UNIX_TIMESTAMP(NOW()) - 5*86400) * 1000),
('5eed0003-0000-4000-8000-000000000009', '5eed0002-0000-4000-8000-000000000004', '5eed0001-0000-4000-8000-000000000003', 5, NULL, (UNIX_TIMESTAMP(NOW()) - 4*86400) * 1000),
('5eed0003-0000-4000-8000-000000000010', '5eed0002-0000-4000-8000-000000000004', '5eed0001-0000-4000-8000-000000000006', 4, NULL, (UNIX_TIMESTAMP(NOW()) - 4*86400) * 1000),

-- Degustacija
('5eed0003-0000-4000-8000-000000000011', '5eed0002-0000-4000-8000-000000000005', '5eed0001-0000-4000-8000-000000000005', 5, 'Vredi svakog dinara.', (UNIX_TIMESTAMP(NOW()) - 2*86400) * 1000),
('5eed0003-0000-4000-8000-000000000012', '5eed0002-0000-4000-8000-000000000005', '5eed0001-0000-4000-8000-000000000002', 3, 'Previše gužve.', (UNIX_TIMESTAMP(NOW()) - 2*86400) * 1000),

-- Koncert
('5eed0003-0000-4000-8000-000000000013', '5eed0002-0000-4000-8000-000000000006', '5eed0001-0000-4000-8000-000000000003', 4, NULL, (UNIX_TIMESTAMP(NOW()) - 6*86400) * 1000),
('5eed0003-0000-4000-8000-000000000014', '5eed0002-0000-4000-8000-000000000006', '5eed0001-0000-4000-8000-000000000004', 5, 'Odličan zvuk za otvoreni prostor.', (UNIX_TIMESTAMP(NOW()) - 6*86400) * 1000),

-- Avala
('5eed0003-0000-4000-8000-000000000015', '5eed0002-0000-4000-8000-000000000007', '5eed0001-0000-4000-8000-000000000006', 5, NULL, (UNIX_TIMESTAMP(NOW()) - 3*86400) * 1000),

-- Basket
('5eed0003-0000-4000-8000-000000000016', '5eed0002-0000-4000-8000-000000000008', '5eed0001-0000-4000-8000-000000000002', 4, 'Dobra organizacija, slabi tereni.', (UNIX_TIMESTAMP(NOW()) - 7*86400) * 1000),
('5eed0003-0000-4000-8000-000000000017', '5eed0002-0000-4000-8000-000000000008', '5eed0001-0000-4000-8000-000000000005', 2, 'Kasnio je početak sat vremena.', (UNIX_TIMESTAMP(NOW()) - 7*86400) * 1000),

-- Rodjendan (privatni)
('5eed0003-0000-4000-8000-000000000018', '5eed0002-0000-4000-8000-000000000009', '5eed0001-0000-4000-8000-000000000001', 5, NULL, (UNIX_TIMESTAMP(NOW()) - 1*86400) * 1000);

-- Preracunava prosek i broj ocena iz ubacenih redova
UPDATE events e
LEFT JOIN (
    SELECT event_id,
           AVG(value) AS avg_value,
           COUNT(*)   AS total
    FROM ratings
    GROUP BY event_id
) r ON r.event_id = e.id
SET e.avg_rating  = COALESCE(r.avg_value, 0),
    e.rating_count = COALESCE(r.total, 0)
WHERE e.id LIKE '5eed%';

-- Pregled
SELECT 'users'         AS table_name, COUNT(*) AS seeded FROM users         WHERE id         LIKE '5eed%'
UNION ALL SELECT 'events',            COUNT(*) FROM events        WHERE id         LIKE '5eed%'
UNION ALL SELECT 'ratings',           COUNT(*) FROM ratings       WHERE id         LIKE '5eed%';
