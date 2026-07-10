-- PostgreSQL is the single Source Of Truth for subscription configuration.

CREATE TABLE subscription (
    id               VARCHAR(64)  PRIMARY KEY,
    subscriber_name  VARCHAR(255) NOT NULL,
    topic_postfix    VARCHAR(255) NOT NULL,
    engine           VARCHAR(64)  NOT NULL,
    filter           TEXT,
    status           VARCHAR(32)  NOT NULL,
    failure_reason   VARCHAR(255),
    failure_message  TEXT,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL
);

CREATE TABLE subscription_fields (
    subscription_id  VARCHAR(64)  NOT NULL REFERENCES subscription (id) ON DELETE CASCADE,
    field_name       VARCHAR(255) NOT NULL,
    field_order      INTEGER      NOT NULL,
    PRIMARY KEY (subscription_id, field_order)
);

CREATE INDEX idx_subscription_subscriber ON subscription (subscriber_name);
CREATE INDEX idx_subscription_subscriber_status ON subscription (subscriber_name, status);
CREATE INDEX idx_subscription_subscriber_topic ON subscription (subscriber_name, topic_postfix);
