-- Orbit: prosireni katalog dogadjaja za pretragu (id-jevi ca7a)
--
-- Pokrenuti POSLE seed.sql; koristi njegove korisnike i dodaje sest novih.
-- Prefiks je namerno ca7a, a ne d3m0: demo.sql brise sve sto pocinje na 'd3m0',
-- pa bi ovaj katalog nestao svaki put pre snimanja demonstracije.
--
-- Vektori za pretragu se ne prave ovde - server ih popuni pri pokretanju.
-- Skripta moze da se pokrene vise puta.

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Brisanje starih redova ovog kataloga, prvo zavisne tabele
DELETE FROM event_members    WHERE event_id LIKE 'ca7a%';
DELETE FROM attendances      WHERE event_id LIKE 'ca7a%';
DELETE FROM registrations    WHERE event_id LIKE 'ca7a%';
DELETE FROM ratings          WHERE event_id LIKE 'ca7a%';
DELETE FROM event_embeddings WHERE event_id LIKE 'ca7a%';
DELETE FROM events           WHERE id       LIKE 'ca7a%';
DELETE FROM blocked_users    WHERE blocker_id LIKE 'ca7a%' OR blocked_id LIKE 'ca7a%';
DELETE FROM user_credentials WHERE user_id LIKE 'ca7a%';
DELETE FROM users            WHERE id      LIKE 'ca7a%';

-- Jos sest organizatora, da katalog ne visi o jednom nalogu
INSERT INTO users (id, display_name, interests) VALUES
('ca7a9001-0000-4000-8000-000000000901', 'Ivana Marković', JSON_ARRAY('art','music')),
('ca7a9002-0000-4000-8000-000000000902', 'Luka Stojanović', JSON_ARRAY('sport','outdoor')),
('ca7a9003-0000-4000-8000-000000000903', 'Tijana Radovanović', JSON_ARRAY('food','social')),
('ca7a9004-0000-4000-8000-000000000904', 'Andreas Weber', JSON_ARRAY('social','art')),
('ca7a9005-0000-4000-8000-000000000905', 'Elena Sokolova', JSON_ARRAY('social','food')),
('ca7a9006-0000-4000-8000-000000000906', 'Filip Đukić', JSON_ARRAY('tech','other'));

-- Nalozi novih organizatora, lozinka je orbit123 kao i za ostale demo naloge
INSERT INTO user_credentials (user_id, email, password_hash, created_at) VALUES
('ca7a9001-0000-4000-8000-000000000901', 'ivana@orbit.test', '$2a$12$MLZz.NYq9YHobC0TZXKNduGOVqqs9ETSySiwqStoVO9OoWXI1Y.T.', UNIX_TIMESTAMP(NOW()) * 1000),
('ca7a9002-0000-4000-8000-000000000902', 'luka@orbit.test', '$2a$12$MLZz.NYq9YHobC0TZXKNduGOVqqs9ETSySiwqStoVO9OoWXI1Y.T.', UNIX_TIMESTAMP(NOW()) * 1000),
('ca7a9003-0000-4000-8000-000000000903', 'tijana@orbit.test', '$2a$12$MLZz.NYq9YHobC0TZXKNduGOVqqs9ETSySiwqStoVO9OoWXI1Y.T.', UNIX_TIMESTAMP(NOW()) * 1000),
('ca7a9004-0000-4000-8000-000000000904', 'andreas@orbit.test', '$2a$12$MLZz.NYq9YHobC0TZXKNduGOVqqs9ETSySiwqStoVO9OoWXI1Y.T.', UNIX_TIMESTAMP(NOW()) * 1000),
('ca7a9005-0000-4000-8000-000000000905', 'elena@orbit.test', '$2a$12$MLZz.NYq9YHobC0TZXKNduGOVqqs9ETSySiwqStoVO9OoWXI1Y.T.', UNIX_TIMESTAMP(NOW()) * 1000),
('ca7a9006-0000-4000-8000-000000000906', 'filip@orbit.test', '$2a$12$MLZz.NYq9YHobC0TZXKNduGOVqqs9ETSySiwqStoVO9OoWXI1Y.T.', UNIX_TIMESTAMP(NOW()) * 1000);

INSERT INTO events
(id, owner_id, title, description, latitude, longitude, address,
 start_time, duration_minutes, category, visibility, image_uris,
 capacity, price, access_code,
 avg_rating, rating_count, created_at)
VALUES

-- =========================
-- MUSIC
-- =========================

('ca7a0002-0000-4000-8000-000000000002',
 'ca7a9001-0000-4000-8000-000000000901',
 'Indie veče na Savamali',
 'Veče domaćih i regionalnih indie bendova uz nekoliko akustičnih nastupa.',
 44.8137, 20.4504, 'Savamala, Beograd',
 UNIX_TIMESTAMP('2026-09-26 20:00:00') * 1000, 210, 'MUSIC', 'PUBLIC', JSON_ARRAY(),
 120, 900, NULL, 0, 0, UNIX_TIMESTAMP('2026-09-10 12:00:00') * 1000),

