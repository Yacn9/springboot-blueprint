-- The users module's schema is an Oracle user that cannot log in; the
-- application user creates it (see oracle-init/01-privileges.sql).
CREATE USER users NO AUTHENTICATION
    DEFAULT TABLESPACE users QUOTA UNLIMITED ON users;

CREATE TABLE users.user_account
(
    id            RAW(16)                     NOT NULL,
    version       NUMBER(19)                  NOT NULL,
    national_code VARCHAR2(10 CHAR)           NOT NULL,
    first_name    VARCHAR2(100 CHAR)          NOT NULL,
    last_name     VARCHAR2(100 CHAR)          NOT NULL,
    mobile        VARCHAR2(11 CHAR)           NOT NULL,
    email         VARCHAR2(254 CHAR)          NOT NULL,
    tier          VARCHAR2(20 CHAR)           NOT NULL,
    branch        VARCHAR2(100 CHAR)          NOT NULL,
    notes         VARCHAR2(500 CHAR),
    status        VARCHAR2(20 CHAR)           NOT NULL,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT user_pk PRIMARY KEY (id),
    CONSTRAINT user_national_code_uk UNIQUE (national_code)
);

-- One index per sort key, with the id as the tiebreaker (see SortMapping).
CREATE INDEX users.user_created_at_idx ON users.user_account (created_at, id);
CREATE INDEX users.user_last_name_idx ON users.user_account (last_name, id);
