-- The notifications module owns the "notifications" schema.
-- customer_id deliberately has no foreign key into customers.customer:
-- modules reference each other's data by id only, never by join or FK.

CREATE SCHEMA IF NOT EXISTS notifications;

CREATE TABLE notifications.notification
(
    id          UUID                     NOT NULL,
    version     BIGINT                   NOT NULL,
    customer_id UUID                     NOT NULL,
    kind        VARCHAR(20)              NOT NULL,
    recipient   VARCHAR(254)             NOT NULL,
    subject     VARCHAR(200)             NOT NULL,
    body        VARCHAR(2000)            NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT notification_pk PRIMARY KEY (id)
);

CREATE INDEX notification_customer_idx ON notifications.notification (customer_id, created_at DESC);

-- Backs up the listener's idempotency check if two deliveries of the same
-- CustomerRegistered event ever race.
CREATE UNIQUE INDEX notification_one_welcome_per_customer_uk
    ON notifications.notification (customer_id) WHERE kind = 'WELCOME';