('ca7a0003-0000-4000-8000-000000000003',
 '5eed0001-0000-4000-8000-000000000005',
 'Veče synthwave muzike',
 'Elektronska muzika, retro vizuelni efekti i neonska atmosfera.',
 44.8083, 20.4671, 'Klub KST, Bulevar kralja Aleksandra 73, Beograd',
 UNIX_TIMESTAMP('2026-10-10 21:00:00') * 1000, 240, 'MUSIC', 'PUBLIC', JSON_ARRAY(),
 300, 800, NULL, 0, 0, UNIX_TIMESTAMP('2026-09-15 14:00:00') * 1000),

('ca7a0004-0000-4000-8000-000000000004',
 '5eed0001-0000-4000-8000-000000000001',
 'Jazz nedelja u Zemunu',
 'Opušteno nedeljno veče uz live jazz trio i mali izbor pića.',
 44.8462, 20.4101, 'Zemunski kej, Beograd',
 UNIX_TIMESTAMP('2026-11-08 19:00:00') * 1000, 150, 'MUSIC', 'PUBLIC', JSON_ARRAY(),
 80, NULL, NULL, 0, 0, UNIX_TIMESTAMP('2026-10-20 10:00:00') * 1000),

('ca7a0005-0000-4000-8000-000000000005',
 'ca7a9001-0000-4000-8000-000000000901',
 'Akustično veče uz Savu',
 'Gitara, vokali i neformalno druženje pored reke. Ponesite ćebe i dobro raspoloženje.',
 44.8028, 20.4350, 'Savski kej, Novi Beograd',
 UNIX_TIMESTAMP('2026-12-05 18:00:00') * 1000, 180, 'MUSIC', 'PUBLIC', JSON_ARRAY(),
 100, NULL, NULL, 0, 0, UNIX_TIMESTAMP('2026-11-20 09:00:00') * 1000),

-- =========================
-- SPORT
-- =========================

('ca7a0006-0000-4000-8000-000000000006',
 'ca7a9002-0000-4000-8000-000000000902',
 'Turnir u stonom tenisu',
 'Rekreativni turnir za početnike i iskusnije igrače. Sistem eliminacija uz grupnu fazu.',
 44.8019, 20.4762, 'Sportski centar Tašmajdan, Beograd',
 UNIX_TIMESTAMP('2026-10-03 10:00:00') * 1000, 300, 'SPORT', 'PUBLIC', JSON_ARRAY(),
 64, 500, NULL, 0, 0, UNIX_TIMESTAMP('2026-09-18 11:00:00') * 1000),

('ca7a0007-0000-4000-8000-000000000007',
 '5eed0001-0000-4000-8000-000000000004',
 'Noćni basket na Novom Beogradu',
 '3x3 rekreativni basket pod reflektorima. Ekipe se prijavljuju do popune mesta.',
 44.8148, 20.3966, 'Blok 23, Novi Beograd',
 UNIX_TIMESTAMP('2026-10-24 19:00:00') * 1000, 210, 'SPORT', 'PUBLIC', JSON_ARRAY(),
 48, 700, NULL, 0, 0, UNIX_TIMESTAMP('2026-10-01 15:00:00') * 1000),

('ca7a0008-0000-4000-8000-000000000008',
 'ca7a9002-0000-4000-8000-000000000902',
 'Rekreativna odbojka na pesku',
 'Mešovite ekipe od četiri igrača. Nema potrebe za prethodnim iskustvom.',
 44.7932, 20.4070, 'Ada Ciganlija, Beograd',
 UNIX_TIMESTAMP('2026-09-27 15:00:00') * 1000, 180, 'SPORT', 'PUBLIC', JSON_ARRAY(),
 32, NULL, NULL, 0, 0, UNIX_TIMESTAMP('2026-09-12 12:00:00') * 1000),

('ca7a0009-0000-4000-8000-000000000009',
 '5eed0001-0000-4000-8000-000000000002',
 'Zimski turnir u pikadu',
 'Takmičenje u 501 i Cricket formatu. Obezbeđene strelice i oprema za učesnike.',
 44.8218, 20.4605, 'Dorćol, Beograd',
 UNIX_TIMESTAMP('2027-01-16 16:00:00') * 1000, 240, 'SPORT', 'PUBLIC', JSON_ARRAY(),
 40, 600, NULL, 0, 0, UNIX_TIMESTAMP('2026-12-15 13:00:00') * 1000),

-- =========================
-- FOOD
-- =========================

('ca7a0010-0000-4000-8000-000000000010',
 'ca7a9003-0000-4000-8000-000000000903',
 'Festival domaćeg street fooda',
 'Dan posvećen domaćim burgerima, sendvičima, pecivima, desertima i zanatskim sosovima.',
 44.8201, 20.4492, 'Donji grad, Kalemegdan, Beograd',
 UNIX_TIMESTAMP('2026-10-17 12:00:00') * 1000, 360, 'FOOD', 'PUBLIC', JSON_ARRAY(),
 500, NULL, NULL, 0, 0, UNIX_TIMESTAMP('2026-09-25 10:00:00') * 1000),

