-- Spring Modulith's event publication registry (v2 schema), in the
-- application user's own schema. Shaped after Modulith's schema-oracle.sql,
-- with the types its JPA entity maps to: a UUID is RAW(16), an Instant is a
-- TIMESTAMP WITH TIME ZONE. No IF NOT EXISTS: Flyway's Oracle parser reads
-- the IF as a PL/SQL block, and a migration runs once anyway.
CREATE TABLE event_publication
(
    id                     RAW(16)                     NOT NULL,
    listener_id            VARCHAR2(512 CHAR)          NOT NULL,
    event_type             VARCHAR2(512 CHAR)          NOT NULL,
    serialized_event       VARCHAR2(4000)              NOT NULL,
    publication_date       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP(6) WITH TIME ZONE,
    status                 VARCHAR2(20 CHAR),
    completion_attempts    NUMBER(10),
    last_resubmission_date TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT event_publication_pk PRIMARY KEY (id)
);

CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);

CREATE TABLE event_publication_archive
(
    id                     RAW(16)                     NOT NULL,
    listener_id            VARCHAR2(512 CHAR)          NOT NULL,
    event_type             VARCHAR2(512 CHAR)          NOT NULL,
    serialized_event       VARCHAR2(4000)              NOT NULL,
    publication_date       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP(6) WITH TIME ZONE,
    status                 VARCHAR2(20 CHAR),
    completion_attempts    NUMBER(10),
    last_resubmission_date TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT event_publication_archive_pk PRIMARY KEY (id)
);

CREATE INDEX event_publication_archive_by_completion_date_idx
    ON event_publication_archive (completion_date);
