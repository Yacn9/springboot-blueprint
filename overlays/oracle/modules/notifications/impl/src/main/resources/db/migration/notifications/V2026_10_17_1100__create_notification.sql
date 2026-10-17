-- The notifications module's schema is an Oracle user that cannot log in; the
-- application user creates it (see oracle-init/01-privileges.sql).
CREATE USER notifications NO AUTHENTICATION
    DEFAULT TABLESPACE users QUOTA UNLIMITED ON users;

CREATE TABLE notifications.notification
(
    id          RAW(16)                     NOT NULL,
    version     NUMBER(19)                  NOT NULL,
    user_id RAW(16)                     NOT NULL,
    kind        VARCHAR2(20 CHAR)           NOT NULL,
    recipient   VARCHAR2(254 CHAR)          NOT NULL,
    subject     VARCHAR2(200 CHAR)          NOT NULL,
    body        VARCHAR2(2000 CHAR)         NOT NULL,
    created_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    sent_at     TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT notification_pk PRIMARY KEY (id)
);

CREATE INDEX notifications.notification_user_idx
    ON notifications.notification (user_id, created_at DESC);

-- At most one welcome per user. Oracle has no partial index: rows whose
-- expression is NULL (every other kind) are simply not indexed.
CREATE UNIQUE INDEX notifications.notification_one_welcome_per_user_uk
    ON notifications.notification (CASE WHEN kind = 'WELCOME' THEN user_id END);