('ca7a0011-0000-4000-8000-000000000011',
 '5eed0001-0000-4000-8000-000000000006',
 'Radionica pravljenja paste',
 'Praktična radionica na kojoj učesnici prave svežu pastu i jednostavan domaći sos.',
 44.8166, 20.4544, 'Stari grad, Beograd',
 UNIX_TIMESTAMP('2026-11-14 17:00:00') * 1000, 180, 'FOOD', 'PUBLIC', JSON_ARRAY(),
 20, 1800, NULL, 0, 0, UNIX_TIMESTAMP('2026-10-28 16:00:00') * 1000),

('ca7a0012-0000-4000-8000-000000000012',
 'ca7a9003-0000-4000-8000-000000000903',
 'Brunch & coffee tasting',
 'Degustacija različitih vrsta kafe uz brunch meni i kratko predavanje o pripremi kafe.',
 44.8209, 20.4578, 'Dorćol, Beograd',
 UNIX_TIMESTAMP('2027-02-06 11:00:00') * 1000, 150, 'FOOD', 'PUBLIC', JSON_ARRAY(),
 35, 1500, NULL, 0, 0, UNIX_TIMESTAMP('2027-01-10 10:00:00') * 1000),

-- =========================
-- ART
-- =========================

('ca7a0013-0000-4000-8000-000000000013',
 'ca7a9001-0000-4000-8000-000000000901',
 'Akvarel Beograda',
 'Kreativna radionica slikanja beogradskih motiva tehnikom akvarela.',
 44.8187, 20.4570, 'Studentski trg, Beograd',
 UNIX_TIMESTAMP('2026-10-04 14:00:00') * 1000, 180, 'ART', 'PUBLIC', JSON_ARRAY(),
 24, 1200, NULL, 0, 0, UNIX_TIMESTAMP('2026-09-20 13:00:00') * 1000),

('ca7a0014-0000-4000-8000-000000000014',
 '5eed0001-0000-4000-8000-000000000005',
 'Street art tura Dorćol',
 'Vođena šetnja kroz dorćolske murale, grafite i skrivene umetničke intervencije.',
 44.8238, 20.4625, 'Dorćol, Beograd',
 UNIX_TIMESTAMP('2026-11-01 13:00:00') * 1000, 150, 'ART', 'PUBLIC', JSON_ARRAY(),
 30, 500, NULL, 0, 0, UNIX_TIMESTAMP('2026-10-10 12:00:00') * 1000),

('ca7a0015-0000-4000-8000-000000000015',
 '5eed0001-0000-4000-8000-000000000005',
 'Veče kratkog filma',
 'Projekcija nekoliko kratkih autorskih filmova nakon kojih sledi razgovor sa publikom.',
 44.8174, 20.4511, 'KC Grad, Beograd',
 UNIX_TIMESTAMP('2027-02-20 19:30:00') * 1000, 180, 'ART', 'PUBLIC', JSON_ARRAY(),
 100, 700, NULL, 0, 0, UNIX_TIMESTAMP('2027-01-25 14:00:00') * 1000),

-- =========================
-- TECH
-- =========================

('ca7a0016-0000-4000-8000-000000000016',
 '5eed0001-0000-4000-8000-000000000002',
 'AI & Android meetup',
 'Neformalno okupljanje programera uz kratka predavanja o korišćenju AI servisa u Android aplikacijama.',
 44.8069, 20.4771, 'ETF, Bulevar kralja Aleksandra 73, Beograd',
 UNIX_TIMESTAMP('2026-10-08 18:00:00') * 1000, 150, 'TECH', 'PUBLIC', JSON_ARRAY(),
 80, NULL, NULL, 0, 0, UNIX_TIMESTAMP('2026-09-21 10:00:00') * 1000),

('ca7a0017-0000-4000-8000-000000000017',
 'ca7a9006-0000-4000-8000-000000000906',
 'Radionica Git & GitHub',
 'Praktična radionica o granama, merge-ovima, pull requestovima i rešavanju konflikata.',
 44.8071, 20.4774, 'ETF, Beograd',
 UNIX_TIMESTAMP('2026-11-07 11:00:00') * 1000, 210, 'TECH', 'PUBLIC', JSON_ARRAY(),
 40, 500, NULL, 0, 0, UNIX_TIMESTAMP('2026-10-25 09:00:00') * 1000),

