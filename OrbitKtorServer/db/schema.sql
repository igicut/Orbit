-- Orbit: kreiranje baze i tabela, pokrenuti pre seed.sql

SET NAMES utf8mb4;

-- Baza: utf8mb4 zbog emoji i srpskih slova
CREATE DATABASE IF NOT EXISTS orbit_database
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE orbit_database;

-- users (db/UserTable.kt): bez lozinke, nema logovanja
CREATE TABLE IF NOT EXISTS users (
    id           VARCHAR(36)  NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    interests    JSON         NOT NULL,

    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- events (db/EventTable.kt): indeksi na kolonama za filtere
CREATE TABLE IF NOT EXISTS events (
    id                   VARCHAR(36)  NOT NULL,
    owner_id             VARCHAR(36)  NOT NULL,
    title                VARCHAR(200) NOT NULL,
    description          TEXT         NOT NULL,

    latitude             DOUBLE       NOT NULL,
    longitude            DOUBLE       NOT NULL,
    address              VARCHAR(300)     NULL,

    start_time           BIGINT       NOT NULL,
    duration_minutes     INT              NULL,

    category             VARCHAR(32)  NOT NULL,
    visibility           VARCHAR(16)  NOT NULL,

    image_uris           JSON         NOT NULL,

    capacity             INT              NULL,
    price                DOUBLE           NULL,
    requires_reservation TINYINT(1)   NOT NULL DEFAULT 0,

    access_code          VARCHAR(8)       NULL,

    avg_rating           FLOAT        NOT NULL DEFAULT 0,
    rating_count         INT          NOT NULL DEFAULT 0,

    created_at           BIGINT       NOT NULL,

    PRIMARY KEY (id),
    KEY events_owner_id    (owner_id),
    KEY events_start_time  (start_time),
    KEY events_category    (category),
    KEY events_visibility  (visibility),
    KEY events_access_code (access_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ratings (db/RatingTable.kt): jedna ocena po osobi, bez FK
CREATE TABLE IF NOT EXISTS ratings (
    id         VARCHAR(36) NOT NULL,
    event_id   VARCHAR(36) NOT NULL,
    user_id    VARCHAR(36) NOT NULL,
    value      INT         NOT NULL,
    comment    TEXT            NULL,
    created_at BIGINT      NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY ratings_event_id_user_id_unique (event_id, user_id),
    KEY ratings_event_id (event_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Opciono: poseban DB korisnik, lozinku ne commit-ovati
-- CREATE USER IF NOT EXISTS 'orbit'@'localhost' IDENTIFIED BY 'change-me';
-- GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, INDEX
--     ON orbit_database.* TO 'orbit'@'localhost';
-- FLUSH PRIVILEGES;

-- RESET: brise SVE podatke, namerno zakomentarisano
-- DROP TABLE IF EXISTS ratings;
-- DROP TABLE IF EXISTS events;
-- DROP TABLE IF EXISTS users;

-- Pregled
SELECT table_name AS created_table
FROM information_schema.tables
WHERE table_schema = 'orbit_database'
ORDER BY table_name;
