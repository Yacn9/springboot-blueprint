-- The customers module owns the "customers" schema. No other module reads or
-- writes these tables; they reach customer data through CustomerDirectory or
-- the CustomerRegistered event.

CREATE SCHEMA IF NOT EXISTS customers;

CREATE TABLE customers.customer
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
    CONSTRAINT customer_pk PRIMARY KEY (id),
    CONSTRAINT customer_national_code_uk UNIQUE (national_code)
);

-- One index per public sort key (CustomerController.SORTING), each ending in
-- the id tiebreaker.
CREATE INDEX customer_created_at_idx ON customers.customer (created_at, id);
CREATE INDEX customer_last_name_idx ON customers.customer (last_name, id);