('ca7a0018-0000-4000-8000-000000000018',
 '5eed0001-0000-4000-8000-000000000002',
 'Kotlin Compose Lab',
 'Hands-on sesija pravljenja moderne Android aplikacije koristeći Jetpack Compose.',
 44.8067, 20.4770, 'ETF, Beograd',
 UNIX_TIMESTAMP('2026-12-12 12:00:00') * 1000, 240, 'TECH', 'PUBLIC', JSON_ARRAY(),
 35, 800, NULL, 0, 0, UNIX_TIMESTAMP('2026-11-14 10:00:00') * 1000),

('ca7a0019-0000-4000-8000-000000000019',
 'ca7a9006-0000-4000-8000-000000000906',
 'Indie Hackers Balkan meetup',
 'Razgovor o pravljenju malih softverskih proizvoda, validaciji ideja i dolasku do prvih korisnika.',
 44.8162, 20.4518, 'Savamala, Beograd',
 UNIX_TIMESTAMP('2027-03-06 17:00:00') * 1000, 180, 'TECH', 'PUBLIC', JSON_ARRAY(),
 90, NULL, NULL, 0, 0, UNIX_TIMESTAMP('2027-02-05 12:00:00') * 1000),

-- =========================
-- OUTDOOR
-- =========================

('ca7a0020-0000-4000-8000-000000000020',
 '5eed0001-0000-4000-8000-000000000003',
 'Zalazak sunca na Avali',
 'Lagano pešačenje do Avalskog tornja i zajedničko posmatranje zalaska sunca.',
 44.6962, 20.5166, 'Avalski toranj, Avala',
 UNIX_TIMESTAMP('2026-09-30 17:00:00') * 1000, 180, 'OUTDOOR', 'PUBLIC', JSON_ARRAY(),
 40, NULL, NULL, 0, 0, UNIX_TIMESTAMP('2026-09-15 09:00:00') * 1000),

('ca7a0021-0000-4000-8000-000000000021',
 'ca7a9002-0000-4000-8000-000000000902',
 'Biciklistička tura Zemun–Batajnica',
 'Rekreativna vožnja biciklom uz Dunav, sa nekoliko kratkih pauza.',
 44.8460, 20.4097, 'Zemunski kej, Beograd',
 UNIX_TIMESTAMP('2026-10-25 09:00:00') * 1000, 240, 'OUTDOOR', 'PUBLIC', JSON_ARRAY(),
 25, NULL, NULL, 0, 0, UNIX_TIMESTAMP('2026-10-05 11:00:00') * 1000),

('ca7a0022-0000-4000-8000-000000000022',
 '5eed0001-0000-4000-8000-000000000006',
 'Zimska šetnja Košutnjakom',
 'Lagano zimsko pešačenje kroz Košutnjak. Pogodno i za početnike.',
 44.7572, 20.4287, 'Košutnjak, Beograd',
 UNIX_TIMESTAMP('2027-01-23 11:00:00') * 1000, 180, 'OUTDOOR', 'PUBLIC', JSON_ARRAY(),
 35, NULL, NULL, 0, 0, UNIX_TIMESTAMP('2027-01-04 10:00:00') * 1000),

-- =========================
-- SOCIAL
-- =========================

('ca7a0023-0000-4000-8000-000000000023',
 'ca7a9003-0000-4000-8000-000000000903',
 'Speed friending Beograd',
 'Brzo upoznavanje novih ljudi kroz kratke razgovore i nekoliko društvenih igara.',
 44.8214, 20.4586, 'Dorćol, Beograd',
 UNIX_TIMESTAMP('2026-10-15 19:00:00') * 1000, 150, 'SOCIAL', 'PUBLIC', JSON_ARRAY(),
 60, 600, NULL, 0, 0, UNIX_TIMESTAMP('2026-09-28 12:00:00') * 1000),

('ca7a0024-0000-4000-8000-000000000024',
 '5eed0001-0000-4000-8000-000000000003',
 'International Students Hangout',
 'Neformalno druženje studenata uz razgovor, kviz pitanja i razmenu iskustava.',
 44.8185, 20.4581, 'Studentski trg, Beograd',
 UNIX_TIMESTAMP('2026-11-21 18:00:00') * 1000, 180, 'SOCIAL', 'PUBLIC', JSON_ARRAY(),
 70, NULL, NULL, 0, 0, UNIX_TIMESTAMP('2026-10-30 10:00:00') * 1000),

('ca7a0025-0000-4000-8000-000000000025',
 'ca7a9003-0000-4000-8000-000000000903',
 'Board Game Sunday',
 'Popodnevno druženje uz Catan, Carcassonne, Dixit, Ticket to Ride i druge igre.',
 44.8212, 20.4580, 'Dorćol, Beograd',
 UNIX_TIMESTAMP('2026-12-20 14:00:00') * 1000, 240, 'SOCIAL', 'PUBLIC', JSON_ARRAY(),
 50, 400, NULL, 0, 0, UNIX_TIMESTAMP('2026-11-30 15:00:00') * 1000),

