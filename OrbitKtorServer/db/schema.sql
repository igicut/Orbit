-- Orbit: kreiranje baze i tabela, pokrenuti pre seed.sql

SET NAMES utf8mb4;

-- Baza: utf8mb4 zbog emoji i srpskih slova
CREATE DATABASE IF NOT EXISTS orbit_database
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE orbit_database;

-- users (db/UserTable.kt): javni profil, lozinke su u user_credentials
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
    registered_count     INT          NOT NULL DEFAULT 0,

    access_code          VARCHAR(8)       NULL,

    avg_rating           FLOAT        NOT NULL DEFAULT 0,
    rating_count         INT          NOT NULL DEFAULT 0,

    created_at           BIGINT       NOT NULL,

    -- F-39: otkazan dogadjaj ostaje u tabeli, samo menja status
    status               VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    cancel_reason        VARCHAR(255)     NULL,

    -- F-41: kod iz QR-a na ulazu; nastaje kad ga organizator prvi put otvori
    check_in_code        VARCHAR(8)       NULL,

    PRIMARY KEY (id),
    KEY events_status      (status),
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
    -- F-40: jedna fotografija uz utisak
    image_path VARCHAR(255)    NULL,

    PRIMARY KEY (id),
    UNIQUE KEY ratings_event_id_user_id_unique (event_id, user_id),
    KEY ratings_event_id (event_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- user_credentials (db/CredentialTable.kt): email i bcrypt hash, bez FK
CREATE TABLE IF NOT EXISTS user_credentials (
    user_id       VARCHAR(36)  NOT NULL,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(60)  NOT NULL,
    created_at    BIGINT       NOT NULL,

    PRIMARY KEY (user_id),
    UNIQUE KEY user_credentials_email_unique (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- registrations (db/RegistrationTable.kt): prijave, broj je u events.registered_count
CREATE TABLE IF NOT EXISTS registrations (
    event_id      VARCHAR(36) NOT NULL,
    user_id       VARCHAR(36) NOT NULL,
    registered_at BIGINT      NOT NULL,

    PRIMARY KEY (event_id, user_id),
    KEY registrations_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- attendances (db/AttendanceTable.kt): potvrdjeni dolasci, svaki ima i red u registrations
CREATE TABLE IF NOT EXISTS attendances (
    event_id      VARCHAR(36) NOT NULL,
    user_id       VARCHAR(36) NOT NULL,
    checked_in_at BIGINT      NOT NULL,

    PRIMARY KEY (event_id, user_id),
    KEY attendances_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- blocked_users (db/BlockedUserTable.kt): blokiranja po nalogu
CREATE TABLE IF NOT EXISTS blocked_users (
    blocker_id VARCHAR(36) NOT NULL,
    blocked_id VARCHAR(36) NOT NULL,
    created_at BIGINT      NOT NULL,

    PRIMARY KEY (blocker_id, blocked_id),
    -- Blokiranje vazi u oba smera, pa se trazi i po blokiranom; PK pokriva samo blocker_id
    KEY blocked_users_blocked_id (blocked_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- event_members (db/EventMemberTable.kt): ko je kodom usao u privatni dogadjaj
CREATE TABLE IF NOT EXISTS event_members (
    event_id  VARCHAR(36) NOT NULL,
    user_id   VARCHAR(36) NOT NULL,
    joined_at BIGINT      NOT NULL,

    PRIMARY KEY (event_id, user_id),
    KEY event_members_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- event_embeddings (db/EmbeddingTable.kt): vektor naslova i opisa za semanticku pretragu
-- Odvojeno od events da upiti nad dogadjajima ne vuku 768 brojeva po redu
CREATE TABLE IF NOT EXISTS event_embeddings (
    event_id   VARCHAR(36) NOT NULL,
    vector     JSON        NOT NULL,
    updated_at BIGINT      NOT NULL,

    PRIMARY KEY (event_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Opciono: poseban DB korisnik, lozinku ne commit-ovati
-- CREATE USER IF NOT EXISTS 'orbit'@'localhost' IDENTIFIED BY 'change-me';
-- GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, INDEX
--     ON orbit_database.* TO 'orbit'@'localhost';
-- FLUSH PRIVILEGES;

-- RESET: brise SVE podatke, namerno zakomentarisano
-- DROP TABLE IF EXISTS event_embeddings;
-- DROP TABLE IF EXISTS event_members;
-- DROP TABLE IF EXISTS blocked_users;
-- DROP TABLE IF EXISTS attendances;
-- DROP TABLE IF EXISTS registrations;
-- DROP TABLE IF EXISTS user_credentials;
-- DROP TABLE IF EXISTS ratings;
-- DROP TABLE IF EXISTS events;
-- DROP TABLE IF EXISTS users;

-- Pregled
SELECT table_name AS created_table
FROM information_schema.tables
WHERE table_schema = 'orbit_database'
ORDER BY table_name;
