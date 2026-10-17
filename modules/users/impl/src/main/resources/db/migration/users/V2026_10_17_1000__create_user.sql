-- The users module owns the "users" schema. No other module reads or
-- writes these tables; they reach user data through UserDirectory or
-- the UserRegistered event.

CREATE SCHEMA IF NOT EXISTS users;

CREATE TABLE users.user_account
(
    id            UUID                     NOT NULL,
    version       BIGINT                   NOT NULL,
    national_code VARCHAR(10)              NOT NULL,
    first_name    VARCHAR(100)             NOT NULL,
    last_name     VARCHAR(100)             NOT NULL,
    mobile        VARCHAR(11)              NOT NULL,
    email         VARCHAR(254)             NOT NULL,
    tier          VARCHAR(20)              NOT NULL,
    branch        VARCHAR(100)             NOT NULL,
    notes         VARCHAR(500),
    status        VARCHAR(20)              NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT user_pk PRIMARY KEY (id),
    CONSTRAINT user_national_code_uk UNIQUE (national_code)
);

-- One index per public sort key (UserController.SORTING), each ending in
-- the id tiebreaker.
CREATE INDEX user_created_at_idx ON users.user_account (created_at, id);
CREATE INDEX user_last_name_idx ON users.user_account (last_name, id);