('ca7a0026-0000-4000-8000-000000000026',
 '5eed0001-0000-4000-8000-000000000003',
 'Tajna kućna žurka',
 'Privatno druženje. Lokacija i dodatne informacije dostupne samo pozvanim gostima.',
 44.8295, 20.4387, 'Beograd',
 UNIX_TIMESTAMP('2027-02-13 20:00:00') * 1000, 300, 'SOCIAL', 'PRIVATE', JSON_ARRAY(),
 25, NULL, 'BG2027', 0, 0, UNIX_TIMESTAMP('2027-01-20 12:00:00') * 1000),

-- =========================
-- OTHER
-- =========================

('ca7a0027-0000-4000-8000-000000000027',
 'ca7a9006-0000-4000-8000-000000000906',
 'Diskusija: Nature vs. Nurture',
 'Otvorena diskusija o tome koliko biologija i okruženje oblikuju našu ličnost i ponašanje.',
 44.8181, 20.4518, 'Stari grad, Beograd',
 UNIX_TIMESTAMP('2026-10-29 18:30:00') * 1000, 120, 'OTHER', 'PUBLIC', JSON_ARRAY(),
 60, NULL, NULL, 0, 0, UNIX_TIMESTAMP('2026-10-10 09:00:00') * 1000),

('ca7a0028-0000-4000-8000-000000000028',
 '5eed0001-0000-4000-8000-000000000001',
 'Social Media & Self-Esteem diskusija',
 'Razgovor o uticaju društvenih mreža na samopouzdanje, poređenje sa drugima i digitalne navike.',
 44.8178, 20.4522, 'Stari grad, Beograd',
 UNIX_TIMESTAMP('2026-12-03 18:00:00') * 1000, 120, 'OTHER', 'PUBLIC', JSON_ARRAY(),
 50, NULL, NULL, 0, 0, UNIX_TIMESTAMP('2026-11-05 11:00:00') * 1000),

('ca7a0029-0000-4000-8000-000000000029',
 'ca7a9006-0000-4000-8000-000000000906',
 'Veče astronomije',
 'Posmatranje noćnog neba uz kratko uvodno predavanje o planetama i sazvežđima.',
 44.7930, 20.4075, 'Ada Ciganlija, Beograd',
 UNIX_TIMESTAMP('2027-03-13 19:00:00') * 1000, 180, 'OTHER', 'PUBLIC', JSON_ARRAY(),
 45, 300, NULL, 0, 0, UNIX_TIMESTAMP('2027-02-20 10:00:00') * 1000),

-- =========================
-- EXTRA MIXED EVENTS
-- =========================

('ca7a0030-0000-4000-8000-000000000030',
 '5eed0001-0000-4000-8000-000000000005',
 'Kviz opšte kulture - specijal',
 'Tematski kviz sa ekipama do četiri člana i finalnom rundom sa filmskim i muzičkim pitanjima.',
 44.8246, 20.4612, 'Dorćol, Beograd',
 UNIX_TIMESTAMP('2026-09-24 20:00:00') * 1000, 150, 'SOCIAL', 'PUBLIC', JSON_ARRAY(),
 100, 400, NULL, 0, 0, UNIX_TIMESTAMP('2026-09-17 12:00:00') * 1000),

('ca7a0031-0000-4000-8000-000000000031',
 '5eed0001-0000-4000-8000-000000000001',
 'Čas gitare u prirodi',
 'Početni čas gitare na otvorenom uz jednostavne akorde i zajedničko sviranje.',
 44.7888, 20.4162, 'Ada Ciganlija, Beograd',
 UNIX_TIMESTAMP('2026-10-11 11:00:00') * 1000, 120, 'MUSIC', 'PUBLIC', JSON_ARRAY(),
 15, 1000, NULL, 0, 0, UNIX_TIMESTAMP('2026-09-26 12:00:00') * 1000),

('ca7a0032-0000-4000-8000-000000000032',
 '5eed0001-0000-4000-8000-000000000004',
 'Mini Game Jam',
 'Jednodnevno pravljenje malih video igara u timovima od dva do četiri člana.',
 44.8070, 20.4772, 'ETF, Beograd',
 UNIX_TIMESTAMP('2027-01-30 10:00:00') * 1000, 540, 'TECH', 'PUBLIC', JSON_ARRAY(),
 60, NULL, NULL, 0, 0, UNIX_TIMESTAMP('2027-01-05 13:00:00') * 1000),

('ca7a0033-0000-4000-8000-000000000033',
 'ca7a9001-0000-4000-8000-000000000901',
 'Zimski foto-walk kroz centar',
 'Fotografska šetnja kroz Knez Mihailovu, Kalemegdan i okolne ulice uz zadate foto-teme.',
 44.8170, 20.4572, 'Knez Mihailova, Beograd',
 UNIX_TIMESTAMP('2027-02-27 15:00:00') * 1000, 180, 'ART', 'PUBLIC', JSON_ARRAY(),
 25, NULL, NULL, 0, 0, UNIX_TIMESTAMP('2027-02-01 11:00:00') * 1000),

('ca7a0034-0000-4000-8000-000000000034',
 'ca7a9002-0000-4000-8000-000000000902',
 'Porodični sportski dan',
 'Lagane sportske aktivnosti za sve uzraste: štafete, badminton, mali fudbal i igre spretnosti.',
 44.7954, 20.4077, 'Ada Ciganlija, Beograd',
 UNIX_TIMESTAMP('2027-03-07 11:00:00') * 1000, 300, 'SPORT', 'PUBLIC', JSON_ARRAY(),
 120, NULL, NULL, 0, 0, UNIX_TIMESTAMP('2027-02-10 10:00:00') * 1000),

('ca7a0035-0000-4000-8000-000000000035',
 '5eed0001-0000-4000-8000-000000000003',
 'Radionica pravljenja koktela bez alkohola',
 'Kreativna radionica pravljenja bezalkoholnih koktela, sirupa i ukrasa od svežeg voća.',
 44.8464, 20.4108, 'Zemun, Beograd',
 UNIX_TIMESTAMP('2027-02-28 17:00:00') * 1000, 150, 'FOOD', 'PUBLIC', JSON_ARRAY(),
 30, 1300, NULL, 0, 0, UNIX_TIMESTAMP('2027-02-01 16:00:00') * 1000),

('ca7a0036-0000-4000-8000-000000000036',
 '5eed0001-0000-4000-8000-000000000001',
 'Noćni paint & chill',
 'Opuštena večernja radionica slikanja uz muziku i slobodnu temu.',
 44.8139, 20.4514, 'Savamala, Beograd',
 UNIX_TIMESTAMP('2026-12-18 20:00:00') * 1000, 180, 'ART', 'PUBLIC', JSON_ARRAY(),
 35, 1600, NULL, 0, 0, UNIX_TIMESTAMP('2026-11-25 10:00:00') * 1000), 

-- =========================================================
-- ENGLISH
-- =========================================================

('ca7a0037-0000-4000-8000-000000000037',
 'ca7a9001-0000-4000-8000-000000000901',
 'Urban Sketching Walk',
 'A relaxed outdoor drawing session through the historic center of Belgrade. Bring a sketchbook and your favorite drawing tools.',
 44.8172, 20.4569, 'Republic Square, Belgrade',
 UNIX_TIMESTAMP('2026-10-18 11:00:00') * 1000, 180, 'ART', 'PUBLIC', JSON_ARRAY(),
 25, NULL, NULL, 0, 0, UNIX_TIMESTAMP('2026-09-25 11:00:00') * 1000),

('ca7a0038-0000-4000-8000-000000000038',
 'ca7a9006-0000-4000-8000-000000000906',
 'Open Source Evening',
 'An informal meetup for developers interested in contributing to open source projects and collaborating with other programmers.',
 44.8068, 20.4772, 'Faculty of Electrical Engineering, Belgrade',
 UNIX_TIMESTAMP('2026-10-22 18:30:00') * 1000, 150, 'TECH', 'PUBLIC', JSON_ARRAY(),
 70, NULL, NULL, 0, 0, UNIX_TIMESTAMP('2026-10-01 10:00:00') * 1000),

('ca7a0039-0000-4000-8000-000000000039',
 'ca7a9002-0000-4000-8000-000000000902',
 'Sunset Kayaking Session',
 'A beginner-friendly kayaking trip on the Sava with a short safety briefing before departure.',
 44.7953, 20.4070, 'Ada Ciganlija, Belgrade',
 UNIX_TIMESTAMP('2026-09-29 17:00:00') * 1000, 150, 'OUTDOOR', 'PUBLIC', JSON_ARRAY(),
 18, 1200, NULL, 0, 0, UNIX_TIMESTAMP('2026-09-18 12:00:00') * 1000),

('ca7a0040-0000-4000-8000-000000000040',
 'ca7a9003-0000-4000-8000-000000000903',
 'International Cooking Exchange',
 'Participants prepare a dish from their home country and share recipes, stories and cooking tips.',
 44.8204, 20.4501, 'Dorćol, Belgrade',
 UNIX_TIMESTAMP('2026-11-05 18:00:00') * 1000, 210, 'FOOD', 'PUBLIC', JSON_ARRAY(),
 30, 1000, NULL, 0, 0, UNIX_TIMESTAMP('2026-10-15 09:00:00') * 1000),

('ca7a0041-0000-4000-8000-000000000041',
 '5eed0001-0000-4000-8000-000000000001',
 'Creative Writing Circle',
 'A small group session focused on short stories, writing prompts and constructive feedback.',
 44.8184, 20.4520, 'Old Town, Belgrade',
 UNIX_TIMESTAMP('2026-11-19 19:00:00') * 1000, 120, 'OTHER', 'PUBLIC', JSON_ARRAY(),
 18, 400, NULL, 0, 0, UNIX_TIMESTAMP('2026-10-28 12:00:00') * 1000),

('ca7a0042-0000-4000-8000-000000000042',
 '5eed0001-0000-4000-8000-000000000005',
 'Christmas Vinyl Night',
 'A cozy evening of classic records, winter-themed music and casual conversation.',
 44.8135, 20.4508, 'Savamala, Belgrade',
 UNIX_TIMESTAMP('2026-12-19 21:00:00') * 1000, 240, 'MUSIC', 'PUBLIC', JSON_ARRAY(),
 130, 900, NULL, 0, 0, UNIX_TIMESTAMP('2026-11-28 10:00:00') * 1000),

-- =========================================================
-- GERMAN
-- =========================================================

('ca7a0043-0000-4000-8000-000000000043',
 'ca7a9004-0000-4000-8000-000000000904',
 'Deutscher Sprachstammtisch',
 'Ein lockeres Treffen für Deutschlernende und Muttersprachler. Wir sprechen über Reisen, Filme, Alltag und Kultur.',
 44.8210, 20.4583, 'Dorćol, Belgrad',
 UNIX_TIMESTAMP('2026-10-07 19:00:00') * 1000, 120, 'SOCIAL', 'PUBLIC', JSON_ARRAY(),
 35, NULL, NULL, 0, 0, UNIX_TIMESTAMP('2026-09-20 11:00:00') * 1000),

('ca7a0044-0000-4000-8000-000000000044',
 'ca7a9004-0000-4000-8000-000000000904',
 'Workshop: Berliner Brot & Brezeln',
 'Gemeinsamer Backworkshop mit traditionellen deutschen Brotsorten und Brezeln.',
 44.8460, 20.4105, 'Zemun, Belgrad',
 UNIX_TIMESTAMP('2026-11-22 12:00:00') * 1000, 180, 'FOOD', 'PUBLIC', JSON_ARRAY(),
 20, 1600, NULL, 0, 0, UNIX_TIMESTAMP('2026-10-30 14:00:00') * 1000),

('ca7a0045-0000-4000-8000-000000000045',
 'ca7a9004-0000-4000-8000-000000000904',
 'Deutschsprachiger Filmabend',
 'Ein Filmabend mit einem deutschsprachigen Independent-Film und anschließender Diskussion.',
 44.8174, 20.4511, 'KC Grad, Belgrad',
 UNIX_TIMESTAMP('2026-12-10 19:30:00') * 1000, 180, 'ART', 'PUBLIC', JSON_ARRAY(),
 80, 600, NULL, 0, 0, UNIX_TIMESTAMP('2026-11-20 12:00:00') * 1000),

('ca7a0046-0000-4000-8000-000000000046',
 'ca7a9004-0000-4000-8000-000000000904',
 'Abendlicher Fotospaziergang',
 'Ein gemeinsamer Fotospaziergang bei Nacht durch Belgrad mit Fokus auf Licht, Architektur und Straßenfotografie.',
 44.8170, 20.4572, 'Knez Mihailova, Belgrad',
 UNIX_TIMESTAMP('2027-01-09 18:00:00') * 1000, 150, 'ART', 'PUBLIC', JSON_ARRAY(),
 25, NULL, NULL, 0, 0, UNIX_TIMESTAMP('2026-12-15 10:00:00') * 1000),

('ca7a0047-0000-4000-8000-000000000047',
 'ca7a9004-0000-4000-8000-000000000904',
 'Winterlauf am Fluss',
 'Ein gemeinsamer lockerer Lauf entlang der Donau. Verschiedene Geschwindigkeitsgruppen sind willkommen.',
 44.8392, 20.4086, 'Zemun Quay, Belgrad',
 UNIX_TIMESTAMP('2027-01-24 10:00:00') * 1000, 100, 'SPORT', 'PUBLIC', JSON_ARRAY(),
 40, NULL, NULL, 0, 0, UNIX_TIMESTAMP('2027-01-05 10:00:00') * 1000),

('ca7a0048-0000-4000-8000-000000000048',
 'ca7a9004-0000-4000-8000-000000000904',
 'Spieleabend auf Deutsch',
 'Ein geselliger Abend mit Brettspielen, Kartenspielen und kleinen Team-Challenges.',
 44.8208, 20.4582, 'Dorćol, Belgrad',
 UNIX_TIMESTAMP('2027-02-05 19:00:00') * 1000, 210, 'SOCIAL', 'PUBLIC', JSON_ARRAY(),
 45, 500, NULL, 0, 0, UNIX_TIMESTAMP('2027-01-15 13:00:00') * 1000),

-- =========================================================
-- RUSSIAN
-- =========================================================

('ca7a0049-0000-4000-8000-000000000049',
 'ca7a9005-0000-4000-8000-000000000905',
 'Русский разговорный клуб',
 'Неформальная встреча для тех, кто изучает русский язык или хочет просто поговорить в дружеской атмосфере.',
 44.8187, 20.4570, 'Студентская площадь, Белград',
 UNIX_TIMESTAMP('2026-10-14 18:30:00') * 1000, 120, 'SOCIAL', 'PUBLIC', JSON_ARRAY(),
 40, NULL, NULL, 0, 0, UNIX_TIMESTAMP('2026-09-22 11:00:00') * 1000),

('ca7a0050-0000-4000-8000-000000000050',
 'ca7a9005-0000-4000-8000-000000000905',
 'Вечер русской поэзии',
 'Чтение стихотворений русских поэтов и свободное обсуждение любимых произведений.',
 44.8200, 20.4500, 'Стари Град, Белград',
 UNIX_TIMESTAMP('2026-11-12 19:00:00') * 1000, 150, 'ART', 'PUBLIC', JSON_ARRAY(),
 50, 400, NULL, 0, 0, UNIX_TIMESTAMP('2026-10-20 13:00:00') * 1000),

('ca7a0051-0000-4000-8000-000000000051',
 'ca7a9005-0000-4000-8000-000000000905',
 'Мастер-класс по пельменям',
 'Совместное приготовление домашних пельменей с разными начинками и знакомство с традиционными рецептами.',
 44.8461, 20.4106, 'Земун, Белград',
 UNIX_TIMESTAMP('2026-12-06 13:00:00') * 1000, 180, 'FOOD', 'PUBLIC', JSON_ARRAY(),
 24, 1400, NULL, 0, 0, UNIX_TIMESTAMP('2026-11-10 12:00:00') * 1000),

('ca7a0052-0000-4000-8000-000000000052',
 'ca7a9005-0000-4000-8000-000000000905',
 'Вечер настольных игр',
 'Дружеская встреча с настольными играми, карточными играми и небольшими командными соревнованиями.',
 44.8211, 20.4580, 'Дорчол, Белград',
 UNIX_TIMESTAMP('2027-01-15 18:00:00') * 1000, 240, 'SOCIAL', 'PUBLIC', JSON_ARRAY(),
 45, 500, NULL, 0, 0, UNIX_TIMESTAMP('2026-12-20 12:00:00') * 1000),

('ca7a0053-0000-4000-8000-000000000053',
 'ca7a9005-0000-4000-8000-000000000905',
 'Лекция о космосе и будущих миссиях',
 'Популярная лекция о современных космических миссиях, новых телескопах и поиске жизни за пределами Земли.',
 44.8071, 20.4774, 'Электротехнический факультет, Белград',
 UNIX_TIMESTAMP('2027-02-19 18:00:00') * 1000, 120, 'TECH', 'PUBLIC', JSON_ARRAY(),
 100, NULL, NULL, 0, 0, UNIX_TIMESTAMP('2027-01-20 10:00:00') * 1000),

('ca7a0054-0000-4000-8000-000000000054',
 'ca7a9005-0000-4000-8000-000000000905',
 'Зимняя прогулка по Кошутняку',
 'Спокойная прогулка по лесу с остановками для фотографий и горячим чаем.',
 44.7574, 20.4285, 'Кошутняк, Белград',
 UNIX_TIMESTAMP('2027-03-07 12:00:00') * 1000, 180, 'OUTDOOR', 'PUBLIC', JSON_ARRAY(),
 30, 300, NULL, 0, 0, UNIX_TIMESTAMP('2027-02-10 11:00:00') * 1000),

-- =========================================================
-- MIXED / MULTILINGUAL
-- =========================================================

('ca7a0055-0000-4000-8000-000000000055',
 'ca7a9003-0000-4000-8000-000000000903',
 'Language Exchange: English / Deutsch / Русский',
 'A multilingual social evening where participants rotate between English, German and Russian conversation tables.',
 44.8179, 20.4520, 'Stari Grad, Belgrade',
 UNIX_TIMESTAMP('2027-02-26 18:00:00') * 1000, 180, 'SOCIAL', 'PUBLIC', JSON_ARRAY(),
 60, 400, NULL, 0, 0, UNIX_TIMESTAMP('2027-01-30 12:00:00') * 1000),

('ca7a0056-0000-4000-8000-000000000056',
 '5eed0001-0000-4000-8000-000000000006',
 'Global Trivia Night',
 'A multilingual trivia competition covering geography, cinema, science, music and unusual facts from around the world.',
 44.8245, 20.4610, 'Dorćol, Belgrade',
 UNIX_TIMESTAMP('2027-03-14 19:00:00') * 1000, 150, 'OTHER', 'PUBLIC', JSON_ARRAY(),
 100, 500, NULL, 0, 0, UNIX_TIMESTAMP('2027-02-15 10:00:00') * 1000);

-- Pregled
SELECT COUNT(*) AS katalog_dogadjaja FROM events WHERE id LIKE 'ca7a%';
SELECT u.display_name, COUNT(*) AS dogadjaja
FROM events e JOIN users u ON u.id = e.owner_id
WHERE e.id LIKE 'ca7a%'
GROUP BY u.display_name ORDER BY dogadjaja DESC;
